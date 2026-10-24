import { http } from './request';

/** 用户在微信支付密码弹层点了取消/关闭（非失败，不弹错误提示） */
export const PAY_CANCEL = 'CANCEL';
/** 支付被确认失败：渠道下单失败、调起失败、支付单 FAIL/CLOSED/REFUND */
export const PAY_FAIL = 'CONFIRM_FAIL';
/** 调起成功但轮询窗口内未等到后端 SUCCESS（可能正在回调，需稍后自查） */
export const PAY_PENDING = 'PENDING_TIMEOUT';

function payError(code, message) {
  return Object.assign(new Error(message), { code });
}

/**
 * 轮询支付单终态。以前端 requestPayment 成功回调为准不可靠（存在"前端成功、回调未到"
 * 与用户声称已扣款），一律以后端 GET /api/payment/user/query/{tradeNo} 的 status 为准。
 * 2s 一次、最多 90 次（3 分钟，对齐 H5 收银台窗口）；单次网络抖动不判失败。
 * 注意：定时器不绑定页面生命周期，页面提前销毁后请求仍会跑到上限自停，无页面数据读写。
 */
function waitPaymentConfirmed(tradeNo) {
  const MAX_TIMES = 90;
  let times = 0;
  return new Promise((resolve, reject) => {
    const timer = setInterval(() => {
      times += 1;
      http.get('/api/payment/user/query/' + encodeURIComponent(tradeNo), null, { silent: true })
        .then(res => {
          const status = res && res.status;
          if (status === 'SUCCESS') {
            clearInterval(timer);
            resolve();
          } else if (status === 'FAIL' || status === 'CLOSED' || status === 'REFUND') {
            clearInterval(timer);
            reject(payError(PAY_FAIL, '支付失败，如已扣款请稍后在订单详情查看'));
          } else if (times >= MAX_TIMES) {
            clearInterval(timer);
            reject(payError(PAY_PENDING, '支付结果确认超时'));
          }
        })
        .catch(() => {
          if (times >= MAX_TIMES) {
            clearInterval(timer);
            reject(payError(PAY_PENDING, '支付结果确认超时'));
          }
        });
    }, 2000);
  });
}

/** 调起微信支付；取消与失败分流，errMsg 含 cancel 即用户主动放弃 */
function invokeWxPay(jsapiParams) {
  return new Promise((resolve, reject) => {
    uni.requestPayment({
      provider: 'wxpay',
      timeStamp: jsapiParams.timeStamp,
      nonceStr: jsapiParams.nonceStr,
      package: jsapiParams.package,
      signType: jsapiParams.signType,
      paySign: jsapiParams.paySign,
      success: resolve,
      fail: err => {
        const raw = (err && err.errMsg) || '';
        if (raw.indexOf('cancel') !== -1) {
          reject(payError(PAY_CANCEL, '支付已取消'));
        } else {
          reject(payError(PAY_FAIL, '微信支付调起失败'));
        }
      }
    });
  });
}

/**
 * 小程序微信 JSAPI 支付闭环（顾客端结算页/订单详情共用）：
 * 创建支付单 → wx.requestPayment → 轮询后端支付单 SUCCESS。
 *
 * @param {number|string} orderId 业务订单ID（/order/submit 返回的 id）
 * @param {number|string} amount 订单应付金额（元），仅 mock 沙箱模拟回调计分数用，真实支付金额以后端订单为准
 * @returns {Promise<{zeroAmount?: boolean, mock?: boolean, paid?: boolean}>}
 *          0 元单（券抵完）后端已自动完成支付，resolve zeroAmount；沙箱 resolve mock；真机 resolve paid
 * @rejects Error（message 可直接展示），err.code 取 PAY_CANCEL / PAY_FAIL / PAY_PENDING；
 *          无 code 为后端业务拒绝（如"当前账号未绑定微信…"、未配置支付渠道），同样展示 message
 */
export function payOrderByWechat(orderId, amount) {
  uni.showLoading({ title: '正在发起支付', mask: true });
  return http.post('/api/payment/pay', {
    orderId: orderId,
    channel: 'WECHAT',
    payType: 'JSAPI'
  }, { silent: true }).then(data => {
    // 渠道层失败：HTTP/R 均成功但 data.success=false（如租户未配置微信支付渠道、JSAPI 下单异常）
    if (!data || data.success === false) {
      throw payError(PAY_FAIL, (data && data.errorMsg) || '支付下单失败，请稍后重试');
    }
    // 0 元单（券抵扣后实付为 0）：后端已同步完成支付，不调渠道、无支付单号
    if (!data.tradeNo) {
      uni.hideLoading();
      return { zeroAmount: true };
    }
    // mock 沙箱：mock 渠道不产 JSAPI 调起参数，按 H5 收银台同契约打模拟回调（金额分）完成支付
    if (data.mockMode && !(data.jsapiParams && data.jsapiParams.paySign)) {
      return http.post('/api/payment/notify/WECHAT', {
        out_trade_no: data.tradeNo,
        total_fee: Math.round(Number(amount || 0) * 100)
      }, { silent: true }).then(() => waitPaymentConfirmed(data.tradeNo)).then(() => {
        uni.hideLoading();
        return { mock: true };
      });
    }
    const params = data.jsapiParams;
    if (!params || !params.paySign) {
      throw payError(PAY_FAIL, '支付参数缺失，请稍后重试');
    }
    // 系统支付密码弹层前必须收起 loading
    uni.hideLoading();
    return invokeWxPay(params).then(() => {
      uni.showLoading({ title: '支付结果确认中', mask: true });
      return waitPaymentConfirmed(data.tradeNo);
    }).then(() => {
      uni.hideLoading();
      return { paid: true };
    });
  }).catch(err => {
    uni.hideLoading();
    throw err;
  });
}

/**
 * 轮询充值单终态。会员充值走独立控制器（/api/payment/recharge/**），
 * 成功入账由支付回调按 bizType=RECHARGE 联动，前端只认充值单 SUCCESS。
 * 窗口/抖动口径与订单支付一致（2s × 90 = 3 分钟）。
 */
function waitRechargeConfirmed(rechargeNo) {
  const MAX_TIMES = 90;
  let times = 0;
  return new Promise((resolve, reject) => {
    const timer = setInterval(() => {
      times += 1;
      http.get('/api/member/portal/recharge/status/' + encodeURIComponent(rechargeNo), null, { silent: true })
        .then(res => {
          const status = res && res.status;
          if (status === 'SUCCESS') {
            clearInterval(timer);
            resolve();
          } else if (status === 'CANCELLED') {
            clearInterval(timer);
            reject(payError(PAY_FAIL, '充值失败，如已扣款请联系客服'));
          } else if (times >= MAX_TIMES) {
            clearInterval(timer);
            reject(payError(PAY_PENDING, '充值结果确认超时'));
          }
        })
        .catch(() => {
          if (times >= MAX_TIMES) {
            clearInterval(timer);
            reject(payError(PAY_PENDING, '充值结果确认超时'));
          }
        });
    }, 2000);
  });
}

/**
 * 小程序微信 JSAPI 会员储值充值闭环（会员中心用）：
 * 创建充值单后调用 → 充值支付单 → wx.requestPayment → 轮询充值单 SUCCESS（余额已入账）。
 *
 * @param {string} rechargeNo /api/member/portal/recharge/create 返回的充值单号
 * @param {number|string} amount 充值金额（元），仅 mock 沙箱模拟回调计分数用
 * @returns {Promise<{mock?: boolean, paid?: boolean}>}
 * @rejects 错误口径同 payOrderByWechat（PAY_CANCEL / PAY_FAIL / PAY_PENDING / 后端业务拒绝）
 */
export function payRechargeByWechat(rechargeNo, amount) {
  uni.showLoading({ title: '正在发起支付', mask: true });
  return http.post('/api/payment/recharge/pay', {
    rechargeNo: rechargeNo,
    channel: 'WECHAT',
    payType: 'JSAPI'
  }, { silent: true }).then(data => {
    if (!data || data.success === false) {
      throw payError(PAY_FAIL, (data && data.errorMsg) || '充值下单失败，请稍后重试');
    }
    // mock 沙箱：与订单支付同契约打模拟回调，RECHARGE 支付单成功后回调联动充值入账
    if (data.mockMode && !(data.jsapiParams && data.jsapiParams.paySign)) {
      return http.post('/api/payment/notify/WECHAT', {
        out_trade_no: data.tradeNo,
        total_fee: Math.round(Number(amount || 0) * 100)
      }, { silent: true }).then(() => waitRechargeConfirmed(rechargeNo)).then(() => {
        uni.hideLoading();
        return { mock: true };
      });
    }
    const params = data.jsapiParams;
    if (!params || !params.paySign) {
      throw payError(PAY_FAIL, '支付参数缺失，请稍后重试');
    }
    uni.hideLoading();
    return invokeWxPay(params).then(() => {
      uni.showLoading({ title: '支付结果确认中', mask: true });
      return waitRechargeConfirmed(rechargeNo);
    }).then(() => {
      uni.hideLoading();
      return { paid: true };
    });
  }).catch(err => {
    uni.hideLoading();
    throw err;
  });
}
