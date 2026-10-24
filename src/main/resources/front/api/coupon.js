// 优惠券 API 模块（C端 /front/coupon 系列）
// 注意：函数名统一加 Front 后缀，避免与 member-center.js 同名函数（/api/member/coupon-* 系列）冲突

// 获取下单可用优惠券列表
function getUsableCoupons(orderAmount) {
  return $axios({
    url: '/front/coupon/usable',
    method: 'get',
    params: { orderAmount: orderAmount }
  })
}

// 获取我的优惠券列表
function getMyCouponsFront() {
  return $axios({
    url: '/front/coupon/my',
    method: 'get'
  })
}

// 获取可领取优惠券列表
function getAvailableCouponsFront() {
  return $axios({
    url: '/front/coupon/available',
    method: 'get'
  })
}

// 领取优惠券
function claimCouponFront(templateId) {
  return $axios({
    url: '/front/coupon/claim/' + templateId,
    method: 'post'
  })
}

// 积分兑换优惠券（P1-5）
function exchangeCouponFront(templateId) {
  return $axios({
    url: '/front/coupon/exchange/' + templateId,
    method: 'post'
  })
}

// ── 付费券包（O-18，2026-10-24）：多张券打包售卖，支付成功自动拆包发券 ──

// 上架券包列表（匿名可看，购买需登录）
function getCouponPackagesFront() {
  return $axios({
    url: '/api/coupon-package/list',
    method: 'get'
  })
}

// 创建购买单（幂等：同用户同包复用待支付单），返回 {orderNo, price, ...}
function buyCouponPackageFront(packageId) {
  return $axios({
    url: '/api/coupon-package/buy',
    method: 'post',
    params: { packageId: packageId }
  })
}

// 发起券包支付（H5 走 WECHAT 渠道默认 NATIVE 扫码；mock 沙箱返回 mockMode+tradeNo）
function payCouponPackageFront(orderNo) {
  return $axios({
    url: '/api/payment/coupon-package/pay',
    method: 'post',
    data: { orderNo: orderNo, channel: 'WECHAT' }
  })
}
