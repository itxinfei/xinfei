<template>
  <view class="store">
    <!-- 修改点(2026-10-17)：未绑定租户 / 加载失败显式状态（此前二者都表现为"加载中"） -->
    <x-empty v-if="noTenant" text="当前账号未绑定租户/门店" sub="请联系管理员在「门店管理」中为你分配门店" />
    <view v-else-if="listError" class="store__error">
      <text>门店信息加载失败</text>
      <text class="store__retry" @tap="load()">重试</text>
    </view>
    <template v-else>
    <view class="head">
      <text class="head__name">{{ detail.storeName || '门店信息加载中' }}</text>
      <text class="head__code">编码 {{ detail.storeCode || '-' }}｜租户 {{ detail.tenantId || tenantId }}</text>
    </view>

    <view class="switch-card">
      <view class="switch-row">
        <view class="switch-row__text">
          <text class="switch-row__title">门店启用</text>
          <text class="switch-row__desc">{{ enabled ? '已启用（对外可见）' : '已停用（对外隐藏）' }}</text>
        </view>
        <x-switch :checked="enabled" @change="toggleStatus" />
      </view>
      <view class="divider"></view>
      <view class="switch-row">
        <view class="switch-row__text">
          <text class="switch-row__title">暂停接单</text>
          <text class="switch-row__desc">{{ paused ? '已暂停：门店在线但不接新单' : '正常接单中' }}</text>
        </view>
        <x-switch :checked="!paused" @change="togglePause" />
      </view>
    </view>

    <view class="info">
      <view class="info__row"><text class="info__label">营业时间</text><text class="info__value">{{ detail.businessHours || '-' }}</text></view>
      <view class="info__row"><text class="info__label">联系电话</text><text class="info__value">{{ detail.contactPhone || '-' }}</text></view>
      <view class="info__row"><text class="info__label">负责人</text><text class="info__value">{{ detail.contactPerson || '-' }}</text></view>
      <view class="info__row"><text class="info__label">地址</text><text class="info__value">{{ detail.address || '-' }}</text></view>
      <view class="info__row"><text class="info__label">今日单量</text><text class="info__value">{{ detail.todayOrders || 0 }}</text></view>
      <view class="info__row"><text class="info__label">今日营业额</text><text class="info__value">¥{{ fmtAmount(detail.todayAmount) }}</text></view>
    </view>
    </template>
  </view>
</template>

<script>
import { http, qs, employeeAuth, listState, fmtAmount } from '@xinfei/mini-shared'

export default {
  data() {
    // 修改点(2026-10-17)：接入列表三态（listError）
    return Object.assign(listState(), {
      detail: {},
      // 暂停接单状态由后端 businessHoursService 维护，详情接口不回传，本页只在写成功后本地回填
      paused: false
    })
  },
  computed: {
    employee() {
      return employeeAuth.cached() || {}
    },
    tenantId() {
      return this.detail.tenantId || this.employee.tenantId
    },
    // 修改点(2026-10-17)：未绑定租户显式空态——此前 load() 直接 return，页面永久停在"门店信息加载中"
    noTenant() {
      return !this.employee.tenantId
    },
    enabled() {
      return Number(this.detail.status) === 1
    }
  },
  onShow() {
    this.load()
  },
  methods: {
    fmtAmount,
    load() {
      const tid = this.employee.tenantId
      if (!tid) return Promise.resolve()
      this.listError = false
      return http.get('/store/detail/' + tid).then(data => {
        this.detail = data || {}
      }).catch(() => { this.listError = true })
    },
    toggleStatus(val) {
      // x-switch 直接回传布尔值（原生 switch 需 e.detail.value）
      const next = val ? 1 : 0
      http.put(qs('/store/' + this.tenantId + '/status', { status: next })).then(() => {
        this.detail = Object.assign({}, this.detail, { status: next })
        uni.showToast({ title: next ? '已启用' : '已停用', icon: 'none' })
      }).catch(() => {
        this.load()
      })
    },
    togglePause(val) {
      // x-switch 直接回传布尔值（原生 switch 需 e.detail.value）
      const pause = !val
      http.put(qs('/store/' + this.tenantId + '/pause', { pause })).then(() => {
        this.paused = pause
        uni.showToast({ title: pause ? '已暂停接单' : '已恢复接单', icon: 'none' })
      }).catch(() => {})
    }
  }
}
</script>

<style>
.store {
  min-height: 100%;
  padding-bottom: 40rpx;
  background: var(--bg-page);
}

.head {
  padding: 40rpx 32rpx;
  background: var(--color-primary-gradient);
  display: flex;
  flex-direction: column;
}

.head__name {
  font-size: 36rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

.head__code {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: var(--color-text-secondary);
}

.switch-card {
  margin: 24rpx;
  padding: 8rpx 24rpx;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
  box-shadow: var(--shadow-card);
}

.switch-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24rpx 0;
}

.switch-row__text {
  display: flex;
  flex-direction: column;
  flex: 1;
  margin-right: 24rpx;
}

.switch-row__title {
  font-size: 30rpx;
  color: var(--color-text-primary);
}

.switch-row__desc {
  margin-top: 6rpx;
  font-size: 24rpx;
  color: var(--color-text-hint);
}

.divider {
  height: 1rpx;
  background: var(--border-light);
}

.info {
  margin: 0 24rpx;
  padding: 8rpx 24rpx;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
}

.info__row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 22rpx 0;
  border-bottom: 1rpx solid var(--border-light);
}

.info__row:last-child {
  border-bottom: none;
}

.info__label {
  font-size: 26rpx;
  color: var(--color-text-hint);
}

.info__value {
  flex: 1;
  margin-left: 24rpx;
  text-align: right;
  font-size: 26rpx;
  line-height: 38rpx;
  color: var(--color-text-primary);
}

.store__empty {
  padding: 160rpx 48rpx;
  text-align: center;
  font-size: 28rpx;
  color: var(--color-text-secondary);
}

.store__empty-sub {
  display: block;
  margin-top: 16rpx;
  font-size: 24rpx;
  color: var(--color-text-hint);
}

.store__error {
  padding: 160rpx 48rpx;
  text-align: center;
  font-size: 28rpx;
  color: var(--color-text-hint);
}

.store__retry {
  margin-left: 12rpx;
  color: var(--color-primary-text);
}
</style>
