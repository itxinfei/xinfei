<template>
  <view class="exception">
    <scroll-view class="body" scroll-y @scrolltolower="loadMore">
      <!-- 上报表单 -->
      <view class="card form">
        <text class="card__title">上报异常</text>

        <text class="form__label">选择订单</text>
        <view class="subtabs">
          <text
            v-for="s in orderScopes"
            :key="s.scope"
            class="subtab"
            :class="{ 'subtab--on': s.scope === orderScope }"
            hover-class="hover-press"
            @tap="switchOrderScope(s.scope)"
          >{{ s.label }}</text>
        </view>
        <view v-if="orderLoading" class="form__hint"><text>订单加载中…</text></view>
        <view v-else-if="!orders.length" class="form__hint"><text>{{ orderScope === 'delivering' ? '暂无在途订单' : '暂无历史订单' }}</text></view>
        <scroll-view v-else class="orders" scroll-y>
          <view
            v-for="o in orders"
            :key="o.id"
            class="order"
            :class="{ 'order--on': o.id === form.orderId }"
            hover-class="hover-press"
            @tap="form.orderId = o.id"
          >
            <view class="order__text">
              <text class="order__no">{{ o.number }}</text>
              <text class="order__addr">{{ o.address }}</text>
            </view>
            <text v-if="o.id === form.orderId" class="order__check">✓</text>
          </view>
        </scroll-view>

        <text class="form__label">异常类型</text>
        <view class="types">
          <text
            v-for="t in typeOptions"
            :key="t.value"
            class="type"
            :class="{ 'type--on': t.value === form.exceptionType }"
            hover-class="hover-press"
            @tap="form.exceptionType = t.value"
          >{{ t.label }}</text>
        </view>

        <text class="form__label">问题描述</text>
        <textarea
          class="form__textarea"
          v-model="form.description"
          :maxlength="200"
          placeholder="请描述遇到的问题（选填，最多 200 字）"
          placeholder-class="form__placeholder"
        />
        <view class="form__count"><text>{{ (form.description || '').length }}/200</text></view>

        <button
          class="submit"
          :class="{ 'submit--off': submitLocked }"
          :disabled="submitLocked"
          @tap="submit"
        >{{ submitting ? '提交中…' : '提交上报' }}</button>
      </view>

      <!-- 我的工单 -->
      <view class="section-title"><text>我的异常工单</text></view>
      <view v-for="e in records" :key="e.id" class="card">
        <view class="card__head">
          <text class="card__type">{{ excTypeText(e.exceptionType) }}</text>
          <text class="badge" :class="statusCls(e.status)">{{ statusText(e.status) }}</text>
        </view>
        <text class="card__no">订单号：{{ e.orderNumber }}</text>
        <text v-if="e.description" class="card__desc">{{ e.description }}</text>
        <text class="card__time">{{ formatTime(e.createTime) }}</text>
        <view v-if="e.handleNote" class="note">
          <text>处理意见：{{ e.handleNote }}</text>
        </view>
      </view>

      <view v-if="loading && !records.length" class="list__loading"><text>加载中…</text></view>
      <view v-if="listError" class="list__error">
        <text>加载失败</text>
        <text class="list__retry" @tap="loadRecords(true)">重试</text>
      </view>
      <x-empty v-if="!records.length && !loading && !listError" text="暂无异常工单" sub="配送中遇到问题可从上方上报" />
      <view v-if="!hasMore && records.length" class="list__nomore"><text>没有更多了</text></view>
    </scroll-view>
  </view>
</template>

<script>
import { http, qs, listState, busyLock } from '@xinfei/mini-shared'

// 异常类型单一真源：后端 RiderExceptionOrder 常量（1联系不上顾客 2商品破损 3地址有误 4顾客拒收 5申请转单 6其他），
// 与 Web 版 util.js 的 EXC_TYPE_MAP、管理端筛选口径一致
const EXC_TYPE_OPTIONS = [
  { value: 1, label: '联系不上顾客' },
  { value: 2, label: '商品破损' },
  { value: 3, label: '地址有误' },
  { value: 4, label: '顾客拒收' },
  { value: 5, label: '申请转单' },
  { value: 6, label: '其他' }
]

// 状态：0待处理 1已处理 2已转单 3已关闭（RiderExceptionOrder.STATUS_*）
const EXC_STATUS = {
  0: { text: '待处理', cls: 'badge--warning' },
  1: { text: '已处理', cls: 'badge--success' },
  2: { text: '已转单', cls: 'badge--info' },
  3: { text: '已关闭', cls: 'badge--default' }
}

export default {
  data() {
    return Object.assign(listState(), busyLock(), {
      typeOptions: EXC_TYPE_OPTIONS,
      form: { orderId: null, exceptionType: 1, description: '' },
      submitting: false,
      orderScopes: [
        { scope: 'delivering', label: '在途订单' },
        { scope: 'history', label: '历史订单' }
      ],
      orderScope: 'delivering',
      orders: [],
      orderLoading: false,
      records: [],
      page: 1,
      size: 10,
      hasMore: true,
      loading: false
    })
  },
  computed: {
    submitLocked() {
      return this.submitting || this.busy('submit') || !this.form.orderId
    }
  },
  onShow() {
    this.loadOrders()
    this.loadRecords(true)
  },
  methods: {
    excTypeText(t) {
      const hit = EXC_TYPE_OPTIONS.find(o => o.value === t)
      return hit ? hit.label : '异常'
    },
    statusText(s) {
      return (EXC_STATUS[s] || {}).text || '未知'
    },
    statusCls(s) {
      return (EXC_STATUS[s] || {}).cls || 'badge--default'
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
    switchOrderScope(scope) {
      if (scope === this.orderScope) return
      this.orderScope = scope
      this.loadOrders()
    },
    /** 候选订单复用任务列表接口：在途 / 历史各取一页，字段（id/number/address）与 task.vue 一致 */
    loadOrders() {
      this.orderLoading = true
      return http.get(qs('/api/rider/tasks/mine', { scope: this.orderScope })).then(list => {
        this.orders = list || []
        // 默认选中第一单，减少一步操作；切换范围后原选中单不在列表里时重置
        if (!this.orders.some(o => o.id === this.form.orderId)) {
          this.form.orderId = this.orders.length ? this.orders[0].id : null
        }
      }).catch(() => { this.orders = [] }).then(() => {
        this.orderLoading = false
      })
    },
    submit() {
      if (this.submitLocked) return
      if (!this.form.orderId) {
        uni.showToast({ title: '请先选择订单', icon: 'none' })
        return
      }
      this.setBusy('submit', true)
      this.submitting = true
      // POST body：{orderId, exceptionType, description}，字段名与 Controller 逐字一致
      http.post('/api/rider-exception', {
        orderId: this.form.orderId,
        exceptionType: this.form.exceptionType,
        description: (this.form.description || '').trim()
      }).then(() => {
        uni.showToast({ title: '已提交，等待处理', icon: 'none' })
        this.form.description = ''
        this.loadRecords(true)
      }).catch(() => {}).then(() => {
        this.submitting = false
        this.setBusy('submit', false)
      })
    },
    loadRecords(reset) {
      if (reset) {
        this.page = 1
        this.records = []
        this.hasMore = true
      }
      if (this.loading || !this.hasMore) return Promise.resolve()
      this.loading = true
      this.listError = false
      return http.get(qs('/api/rider-exception/mine', { page: this.page, size: this.size })).then(data => {
        const list = (data && data.records) || []
        this.records = this.records.concat(list)
        this.hasMore = list.length === this.size
        this.page += 1
      }).catch(() => { this.listError = true }).then(() => {
        this.loading = false
      })
    },
    loadMore() {
      this.loadRecords(false)
    }
  }
}
</script>

<style>
.exception {
  height: 100%;
  background: var(--bg-page);
}

.body {
  height: 100%;
  box-sizing: border-box;
  padding: 24rpx 0 40rpx;
}

.card {
  margin: 0 24rpx 16rpx;
  padding: 24rpx;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
  box-shadow: var(--shadow-card);
}

.card__title {
  display: block;
  font-size: 32rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

.card__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card__type {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

.card__no {
  display: block;
  margin-top: 12rpx;
  font-size: 24rpx;
  color: var(--color-text-secondary);
}

.card__desc {
  display: block;
  margin-top: 12rpx;
  font-size: 28rpx;
  line-height: 40rpx;
  color: var(--color-text-primary);
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

.badge--info {
  color: var(--color-primary-text-strong);
  background: var(--color-primary-tint);
}

.badge--default {
  color: var(--color-text-secondary);
  background: var(--color-neutral-tint);
}

.form__label {
  display: block;
  margin-top: 28rpx;
  font-size: 26rpx;
  color: var(--color-text-secondary);
}

.subtabs {
  display: flex;
  margin-top: 16rpx;
}

.subtab {
  padding: 10rpx 28rpx;
  margin-right: 16rpx;
  border-radius: var(--radius-round);
  font-size: 26rpx;
  color: var(--color-text-secondary);
  background: var(--bg-input);
}

.subtab--on {
  color: var(--color-primary-text);
  background: var(--color-primary);
}

.form__hint {
  margin-top: 16rpx;
  font-size: 24rpx;
  color: var(--color-text-hint);
}

.orders {
  margin-top: 8rpx;
  max-height: 360rpx;
}

.order {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12rpx;
  padding: 16rpx 20rpx;
  border-radius: var(--radius-md);
  border: 2rpx solid var(--border-light);
  background: var(--bg-input);
}

.order--on {
  border-color: var(--color-primary);
  background: var(--color-primary-tint);
}

.order__text {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.order__no {
  font-size: 26rpx;
  color: var(--color-text-primary);
}

.order__addr {
  margin-top: 4rpx;
  font-size: 22rpx;
  color: var(--color-text-hint);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order__check {
  margin-left: 16rpx;
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-primary-text-strong);
}

.types {
  display: flex;
  flex-wrap: wrap;
  margin-top: 8rpx;
}

.type {
  margin: 12rpx 16rpx 0 0;
  padding: 10rpx 24rpx;
  border-radius: var(--radius-round);
  font-size: 24rpx;
  color: var(--color-text-secondary);
  border: 2rpx solid var(--border-light);
  background: var(--bg-input);
}

.type--on {
  color: var(--color-primary-text-strong);
  border-color: var(--color-primary);
  background: var(--color-primary-tint);
}

.form__textarea {
  box-sizing: border-box;
  width: 100%;
  height: 160rpx;
  margin-top: 16rpx;
  padding: 20rpx;
  border-radius: var(--radius-md);
  font-size: 28rpx;
  line-height: 40rpx;
  background: var(--bg-input);
  color: var(--color-text-primary);
}

.form__placeholder {
  color: var(--color-text-placeholder);
}

.form__count {
  margin-top: 8rpx;
  text-align: right;
  font-size: 22rpx;
  color: var(--color-text-hint);
}

.submit {
  margin-top: 28rpx;
  border-radius: var(--radius-round);
  font-size: 30rpx;
  color: var(--color-primary-text);
  background: var(--color-primary);
}

.submit--off {
  opacity: 0.5;
}

.section-title {
  padding: 8rpx 32rpx 16rpx;
  font-size: 26rpx;
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

.list__nomore {
  padding: 24rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: var(--color-text-hint);
}

.list__retry {
  margin-left: 12rpx;
  color: var(--color-primary-text);
}
</style>
