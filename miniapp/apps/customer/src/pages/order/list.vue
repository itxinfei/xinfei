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
      <view v-for="o in records" :key="o.id" class="card">
        <view class="card__head">
          <text class="card__no">{{ o.number }}</text>
          <text class="card__status">{{ orderText(o.status) }}</text>
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
          <text class="card__amount">¥{{ fmtAmount(o.amount) }}</text>
        </view>

        <view class="ops">
          <text class="op" :class="{ 'op--off': busy('again:' + o.id) }" hover-class="hover-press" @tap="again(o)">再来一单</text>
          <text v-if="o.status === 1 || o.status === 2" class="op" :class="{ 'op--off': busy('cancel:' + o.id) }" hover-class="hover-press" @tap="cancel(o)">取消订单</text>
          <text v-if="o.status === 3" class="op op--main" :class="{ 'op--off': busy('receipt:' + o.id) }" hover-class="hover-press" @tap="confirmReceipt(o)">确认收货</text>
        </view>
      </view>

      <view v-if="listError" class="list__error">
        <text>加载失败</text>
        <text class="list__retry" @tap="load(true)">重试</text>
      </view>
      <view v-if="!records.length && !loading && !listError" class="list__empty">
        <text>暂无订单</text>
      </view>
      <view v-if="loading" class="list__more"><text>加载中…</text></view>
    </scroll-view>
  </view>
</template>

<script>
import { http, qs, orderText, fullImage, listState, busyLock, fmtAmount } from '@xinfei/mini-shared'

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
      this.status = Number(handoff)
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
      } else if (this.loading || !this.hasMore) {
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

.tabs {
  flex-shrink: 0;
  white-space: nowrap;
  background: var(--bg-card);
  border-bottom: 1rpx solid var(--border-light);
}

.tab {
  display: inline-block;
  padding: 24rpx 28rpx;
  font-size: 28rpx;
  color: var(--color-text-secondary);
  border-bottom: 4rpx solid transparent;
}

.tab--on {
  color: var(--color-text-primary);
  font-weight: 600;
  border-bottom-color: var(--color-primary);
}

.list {
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
  width: 88rpx;
  height: 88rpx;
  border-radius: var(--radius-sm);
  flex-shrink: 0;
  background: var(--bg-input);
}

.item__name {
  flex: 1;
  margin-left: 16rpx;
  font-size: 28rpx;
  color: var(--color-text-primary);
}

.item__num {
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

.card__amount {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--color-price);
}

.ops {
  display: flex;
  justify-content: flex-end;
  margin-top: 16rpx;
}

.op {
  margin-left: 20rpx;
  padding: 10rpx 28rpx;
  border-radius: 32rpx;
  font-size: 26rpx;
  color: var(--color-text-secondary);
  border: 1rpx solid var(--border-medium);
}

.op--main {
  color: var(--color-primary-text);
  background: var(--color-primary);
  border-color: var(--color-primary);
}

.list__empty,
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

.op--off {
  opacity: 0.5;
}
</style>
