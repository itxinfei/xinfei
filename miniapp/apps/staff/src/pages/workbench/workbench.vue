<template>
  <view class="wb">
    <view class="hero">
      <text class="hero__label">今日营业额（元）</text>
      <text class="hero__value">{{ overview.totalRevenue || '0.00' }}</text>
      <text class="hero__sub">完成率 {{ completionRate }}｜数据源 {{ overview.cacheSource || '-' }}</text>
    </view>

    <view class="grid">
      <view v-for="k in kpis" :key="k.key" class="kpi">
        <text class="kpi__num">{{ overview[k.key] === undefined ? '-' : overview[k.key] }}</text>
        <text class="kpi__label">{{ k.label }}</text>
      </view>
    </view>

    <view class="section">
      <text class="section__title">待处理</text>
      <view class="todo" @tap="toOrders(2)">
        <text class="todo__label">待接单</text>
        <text class="todo__num">{{ overview.pendingOrders || 0 }}</text>
      </view>
      <view class="todo" @tap="toOrders(3)">
        <text class="todo__label">配送中</text>
        <text class="todo__num">{{ stats.deliveringOrders || 0 }}</text>
      </view>
    </view>

    <view class="quick">
      <text class="quick__title">快捷操作</text>
      <view class="quick__row">
        <text class="quick__item" @tap="toOrders(null)">订单列表</text>
        <text class="quick__item" @tap="toStore">门店状态</text>
      </view>
    </view>
  </view>
</template>

<script>
import { http, employeeAuth } from '@xinfei/mini-shared'

export default {
  data() {
    return {
      overview: {},
      stats: {},
      kpis: [
        { key: 'totalOrders', label: '今日订单' },
        { key: 'pendingOrders', label: '待接单' },
        { key: 'completedOrders', label: '已完成' },
        { key: 'cancelledOrders', label: '已取消' },
        { key: 'avgAmount', label: '客单价' },
        { key: 'totalUsers', label: '用户数' }
      ]
    }
  },
  computed: {
    completionRate() {
      const rate = this.overview.completionRate
      if (rate === undefined || rate === null) return '-'
      return rate
    },
    employee() {
      return employeeAuth.cached() || {}
    }
  },
  onShow() {
    this.load()
  },
  onPullDownRefresh() {
    this.load().then(() => uni.stopPullDownRefresh())
  },
  methods: {
    load() {
      return Promise.all([
        http.get('/api/dashboard/overview').then(data => { this.overview = data || {} }).catch(() => {}),
        http.get('/order/statistics').then(data => { this.stats = data || {} }).catch(() => {})
      ])
    },
    toOrders(status) {
      // 订单页在 tabBar 上，switchTab 不接受 query，用本地存储交接筛选状态
      if (status) uni.setStorageSync('staffOrderStatus', String(status))
      uni.switchTab({ url: '/pages/order/order' })
    },
    toStore() {
      uni.switchTab({ url: '/pages/store/store' })
    }
  }
}
</script>

<style>
.wb {
  min-height: 100vh;
  padding-bottom: 40rpx;
  background: var(--bg-page);
}

.hero {
  padding: 40rpx 32rpx;
  background: var(--color-primary-gradient);
}

.hero__label {
  font-size: 24rpx;
  color: var(--color-primary-text);
}

.hero__value {
  display: block;
  margin-top: 8rpx;
  font-size: 64rpx;
  font-weight: 600;
  color: var(--color-primary-text);
}

.hero__sub {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: var(--color-primary-text);
}

.grid {
  display: flex;
  flex-wrap: wrap;
  margin: 24rpx;
  padding: 8rpx 0;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
  box-shadow: var(--shadow-card);
}

.kpi {
  width: 33.33%;
  padding: 24rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.kpi__num {
  font-size: 36rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

.kpi__label {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: var(--color-text-hint);
}

.section {
  margin: 0 24rpx;
  padding: 24rpx;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
}

.section__title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

.todo {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 20rpx;
  padding: 20rpx 24rpx;
  border-radius: var(--radius-md);
  background: var(--bg-input);
}

.todo__label {
  font-size: 28rpx;
  color: var(--color-text-secondary);
}

.todo__num {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--color-danger-text);
}

.quick {
  margin: 24rpx;
  padding: 24rpx;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
}

.quick__title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

.quick__row {
  display: flex;
  margin-top: 20rpx;
}

.quick__item {
  flex: 1;
  margin-right: 16rpx;
  padding: 20rpx 0;
  text-align: center;
  font-size: 28rpx;
  color: var(--color-primary-text);
  border-radius: var(--radius-md);
  background: var(--color-primary-tint);
}

.quick__item:last-child {
  margin-right: 0;
}
</style>
