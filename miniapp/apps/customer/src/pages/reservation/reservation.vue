<template>
  <view class="resv">
    <!-- 预订表单 -->
    <view class="card">
      <view class="section-title">提交预订</view>
      <view class="frow">
        <text class="frow__label">姓名</text>
        <input
          v-model="form.customerName"
          class="frow__input"
          maxlength="20"
          placeholder="选填，默认登录姓名"
          placeholder-class="frow__ph"
        />
      </view>
      <picker class="frow" mode="date" :value="form.date" :start="minDate" :end="maxDate" @change="onDateChange">
        <view class="frow__inner">
          <text class="frow__label frow__label--required">预订日期</text>
          <text class="frow__value" :class="{ 'frow__value--ph': !form.date }">{{ form.date || '请选择日期' }}</text>
        </view>
      </picker>
      <picker class="frow" mode="time" :value="form.time" @change="onTimeChange">
        <view class="frow__inner">
          <text class="frow__label frow__label--required">到店时间</text>
          <text class="frow__value" :class="{ 'frow__value--ph': !form.time }">{{ form.time || '请选择时间' }}</text>
        </view>
      </picker>
      <view class="frow">
        <text class="frow__label">就餐人数</text>
        <picker class="frow__picker" mode="selector" :range="seatRange" :value="seatIndex" @change="onSeatChange">
          <view class="frow__value">{{ form.seatCount }} 人</view>
        </picker>
      </view>
      <view class="frow frow--top">
        <text class="frow__label">备注</text>
        <textarea
          v-model="form.remark"
          class="frow__textarea"
          maxlength="200"
          placeholder="如忌口、宝宝椅等（选填）"
          placeholder-class="frow__ph"
        />
      </view>
      <text class="btn btn--main btn--block" :class="{ 'btn--off': submitting }" hover-class="hover-press" @tap="submit">
        {{ submitting ? '提交中…' : '提交预订' }}
      </text>
      <view class="tipnote">提交后请等待商家确认，CONFIRMED 状态即预订成功。</view>
    </view>

    <!-- 我的预订 -->
    <view class="list-head"><text>我的预订</text></view>
    <view v-if="listLoading" class="list__more"><text>加载中…</text></view>
    <view v-else-if="listError" class="list__error">
      <text>加载失败</text>
      <text class="list__retry" @tap="loadList">重试</text>
    </view>
    <template v-else>
      <view v-for="item in list" :key="item.id" class="card">
        <view class="rhead">
          <text class="rhead__time">{{ fmtTime(item.reservedTime) }}</text>
          <text class="rhead__tag" :class="'rhead__tag--' + String(item.status).toLowerCase()">{{ statusText(item.status) }}</text>
        </view>
        <view class="rmeta">
          <text>{{ item.seatCount }} 人就餐{{ item.tableName ? ' · ' + item.tableName : '' }}</text>
          <text class="rmeta__name">{{ item.customerName }}</text>
        </view>
        <text v-if="item.remark" class="rremark">备注：{{ item.remark }}</text>
        <view v-if="item.status === 'PENDING' || item.status === 'CONFIRMED'" class="rops">
          <text
            class="btn btn--danger btn--small"
            :class="{ 'btn--off': busy('cancel:' + item.id) }"
            hover-class="hover-press"
            @tap="confirmCancel(item)"
          >{{ busy('cancel:' + item.id) ? '取消中…' : '取消预订' }}</text>
        </view>
      </view>
      <x-empty v-if="!list.length" text="暂无预订记录" sub="可在上方提交到店预订" />
    </template>
  </view>
</template>

<script>
import { http, busyLock } from '@xinfei/mini-shared'

const STATUS_TEXT = { PENDING: '待确认', CONFIRMED: '已确认', ARRIVED: '已到店', CANCELLED: '已取消' }

function pad(n) {
  return n < 10 ? '0' + n : '' + n
}

export default {
  data() {
    const now = new Date()
    return Object.assign(busyLock(), {
      form: {
        customerName: '',
        date: '',
        time: '',
        seatCount: 2,
        remark: ''
      },
      // 可预订窗口：今天 ~ 30 天后（对齐 Web 版）
      minDate: now.getFullYear() + '-' + pad(now.getMonth() + 1) + '-' + pad(now.getDate()),
      maxDate: new Date(now.getTime() + 30 * 24 * 60 * 60 * 1000).getFullYear() + '-'
        + pad(new Date(now.getTime() + 30 * 24 * 60 * 60 * 1000).getMonth() + 1) + '-'
        + pad(new Date(now.getTime() + 30 * 24 * 60 * 60 * 1000).getDate()),
      seatRange: Array.from({ length: 20 }, (_, i) => (i + 1) + ' 人'),
      submitting: false,
      // 我的预订（接口无分页，最新在前）
      list: [],
      listLoading: false,
      listError: false
    })
  },
  computed: {
    seatIndex() {
      return Math.max(0, Math.min(19, this.form.seatCount - 1))
    }
  },
  onLoad() {
    this.loadList()
  },
  methods: {
    statusText(s) {
      return STATUS_TEXT[s] || s
    },
    fmtTime(t) {
      // 后端 LocalDateTime 序列化通常为 'yyyy-MM-ddTHH:mm:ss'，替换 T 即可展示
      return t ? String(t).replace('T', ' ').slice(0, 16) : ''
    },
    onDateChange(e) {
      this.form.date = e.detail.value
    },
    onTimeChange(e) {
      this.form.time = e.detail.value
    },
    onSeatChange(e) {
      this.form.seatCount = Number(e.detail.value) + 1
    },
    // 我的预订列表（最新在前）
    loadList() {
      this.listLoading = true
      this.listError = false
      return http.get('/api/dining/reservation/customer/my', null, { silent: true }).then(list => {
        this.list = Array.isArray(list) ? list : []
      }).catch(err => {
        if (err && err.notLogin) return
        this.listError = true
      }).then(() => {
        this.listLoading = false
      })
    },
    // 顾客创建预订：姓名/手机自动带入登录用户（姓名允许修改），tableId 不传由商家安排
    submit() {
      if (this.submitting) return
      if (!this.form.date || !this.form.time) {
        uni.showToast({ title: '请选择预订时间', icon: 'none' })
        return
      }
      this.submitting = true
      // reservedTime 按 @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") 提交
      const payload = {
        customerName: (this.form.customerName || '').trim() || null,
        reservedTime: this.form.date + ' ' + this.form.time + ':00',
        seatCount: this.form.seatCount,
        remark: (this.form.remark || '').trim() || null
      }
      http.post('/api/dining/reservation/customer', payload, { silent: true }).then(() => {
        uni.showToast({ title: '预订提交成功，请等待商家确认', icon: 'none' })
        this.form.customerName = ''
        this.form.date = ''
        this.form.time = ''
        this.form.remark = ''
        this.loadList()
      }).catch(err => {
        if (err && err.notLogin) return
        uni.showToast({ title: (err && err.message) || '提交失败，请稍后重试', icon: 'none' })
      }).then(() => {
        this.submitting = false
      })
    },
    confirmCancel(item) {
      uni.showModal({
        title: '取消预订',
        content: '确定取消 ' + this.fmtTime(item.reservedTime) + ' 的预订？',
        confirmText: '取消预订',
        cancelText: '再想想',
        success: r => {
          if (!r.confirm) return
          const key = 'cancel:' + item.id
          if (this.busy(key)) return
          this.setBusy(key, true)
          http.put('/api/dining/reservation/customer/' + item.id + '/cancel', null, { silent: true })
            .then(() => {
              uni.showToast({ title: '已取消预订', icon: 'none' })
              this.loadList()
            })
            .catch(err => {
              if (err && err.notLogin) return
              uni.showToast({ title: (err && err.message) || '取消失败，请稍后重试', icon: 'none' })
            })
            .then(() => { this.setBusy(key, false) })
        }
      })
    }
  }
}
</script>

<style>
.resv {
  min-height: 100%;
  padding: 8rpx 0 40rpx;
  background: var(--bg-page);
}

.card {
  margin: 16rpx 24rpx;
  padding: 24rpx;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
  box-shadow: var(--shadow-card);
}

.section-title {
  margin-bottom: 8rpx;
  font-size: 30rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

.list-head {
  margin: 24rpx 28rpx 0;
  font-size: 26rpx;
  color: var(--color-text-secondary);
}

/* ---- 表单 ---- */
.frow {
  display: flex;
  align-items: center;
  padding: 18rpx 0;
  border-bottom: 1rpx solid var(--border-light);
}

.frow--top {
  align-items: flex-start;
}

.frow__inner {
  flex: 1;
  display: flex;
  align-items: center;
}

.frow__label {
  width: 160rpx;
  flex-shrink: 0;
  font-size: 28rpx;
  color: var(--color-text-secondary);
}

.frow__label--required::before {
  content: '*';
  margin-right: 6rpx;
  color: var(--color-danger);
}

.frow__input {
  flex: 1;
  height: 64rpx;
  font-size: 28rpx;
  color: var(--color-text-primary);
}

.frow__picker {
  flex: 1;
  margin-left: 24rpx;
}

.frow__value {
  flex: 1;
  text-align: right;
  font-size: 28rpx;
  color: var(--color-text-primary);
}

.frow__value--ph {
  color: var(--color-text-disabled);
}

.frow__textarea {
  flex: 1;
  height: 120rpx;
  padding: 12rpx 20rpx;
  box-sizing: border-box;
  border-radius: var(--radius-md);
  background: var(--bg-input);
  font-size: 28rpx;
}

.frow__ph {
  color: var(--color-text-disabled);
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
  margin-top: 16rpx;
}

.btn--small {
  padding: 8rpx 28rpx;
  font-size: 24rpx;
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

/* ---- 预订记录 ---- */
.rhead {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.rhead__time {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

.rhead__tag {
  padding: 4rpx 18rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
}

.rhead__tag--pending {
  color: var(--color-warning);
  background: var(--color-primary-tint);
}

.rhead__tag--confirmed {
  color: var(--color-success-text);
  background: var(--color-success-tint);
}

.rhead__tag--arrived {
  color: var(--color-primary-text-strong);
  background: var(--color-info-tint);
}

.rhead__tag--cancelled {
  color: var(--color-text-hint);
  background: var(--color-neutral-tint);
}

.rmeta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12rpx;
  font-size: 26rpx;
  color: var(--color-text-secondary);
}

.rmeta__name {
  color: var(--color-text-hint);
}

.rremark {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: var(--color-text-hint);
}

.rops {
  display: flex;
  justify-content: flex-end;
  margin-top: 16rpx;
}

/* ---- 列表状态 ---- */
.list__error {
  padding: 100rpx 0;
  text-align: center;
  font-size: 26rpx;
  color: var(--color-text-hint);
}

.list__retry {
  margin-left: 12rpx;
  color: var(--color-primary-text);
}

.list__more {
  padding: 24rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: var(--color-text-hint);
}
</style>
