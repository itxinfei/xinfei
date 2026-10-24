<template>
  <view class="detail">
    <view v-if="loading" class="tip"><text>加载中…</text></view>
    <view v-else-if="notFound" class="tip"><text>未找到该订单</text></view>
    <view v-else-if="loadError" class="tip">
      <text>加载失败，请稍后重试</text>
      <text class="tip__btn" hover-class="hover-press" @tap="loadDetail">重新加载</text>
    </view>

    <template v-else>
      <!-- 状态横幅 -->
      <view class="hero card">
        <text class="hero__title">{{ heroTitle }}</text>
        <text v-if="isDeliveringLive" class="hero__eta">
          预计 <text class="hero__eta-b">{{ order.expectDeliveryTime || '尽快' }}</text> 送达
        </text>
        <text v-else-if="statusDesc" class="hero__desc">{{ statusDesc }}</text>
      </view>

      <!-- 配送快捷入口 -->
      <view v-if="isDeliveringLive && canViewTracking" class="entry card" hover-class="hover-press" @tap="viewTracking">
        <text class="entry__text">查看骑手实时位置</text>
        <text class="entry__arrow">›</text>
      </view>

      <!-- 收货 / 堂食 / 自提信息 -->
      <view class="card">
        <view v-if="isSelfPickup" class="row row--pickup">
          <text class="row__label">取餐码</text>
          <text class="pickup-code">{{ order.pickupCode || '—' }}</text>
        </view>
        <view v-if="isSelfPickup" class="row">
          <text class="row__label">取餐方式</text>
          <text class="row__value">到店自提 · 向店员出示取餐码</text>
        </view>
        <view v-else-if="isEatIn" class="row">
          <text class="row__label">就餐桌台</text>
          <text class="row__value">{{ order.tableName }} · 堂食点餐</text>
        </view>
        <template v-else>
          <view class="row">
            <text class="row__label">收货人</text>
            <text class="row__value">{{ order.consignee }}　{{ order.phone }}</text>
            <text v-if="order.phone" class="row__mini" hover-class="hover-press" @tap="callConsignee">拨打</text>
          </view>
          <view class="row row--top">
            <text class="row__label">收货地址</text>
            <text class="row__value">{{ order.address }}</text>
            <text class="row__mini" hover-class="hover-press" @tap="copyAddress">复制</text>
          </view>
        </template>
      </view>

      <!-- 商品清单 -->
      <view class="card">
        <view class="section-title">商品清单</view>
        <view v-for="(d, i) in details" :key="i" class="goods">
          <image v-if="d.image" class="goods__img" :src="fullImage(d.image)" mode="aspectFill" lazy-load />
          <view v-else class="goods__img goods__img--ph"></view>
          <view class="goods__info">
            <text class="goods__name">{{ d.name }}</text>
            <text v-if="d.dishFlavor" class="goods__flavor">{{ d.dishFlavor }}</text>
          </view>
          <text class="goods__qty">×{{ d.number }}</text>
          <text class="goods__price">¥{{ fmtAmount(d.amount) }}</text>
        </view>
      </view>

      <!-- 价格明细（不做 H5 的打包费残差倒推：后端无打包费字段，残差口径不可靠） -->
      <view class="card">
        <view class="section-title">价格明细</view>
        <view class="row">
          <text class="row__label">商品小计</text>
          <text class="row__value row__value--right">¥{{ fmtAmount(goodsAmount) }}</text>
        </view>
        <view v-if="deliveryFee > 0" class="row">
          <text class="row__label">配送费</text>
          <text class="row__value row__value--right">¥{{ fmtAmount(order.deliveryFee) }}</text>
        </view>
        <view v-if="discount > 0" class="row">
          <text class="row__label">优惠</text>
          <text class="row__value row__value--right row__value--off">-¥{{ fmtAmount(discount) }}</text>
        </view>
        <view class="row row--pay">
          <text class="row__label">实付款</text>
          <text class="pay-value">¥{{ fmtAmount(order.amount) }}</text>
        </view>
      </view>

      <!-- 订单信息 -->
      <view class="card">
        <view class="section-title">订单信息</view>
        <view class="row">
          <text class="row__label">订单号</text>
          <text class="row__value">{{ order.number || order.id }}</text>
          <text class="row__mini" hover-class="hover-press" @tap="copyOrderNo">复制</text>
        </view>
        <view class="row">
          <text class="row__label">下单时间</text>
          <text class="row__value">{{ fmtTime(order.orderTime) }}</text>
        </view>
        <view class="row">
          <text class="row__label">支付方式</text>
          <text class="row__value">{{ payMethodText }}</text>
        </view>
        <view v-if="!isEatIn && order.expectDeliveryTime" class="row">
          <text class="row__label">期望送达</text>
          <text class="row__value">{{ order.expectDeliveryTime }}</text>
        </view>
        <view v-if="order.remark" class="row row--top">
          <text class="row__label">备注</text>
          <text class="row__value">{{ order.remark }}</text>
        </view>
      </view>

      <!-- 取消 / 退款原因回执 -->
      <view v-if="order.cancelReason && (status === 5 || status === 6)" class="card">
        <view class="section-title">{{ status === 5 ? '取消原因' : '退款原因' }}</view>
        <text class="reason">{{ order.cancelReason }}</text>
      </view>

      <!-- 售后记录 -->
      <view v-if="refunds.length" class="card">
        <view class="section-title">售后记录</view>
        <view v-for="r in refunds" :key="r.id" class="refund">
          <view class="refund__head">
            <text class="refund__status">{{ refundText(r.status) }}</text>
            <text class="refund__amount">¥{{ fmtAmount(r.amount) }}</text>
          </view>
          <text v-if="r.reason" class="refund__reason">申请原因：{{ r.reason }}</text>
          <text v-if="r.status === 'rejected' && r.rejectReason" class="refund__reason">拒绝原因：{{ r.rejectReason }}</text>
          <text class="refund__time">{{ fmtTime(r.createdTime) }}</text>
        </view>
      </view>

      <view class="bar-placeholder"></view>

      <!-- 底部操作栏：按订单状态（评价/发票/客服目标页在小程序尚不存在，不放入口） -->
      <view class="bar">
        <template v-if="status === 1">
          <text class="btn" :class="{ 'btn--off': busy('cancel') }" hover-class="hover-press" @tap="cancel">取消订单</text>
          <text class="btn btn--main" :class="{ 'btn--off': busy('pay') }" hover-class="hover-press" @tap="payOrder">{{ busy('pay') ? '支付中…' : '去支付' }}</text>
        </template>
        <template v-else-if="status === 2">
          <text class="btn" :class="{ 'btn--off': busy('urge') }" hover-class="hover-press" @tap="urge">{{ isEatIn ? '催菜' : '催单' }}</text>
          <text v-if="canViewTracking" class="btn" hover-class="hover-press" @tap="viewTracking">查看配送</text>
        </template>
        <template v-else-if="status === 3">
          <text class="btn" :class="{ 'btn--off': busy('urge') }" hover-class="hover-press" @tap="urge">{{ isEatIn ? '催菜' : '催单' }}</text>
          <text v-if="canViewTracking" class="btn" hover-class="hover-press" @tap="viewTracking">查看配送</text>
          <!-- 堂食由门店上菜完成、自提到店取餐，顾客均无确认收货入口 -->
          <text v-if="!isEatIn && !isSelfPickup" class="btn btn--main" :class="{ 'btn--off': busy('receipt') }" hover-class="hover-press" @tap="confirmReceipt">确认收货</text>
        </template>
        <template v-else-if="status === 4">
          <text v-if="canEvaluateDish" class="btn btn--main" hover-class="hover-press" @tap="goEvaluate">去评价</text>
          <text v-if="canEvaluateRider" class="btn" hover-class="hover-press" @tap="goEvaluate">评价骑手</text>
          <text class="btn" hover-class="hover-press" @tap="openRefund">申请售后</text>
          <text v-if="canViewTracking" class="btn" hover-class="hover-press" @tap="viewTracking">查看配送</text>
          <text class="btn" :class="{ 'btn--off': busy('again') }" hover-class="hover-press" @tap="again">再来一单</text>
        </template>
        <template v-else-if="status === 5 || status === 6">
          <text class="btn btn--main" :class="{ 'btn--off': busy('again') }" hover-class="hover-press" @tap="again">再来一单</text>
        </template>
      </view>
    </template>

    <!-- 申请售后退款弹层：uni.showModal 不支持输入框，自建（对齐 H5 自由文本原因，≤200 字） -->
    <view v-if="refundVisible" class="modal-mask" @tap="closeRefund">
      <view class="modal" @tap.stop="">
        <text class="modal__title">申请售后退款</text>
        <text class="modal__tip">请填写退款原因（如商品质量问题、漏送、错送等），提交后客服将尽快处理。</text>
        <textarea
          v-model="refundReason"
          class="modal__input"
          maxlength="200"
          placeholder="请输入退款原因"
          placeholder-class="modal__ph"
        />
        <text class="modal__count">{{ refundReason.length }}/200</text>
        <text v-if="refundError" class="modal__error">{{ refundError }}</text>
        <view class="modal__ops">
          <text class="btn" hover-class="hover-press" @tap="closeRefund">取消</text>
          <text class="btn btn--main" :class="{ 'btn--off': busy('refundSubmit') }" hover-class="hover-press" @tap="submitRefund">提交申请</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import {
  http, qs, orderText, refundText, fullImage, busyLock, fmtAmount,
  payOrderByWechat, PAY_CANCEL, PAY_PENDING
} from '@xinfei/mini-shared'

const PAY_METHOD_TEXT = {
  1: '现金支付',
  2: '微信支付',
  3: '支付宝支付',
  4: '银行卡支付',
  5: '会员储值支付',
  6: '货到付款'
}

function pad2(n) {
  return n < 10 ? '0' + n : '' + n
}

// 兼容 Jackson 两种序列化：字符串 "2026-10-22 12:00:00" / 数组 [2026,10,22,12,0]
function fmtTime(t) {
  if (t == null || t === '') return ''
  if (Array.isArray(t)) {
    return t[0] + '-' + pad2(t[1]) + '-' + pad2(t[2]) + ' ' + pad2(t[3] || 0) + ':' + pad2(t[4] || 0)
  }
  return String(t).replace('T', ' ').slice(0, 16)
}

export default {
  data() {
    return Object.assign(busyLock(), {
      orderId: '',
      order: {},
      refunds: [],
      loading: true,
      loadError: false,
      notFound: false,
      refundVisible: false,
      refundReason: '',
      refundError: '',
      // 评价入口判定：dishEvalKeys 存已评商品 key（d_<dishId>/s_<setmealId>），riderEval 非空=骑手已评
      dishEvalKeys: {},
      riderEval: null,
      evalLoaded: false
    })
  },
  computed: {
    status() {
      return this.order.status || 0
    },
    details() {
      return this.order.orderDetails || []
    },
    isEatIn() {
      return !!this.order.tableId
    },
    isSelfPickup() {
      return this.order.source === 'SELF_PICKUP'
    },
    // 外卖单待接单/配送中：横幅显示预计送达而非静态描述（对齐 H5）
    isDeliveringLive() {
      return !this.isEatIn && !this.isSelfPickup && (this.status === 2 || this.status === 3)
    },
    canViewTracking() {
      const o = this.order
      // 平台单走平台单号追踪；本地单需有收货地址、非堂食且状态在 待接单/配送中/已完成
      return !!(o.platformOrderId ||
        (o.addressBookId != null && o.tableId == null && [2, 3, 4].indexOf(this.status) !== -1))
    },
    heroTitle() {
      if (this.isEatIn && this.status === 3) return '制作中'
      return orderText(this.status)
    },
    statusDesc() {
      if (this.isEatIn && this.status === 3) return '餐品制作中，请耐心等待'
      const map = {
        1: '请尽快完成支付',
        2: '商家正在确认订单',
        4: '订单已完成，感谢惠顾',
        5: '订单已取消',
        6: '订单已退款'
      }
      return map[this.status] || ''
    },
    goodsAmount() {
      return this.details.reduce((sum, d) => sum + Number(d.amount || 0), 0)
    },
    deliveryFee() {
      return Number(this.order.deliveryFee || 0)
    },
    discount() {
      return Number(this.order.fullReductionAmount || 0) + Number(this.order.newCustomerDiscountAmount || 0)
    },
    payMethodText() {
      return PAY_METHOD_TEXT[this.order.payMethod] || '—'
    },
    // 已完成订单且存在未评商品时显示「去评价」；同菜多风味只算一个商品（与评价页合并口径一致）
    canEvaluateDish() {
      if (this.status !== 4) return false
      const keys = {}
      this.details.forEach(d => {
        if (d.dishId) keys['d_' + d.dishId] = true
        else if (d.setmealId) keys['s_' + d.setmealId] = true
      })
      return Object.keys(keys).some(k => !this.dishEvalKeys[k])
    },
    // 自营骑手配送单且尚未评价骑手；堂食/自提/平台单无 riderId 不显示
    canEvaluateRider() {
      return this.status === 4 && !!this.order.riderId && !this.riderEval
    }
  },
  onLoad(query) {
    this.orderId = query && query.id ? String(query.id) : ''
    this.loadDetail()
  },
  onShow() {
    // 从评价页 navigateBack 后刷新入口显隐；首屏 onShow 时评价数据尚未加载，跳过避免重复请求
    if (this.orderId && this.evalLoaded) this.loadDetail()
  },
  methods: {
    fullImage,
    fmtAmount,
    refundText,
    fmtTime,
    loadDetail() {
      if (!this.orderId) {
        this.loading = false
        this.notFound = true
        return Promise.resolve()
      }
      this.loading = true
      this.loadError = false
      this.notFound = false
      return http.get('/order/' + encodeURIComponent(this.orderId), null, { silent: true }).then(data => {
        this.order = data || {}
        this.loading = false
        this.loadRefunds()
        this.loadEvalStatus()
      }).catch(err => {
        this.loading = false
        if (err && err.notLogin) return
        // 后端详情失败文案：订单不存在 / 不属于当前租户 / 无权操作，统一按"未找到"空态
        const msg = (err && err.message) || ''
        if (msg.indexOf('不存在') !== -1 || msg.indexOf('无权') !== -1 || msg.indexOf('租户') !== -1) {
          this.notFound = true
        } else {
          this.loadError = true
        }
      })
    },
    loadRefunds() {
      http.get(qs('/order/userRefundRecords', { id: this.orderId }), null, { silent: true })
        .then(list => { this.refunds = Array.isArray(list) ? list : [] })
        .catch(() => {})
    },
    // 评价入口数据：两查询各自失败不阻塞详情页（失败时保守显示入口，评价页内还有一次查重）
    loadEvalStatus() {
      this.dishEvalKeys = {}
      this.riderEval = null
      this.evalLoaded = false
      const dishReq = http.get('/api/dish-evaluation/order/' + encodeURIComponent(this.orderId), null, { silent: true })
        .then(list => {
          (list || []).forEach(ev => {
            const k = ev.dishId ? 'd_' + ev.dishId : 's_' + ev.setmealId
            this.dishEvalKeys[k] = true
          })
        }).catch(() => {})
      const riderReq = this.order.riderId
        ? http.get(qs('/api/rider-evaluation/order/' + encodeURIComponent(this.orderId), { riderId: this.order.riderId }), null, { silent: true })
          .then(ev => { this.riderEval = ev || null }).catch(() => {})
        : Promise.resolve()
      Promise.all([dishReq, riderReq]).then(() => { this.evalLoaded = true })
    },
    goEvaluate() {
      uni.navigateTo({ url: '/pages/order/evaluate?id=' + encodeURIComponent(this.orderId) })
    },
    urge() {
      const key = 'urge'
      if (this.busy(key)) return
      this.setBusy(key, true)
      http.post('/api/urgency/customer/trigger/' + encodeURIComponent(this.orderId), {}, { silent: true })
        .then(d => {
          const data = d || {}
          if (data.remainCount !== undefined && data.remainCount !== null) {
            uni.showToast({ title: '已通知商家，今日还可催' + data.remainCount + '次', icon: 'none' })
          } else {
            uni.showToast({ title: '已通知商家，请耐心等待', icon: 'none' })
          }
        })
        .catch(err => {
          if (err && err.notLogin) return
          uni.showToast({ title: (err && err.message) || '催单失败，请稍后重试', icon: 'none' })
        })
        .then(() => { this.setBusy(key, false) })
    },
    cancel() {
      uni.showModal({
        title: '取消订单',
        content: '确定要取消该订单吗？',
        confirmText: '确定',
        cancelText: '再看看',
        success: res => {
          if (!res.confirm) return
          const key = 'cancel'
          if (this.busy(key)) return
          this.setBusy(key, true)
          // /order/userCancel 是 @RequestParam，必须拼 query
          http.put(qs('/order/userCancel', { id: this.orderId }))
            .then(() => {
              uni.showToast({ title: '订单已取消', icon: 'none' })
              this.loadDetail()
            })
            .catch(() => {})
            .then(() => { this.setBusy(key, false) })
        }
      })
    },
    // 待支付订单重新发起微信 JSAPI 支付；成功/终态以后端支付单回查为准（见 shared/pay.js）
    payOrder() {
      const key = 'pay'
      if (this.busy(key)) return
      this.setBusy(key, true)
      payOrderByWechat(this.orderId, this.order.amount).then(() => {
        uni.showToast({ title: '支付成功', icon: 'success' })
        this.loadDetail()
      }).catch(err => {
        if (err && err.code === PAY_CANCEL) {
          uni.showToast({ title: '支付已取消', icon: 'none' })
          return
        }
        if (err && err.code === PAY_PENDING) {
          uni.showModal({
            title: '支付结果确认中',
            content: '如已扣款，请稍后在订单详情查看支付结果',
            showCancel: false,
            success: () => this.loadDetail()
          })
          return
        }
        // PAY_FAIL 或后端业务拒绝（未绑微信/未配置渠道）：展示原文案，订单仍可再次发起
        uni.showToast({ title: (err && err.message) || '支付失败，请稍后重试', icon: 'none' })
        this.loadDetail()
      }).then(() => { this.setBusy(key, false) })
    },
    confirmReceipt() {
      uni.showModal({
        title: '确认收货',
        content: '确认已收到商品？确认后订单将完成。',
        confirmText: '确认收货',
        cancelText: '再等等',
        success: res => {
          if (!res.confirm) return
          const key = 'receipt'
          if (this.busy(key)) return
          this.setBusy(key, true)
          http.put(qs('/order/userConfirmReceipt', { id: this.orderId }))
            .then(() => {
              uni.showToast({ title: '已确认收货', icon: 'none' })
              this.loadDetail()
            })
            .catch(() => {})
            .then(() => { this.setBusy(key, false) })
        }
      })
    },
    again() {
      const key = 'again'
      if (this.busy(key)) return
      this.setBusy(key, true)
      http.post('/order/again', { id: this.orderId })
        .then(() => { uni.showToast({ title: '已加入购物车', icon: 'none' }) })
        .catch(() => {})
        .then(() => { this.setBusy(key, false) })
    },
    viewTracking() {
      // 平台单传平台单号（tracking 端点先按平台单号查），本地单传订单 ID
      const target = this.order.platformOrderId || this.orderId
      uni.navigateTo({ url: '/pages/delivery/tracking?id=' + encodeURIComponent(target) })
    },
    callConsignee() {
      if (!this.order.phone) return
      uni.makePhoneCall({ phoneNumber: String(this.order.phone) })
    },
    copyAddress() {
      uni.setClipboardData({ data: this.order.address || '' })
    },
    copyOrderNo() {
      uni.setClipboardData({ data: this.order.number || String(this.order.id) })
    },
    openRefund() {
      this.refundReason = ''
      this.refundError = ''
      this.refundVisible = true
    },
    closeRefund() {
      this.refundVisible = false
    },
    submitRefund() {
      const reason = this.refundReason ? this.refundReason.trim() : ''
      if (!reason) {
        this.refundError = '退款原因不能为空'
        return
      }
      if (reason.length > 200) {
        this.refundError = '退款原因不超过 200 字'
        return
      }
      this.refundError = ''
      const key = 'refundSubmit'
      if (this.busy(key)) return
      this.setBusy(key, true)
      // userApplyRefund 的 id、reason 都是 @RequestParam，走 query；接口有限流 3 次/秒
      http.post(qs('/order/userApplyRefund', { id: this.orderId, reason }))
        .then(d => {
          const refundNo = d && d.refundNo ? '：' + d.refundNo : ''
          uni.showToast({ title: '售后申请已提交' + refundNo, icon: 'none' })
          this.refundVisible = false
          this.loadDetail()
          this.loadRefunds()
        })
        .catch(() => {})
        .then(() => { this.setBusy(key, false) })
    }
  }
}
</script>

<style>
.detail {
  min-height: 100%;
  padding: 8rpx 0 40rpx;
  background: var(--bg-page);
}

.card {
  margin: 16rpx 24rpx;
  padding: 24rpx;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
  box-shadow: var(--shadow-card);
}

.tip {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 200rpx 0;
  font-size: 28rpx;
  color: var(--color-text-hint);
}

.tip__btn {
  margin-top: 24rpx;
  padding: 12rpx 40rpx;
  border-radius: 32rpx;
  font-size: 26rpx;
  color: var(--color-primary-text);
  background: var(--color-primary);
}

.hero {
  display: flex;
  flex-direction: column;
}

.hero__title {
  font-size: 38rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

.hero__desc {
  margin-top: 12rpx;
  font-size: 26rpx;
  color: var(--color-text-hint);
}

.hero__eta {
  margin-top: 12rpx;
  font-size: 26rpx;
  color: var(--color-text-secondary);
}

.hero__eta-b {
  color: var(--color-primary-text-strong);
  font-weight: 600;
}

.entry {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.entry__text {
  font-size: 28rpx;
  color: var(--color-primary-text-strong);
}

.entry__arrow {
  font-size: 36rpx;
  color: var(--color-text-hint);
}

.section-title {
  margin-bottom: 12rpx;
  font-size: 30rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

.row {
  display: flex;
  align-items: center;
  padding: 12rpx 0;
}

.row--top {
  align-items: flex-start;
}

.row__label {
  width: 140rpx;
  flex-shrink: 0;
  font-size: 26rpx;
  color: var(--color-text-hint);
}

.row__value {
  flex: 1;
  font-size: 28rpx;
  color: var(--color-text-primary);
  word-break: break-all;
}

.row__value--right {
  text-align: right;
}

.row__value--off {
  color: var(--color-price);
}

.row__mini {
  flex-shrink: 0;
  margin-left: 16rpx;
  padding: 6rpx 20rpx;
  border-radius: 28rpx;
  font-size: 24rpx;
  color: var(--color-primary-text-strong);
  background: var(--color-primary-tint);
}

.pickup-code {
  font-size: 40rpx;
  font-weight: 700;
  letter-spacing: 6rpx;
  color: var(--color-success);
}

.goods {
  display: flex;
  align-items: center;
  margin-top: 16rpx;
}

.goods__img {
  width: 88rpx;
  height: 88rpx;
  flex-shrink: 0;
  border-radius: var(--radius-sm);
  background: var(--bg-input);
}

.goods__img--ph {
  background: var(--bg-input);
}

.goods__info {
  flex: 1;
  margin-left: 16rpx;
  display: flex;
  flex-direction: column;
}

.goods__name {
  font-size: 28rpx;
  color: var(--color-text-primary);
}

.goods__flavor {
  margin-top: 6rpx;
  font-size: 24rpx;
  color: var(--color-text-hint);
}

.goods__qty {
  margin: 0 20rpx;
  font-size: 26rpx;
  color: var(--color-text-hint);
}

.goods__price {
  width: 120rpx;
  text-align: right;
  font-size: 28rpx;
  color: var(--color-text-primary);
}

.row--pay {
  margin-top: 8rpx;
  padding-top: 20rpx;
  border-top: 1rpx solid var(--border-light);
}

.pay-value {
  flex: 1;
  text-align: right;
  font-size: 32rpx;
  font-weight: 600;
  color: var(--color-price);
}

.reason {
  font-size: 26rpx;
  color: var(--color-text-secondary);
}

.refund {
  padding: 16rpx 0;
}

.refund + .refund {
  border-top: 1rpx solid var(--border-light);
}

.refund__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.refund__status {
  font-size: 26rpx;
  color: var(--color-text-primary);
}

.refund__amount {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-price);
}

.refund__reason {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: var(--color-text-hint);
}

.refund__time {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: var(--color-text-hint);
}

.bar-placeholder {
  /* 已完成未评价时底部最多 5 个按钮会换成两行，占位一并加高 */
  height: 180rpx;
}

.bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 10;
  display: flex;
  flex-wrap: wrap;
  row-gap: 16rpx;
  justify-content: flex-end;
  align-items: center;
  padding: 16rpx 24rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background: var(--bg-card);
  box-shadow: var(--shadow-bar-top);
}

.btn {
  margin-left: 20rpx;
  padding: 14rpx 32rpx;
  border-radius: 36rpx;
  font-size: 26rpx;
  color: var(--color-text-secondary);
  background: var(--bg-card);
  border: 1rpx solid var(--border-medium);
}

.btn--main {
  color: var(--color-primary-text);
  background: var(--color-primary);
  border-color: var(--color-primary);
}

.btn--off {
  opacity: 0.5;
}

.modal-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  padding: 0 64rpx;
  background: rgba(0, 0, 0, 0.5);
}

.modal {
  width: 100%;
  padding: 32rpx;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
}

.modal__title {
  display: block;
  font-size: 32rpx;
  font-weight: 600;
  text-align: center;
  color: var(--color-text-primary);
}

.modal__tip {
  display: block;
  margin-top: 16rpx;
  font-size: 24rpx;
  line-height: 36rpx;
  color: var(--color-text-hint);
}

.modal__input {
  margin-top: 20rpx;
  width: 100%;
  height: 180rpx;
  padding: 16rpx;
  box-sizing: border-box;
  border-radius: var(--radius-md);
  background: var(--bg-input);
  font-size: 28rpx;
}

.modal__ph {
  color: var(--color-text-disabled);
}

.modal__count {
  display: block;
  margin-top: 8rpx;
  text-align: right;
  font-size: 22rpx;
  color: var(--color-text-hint);
}

.modal__error {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: var(--color-danger);
}

.modal__ops {
  display: flex;
  justify-content: flex-end;
  margin-top: 24rpx;
}
</style>
