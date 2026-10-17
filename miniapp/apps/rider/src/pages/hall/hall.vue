<template>
  <view class="hall">
    <view class="bar">
      <text class="bar__title">可抢订单</text>
      <text class="bar__count">{{ tasks.length }} 单</text>
    </view>

    <scroll-view class="list" scroll-y>
      <view v-for="t in tasks" :key="t.id" class="card">
        <view class="card__head">
          <text class="card__no">{{ t.number }}</text>
          <text class="card__fee">配送费 ¥{{ fmtAmount(t.deliveryFee) }}</text>
        </view>

        <view class="line">
          <text class="line__tag">取</text>
          <view class="line__body">
            <text class="line__title">{{ t.storeName }}</text>
            <text class="line__sub">{{ t.storeAddress }}</text>
          </view>
        </view>

        <view class="line">
          <text class="line__tag line__tag--dest">送</text>
          <view class="line__body">
            <text class="line__title">{{ t.consignee }} {{ t.phone }}</text>
            <text class="line__sub">{{ t.address }}</text>
          </view>
        </view>

        <view class="card__foot">
          <text class="card__meta">订单额 ¥{{ fmtAmount(t.amount) }}｜{{ t.expectDeliveryTime || '立即配送' }}</text>
          <text class="card__btn" :class="{ 'card__btn--off': grabbed[t.id] || busy('grab:' + t.id) }" hover-class="hover-press" @tap="grab(t)">
            {{ grabbed[t.id] ? '已抢' : '抢单' }}
          </text>
        </view>
      </view>

      <view v-if="loading && !tasks.length" class="list__loading"><text>加载中…</text></view>
      <view v-if="listError" class="list__error">
        <text>加载失败</text>
        <text class="list__retry" @tap="load()">重试</text>
      </view>
      <x-empty v-if="!tasks.length && !loading && !listError" text="当前没有可抢订单" sub="下拉刷新试试" />
    </scroll-view>
  </view>
</template>

<script>
import { http, listState, busyLock, fmtAmount } from '@xinfei/mini-shared'

export default {
  data() {
    // 修改点(2026-10-17)：接入列表三态（listError）与抢单 in-flight 锁（busyLock）
    return Object.assign(listState(), busyLock(), {
      tasks: [],
      grabbed: {},
      loading: false
    })
  },
  onShow() {
    this.load()
  },
  onPullDownRefresh() {
    this.load().then(() => uni.stopPullDownRefresh())
  },
  methods: {
    load() {
      this.loading = true
      this.listError = false
      return http.get('/api/rider/tasks/hall').then(list => {
        this.tasks = list || []
      }).catch(() => { this.listError = true }).then(() => {
        this.loading = false
      })
    },
    grab(task) {
      const key = 'grab:' + task.id
      if (this.grabbed[task.id] || this.busy(key)) return
      this.setBusy(key, true)
      http.post('/api/rider/tasks/' + task.id + '/grab').then(() => {
        uni.showToast({ title: '抢单成功', icon: 'none' })
        this.grabbed[task.id] = true
        uni.switchTab({ url: '/pages/task/task' })
      }).catch(() => {}).then(() => { this.setBusy(key, false) })
    }
  }
}
</script>

<style>
.hall {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg-page);
}

.bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24rpx 32rpx;
  background: var(--bg-card);
  border-bottom: 1rpx solid var(--border-light);
}

.bar__title {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

.bar__count {
  font-size: 26rpx;
  color: var(--color-text-hint);
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
  margin-bottom: 16rpx;
}

.card__no {
  font-size: 26rpx;
  color: var(--color-text-secondary);
}

.card__fee {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--color-price);
}

.line {
  display: flex;
  align-items: flex-start;
  margin-top: 12rpx;
}

.line__tag {
  width: 44rpx;
  height: 44rpx;
  line-height: 44rpx;
  text-align: center;
  border-radius: var(--radius-sm);
  font-size: 24rpx;
  color: var(--color-text-white);
  background: var(--color-success);
  flex-shrink: 0;
}

.line__tag--dest {
  background: var(--color-primary-dark);
}

.line__body {
  margin-left: 16rpx;
  display: flex;
  flex-direction: column;
  flex: 1;
}

.line__title {
  font-size: 28rpx;
  color: var(--color-text-primary);
}

.line__sub {
  margin-top: 4rpx;
  font-size: 24rpx;
  line-height: 34rpx;
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

.card__meta {
  font-size: 24rpx;
  color: var(--color-text-hint);
}

.card__btn {
  padding: 12rpx 36rpx;
  border-radius: 32rpx;
  font-size: 28rpx;
  color: var(--color-primary-text);
  background: var(--color-primary);
}

.card__btn--off {
  color: var(--color-text-disabled);
  background: var(--bg-input);
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
</style>
