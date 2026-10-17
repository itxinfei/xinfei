<template>
  <view class="task">
    <view class="tabs">
      <text
        v-for="t in tabs"
        :key="t.scope"
        class="tab"
        :class="{ 'tab--on': t.scope === scope }"
        hover-class="hover-press"
        @tap="switchScope(t.scope)"
      >{{ t.label }}</text>
    </view>

    <scroll-view class="list" scroll-y>
      <view v-for="t in tasks" :key="t.id" class="card">
        <view class="card__head">
          <text class="card__no">{{ t.number }}</text>
          <text class="card__status">{{ orderText(t.status) }}</text>
        </view>

        <text class="card__addr">送：{{ t.address }}</text>
        <text class="card__contact">{{ t.consignee }} {{ t.phone }}</text>

        <view v-if="t.items && t.items.length" class="items">
          <text v-for="(d, i) in t.items" :key="i" class="chip">{{ d.name }} ×{{ d.number }}</text>
        </view>

        <view class="card__foot">
          <text class="card__meta">配送费 ¥{{ fmtAmount(t.deliveryFee) }}</text>
          <view class="ops">
            <text v-if="scope === 'todo'" class="op op--main" :class="{ 'op--off': busy('accept:' + t.id) }" hover-class="hover-press" @tap="accept(t)">接单</text>
            <text v-if="scope === 'delivering'" class="op" :class="{ 'op--off': busy('pickup:' + t.id) }" hover-class="hover-press" @tap="pickup(t)">取餐</text>
            <text v-if="scope === 'delivering'" class="op op--main" :class="{ 'op--off': busy('deliver:' + t.id) }" hover-class="hover-press" @tap="deliver(t)">送达</text>
            <text class="op" hover-class="hover-press" @tap="navigate(t)">导航</text>
          </view>
        </view>
      </view>

      <view v-if="loading && !tasks.length" class="list__loading"><text>加载中…</text></view>
      <view v-if="listError" class="list__error">
        <text>加载失败</text>
        <text class="list__retry" @tap="load()">重试</text>
      </view>
      <x-empty v-if="!tasks.length && !loading && !listError" :text="emptyText" />
    </scroll-view>

    <x-input-modal
      :visible="pickupModal.visible"
      title="确认取餐"
      desc="如有取餐码请输入，留空直接确认"
      placeholder="取餐码"
      @confirm="onPickupConfirm"
      @cancel="pickupModal.visible = false"
    />
  </view>
</template>

<script>
import { http, qs, orderText, listState, busyLock, fmtAmount } from '@xinfei/mini-shared'

export default {
  data() {
    // 修改点(2026-10-17)：接入列表三态（listError）与接单/取餐/送达 in-flight 锁（busyLock）
    return Object.assign(listState(), busyLock(), {
      tabs: [
        { scope: 'todo', label: '待接单' },
        { scope: 'delivering', label: '配送中' },
        { scope: 'history', label: '历史' }
      ],
      scope: 'delivering',
      tasks: [],
      loading: false,
      pickupModal: { visible: false, taskId: null }
    })
  },
  computed: {
    emptyText() {
      return { todo: '暂无派单待接', delivering: '暂无在途订单', history: '暂无历史订单' }[this.scope]
    }
  },
  onShow() {
    this.load()
  },
  onPullDownRefresh() {
    this.load().then(() => uni.stopPullDownRefresh())
  },
  methods: {
    orderText,
    switchScope(scope) {
      if (scope === this.scope) return
      this.scope = scope
      this.load()
    },
    load() {
      this.loading = true
      this.listError = false
      return http.get(qs('/api/rider/tasks/mine', { scope: this.scope })).then(list => {
        this.tasks = list || []
      }).catch(() => { this.listError = true }).then(() => {
        this.loading = false
      })
    },
    accept(task) {
      const key = 'accept:' + task.id
      if (this.busy(key)) return
      this.setBusy(key, true)
      http.post('/api/rider/tasks/' + task.id + '/accept').then(() => {
        uni.showToast({ title: '已接单', icon: 'none' })
        this.load()
      }).catch(() => {}).then(() => { this.setBusy(key, false) })
    },
    pickup(task) {
      // P0-6：订单已生成取餐码时，后端要求带正确取餐码方可核销
      const key = 'pickup:' + task.id
      if (this.busy(key)) return
      this.pickupModal.taskId = task.id
      this.pickupModal.visible = true
    },
    onPickupConfirm(pickupCode) {
      const taskId = this.pickupModal.taskId
      this.pickupModal.visible = false
      const key = 'pickup:' + taskId
      this.setBusy(key, true)
      http.post(qs('/api/rider/tasks/' + taskId + '/pickup', { pickupCode })).then(() => {
        uni.showToast({ title: '已取餐', icon: 'none' })
        this.load()
      }).catch(() => {}).then(() => { this.setBusy(key, false) })
    },
    deliver(task) {
      const key = 'deliver:' + task.id
      if (this.busy(key)) return
      this.setBusy(key, true)
      http.post('/api/rider/tasks/' + task.id + '/deliver').then(() => {
        uni.showToast({ title: '已送达', icon: 'none' })
        this.load()
      }).catch(() => {}).then(() => { this.setBusy(key, false) })
    },
    navigate(task) {
      if (task.destLatitude == null || task.destLongitude == null) {
        uni.showToast({ title: '该订单无坐标', icon: 'none' })
        return
      }
      uni.openLocation({
        latitude: Number(task.destLatitude),
        longitude: Number(task.destLongitude),
        name: task.consignee || '收货人',
        address: task.address,
        fail: () => uni.showToast({ title: '打开地图失败', icon: 'none' })
      })
    }
  }
}
</script>

<style>
.task {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg-page);
}

.tabs {
  display: flex;
  background: var(--bg-card);
  border-bottom: 1rpx solid var(--border-light);
}

.tab {
  flex: 1;
  padding: 24rpx 0;
  text-align: center;
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

.card__addr {
  display: block;
  margin-top: 16rpx;
  font-size: 30rpx;
  line-height: 42rpx;
  color: var(--color-text-primary);
}

.card__contact {
  display: block;
  margin-top: 8rpx;
  font-size: 26rpx;
  color: var(--color-text-hint);
}

.items {
  display: flex;
  flex-wrap: wrap;
  margin-top: 16rpx;
}

.chip {
  margin: 8rpx 12rpx 0 0;
  padding: 6rpx 16rpx;
  border-radius: var(--radius-sm);
  font-size: 24rpx;
  color: var(--color-text-secondary);
  background: var(--bg-input);
}

.card__foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 20rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid var(--border-light);
}

.card__meta {
  font-size: 26rpx;
  color: var(--color-price);
}

.ops {
  display: flex;
}

.op {
  margin-left: 16rpx;
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
.list__error,
.list__loading {
  padding: 120rpx 0;
  text-align: center;
  font-size: 26rpx;
  color: var(--color-text-hint);
}

.list__retry {
  margin-left: 12rpx;
  color: var(--color-primary-text);
}

.op--off {
  opacity: 0.5;
}
</style>
