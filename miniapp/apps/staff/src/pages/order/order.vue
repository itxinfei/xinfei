<template>
  <view class="staff-order">
    <scroll-view class="tabs" scroll-x>
      <view
        v-for="t in tabs"
        :key="String(t.status)"
        class="tab"
        :class="{ 'tab--on': t.status === status }"
        @tap="switchTab(t.status)"
      ><text>{{ t.label }}</text></view>
    </scroll-view>

    <scroll-view class="list" scroll-y @scrolltolower="loadMore">
      <view v-for="o in records" :key="o.id" class="card">
        <view class="card__head">
          <text class="card__no">{{ o.number }}</text>
          <text class="card__status">{{ orderText(o.status) }}</text>
        </view>

        <view class="row">
          <text class="row__label">顾客</text>
          <text class="row__value">{{ o.consignee || o.userName || '-' }} {{ o.phone }}</text>
        </view>
        <view class="row">
          <text class="row__label">地址</text>
          <text class="row__value">{{ o.address || '到店自提' }}</text>
        </view>
        <view class="row" v-if="o.remark">
          <text class="row__label">备注</text>
          <text class="row__value">{{ o.remark }}</text>
        </view>
        <view class="row" v-if="o.pickupCode">
          <text class="row__label">取餐码</text>
          <text class="row__value row__value--code">{{ o.pickupCode }}</text>
        </view>

        <view class="card__foot">
          <text class="card__time">{{ o.orderTime }}｜¥{{ fmtAmount(o.amount) }}</text>
          <view class="ops">
            <text v-if="o.status === 2" class="op op--danger" :class="{ 'op--off': busy('act:reject:' + o.id) }" @tap="act('reject', o)">拒单</text>
            <text v-if="o.status === 2" class="op op--main" :class="{ 'op--off': busy('act:confirm:' + o.id) }" @tap="act('confirm', o)">接单</text>
            <text v-if="o.status === 3" class="op op--main" :class="{ 'op--off': busy('act:complete:' + o.id) }" @tap="act('complete', o)">完成</text>
            <text v-if="o.status !== 4 && o.status !== 5" class="op" :class="{ 'op--off': busy('act:cancel:' + o.id) }" @tap="act('cancel', o)">取消</text>
          </view>
        </view>
      </view>

      <view v-if="listError" class="list__error">
        <text>加载失败</text>
        <text class="list__retry" @tap="load(true)">重试</text>
      </view>
      <view v-if="!records.length && !loading && !listError" class="list__empty"><text>暂无订单</text></view>
      <view v-if="loading && !records.length" class="list__loading"><text>加载中…</text></view>
    </scroll-view>
  </view>
</template>

<script>
import { http, qs, orderText, listState, busyLock, fmtAmount } from '@xinfei/mini-shared'

// 状态流转与服务端一致：2 待接单 →(confirm) 3 配送中 →(complete) 4 已完成；2/3 →(cancel|reject) 5 已取消
const ACTIONS = {
  confirm: { path: '/order/confirm', label: '接单' },
  reject: { path: '/order/reject', label: '拒单' },
  complete: { path: '/order/complete', label: '完成' },
  cancel: { path: '/order/cancel', label: '取消' }
}

export default {
  data() {
    // 修改点(2026-10-17)：接入列表三态（listError）与操作 in-flight 锁（busyLock）
    return Object.assign(listState(), busyLock(), {
      tabs: [
        { label: '待接单', status: 2 },
        { label: '配送中', status: 3 },
        { label: '已完成', status: 4 },
        { label: '已取消', status: 5 },
        { label: '全部', status: null }
      ],
      status: 2,
      records: [],
      page: 1,
      pageSize: 20,
      hasMore: true,
      loading: false
    })
  },
  onLoad(query) {
    if (query && query.status) this.status = Number(query.status)
    this.applyHandoff()
    this.load(true)
  },
  onShow() {
    // 工作台快捷入口经本地存储交接筛选状态（switchTab 不支持 query）
    if (this.applyHandoff()) this.load(true)
  },
  onPullDownRefresh() {
    this.load(true).then(() => uni.stopPullDownRefresh())
  },
  methods: {
    orderText,
    applyHandoff() {
      const handoff = uni.getStorageSync('staffOrderStatus')
      if (!handoff) return false
      uni.removeStorageSync('staffOrderStatus')
      this.status = Number(handoff)
      return true
    },
    switchTab(status) {
      if (status === this.status) return
      this.status = status
      this.load(true)
    },
    load(reset) {
      if (reset) {
        this.page = 1
        this.records = []
        this.hasMore = true
      } else if (this.loading || !this.hasMore) {
        return Promise.resolve()
      }
      this.loading = true
      this.listError = false
      return http.get(qs('/order/page', {
        page: this.page,
        pageSize: this.pageSize,
        status: this.status
      })).then(data => {
        const list = (data && data.records) || []
        this.records = this.records.concat(list)
        this.hasMore = list.length === this.pageSize
        this.page += 1
      }).catch(() => { this.listError = true }).then(() => {
        this.loading = false
      })
    },
    loadMore() {
      this.load(false)
    },
    act(key, order) {
      const action = ACTIONS[key]
      // 修改点(2026-10-17)：in-flight 锁防连点重复提交；拒单/取消的 reason 由后端落库
      //（/order/reject 2026-10-17 起接收可选 reason，此前静默丢弃）
      const lockKey = 'act:' + key + ':' + order.id
      if (this.busy(lockKey)) return
      const doIt = reason => {
        this.setBusy(lockKey, true)
        http.put(qs(action.path, { id: order.id, reason })).then(() => {
          uni.showToast({ title: action.label + '成功', icon: 'none' })
          this.load(true)
        }).catch(() => {}).then(() => { this.setBusy(lockKey, false) })
      }
      if (key === 'cancel' || key === 'reject') {
        uni.showModal({
          title: action.label,
          content: '请输入原因（可留空）',
          editable: true,
          placeholderText: '原因',
          success: res => { if (res.confirm) doIt(res.content) }
        })
        return
      }
      doIt()
    }
  }
}
</script>

<style>
.staff-order {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg-page);
}

.tabs {
  flex-shrink: 0;
  white-space: nowrap;
  background: var(--bg-card);
  border-bottom: 1rpx solid var(--border-light);
}

.tab {
  display: inline-block;
  padding: 24rpx 28rpx;
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

.row {
  display: flex;
  margin-top: 12rpx;
}

.row__label {
  width: 110rpx;
  font-size: 26rpx;
  color: var(--color-text-hint);
}

.row__value {
  flex: 1;
  font-size: 26rpx;
  line-height: 38rpx;
  color: var(--color-text-primary);
}

.row__value--code {
  font-weight: 600;
  color: var(--color-price);
}

.card__foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 20rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid var(--border-light);
}

.card__time {
  font-size: 24rpx;
  color: var(--color-text-hint);
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

.op--danger {
  color: var(--color-danger-text);
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
