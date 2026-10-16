<template>
  <view class="income">
    <view class="ranges">
      <text
        v-for="r in ranges"
        :key="r.key"
        class="range"
        :class="{ 'range--on': r.key === range }"
        @tap="switchRange(r.key)"
      >{{ r.label }}</text>
    </view>

    <view class="hero">
      <text class="hero__label">配送收入（元）</text>
      <text class="hero__value">{{ current.income || '0.00' }}</text>
      <view class="hero__grid">
        <view class="grid">
          <text class="grid__num">{{ current.orderCount || 0 }}</text>
          <text class="grid__label">单量</text>
        </view>
        <view class="grid">
          <text class="grid__num">{{ current.mileage || 0 }}</text>
          <text class="grid__label">里程(km)</text>
        </view>
        <view class="grid">
          <text class="grid__num">{{ formatRate(current.onTimeRate) }}</text>
          <text class="grid__label">准时率</text>
        </view>
        <view class="grid">
          <text class="grid__num">{{ current.avgMinutes || 0 }}</text>
          <text class="grid__label">均时长(分)</text>
        </view>
      </view>
    </view>

    <scroll-view class="list" scroll-y @scrolltolower="loadMore">
      <view v-for="(rec, i) in records" :key="i" class="row">
        <view class="row__left">
          <text class="row__no">{{ rec.orderNumber }}</text>
          <text class="row__time">{{ rec.orderTime }}</text>
        </view>
        <view class="row__right">
          <text class="row__amount">+{{ rec.income }}</text>
          <text class="row__meta">{{ rec.distance }}km｜{{ rec.durationMin }}分</text>
        </view>
      </view>
      <view v-if="!records.length && !loading" class="list__empty"><text>该时间段暂无收入明细</text></view>
    </scroll-view>
  </view>
</template>

<script>
import { http, qs } from '@xinfei/mini-shared'

export default {
  data() {
    return {
      ranges: [
        { key: 'today', label: '今日' },
        { key: 'week', label: '本周' },
        { key: 'month', label: '本月' }
      ],
      range: 'today',
      summary: {},
      records: [],
      page: 1,
      size: 50,
      hasMore: true,
      loading: false
    }
  },
  computed: {
    current() {
      return this.summary[this.range] || {}
    }
  },
  onShow() {
    this.load()
  },
  onPullDownRefresh() {
    this.load().then(() => uni.stopPullDownRefresh())
  },
  methods: {
    formatRate(rate) {
      if (rate === null || rate === undefined || rate === '') return '-'
      return Math.round(Number(rate) * (Number(rate) <= 1 ? 100 : 1)) + '%'
    },
    switchRange(key) {
      if (key === this.range) return
      this.range = key
      this.load()
    },
    load() {
      this.page = 1
      this.records = []
      this.hasMore = true
      return Promise.all([
        http.get('/api/rider/income/summary').then(data => { this.summary = data || {} }).catch(() => {}),
        this.loadRecords()
      ])
    },
    loadRecords() {
      if (this.loading || !this.hasMore) return Promise.resolve()
      this.loading = true
      return http.get(qs('/api/rider/income/records', {
        range: this.range,
        page: this.page,
        size: this.size
      })).then(data => {
        const list = (data && data.records) || []
        this.records = this.records.concat(list)
        this.hasMore = list.length === this.size
        this.page += 1
      }).catch(() => {}).then(() => {
        this.loading = false
      })
    },
    loadMore() {
      this.loadRecords()
    }
  }
}
</script>

<style>
.income {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--bg-page);
}

.ranges {
  display: flex;
  background: var(--bg-card);
  border-bottom: 1rpx solid var(--border-light);
}

.range {
  flex: 1;
  padding: 24rpx 0;
  text-align: center;
  font-size: 28rpx;
  color: var(--color-text-secondary);
}

.range--on {
  color: var(--color-text-primary);
  font-weight: 600;
  border-bottom: 4rpx solid var(--color-primary);
}

.hero {
  margin: 24rpx;
  padding: 32rpx;
  border-radius: var(--radius-lg);
  background: var(--color-primary-gradient);
}

.hero__label {
  font-size: 24rpx;
  color: var(--color-primary-text);
}

.hero__value {
  display: block;
  margin-top: 8rpx;
  font-size: 56rpx;
  font-weight: 600;
  color: var(--color-primary-text);
}

.hero__grid {
  display: flex;
  margin-top: 24rpx;
}

.grid {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.grid__num {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

.grid__label {
  margin-top: 4rpx;
  font-size: 22rpx;
  color: var(--color-primary-text);
}

.list {
  flex: 1;
  padding: 0 24rpx;
  box-sizing: border-box;
}

.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24rpx;
  margin-bottom: 12rpx;
  border-radius: var(--radius-md);
  background: var(--bg-card);
}

.row__left {
  display: flex;
  flex-direction: column;
}

.row__no {
  font-size: 28rpx;
  color: var(--color-text-primary);
}

.row__time {
  margin-top: 6rpx;
  font-size: 22rpx;
  color: var(--color-text-hint);
}

.row__right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.row__amount {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--color-price);
}

.row__meta {
  margin-top: 6rpx;
  font-size: 22rpx;
  color: var(--color-text-hint);
}

.list__empty {
  padding: 80rpx 0;
  text-align: center;
  font-size: 26rpx;
  color: var(--color-text-hint);
}
</style>
