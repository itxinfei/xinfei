<template>
  <view class="orders">
    <scroll-view class="tabs" scroll-x>
      <view
        v-for="t in tabs"
        :key="String(t.status)"
        class="tab"
        :class="{ 'tab--on': t.status === status }"
        hover-class="hover-press"
        @tap="switchTab(t.status)"
      >
        <text>{{ t.label }}</text>
      </view>
    </scroll-view>

    <scroll-view class="list" scroll-y @scrolltolower="loadMore">
      <view v-for="o in records" :key="o.id" class="card" hover-class="hover-press" @tap="openDetail(o)">
        <view class="card__head">
          <text class="card__no">{{ o.number }}</text>
          <text class="card__status" :class="'card__status--s' + o.status">{{ orderText(o.status) }}</text>
        </view>

        <view class="items" v-if="o.orderDetails && o.orderDetails.length">
          <view v-for="(d, i) in o.orderDetails" :key="i" class="item">
            <image class="item__img" :src="img(d.image)" mode="aspectFill" lazy-load />
            <text class="item__name">{{ d.name }}</text>
            <text class="item__num">×{{ d.number }}</text>
          </view>
        </view>

        <view class="card__foot">
          <text class="card__time">{{ o.orderTime }}</text>
          <view class="card__pricebox">
            <text class="card__yen">¥</text>
            <text class="card__amount">{{ fmtAmount(o.amount) }}</text>
          </view>
        </view>

        <view class="ops">
          <text class="op" :class="{ 'op--off': busy('again:' + o.id) }" hover-class="hover-press" @tap.stop="again(o)">再来一单</text>
          <text v-if="o.status === 1 || o.status === 2" class="op" :class="{ 'op--off': busy('cancel:' + o.id) }" hover-class="hover-press" @tap.stop="cancel(o)">取消订单</text>
          <text v-if="o.status === 1" class="op op--main" :class="{ 'op--off': busy('pay:' + o.id) }" hover-class="hover-press" @tap.stop="pay(o)">{{ busy('pay:' + o.id) ? '支付中…' : '去支付' }}</text>
          <text v-if="o.status === 3" class="op op--main" :class="{ 'op--off': busy('receipt:' + o.id) }" hover-class="hover-press" @tap.stop="confirmReceipt(o)">确认收货</text>
          <text v-if="o.status === 4" class="op op--main" hover-class="hover-press" @tap.stop="goEvaluate(o)">去评价</text>
        </view>
      </view>

      <view v-if="listError" class="list__error">
        <text>加载失败</text>
        <text class="list__retry" @tap="load(true)">重试</text>
      </view>
      <x-empty v-if="!records.length && !loading && !listError" text="暂无订单" />
      <view v-if="loading" class="list__more"><text>加载中…</text></view>
      <view v-if="!hasMore && records.length" class="list__more"><text>没有更多了</text></view>
    </scroll-view>
  </view>
</template>

<script>
import { http, qs, orderText, fullImage, listState, busyLock, fmtAmount, payOrderByWechat, PAY_CANCEL, PAY_PENDING } from '@xinfei/mini-shared'

export default {
  data() {
    // 修改点(2026-10-17)：接入列表三态（listError）与写操作 in-flight 锁（busyLock）
    return Object.assign(listState(), busyLock(), {
      tabs: [
        { label: '全部', status: null },
        { label: '待付款', status: 1 },
        { label: '待接单', status: 2 },
        { label: '配送中', status: 3 },
        { label: '已完成', status: 4 },
        { label: '已取消', status: 5 }
      ],
      status: null,
      records: [],
      page: 1,
      pageSize: 10,
      hasMore: true,
      loading: false
    })
  },
  onLoad(query) {
    if (query && query.status) {
      this.status = Number(query.status)
    }
    this.applyHandoff()
    this.load(true)
  },
  onShow() {
    if (this.applyHandoff()) this.load(true)
  },
  onPullDownRefresh() {
    this.load(true).then(() => uni.stopPullDownRefresh())
  },
  methods: {
    orderText,
    img: path => fullImage(path),
    applyHandoff() {
      const handoff = uni.getStorageSync('customerOrderStatus')
      if (!handoff) return false
      uni.removeStorageSync('customerOrderStatus')
      // 'all' 来自个人中心「全部订单」，显式回到全部 tab
      this.status = handoff === 'all' ? null : Number(handoff)
      return true
    },
    switchTab(status) {
      if (status === this.status) return
      this.status = status
      this.load(true)
    },
    load(reset) {
      if (reset) {
        this.page = 1
        this.records = []
        this.hasMore = true
      } else if (this.loading || !hasMore) {
        return Promise.resolve()
      }
      this.loading = true
      this.listError = false
      return http.get(qs('/order/userPage', {
        page: this.page,
        pageSize: this.pageSize,
        status: this.status
      })).then(data => {
        const list = (data && data.records) || []
        this.records = this.records.concat(list)
        this.hasMore = list.length === this.pageSize
        this.page += 1
      }).catch(() => { this.listError = true }).then(() => {
        this.loading = false
      })
    },
    loadMore() {
      this.load(false)
    },
    openDetail(order) {
      uni.navigateTo({ url: '/pages/order/detail?id=' + order.id })
    },
    again(order) {
      const key = 'again:' + order.id
      if (this.busy(key)) return
      this.setBusy(key, true)
      http.post('/order/again', { id: order.id }).then(() => {
        uni.showToast({ title: '已加入购物车', icon: 'none' })
      }).catch(() => {}).then(() => { this.setBusy(key, false) })
    },
    cancel(order) {
      const key = 'cancel:' + order.id
      if (this.busy(key)) return
      uni.showModal({
        title: '取消订单',
        content: '确定取消这笔订单？',
        success: res => {
          if (!res.confirm) return
          this.setBusy(key, true)
          http.put(qs('/order/userCancel', { id: order.id })).then(() => this.load(true)).catch(() => {}).then(() => { this.setBusy(key, false) })
        }
      })
    },
    confirmReceipt(order) {
      const key = 'receipt:' + order.id
      if (this.busy(key)) return
      this.setBusy(key, true)
      http.put(qs('/order/userConfirmReceipt', { id: order.id })).then(() => this.load(true)).catch(() => {}).then(() => { this.setBusy(key, false) })
    },
    // 列表内直接发起微信 JSAPI 支付，终态处理与订单详情页 payOrder 完全一致（见 shared/pay.js）
    pay(order) {
      const key = 'pay:' + order.id
      if (this.busy(key)) return
      this.setBusy(key, true)
      payOrderByWechat(order.id, order.amount).then(() => {
        uni.showToast({ title: '支付成功', icon: 'success' })
        this.load(true)
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
            success: () => this.load(true)
          })
          return
        }
        uni.showToast({ title: (err && err.message) || '支付失败，请稍后重试', icon: 'none' })
        this.load(true)
      }).then(() => { this.setBusy(key, false) })
    },
    // 已完成单直跳评价页；评价页对已评商品只读回显，内部还有一次查重
    goEvaluate(order) {
      uni.navigateTo({ url: '/pages/order/evaluate?id=' + order.id })
    }
  }
}
</script>

<style>
.orders {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg-page);
}

/* ---------------- 状态筛选 tab ---------------- */
.tabs {
  flex-shrink: 0;
  white-space: nowrap;
  background: var(--bg-card);
}

.tab {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 92rpx;
  padding: 0 32rpx;
  font-size: 28rpx;
  color: var(--color-text-secondary);
}

.tab--on {
  color: var(--color-text-primary);
  font-weight: 700;
}

/* 选中指示条：28×3 金色圆角（替旧下划线） */
.tab--on::after {
  content: '';
  position: absolute;
  bottom: 10rpx;
  left: 50%;
  transform: translateX(-50%);
  width: 56rpx;
  height: 6rpx;
  border-radius: 6rpx;
  background: var(--color-primary);
}

/* ---------------- 订单卡 ---------------- */
.list {
  flex: 1;
}

.card {
  margin: 24rpx 24rpx 0;
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

/* 状态淡底胶囊：1 待付款红（催付）/ 2 待接单金 / 3 配送绿 / 4 完成灰 / 5 取消浅灰 */
.card__status {
  padding: 4rpx 18rpx;
  border-radius: 999rpx;
  font-size: 24rpx;
  font-weight: 600;
  line-height: 1.6;
}

.card__status--s1 {
  color: var(--color-danger-text);
  background: var(--color-danger-tint);
}

.card__status--s2 {
  color: var(--color-primary-text-strong);
  background: var(--color-primary-tint-strong);
}

.card__status--s3 {
  color: var(--color-success-text);
  background: var(--color-success-tint);
}

.card__status--s4 {
  color: var(--color-text-hint);
  background: var(--color-neutral-tint);
}

.card__status--s5 {
  color: var(--color-text-hint);
  background: var(--color-neutral-tint);
  opacity: 0.75;
}

.items {
  display: flex;
  flex-wrap: wrap;
  margin-top: 16rpx;
}

.item {
  display: flex;
  align-items: center;
  width: 100%;
  margin-top: 12rpx;
}

.item__img {
  width: 112rpx;
  height: 112rpx;
  border-radius: var(--radius-md);
  flex-shrink: 0;
  background: var(--bg-input);
}

.item__name {
  flex: 1;
  margin-left: 16rpx;
  font-size: 28rpx;
  color: var(--color-text-primary);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.item__num {
  flex-shrink: 0;
  margin-left: 16rpx;
  font-size: 26rpx;
  color: var(--color-text-hint);
}

.card__foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 20rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid var(--border-light);
}

.card__time {
  font-size: 24rpx;
  color: var(--color-text-hint);
}

.card__pricebox {
  display: flex;
  align-items: baseline;
}

.card__yen {
  font-size: 24rpx;
  font-weight: 600;
  color: var(--color-price);
}

.card__amount {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--color-price);
}

/* ---------------- 操作钮 ---------------- */
.ops {
  display: flex;
  justify-content: flex-end;
  margin-top: 16rpx;
}

.op {
  margin-left: 20rpx;
  padding: 0 32rpx;
  height: 72rpx;
  line-height: 70rpx;
  border-radius: 32rpx;
  font-size: 24rpx;
  color: var(--color-text-secondary);
  background: var(--bg-card);
  border: 1rpx solid var(--border-medium);
  box-sizing: border-box;
}

.op--main {
  color: var(--color-primary-text);
  background: var(--color-primary-gradient);
  border-color: transparent;
  box-shadow: var(--shadow-primary-sm);
}

.op--off {
  opacity: 0.5;
}

.list__more,
.list__error {
  padding: 120rpx 0;
  text-align: center;
  font-size: 26rpx;
  color: var(--color-text-hint);
}

.list__more {
  padding: 24rpx 0;
}

.list__retry {
  margin-left: 12rpx;
  color: var(--color-primary-text);
}
</style>
