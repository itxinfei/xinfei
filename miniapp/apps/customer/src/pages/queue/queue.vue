<template>
  <view class="queue">
    <view v-if="loading" class="tip"><text>加载中…</text></view>
    <view v-else-if="loadError" class="tip">
      <text>加载失败，请稍后重试</text>
      <text class="tip__btn" hover-class="hover-press" @tap="loadMy">重新加载</text>
    </view>

    <template v-else>
      <!-- 当前无排队：取号区 -->
      <view v-if="!current" class="card">
        <view class="section-title">排队取号</view>
        <view class="frow">
          <text class="frow__label">就餐人数</text>
          <picker class="frow__picker" mode="selector" :range="seatRange" :value="seatIndex" @change="onSeatChange">
            <view class="frow__value">{{ seatCount }} 人</view>
          </picker>
        </view>
        <text class="btn btn--main btn--block" :class="{ 'btn--off': busy('take') }" hover-class="hover-press" @tap="take">
          {{ busy('take') ? '取号中…' : '立即取号' }}
        </text>
        <view class="tipnote">手机号自动使用登录账号，无需填写。到店后请留意叫号。</view>
      </view>

      <!-- 已有排队：状态区 -->
      <view v-else class="card card--center">
        <text class="status-tag" :class="current.status === 'CALLED' ? 'status-tag--called' : 'status-tag--waiting'">
          {{ statusText(current.status) }}
        </text>
        <view class="queue-no"><text>{{ current.queueNo }}</text></view>
        <text class="queue-tip">{{ tipText }}</text>
        <text class="btn btn--danger" :class="{ 'btn--off': busy('cancel') }" hover-class="hover-press" @tap="confirmCancel">
          {{ busy('cancel') ? '取消中…' : '取消排队' }}
        </text>
      </view>
    </template>
  </view>
</template>

<script>
import { http, busyLock } from '@xinfei/mini-shared'

const STATUS_TEXT = { WAITING: '等待中', CALLED: '已叫号', SEATED: '已入座', CANCELLED: '已取消' }

export default {
  data() {
    return Object.assign(busyLock(), {
      loading: true,
      loadError: false,
      booted: false,
      // record: QueueRecord；waitingAhead: 前面等待桌数；无排队时 current 为 null
      current: null,
      waitingAhead: 0,
      seatCount: 2,
      seatRange: Array.from({ length: 20 }, (_, i) => (i + 1) + ' 人')
    })
  },
  computed: {
    seatIndex() {
      return Math.max(0, Math.min(19, this.seatCount - 1))
    },
    tipText() {
      if (!this.current) return ''
      if (this.current.status === 'CALLED') return '已叫到您的号，请尽快到店就餐'
      return this.waitingAhead > 0
        ? '前面还有 ' + this.waitingAhead + ' 桌，请留意叫号'
        : '您是下一位，请留意叫号'
    }
  },
  onLoad() {
    this.loadMy()
    // 取号期间 15 秒刷新一次叫号进度（对齐 Web 版节奏）
    this._timer = setInterval(() => { this.loadMy() }, 15000)
  },
  onHide() {
    this.stopTimer()
  },
  onShow() {
    // 回前台续刷新；onLoad 已起表则只补一次拉取（首拉进行中时跳过）
    if (!this._timer) {
      this._timer = setInterval(() => { this.loadMy() }, 15000)
    }
    if (!this.loading) this.loadMy()
  },
  onUnload() {
    this.stopTimer()
  },
  methods: {
    statusText(s) {
      return STATUS_TEXT[s] || s
    },
    stopTimer() {
      if (this._timer) {
        clearInterval(this._timer)
        this._timer = null
      }
    },
    onSeatChange(e) {
      this.seatCount = Number(e.detail.value) + 1
    },
    // 查询本人当前排队（最新一条 WAITING/CALLED）及前面等待桌数；无记录 data 为 null
    loadMy() {
      // 只有首拉显示整页 loading；15s 轮询与操作后回查静默刷新，避免状态区闪烁
      if (!this.booted) this.loading = true
      this.loadError = false
      return http.get('/api/dining/queue/customer/my', null, { silent: true }).then(data => {
        this.current = data && data.record ? data.record : null
        this.waitingAhead = data && data.waitingAhead != null ? data.waitingAhead : 0
        this.booted = true
        this.loading = false
      }).catch(err => {
        if (err && err.notLogin) return
        // 已有排队数据时轮询失败不打断展示，仅在首拉失败时进错误态
        if (!this.current) this.loadError = true
        this.booted = true
        this.loading = false
      })
    },
    // 顾客取号（手机号自动取登录用户的）
    take() {
      if (this.busy('take')) return
      this.setBusy('take', true)
      http.post('/api/dining/queue/customer/take', { seatCount: this.seatCount }, { silent: true })
        .then(record => {
          uni.showToast({ title: '取号成功', icon: 'success' })
          // 取号后立即回查，拿到准确的 waitingAhead
          this.loadMy()
          if (record && !this.current) this.current = record
        })
        .catch(err => {
          if (err && err.notLogin) return
          uni.showToast({ title: (err && err.message) || '取号失败，请稍后重试', icon: 'none' })
        })
        .then(() => { this.setBusy('take', false) })
    },
    confirmCancel() {
      if (!this.current) return
      uni.showModal({
        title: '取消排队',
        content: '确定取消当前排队号码 ' + this.current.queueNo + ' ？',
        confirmText: '取消排队',
        cancelText: '再等等',
        success: r => {
          if (!r.confirm) return
          if (this.busy('cancel')) return
          this.setBusy('cancel', true)
          http.put('/api/dining/queue/customer/' + this.current.id + '/cancel', null, { silent: true })
            .then(() => {
              uni.showToast({ title: '已取消排队', icon: 'none' })
              this.current = null
              this.waitingAhead = 0
            })
            .catch(err => {
              if (err && err.notLogin) return
              uni.showToast({ title: (err && err.message) || '取消失败，请稍后重试', icon: 'none' })
            })
            .then(() => { this.setBusy('cancel', false) })
        }
      })
    }
  }
}
</script>

<style>
.queue {
  min-height: 100%;
  padding: 8rpx 0 40rpx;
  background: var(--bg-page);
}

.card {
  margin: 16rpx 24rpx;
  padding: 32rpx 24rpx;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
  box-shadow: var(--shadow-card);
}

.card--center {
  display: flex;
  flex-direction: column;
  align-items: center;
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
  border-radius: 999rpx;
  font-size: 26rpx;
  color: var(--color-primary-text);
  background: var(--color-primary);
}

.section-title {
  margin-bottom: 16rpx;
  font-size: 30rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

.frow {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 0 32rpx;
}

.frow__label {
  font-size: 28rpx;
  color: var(--color-text-secondary);
}

.frow__picker {
  flex: 1;
  margin-left: 24rpx;
}

.frow__value {
  text-align: right;
  font-size: 30rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

/* ---- 排队状态 ---- */
.status-tag {
  padding: 6rpx 26rpx;
  border-radius: 999rpx;
  font-size: 24rpx;
}

.status-tag--waiting {
  color: var(--color-primary-text-strong);
  background: var(--color-primary-tint);
}

.status-tag--called {
  color: var(--color-danger-text);
  background: var(--color-danger-tint);
}

.queue-no {
  margin: 28rpx 0 16rpx;
}

.queue-no text {
  font-size: 88rpx;
  font-weight: 700;
  letter-spacing: 8rpx;
  color: var(--color-text-primary);
}

.queue-tip {
  margin-bottom: 40rpx;
  font-size: 26rpx;
  color: var(--color-text-hint);
}

/* ---- 按钮 ---- */
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 18rpx 40rpx;
  border-radius: 999rpx;
  font-size: 28rpx;
}

.btn--block {
  display: flex;
  width: 100%;
  box-sizing: border-box;
}

.btn--main {
  font-weight: 600;
  color: var(--color-primary-text);
  background: var(--color-primary);
}

.btn--danger {
  color: var(--color-danger-text);
  border: 1rpx solid var(--color-danger);
  background: var(--bg-card);
}

.btn--off {
  opacity: 0.5;
}

.tipnote {
  margin-top: 16rpx;
  font-size: 22rpx;
  line-height: 34rpx;
  color: var(--color-text-hint);
}
</style>
