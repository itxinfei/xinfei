<template>
  <view class="boss">
    <!-- 今日核心：老板视角四指标 + 大字营业额 -->
    <view class="hero">
      <text class="hero__label">今日营业额（元）</text>
      <text class="hero__value">{{ fmtAmount(overview.totalRevenue) }}</text>
      <view class="hero__chips">
        <text class="hero__chip">订单 {{ overview.totalOrders || 0 }}</text>
        <text class="hero__chip">客单 ¥{{ fmtAmount(overview.avgAmount) }}</text>
        <text class="hero__chip">完成率 {{ completionRate }}</text>
      </view>
      <text class="hero__time">数据时间 {{ serverTimeText }}</text>
    </view>

    <view v-if="listError" class="boss__error">
      <text>看板数据加载失败</text>
      <text class="boss__retry" @tap="load()">重试</text>
    </view>

    <!-- 待办提醒：钱相关的两件事优先 -->
    <view class="card">
      <text class="card__title">待处理</text>
      <view class="row" hover-class="hover-press" @tap="toOrders(2)">
        <text class="row__label">待接单</text>
        <text class="row__num">{{ overview.pendingOrders || 0 }}</text>
      </view>
      <view class="row row--danger" hover-class="hover-press" @tap="toOrders(null)">
        <text class="row__label">待退款</text>
        <text class="row__num">{{ overview.pendingRefund || 0 }}</text>
      </view>
    </view>

    <!-- 库存预警：名字+库存/安全线，点门店页看详情（补货在后台做） -->
    <view class="card">
      <text class="card__title">库存预警<text v-if="warnings.length" class="card__count">{{ warnings.length }}</text></text>
      <view v-for="w in warningsTop" :key="w.id" class="warn">
        <view class="warn__main">
          <text class="warn__name">{{ w.name }}</text>
          <text class="warn__line">{{ w.stockQty }}{{ w.unit || '' }} / 安全线 {{ w.minStock }}{{ w.unit || '' }}</text>
        </view>
      </view>
      <text v-if="warnings.length > 5" class="warn__more">等共 {{ warnings.length }} 项食材待补货</text>
      <view v-if="!warningLoading && !warnings.length" class="warn__ok">
        <text>库存全部健康 ✓</text>
      </view>
    </view>

    <!-- 交班台账：今天收银交了几班 -->
    <view class="card">
      <text class="card__title">今日交班</text>
      <view v-if="shifts.length">
        <view v-for="sh in shifts" :key="sh.id" class="shift">
          <text class="shift__who">{{ sh.employeeName || '收银员' }}</text>
          <text class="shift__meta">{{ sh.orderCount || 0 }} 笔 · ¥{{ fmtAmount(sh.totalAmount) }}</text>
          <text class="shift__time">{{ shiftTime(sh.closeTime) }}</text>
        </view>
        <view class="shift__total">
          <text>合计 {{ shiftTotalCount }} 笔 · ¥{{ fmtAmount(shiftTotalAmount) }}</text>
        </view>
      </view>
      <view v-else class="warn__ok"><text>今日暂无交班记录</text></view>
    </view>

    <view class="boss__foot">
      <text>经营明细与报表请前往电脑端后台查看</text>
    </view>
  </view>
</template>

<script>
// 老板驾驶舱（产品修复计划 P0-2，O-43）：老板晚上看一眼今天的核心场景。
// 全部复用现成接口，零后端改动：
//   GET /api/dashboard/overview（totalRevenue/totalOrders/avgAmount/completionRate/pendingOrders/pendingRefund/serverTime）
//   GET /api/inventory/material/warning（低库存食材简版列表）
//   GET /cashier/shift/page?page=1&pageSize=5&shiftDate=今天（交班台账）
import { http, qs, listState, fmtAmount, fmtPercent } from '@xinfei/mini-shared'

export default {
  data() {
    return Object.assign(listState(), {
      overview: {},
      warnings: [],
      warningLoading: true,
      shifts: []
    })
  },
  computed: {
    completionRate() {
      return fmtPercent(this.overview.completionRate)
    },
    warningsTop() {
      // 库存/安全线比值升序 = 最危急在前（后端简版列表无严重度，前端自算）
      const arr = this.warnings.slice()
      arr.sort((a, b) => {
        const ra = Number(a.minStock) > 0 ? Number(a.stockQty) / Number(a.minStock) : 0
        const rb = Number(b.minStock) > 0 ? Number(b.stockQty) / Number(b.minStock) : 0
        return ra - rb
      })
      return arr.slice(0, 5)
    },
    shiftTotalCount() {
      return this.shifts.reduce((s, x) => s + (Number(x.orderCount) || 0), 0)
    },
    shiftTotalAmount() {
      return this.shifts.reduce((s, x) => s + (Number(x.totalAmount) || 0), 0)
    },
    serverTimeText() {
      const t = Number(this.overview.serverTime)
      if (!t) return '-'
      const d = new Date(t)
      const p = n => String(n).padStart(2, '0')
      return p(d.getHours()) + ':' + p(d.getMinutes())
    }
  },
  onLoad() {
    this.load()
  },
  onPullDownRefresh() {
    this.load().then(() => uni.stopPullDownRefresh())
  },
  methods: {
    fmtAmount,
    load() {
      this.listError = false
      const today = new Date()
      const p = n => String(n).padStart(2, '0')
      const todayStr = today.getFullYear() + '-' + p(today.getMonth() + 1) + '-' + p(today.getDate())
      return Promise.all([
        http.get('/api/dashboard/overview').then(data => { this.overview = data || {} }).catch(() => { this.listError = true }),
        http.get('/api/inventory/material/warning').then(data => { this.warnings = data || [] }).catch(() => {}),
        http.get(qs('/cashier/shift/page', { page: 1, pageSize: 5, shiftDate: todayStr }))
          .then(data => { this.shifts = (data && data.records) || [] }).catch(() => {})
      ]).then(() => { this.warningLoading = false })
    },
    shiftTime(t) {
      // closeTime 形如 '2026-10-25T21:03:00'；只展示时分
      if (!t) return ''
      const m = String(t).match(/T(\d{2}:\d{2})/)
      return m ? m[1] : String(t).slice(11, 16)
    },
    toOrders(status) {
      const q = status != null ? '?status=' + status : ''
      uni.navigateTo({ url: '/pages/order/order' + q })
    }
  }
}
</script>

<style>
.boss { padding: 24rpx 24rpx 48rpx; }

.hero {
  background: var(--color-primary-gradient);
  border-radius: var(--radius-lg);
  padding: 36rpx 32rpx 28rpx;
  color: var(--color-primary-text);
  margin-bottom: 24rpx;
}
.hero__label { font-size: 24rpx; opacity: 0.85; display: block; }
.hero__value { font-size: 72rpx; font-weight: 800; line-height: 1.2; display: block; margin: 8rpx 0 16rpx; }
.hero__chips { display: flex; flex-wrap: wrap; gap: 12rpx; margin-bottom: 12rpx; }
.hero__chip {
  font-size: 22rpx; padding: 6rpx 18rpx;
  background: var(--color-primary-tint-strong);
  border-radius: var(--radius-round);
}
.hero__time { font-size: 20rpx; opacity: 0.7; }

.card {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  padding: 24rpx;
  margin-bottom: 24rpx;
}
.card__title { font-size: 28rpx; font-weight: 700; color: var(--color-text-primary); display: block; margin-bottom: 16rpx; }
.card__count {
  font-size: 20rpx; color: var(--color-text-white);
  background: var(--color-danger, #e64340);
  border-radius: var(--radius-round);
  padding: 2rpx 14rpx; margin-left: 12rpx;
}

.row {
  display: flex; justify-content: space-between; align-items: center;
  padding: 18rpx 0;
  border-bottom: 1rpx solid var(--color-border-light);
}
.row:last-child { border-bottom: none; }
.row__label { font-size: 26rpx; color: var(--color-text-secondary); }
.row__num { font-size: 32rpx; font-weight: 700; color: var(--color-text-primary); }
.row--danger .row__num { color: var(--color-danger, #e64340); }

.warn { padding: 14rpx 0; border-bottom: 1rpx solid var(--color-border-light); }
.warn__name { font-size: 26rpx; color: var(--color-text-primary); display: block; }
.warn__line { font-size: 22rpx; color: var(--color-text-hint); display: block; margin-top: 4rpx; }
.warn__more { font-size: 22rpx; color: var(--color-text-hint); display: block; padding-top: 12rpx; }
.warn__ok { padding: 20rpx 0; font-size: 24rpx; color: var(--color-success, #07c160); }

.shift { display: flex; align-items: center; gap: 16rpx; padding: 14rpx 0; border-bottom: 1rpx solid var(--color-border-light); }
.shift__who { flex: 1; font-size: 26rpx; color: var(--color-text-primary); }
.shift__meta { font-size: 24rpx; color: var(--color-primary-text-strong); font-weight: 600; }
.shift__time { font-size: 22rpx; color: var(--color-text-hint); }
.shift__total { padding-top: 16rpx; font-size: 24rpx; color: var(--color-text-secondary); }

.boss__error { text-align: center; padding: 40rpx 0; font-size: 26rpx; color: var(--color-text-hint); }
.boss__retry { color: var(--color-primary-text-strong); display: block; margin-top: 8rpx; }
.boss__foot { text-align: center; font-size: 22rpx; color: var(--color-text-hint); margin-top: 8rpx; }
</style>
