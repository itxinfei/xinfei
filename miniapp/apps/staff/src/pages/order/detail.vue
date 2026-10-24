<template>
  <view class="detail">
    <scroll-view class="detail__body" scroll-y>
      <view v-if="order.id" class="card">
        <view class="card__head">
          <text class="card__no">{{ order.number }}</text>
          <text class="card__status">{{ orderText(order.status) }}</text>
        </view>
        <view class="srcrow">
          <text class="srcrow__tag">{{ sourceText(order.source) }}</text>
          <text v-if="order.expectDeliveryTime" class="srcrow__expect">期望 {{ order.expectDeliveryTime }}</text>
        </view>
      </view>

      <!-- 商品明细：列表页看不到，本页补齐 -->
      <view v-if="order.id" class="card">
        <text class="card__title">商品明细</text>
        <view v-for="(d, i) in order.orderDetails || []" :key="i" class="item">
          <image class="item__img" :src="img(d.image)" mode="aspectFill" lazy-load />
          <view class="item__main">
            <text class="item__name">{{ d.name }}</text>
            <text v-if="d.dishFlavor" class="item__flavor">{{ d.dishFlavor }}</text>
          </view>
          <text class="item__num">×{{ d.number }}</text>
          <text class="item__amount">¥{{ fmtAmount(d.amount) }}</text>
        </view>
        <view class="sum">
          <text class="sum__label">合计</text>
          <text class="sum__value">¥{{ fmtAmount(order.amount) }}</text>
        </view>
      </view>

      <!-- 履约信息 -->
      <view v-if="order.id" class="card">
        <view class="info">
          <view class="info__row">
            <text class="info__label">顾客</text>
            <text class="info__value">{{ order.consignee || order.userName || '-' }}</text>
            <text v-if="order.phone" class="info__call" hover-class="hover-press" @tap="call(order.phone)">拨打</text>
          </view>
          <view class="info__row">
            <text class="info__label">{{ isSelfPickup ? '到店' : '地址' }}</text>
            <text class="info__value">{{ isSelfPickup ? '到店自提' : (order.address || '-') }}</text>
          </view>
          <view v-if="isSelfPickup" class="info__row">
            <text class="info__label">取餐码</text>
            <text class="info__value info__value--code">{{ order.pickupCode || '-' }}</text>
          </view>
          <view v-if="order.remark" class="info__row">
            <text class="info__label">备注</text>
            <text class="info__value">{{ order.remark }}</text>
          </view>
          <view class="info__row">
            <text class="info__label">下单时间</text>
            <text class="info__value">{{ order.orderTime || '-' }}</text>
          </view>
        </view>
      </view>

      <view v-if="listError" class="state">
        <text>加载失败</text>
        <text class="state__retry" @tap="load">重试</text>
      </view>
      <view v-if="loading && !order.id" class="state"><text>加载中…</text></view>
    </scroll-view>

    <!-- 底部操作栏：按订单状态与来源 -->
    <view v-if="order.id && (order.status === 2 || order.status === 3)" class="actionbar">
      <text
        v-if="order.status === 2 || order.status === 3"
        class="btn btn--plain btn--danger"
        :class="{ 'btn--off': busy('act:cancel') }"
        hover-class="hover-press"
        @tap="askReason('cancel')"
      >取消</text>
      <template v-if="order.status === 2">
        <text
          class="btn btn--plain btn--danger"
          :class="{ 'btn--off': busy('act:reject') }"
          hover-class="hover-press"
          @tap="askReason('reject')"
        >拒单</text>
        <text
          class="btn btn--plain"
          :class="{ 'btn--off': busy('dispatch') }"
          hover-class="hover-press"
          @tap="openDispatch"
        >派单</text>
        <text
          class="btn btn--main"
          :class="{ 'btn--off': busy('act:confirm') }"
          hover-class="hover-press"
          @tap="act('confirm')"
        >接单</text>
      </template>
      <template v-else-if="order.status === 3">
        <text
          v-if="isSelfPickup"
          class="btn btn--main"
          :class="{ 'btn--off': busy('act:verify') }"
          hover-class="hover-press"
          @tap="openVerify"
        >核销取餐码</text>
        <text
          v-else
          class="btn btn--main"
          :class="{ 'btn--off': busy('act:complete') }"
          hover-class="hover-press"
          @tap="act('complete')"
        >完成</text>
      </template>
    </view>

    <!-- 派单弹层：在线骑手候选人 -->
    <view v-if="dispatch.visible" class="sheet-mask" @tap="closeDispatch">
      <view class="sheet" @tap.stop>
        <view class="sheet__head">
          <text class="sheet__title">派单给骑手</text>
          <text class="sheet__close" @tap="closeDispatch">×</text>
        </view>
        <scroll-view class="sheet__list" scroll-y>
          <view
            v-for="r in dispatch.riders"
            :key="r.id"
            class="rider"
            hover-class="hover-press"
            @tap="chooseRider(r)"
          >
            <view class="rider__main">
              <text class="rider__name">{{ r.name }}</text>
              <text class="rider__sub">在途 {{ r.currentOrderCount || 0 }} 单</text>
            </view>
            <text class="rider__badge">{{ r.status === 2 ? '忙碌' : '在线' }}</text>
          </view>
          <view v-if="!dispatch.riders.length && !dispatch.loading" class="sheet__empty">
            <text>暂无在线骑手，可让骑手在骑手端抢单</text>
          </view>
          <view v-if="dispatch.loading" class="sheet__empty"><text>加载中…</text></view>
        </scroll-view>
      </view>
    </view>

    <!-- 拒单/取消 原因 -->
    <x-input-modal
      :visible="reasonModal.visible"
      :title="reasonModal.title"
      desc="请输入原因（可留空）"
      placeholder="原因"
      @confirm="onReasonConfirm"
      @cancel="reasonModal.visible = false"
    />

    <!-- 核销取餐码 -->
    <x-input-modal
      :visible="verifyModal.visible"
      title="核销自提订单"
      desc="请输入顾客提供的 6 位取餐码"
      placeholder="6 位取餐码"
      input-type="number"
      :maxlength="6"
      @confirm="onVerifyConfirm"
      @cancel="verifyModal.visible = false"
    />
  </view>
</template>

<script>
import { http, qs, orderText, sourceText, fullImage, listState, busyLock, fmtAmount } from '@xinfei/mini-shared'

// 状态流转与列表页一致；核销走独立接口（body 传参），派单候选人是员工侧骑手分页
const ACTIONS = {
  confirm: { path: '/order/confirm', label: '接单' },
  reject: { path: '/order/reject', label: '拒单' },
  complete: { path: '/order/complete', label: '完成' },
  cancel: { path: '/order/cancel', label: '取消' }
}
const SELF_PICKUP = 'SELF_PICKUP'

export default {
  data() {
    return Object.assign(listState(), busyLock(), {
      orderId: null,
      order: {},
      reasonModal: { visible: false, title: '', action: '' },
      verifyModal: { visible: false },
      dispatch: { visible: false, riders: [], loading: false },
      pendingAction: ''
    })
  },
  computed: {
    isSelfPickup() {
      return this.order.source === SELF_PICKUP
    }
  },
  onLoad(query) {
    this.orderId = query && query.id ? Number(query.id) : null
    // 列表点「派单/核销」直达：数据回来后自动展开对应弹层
    this.pendingAction = query && query.action ? query.action : ''
    this.load()
  },
  methods: {
    orderText,
    sourceText,
    fmtAmount,
    img: path => fullImage(path),
    load() {
      if (!this.orderId) return Promise.resolve()
      this.loading = true
      this.listError = false
      // GET /order/{id} 员工通道：token 凭 request 属性 employeeId 鉴权（后端 2026-10-22 兼容）
      return http.get('/order/' + this.orderId, null, { silent: true }).then(data => {
        this.order = data || {}
        if (this.pendingAction) {
          const action = this.pendingAction
          this.pendingAction = ''
          if (action === 'dispatch') this.openDispatch()
          else if (action === 'verify') this.openVerify()
        }
      }).catch(() => {
        this.listError = true
      }).then(() => { this.loading = false })
    },
    call(phone) {
      uni.makePhoneCall({ phoneNumber: String(phone) })
    },
    act(key) {
      const action = ACTIONS[key]
      const lockKey = 'act:' + key
      if (this.busy(lockKey)) return
      if (key === 'complete') {
        uni.showModal({
          title: '完成订单',
          content: '确认该订单已完成？',
          success: res => {
            if (!res.confirm) return
            this.runAction(key, action)
          }
        })
        return
      }
      this.runAction(key, action)
    },
    runAction(key, action) {
      const lockKey = 'act:' + key
      this.setBusy(lockKey, true)
      http.put(qs(action.path, { id: this.orderId }), null, { silent: true }).then(() => {
        uni.showToast({ title: action.label + '成功', icon: 'none' })
        this.load()
      }).catch(() => {}).then(() => { this.setBusy(lockKey, false) })
    },
    askReason(key) {
      this.reasonModal.action = key
      this.reasonModal.title = ACTIONS[key].label
      this.reasonModal.visible = true
    },
    onReasonConfirm(reason) {
      const { action } = this.reasonModal
      this.reasonModal.visible = false
      const conf = ACTIONS[action]
      const lockKey = 'act:' + action
      this.setBusy(lockKey, true)
      http.put(qs(conf.path, { id: this.orderId, reason }), null, { silent: true }).then(() => {
        uni.showToast({ title: conf.label + '成功', icon: 'none' })
        this.load()
      }).catch(() => {}).then(() => { this.setBusy(lockKey, false) })
    },
    openVerify() {
      // #ifdef MP-WEIXIN
      uni.showActionSheet({
        itemList: ['扫码核销', '手动输入取餐码'],
        success: res => {
          if (res.tapIndex === 0) this.scanCode()
          else this.verifyModal.visible = true
        }
      })
      // #endif
      // #ifndef MP-WEIXIN
      this.verifyModal.visible = true
      // #endif
    },
    scanCode() {
      uni.scanCode({
        onlyFromCamera: false,
        success: res => this.verifyPickupCode((res.result || '').trim()),
        fail: () => {}
      })
    },
    onVerifyConfirm(code) {
      this.verifyModal.visible = false
      this.verifyPickupCode((code || '').trim())
    },
    verifyPickupCode(pickupCode) {
      if (!/^\d{4,6}$/.test(pickupCode)) {
        uni.showToast({ title: '请输入 4-6 位数字取餐码', icon: 'none' })
        return
      }
      const lockKey = 'act:verify'
      if (this.busy(lockKey)) return
      this.setBusy(lockKey, true)
      http.put('/order/selfPickup/verify', { id: this.orderId, pickupCode }, { silent: true }).then(() => {
        uni.showToast({ title: '核销成功', icon: 'success' })
        this.load()
      }).catch(() => {}).then(() => { this.setBusy(lockKey, false) })
    },
    openDispatch() {
      this.dispatch.visible = true
      this.dispatch.loading = true
      this.dispatch.riders = []
      // 员工侧骑手分页：status=1 在线；忙碌骑手不出现在候选人中（与旧 H5"在线可转"口径一致）
      http.get(qs('/api/delivery/rider/page', { page: 1, pageSize: 50, status: 1 }), null, { silent: true })
        .then(page => { this.dispatch.riders = (page && page.records) || [] })
        .catch(() => { this.dispatch.riders = [] })
        .then(() => { this.dispatch.loading = false })
    },
    closeDispatch() {
      this.dispatch.visible = false
    },
    chooseRider(rider) {
      uni.showModal({
        title: '确认派单',
        content: '把订单指派给骑手「' + rider.name + '」？',
        success: res => {
          if (!res.confirm) return
          if (this.busy('dispatch')) return
          this.setBusy('dispatch', true)
          http.post('/order/dispatch', { orderId: this.orderId, riderId: rider.id }, { silent: true })
            .then(() => {
              uni.showToast({ title: '派单成功', icon: 'none' })
              this.closeDispatch()
              this.load()
            }).catch(() => {}).then(() => { this.setBusy('dispatch', false) })
        }
      })
    }
  }
}
</script>

<style>
.detail {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg-page);
}

.detail__body {
  flex: 1;
}

.card {
  margin: 16rpx 24rpx;
  padding: 24rpx;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
  box-shadow: var(--shadow-card);
}

.card__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card__no {
  font-size: 26rpx;
  color: var(--color-text-secondary);
}

.card__status {
  font-size: 26rpx;
  color: var(--color-primary-text-strong);
}

.card__title {
  display: block;
  margin-bottom: 16rpx;
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

.srcrow {
  display: flex;
  align-items: center;
  margin-top: 12rpx;
}

.srcrow__tag {
  padding: 2rpx 12rpx;
  border-radius: var(--radius-sm);
  font-size: 22rpx;
  color: var(--color-primary-text);
  background: var(--color-primary-tint);
}

.srcrow__expect {
  margin-left: 16rpx;
  font-size: 24rpx;
  color: var(--color-text-secondary);
}

.item {
  display: flex;
  align-items: center;
  padding: 16rpx 0;
  border-bottom: 1rpx solid var(--border-light);
}

.item__img {
  width: 88rpx;
  height: 88rpx;
  border-radius: var(--radius-md);
  flex-shrink: 0;
  background: var(--bg-input);
}

.item__main {
  flex: 1;
  min-width: 0;
  margin-left: 16rpx;
  display: flex;
  flex-direction: column;
}

.item__name {
  font-size: 28rpx;
  color: var(--color-text-primary);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.item__flavor {
  margin-top: 6rpx;
  font-size: 22rpx;
  color: var(--color-text-hint);
}

.item__num {
  margin: 0 20rpx;
  font-size: 26rpx;
  color: var(--color-text-secondary);
}

.item__amount {
  font-size: 26rpx;
  color: var(--color-text-primary);
}

.sum {
  display: flex;
  justify-content: flex-end;
  align-items: baseline;
  padding-top: 20rpx;
}

.sum__label {
  font-size: 26rpx;
  color: var(--color-text-secondary);
}

.sum__value {
  margin-left: 16rpx;
  font-size: 36rpx;
  font-weight: 800;
  color: var(--color-price);
}

.info__row {
  display: flex;
  align-items: flex-start;
  padding: 12rpx 0;
}

.info__label {
  width: 120rpx;
  flex-shrink: 0;
  font-size: 26rpx;
  color: var(--color-text-hint);
}

.info__value {
  flex: 1;
  font-size: 26rpx;
  line-height: 38rpx;
  color: var(--color-text-primary);
}

.info__value--code {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--color-price);
}

.info__call {
  margin-left: 16rpx;
  padding: 4rpx 20rpx;
  border-radius: 28rpx;
  font-size: 24rpx;
  color: var(--color-primary-text);
  border: 1rpx solid var(--color-primary);
}

.actionbar {
  flex-shrink: 0;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  padding: 16rpx 24rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background: var(--bg-card);
  box-shadow: var(--shadow-bar-top);
}

.btn {
  margin-left: 16rpx;
  padding: 14rpx 32rpx;
  border-radius: 36rpx;
  font-size: 28rpx;
  color: var(--color-text-secondary);
  border: 1rpx solid var(--border-medium);
}

.btn--main {
  color: var(--color-primary-text);
  background: var(--color-primary-gradient);
  border-color: transparent;
  box-shadow: var(--shadow-primary-sm);
}

.btn--plain {
  background: var(--bg-card);
}

.btn--danger {
  color: var(--color-danger-text);
}

.btn--off {
  opacity: 0.5;
}

.state {
  padding: 120rpx 0;
  text-align: center;
  font-size: 26rpx;
  color: var(--color-text-hint);
}

.state__retry {
  margin-left: 12rpx;
  color: var(--color-primary-text);
}

/* ---------------- 派单底部弹层 ---------------- */
.sheet-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 100;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
}

.sheet {
  background: var(--bg-card);
  border-radius: 32rpx 32rpx 0 0;
  max-height: 70vh;
  display: flex;
  flex-direction: column;
  padding-bottom: env(safe-area-inset-bottom);
}

.sheet__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 28rpx 32rpx;
  border-bottom: 1rpx solid var(--border-light);
}

.sheet__title {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

.sheet__close {
  font-size: 40rpx;
  color: var(--color-text-hint);
  padding: 0 8rpx;
}

.sheet__list {
  max-height: 56vh;
}

.rider {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 26rpx 32rpx;
  border-bottom: 1rpx solid var(--border-hairline);
}

.rider__main {
  display: flex;
  flex-direction: column;
}

.rider__name {
  font-size: 30rpx;
  color: var(--color-text-primary);
}

.rider__sub {
  margin-top: 6rpx;
  font-size: 24rpx;
  color: var(--color-text-hint);
}

.rider__badge {
  padding: 4rpx 16rpx;
  border-radius: 20rpx;
  font-size: 22rpx;
  color: var(--color-primary-text);
  background: var(--color-primary-tint);
}

.sheet__empty {
  padding: 80rpx 48rpx;
  text-align: center;
  font-size: 26rpx;
  line-height: 40rpx;
  color: var(--color-text-hint);
}
</style>
