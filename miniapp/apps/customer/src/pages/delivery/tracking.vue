<template>
  <view class="track">
    <!-- 堂食 / 自提：后端 R.error 带 data.reason，整页空态并停止轮询 -->
    <view v-if="reason" class="reason-empty">
      <x-empty :text="reasonText" />
    </view>

    <template v-else>
      <!-- 地图区：微信原生 map 使用 GCJ-02，坐标已在 applyTracking 中逐点归一 -->
      <view class="map-wrap">
        <map
          v-if="riderPoint"
          id="deliveryMap"
          class="map"
          :latitude="riderPoint.latitude"
          :longitude="riderPoint.longitude"
          :scale="16"
          :markers="markers"
          :polyline="polylines"
        ></map>
        <view v-else class="map-empty">
          <text class="map-empty__text">{{ mapEmptyText }}</text>
        </view>
      </view>

      <!-- 配送节点状态条（后端不产生 PICKING，保留 5 节点与 H5 一致） -->
      <view v-if="tracking" class="card steps">
        <view class="steps__row">
          <view v-for="(s, i) in steps" :key="s.key" class="step">
            <view class="step__main">
              <view
                class="step__dot"
                :class="{ 'step__dot--on': !cancelled && i <= activeIndex, 'step__dot--cancel': cancelled }"
              ></view>
              <view v-if="i < steps.length - 1" class="step__line" :class="{ 'step__line--on': !cancelled && i < activeIndex }"></view>
            </view>
            <text class="step__label" :class="{ 'step__label--on': !cancelled && i <= activeIndex }">{{ s.label }}</text>
          </view>
        </view>
        <view class="steps__foot">
          <text class="steps__status">{{ cancelled ? '订单已取消' : deliveryText(status) }}</text>
          <text v-if="currentStepTime" class="steps__time">{{ currentStepTime }}</text>
        </view>
      </view>

      <!-- 骑手信息 -->
      <view v-if="tracking && (tracking.riderName || distanceText || estimatedText || locationFreshText)" class="card rider">
        <view v-if="tracking.riderName" class="rider__head">
          <text class="rider__name">{{ tracking.riderName }}</text>
          <text v-if="tracking.riderPhone" class="rider__call" hover-class="hover-press" @tap="callRider">拨打 {{ tracking.riderPhone }}</text>
        </view>
        <text v-if="distanceText" class="rider__meta">距收货地 {{ distanceText }}</text>
        <text v-if="estimatedText" class="rider__meta">{{ estimatedText }}</text>
        <text v-if="locationFreshText" class="rider__meta">{{ locationFreshText }}</text>
      </view>

      <!-- 订单信息 -->
      <view v-if="tracking" class="card info">
        <text v-if="tracking.dishSummary" class="info__dish">{{ tracking.dishSummary }}</text>
        <view class="info__row">
          <text class="info__label">订单金额</text>
          <text class="info__value info__value--price">¥{{ fmtAmount(tracking.amount) }}</text>
        </view>
        <view v-if="tracking.platformOrderId" class="info__row">
          <text class="info__label">平台单号</text>
          <text class="info__value">{{ tracking.platformOrderId }}</text>
        </view>
        <view v-if="tracking.address" class="info__row info__row--top">
          <text class="info__label">收货地址</text>
          <text class="info__value">{{ tracking.address }}</text>
        </view>
        <view class="info__row">
          <text class="info__label">订单时间</text>
          <text class="info__value">{{ fmtTime(tracking.orderTime) }}</text>
        </view>
      </view>

      <text class="foot-tip">位置每 15 秒自动刷新{{ stopped ? '，订单已结束，停止刷新' : '' }}</text>

      <view v-if="loadError" class="retry">
        <text>加载失败</text>
        <text class="retry__btn" hover-class="hover-press" @tap="fetchTracking(true)">重试</text>
      </view>
    </template>
  </view>
</template>

<script>
import { http, deliveryText, fmtAmount } from '@xinfei/mini-shared'

// ──────────────────────────────────────────────────────────────────────────
// WGS-84 → GCJ-02：与后端 com.xinfei.utils.GeoUtils 同一算法、同一常量。
// 微信原生 <map> 使用 GCJ-02；骑手新端显式上报 GCJ-02，历史数据可能存 WGS-84，
// 每个点都按自带的 coordSystem 判别（与后端 toGcj02 口径一致：空/未知按 WGS-84 兜底）。
// ──────────────────────────────────────────────────────────────────────────
const GCJ_A = 6378245.0
const GCJ_EE = 0.00669342162296594323

function outOfChina(lng, lat) {
  return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271
}

function deltaLat(x, y) {
  let r = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x))
  r += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0
  r += (20.0 * Math.sin(y * Math.PI) + 40.0 * Math.sin(y / 3.0 * Math.PI)) * 2.0 / 3.0
  r += (160.0 * Math.sin(y / 12.0 * Math.PI) + 320.0 * Math.sin(y * Math.PI / 30.0)) * 2.0 / 3.0
  return r
}

function deltaLng(x, y) {
  let r = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.2 * x * y + 0.1 * Math.sqrt(Math.abs(x))
  r += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0
  r += (20.0 * Math.sin(x * Math.PI) + 40.0 * Math.sin(x / 3.0 * Math.PI)) * 2.0 / 3.0
  r += (150.0 * Math.sin(x / 12.0 * Math.PI) + 300.0 * Math.sin(x / 30.0 * Math.PI)) * 2.0 / 3.0
  return r
}

function wgs84ToGcj02(lng, lat) {
  if (outOfChina(lng, lat)) return [lng, lat]
  let dLat = deltaLat(lng - 105.0, lat - 35.0)
  let dLng = deltaLng(lng - 105.0, lat - 35.0)
  const radLat = lat / 180.0 * Math.PI
  let magic = Math.sin(radLat)
  magic = 1 - GCJ_EE * magic * magic
  const sqrtMagic = Math.sqrt(magic)
  dLat = dLat * 180.0 / ((GCJ_A * (1 - GCJ_EE)) / (magic * sqrtMagic) * Math.PI)
  dLng = dLng * 180.0 / (GCJ_A / sqrtMagic * Math.cos(radLat) * Math.PI)
  return [lng + dLng, lat + dLat]
}

function toGcj02(lng, lat, coordSystem) {
  const sys = coordSystem == null ? '' : String(coordSystem).trim().toUpperCase()
  if (sys === 'GCJ-02') return [lng, lat]
  return wgs84ToGcj02(lng, lat)
}

function pad2(n) {
  return n < 10 ? '0' + n : '' + n
}

// 完整时间，兼容 Jackson 字符串 / 数组两种序列化
function fmtTime(t) {
  if (t == null || t === '') return ''
  if (Array.isArray(t)) {
    return t[0] + '-' + pad2(t[1]) + '-' + pad2(t[2]) + ' ' + pad2(t[3] || 0) + ':' + pad2(t[4] || 0)
  }
  return String(t).replace('T', ' ').slice(0, 16)
}

// 只要 HH:mm
function fmtHM(t) {
  if (t == null || t === '') return ''
  if (Array.isArray(t)) return pad2(t[3] || 0) + ':' + pad2(t[4] || 0)
  return String(t).replace('T', ' ').slice(11, 16)
}

const POLL_INTERVAL = 15000
// 累计 2 小时自动停轮（对标 H5，避免长时间挂页耗电）
const POLL_MAX_MS = 2 * 60 * 60 * 1000
const FINAL_STATUS = ['DELIVERED', 'CANCELLED']

export default {
  data() {
    return {
      orderId: '',
      tracking: null,
      status: '',
      reason: '',
      riderPoint: null,
      trace: [],
      loadError: false,
      stopped: false,
      steps: [
        { key: 'PENDING', label: '提交订单' },
        { key: 'ACCEPTED', label: '商家接单' },
        { key: 'PICKING', label: '骑手取餐' },
        { key: 'DELIVERING', label: '骑手配送' },
        { key: 'DELIVERED', label: '已送达' }
      ],
      timer: null,
      inFlight: false,
      startedAt: 0
    }
  },
  computed: {
    activeIndex() {
      const idx = { PENDING: 0, ACCEPTED: 1, PICKING: 2, DELIVERING: 3, DELIVERED: 4 }
      return Object.prototype.hasOwnProperty.call(idx, this.status) ? idx[this.status] : -1
    },
    cancelled() {
      return this.status === 'CANCELLED'
    },
    reasonText() {
      return this.reason === 'EAT_IN' ? '堂食订单，无需配送追踪' : '该订单为自取，无需配送追踪'
    },
    currentStepTime() {
      return this.tracking ? fmtHM(this.tracking.updateTime) : ''
    },
    mapEmptyText() {
      if (this.tracking && this.tracking.platform) return '平台配送订单暂不支持查看骑手位置'
      return '骑手接单后将显示实时位置'
    },
    distanceText() {
      const d = this.tracking && this.tracking.distance
      if (d == null || !isFinite(Number(d))) return ''
      const km = Number(d)
      if (km >= 1) return km.toFixed(1) + ' 公里'
      return Math.max(0, Math.round(km * 1000)) + ' 米'
    },
    estimatedText() {
      const m = this.tracking && this.tracking.estimatedMinutes
      if (m == null || this.status === 'DELIVERED' || this.status === 'CANCELLED') return ''
      return '预计 ' + m + ' 分钟送达'
    },
    locationFreshText() {
      const t = this.tracking && this.tracking.riderLocation && this.tracking.riderLocation.lastUpdate
      const hm = fmtHM(t)
      return hm ? '位置更新于 ' + hm : ''
    },
    markers() {
      if (!this.riderPoint) return []
      return [{
        id: 1,
        latitude: this.riderPoint.latitude,
        longitude: this.riderPoint.longitude,
        width: 32,
        height: 32,
        // callout 为 map 组件属性，CSS 变量无法注入，色值须显式写
        callout: {
          content: '骑手',
          color: '#333333',
          fontSize: 12,
          borderRadius: 6,
          bgColor: '#ffffff',
          padding: 6,
          display: 'ALWAYS',
          textAlign: 'center'
        }
      }]
    },
    polylines() {
      if (this.trace.length < 2) return []
      return [{
        points: this.trace.map(p => ({ latitude: p.latitude, longitude: p.longitude })),
        // 品牌主色 #ffc200；polyline 同 map 属性，不接受 CSS 变量
        color: '#ffc200',
        width: 5,
        dottedLine: false
      }]
    }
  },
  onLoad(query) {
    this.orderId = query && query.id ? String(query.id) : ''
    this.startedAt = Date.now()
  },
  onShow() {
    // onLoad 之后必触发 onShow，首次加载与回前台恢复统一走这里
    if (this.orderId) this.resume()
  },
  onHide() {
    // 切后台系统挂起 JS 定时器，显式停；回前台 onShow 立即拉取并恢复
    this.stopTimer()
  },
  onUnload() {
    this.stopTimer()
  },
  methods: {
    deliveryText,
    fmtAmount,
    fmtTime,
    resume() {
      if (this.reason || this.stopped) return
      this.fetchTracking().then(() => {
        if (!this.reason && !this.stopped) this.startTimer()
      })
    },
    startTimer() {
      if (this.timer) return
      this.timer = setInterval(() => {
        if (this.inFlight) return
        if (Date.now() - this.startedAt > POLL_MAX_MS) {
          this.stopTimer()
          return
        }
        this.fetchTracking()
      }, POLL_INTERVAL)
    },
    stopTimer() {
      if (this.timer) {
        clearInterval(this.timer)
        this.timer = null
      }
    },
    fetchTracking(manual) {
      if (this.inFlight) return Promise.resolve()
      this.inFlight = true
      // 端点路径参数为 String：平台单号 / 本地订单 ID 双查
      return http.get('/api/delivery/tracking/' + encodeURIComponent(this.orderId), null, { silent: true })
        .then(d => {
          this.loadError = false
          this.applyTracking(d || {})
        })
        .catch(err => {
          if (err && err.notLogin) return
          // 堂食/自提：后端 R.error(msg, data={reason})，请求层把 data 挂到 Error 上
          const reason = err && err.data && err.data.reason
          if (reason === 'EAT_IN' || reason === 'NO_DELIVERY') {
            this.reason = reason
            this.stopTimer()
          } else if (manual || !this.tracking) {
            // 轮询途中的网络抖动保留上一帧数据，不打断查看
            this.loadError = true
          }
        })
        .then(() => {
          this.inFlight = false
        })
    },
    applyTracking(d) {
      this.tracking = d
      this.status = d.status || ''
      if (FINAL_STATUS.indexOf(this.status) !== -1) {
        this.stopped = true
        this.stopTimer()
      }

      // 骑手当前点：按自带 coordSystem 归一到 GCJ-02
      const loc = d.riderLocation
      if (loc && isFinite(Number(loc.longitude)) && isFinite(Number(loc.latitude))) {
        const p = toGcj02(Number(loc.longitude), Number(loc.latitude), loc.coordSystem)
        this.riderPoint = { longitude: p[0], latitude: p[1] }
      } else {
        this.riderPoint = null
      }

      // 历史轨迹逐点归一，并把当前点追加到末尾，保证折线与 marker 连续
      const pts = []
      ;(d.riderHistory || []).forEach(h => {
        if (!isFinite(Number(h.longitude)) || !isFinite(Number(h.latitude))) return
        const p = toGcj02(Number(h.longitude), Number(h.latitude), h.coordSystem)
        pts.push({ longitude: p[0], latitude: p[1] })
      })
      if (this.riderPoint) pts.push(this.riderPoint)
      this.trace = pts
    },
    callRider() {
      if (!this.tracking || !this.tracking.riderPhone) return
      uni.makePhoneCall({ phoneNumber: String(this.tracking.riderPhone) })
    }
  }
}
</script>

<style>
.track {
  min-height: 100%;
  padding-bottom: 40rpx;
  background: var(--bg-page);
}

.map-wrap {
  height: 560rpx;
  background: var(--bg-input);
}

.map {
  width: 100%;
  height: 100%;
}

.map-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.map-empty__text {
  font-size: 26rpx;
  color: var(--color-text-hint);
}

.reason-empty {
  padding-top: 200rpx;
}

.card {
  margin: 16rpx 24rpx;
  padding: 24rpx;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
  box-shadow: var(--shadow-card);
}

.steps__row {
  display: flex;
}

.step {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.step__main {
  position: relative;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.step__dot {
  width: 20rpx;
  height: 20rpx;
  border-radius: var(--radius-round);
  background: var(--border-medium);
  z-index: 1;
}

.step__dot--on {
  background: var(--color-primary);
}

.step__dot--cancel {
  background: var(--color-text-disabled);
}

.step__line {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 100%;
  height: 4rpx;
  transform: translateY(-50%);
  background: var(--border-medium);
}

.step__line--on {
  background: var(--color-primary);
}

.step__label {
  margin-top: 12rpx;
  font-size: 22rpx;
  color: var(--color-text-hint);
  white-space: nowrap;
}

.step__label--on {
  color: var(--color-text-primary);
}

.steps__foot {
  display: flex;
  justify-content: space-between;
  margin-top: 20rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid var(--border-light);
}

.steps__status {
  font-size: 26rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

.steps__time {
  font-size: 24rpx;
  color: var(--color-text-hint);
}

.rider__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.rider__name {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

.rider__call {
  padding: 8rpx 24rpx;
  border-radius: 28rpx;
  font-size: 24rpx;
  color: var(--color-primary-text);
  background: var(--color-primary);
}

.rider__meta {
  display: block;
  margin-top: 12rpx;
  font-size: 26rpx;
  color: var(--color-text-secondary);
}

.info__dish {
  display: block;
  margin-bottom: 12rpx;
  font-size: 28rpx;
  color: var(--color-text-primary);
}

.info__row {
  display: flex;
  align-items: center;
  padding: 10rpx 0;
}

.info__row--top {
  align-items: flex-start;
}

.info__label {
  width: 140rpx;
  flex-shrink: 0;
  font-size: 26rpx;
  color: var(--color-text-hint);
}

.info__value {
  flex: 1;
  font-size: 26rpx;
  color: var(--color-text-primary);
  word-break: break-all;
}

.info__value--price {
  font-weight: 600;
  color: var(--color-price);
}

.foot-tip {
  display: block;
  padding: 24rpx 0 8rpx;
  text-align: center;
  font-size: 22rpx;
  color: var(--color-text-hint);
}

.retry {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 24rpx 0;
  font-size: 26rpx;
  color: var(--color-text-hint);
}

.retry__btn {
  margin-left: 12rpx;
  color: var(--color-primary-text-strong);
}
</style>
