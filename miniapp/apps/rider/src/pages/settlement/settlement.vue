<template>
  <view class="settlement">
    <view class="hero">
      <text class="hero__label">可提现余额（元）</text>
      <text class="hero__value">{{ fmtAmount(balance.withdrawableBalance) }}</text>
      <view class="hero__grid">
        <view class="grid">
          <text class="grid__num">{{ fmtAmount(balance.frozenBalance) }}</text>
          <text class="grid__label">冻结中</text>
        </view>
        <view class="grid">
          <text class="grid__num">{{ fmtAmount(balance.totalIncome) }}</text>
          <text class="grid__label">累计收入</text>
        </view>
        <view class="grid">
          <text class="grid__num">{{ fmtAmount(balance.totalWithdrawn) }}</text>
          <text class="grid__label">已提现</text>
        </view>
      </view>
      <button class="withdraw-btn" :class="{ 'withdraw-btn--off': withdrawBusy }" @tap="openWithdraw">申请提现</button>
    </view>

    <view class="section-title"><text>提现记录</text></view>

    <scroll-view class="list" scroll-y>
      <view v-for="w in withdrawals" :key="w.id" class="card">
        <view class="card__head">
          <text class="card__amount">¥{{ fmtAmount(w.amount) }}</text>
          <text class="badge" :class="statusCls(w.status)">{{ statusText(w.status) }}</text>
        </view>
        <text class="card__time">申请于 {{ formatTime(w.applyTime) }}</text>
        <text v-if="w.reviewTime" class="card__time">{{ reviewLine(w) }}</text>
        <view v-if="w.remark" class="note">
          <text>{{ w.remark }}</text>
        </view>
      </view>

      <view v-if="loading && !withdrawals.length" class="list__loading"><text>加载中…</text></view>
      <view v-if="listError" class="list__error">
        <text>加载失败</text>
        <text class="list__retry" @tap="load()">重试</text>
      </view>
      <x-empty v-if="!withdrawals.length && !loading && !listError" text="暂无提现记录" sub="申请提现后会在这里展示审核进度" />
    </scroll-view>

    <x-input-modal
      :visible="withdrawModal.visible"
      title="申请提现"
      :desc="'可提现 ¥' + fmtAmount(balance.withdrawableBalance) + '，审核通过后打款'"
      placeholder="提现金额（元）"
      @confirm="onWithdrawConfirm"
      @cancel="withdrawModal.visible = false"
    />
  </view>
</template>

<script>
import { http, qs, listState, busyLock, fmtAmount } from '@xinfei/mini-shared'

// 提现状态：PENDING 待审核 / APPROVED 已同意（已打款）/ REJECTED 已拒绝（退回可提现），与 Web 版口径一致
const WITHDRAW_STATUS = {
  PENDING: { text: '待审核', cls: 'badge--warning' },
  APPROVED: { text: '已通过', cls: 'badge--success' },
  REJECTED: { text: '已驳回', cls: 'badge--danger' }
}

export default {
  data() {
    return Object.assign(listState(), busyLock(), {
      balance: {},
      withdrawals: [],
      loading: false,
      withdrawModal: { visible: false },
      withdrawBusy: false
    })
  },
  onShow() {
    this.load()
  },
  onPullDownRefresh() {
    this.load().then(() => uni.stopPullDownRefresh())
  },
  methods: {
    fmtAmount,
    statusText(s) {
      return (WITHDRAW_STATUS[s] || {}).text || s || ''
    },
    statusCls(s) {
      return (WITHDRAW_STATUS[s] || {}).cls || 'badge--default'
    },
    reviewLine(w) {
      return '审核于 ' + this.formatTime(w.reviewTime) + (w.reviewerName ? '（' + w.reviewerName + '）' : '')
    },
    /** 对齐 Web 版 RiderUtil.formatTime：ISO 串去 T 截到分，兼容 Jackson 数组 */
    formatTime(t) {
      if (t == null || t === '') return ''
      if (typeof t === 'string') return t.replace('T', ' ').substring(0, 16)
      if (Array.isArray(t)) {
        const pad = v => (v < 10 ? '0' + v : '' + v)
        return t[0] + '-' + pad(t[1]) + '-' + pad(t[2]) + ' ' + pad(t[3] || 0) + ':' + pad(t[4] || 0)
      }
      return String(t)
    },
    load() {
      this.listError = false
      return Promise.all([
        http.get('/api/rider/settlement/balance').then(data => { this.balance = data || {} })
          .catch(() => { this.listError = true }),
        this.loadWithdrawals()
      ])
    },
    /** /withdrawals 返回全量 List（Controller 无分页参数），一次拉取即可 */
    loadWithdrawals() {
      this.loading = true
      return http.get('/api/rider/settlement/withdrawals').then(list => {
        this.withdrawals = list || []
      }).catch(() => { this.listError = true }).then(() => {
        this.loading = false
      })
    },
    openWithdraw() {
      this.withdrawModal.visible = true
    },
    onWithdrawConfirm(raw) {
      this.withdrawModal.visible = false
      const amount = Number(raw)
      // 前置校验对齐后端 applyWithdraw 契约：金额必须大于 0 且不超过可提现余额
      // （后端无独立"最低提现金额"配置，>0 即可；余额不足由后端 freeze 行数=0 拒绝）
      if (!raw || isNaN(amount) || amount <= 0) {
        uni.showToast({ title: '请输入正确的提现金额', icon: 'none' })
        return
      }
      const available = Number(this.balance.withdrawableBalance || 0)
      if (amount > available) {
        uni.showToast({ title: '超过可提现余额', icon: 'none' })
        return
      }
      if (this.busy('withdraw')) return
      this.setBusy('withdraw', true)
      this.withdrawBusy = true
      // @RequestParam 语义：amount 必须拼进 URL（qs），body 为空
      http.post(qs('/api/rider/settlement/withdraw', { amount: amount })).then(() => {
        uni.showToast({ title: '提现申请已提交', icon: 'none' })
        this.load()
      }).catch(() => {}).then(() => {
        this.withdrawBusy = false
        this.setBusy('withdraw', false)
      })
    }
  }
}
</script>

<style>
.settlement {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg-page);
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

.withdraw-btn {
  margin-top: 28rpx;
  border-radius: var(--radius-round);
  font-size: 30rpx;
  color: var(--color-primary-text-strong);
  background: var(--bg-card);
}

.withdraw-btn--off {
  opacity: 0.5;
}

.section-title {
  padding: 0 32rpx 12rpx;
  font-size: 26rpx;
  color: var(--color-text-secondary);
}

.list {
  flex: 1;
}

.card {
  margin: 0 24rpx 16rpx;
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

.card__amount {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--color-price);
}

.badge {
  padding: 4rpx 16rpx;
  border-radius: var(--radius-round);
  font-size: 22rpx;
}

.badge--warning {
  color: var(--color-warning-text);
  background: var(--color-warning);
}

.badge--success {
  color: var(--color-success-text);
  background: var(--color-success-tint);
}

.badge--danger {
  color: var(--color-danger-text);
  background: var(--color-danger-tint);
}

.badge--default {
  color: var(--color-text-secondary);
  background: var(--color-neutral-tint);
}

.card__time {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: var(--color-text-hint);
}

.note {
  margin-top: 12rpx;
  padding: 16rpx;
  border-radius: var(--radius-md);
  background: var(--bg-hover);
  font-size: 24rpx;
  line-height: 36rpx;
  color: var(--color-text-secondary);
}

.list__empty,
.list__error,
.list__loading {
  padding: 80rpx 0;
  text-align: center;
  font-size: 26rpx;
  color: var(--color-text-hint);
}

.list__retry {
  margin-left: 12rpx;
  color: var(--color-primary-text);
}
</style>
