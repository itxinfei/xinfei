<template>
  <view class="me">
    <view class="profile">
      <view class="profile__avatar"><text>{{ initial }}</text></view>
      <view class="profile__text">
        <text class="profile__name">{{ employee.name || '未登录' }}</text>
        <text class="profile__sub">{{ employee.username || '' }}｜{{ roleText }}</text>
      </view>
    </view>

    <view class="info">
      <view class="info__row"><text class="info__label">工号</text><text class="info__value">{{ employee.jobNumber || '-' }}</text></view>
      <view class="info__row"><text class="info__label">岗位</text><text class="info__value">{{ employee.position || '-' }}</text></view>
      <view class="info__row"><text class="info__label">手机号</text><text class="info__value">{{ employee.phone || '-' }}</text></view>
      <view class="info__row"><text class="info__label">所属门店</text><text class="info__value">租户 {{ employee.tenantId || '-' }}</text></view>
      <view class="info__row"><text class="info__label">账号状态</text><text class="info__value">{{ employee.status === 1 ? '启用' : '停用' }}</text></view>
    </view>

    <view class="cell" @tap="showBaseUrl">
      <text class="cell__label">接口地址</text>
      <text class="cell__value">{{ baseUrl }}</text>
    </view>

    <button class="cell cell--btn" @tap="logout">退出登录</button>
  </view>
</template>

<script>
import { employeeAuth, getBaseUrl } from '@xinfei/mini-shared'

export default {
  data() {
    return {
      employee: {},
      baseUrl: getBaseUrl()
    }
  },
  computed: {
    roleText() {
      const role = this.employee.role
      // 后端历史字段：1 管理员 / 2 店员（RBAC 角色另走 roleKey）
      if (role === 1 || role === '1') return '管理员'
      if (role === 2 || role === '2') return '店员'
      return role || '-'
    },
    initial() {
      const name = this.employee.name || this.employee.username || '?'
      return name.charAt(0)
    }
  },
  onShow() {
    employeeAuth.me().then(employee => {
      this.employee = employee || {}
    }).catch(() => {
      this.employee = employeeAuth.cached() || {}
    })
  },
  methods: {
    showBaseUrl() {
      uni.setClipboardData({ data: this.baseUrl })
    },
    logout() {
      employeeAuth.logout().then(() => {
        uni.reLaunch({ url: '/pages/login/login' })
      }).catch(() => {})
    }
  }
}
</script>

<style>
.me {
  min-height: 100vh;
  padding-bottom: 40rpx;
  background: var(--bg-page);
}

.profile {
  display: flex;
  align-items: center;
  padding: 48rpx 32rpx;
  background: var(--color-primary-gradient);
}

.profile__avatar {
  width: 110rpx;
  height: 110rpx;
  border-radius: var(--radius-round);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 44rpx;
  font-weight: 600;
  color: var(--color-primary-text);
  background: var(--bg-card);
}

.profile__text {
  margin-left: 24rpx;
  display: flex;
  flex-direction: column;
}

.profile__name {
  font-size: 34rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

.profile__sub {
  margin-top: 8rpx;
  font-size: 26rpx;
  color: var(--color-text-secondary);
}

.info {
  margin: 24rpx;
  padding: 8rpx 24rpx;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
  box-shadow: var(--shadow-card);
}

.info__row {
  display: flex;
  justify-content: space-between;
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
  font-size: 26rpx;
  color: var(--color-text-primary);
}

.cell {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 24rpx 24rpx 0;
  padding: 28rpx 24rpx;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
}

.cell__label {
  font-size: 28rpx;
  color: var(--color-text-primary);
}

.cell__value {
  font-size: 26rpx;
  color: var(--color-text-hint);
}

.cell--btn {
  border: none;
  font-size: 28rpx;
  text-align: center;
  color: var(--color-danger-text);
}
</style>
