<template>
  <view class="me">
    <view class="profile">
      <view class="profile__avatar"><text>{{ initial }}</text></view>
      <view class="profile__text">
        <text class="profile__name">{{ rider.name || '未登录' }}</text>
        <text class="profile__phone">{{ rider.phone || '' }}</text>
      </view>
      <text class="profile__badge" :class="{ 'profile__badge--on': online }" @tap="toggle">
        {{ online ? '在线接单中' : '已下班' }}
      </text>
    </view>

    <view class="stats">
      <view class="stat">
        <text class="stat__num">{{ rider.currentOrderCount || 0 }}</text>
        <text class="stat__label">在途单</text>
      </view>
      <view class="stat">
        <text class="stat__num">{{ rider.totalOrderCount || 0 }}</text>
        <text class="stat__label">累计单</text>
      </view>
      <view class="stat">
        <text class="stat__num">{{ rider.rating || '-' }}</text>
        <text class="stat__label">评分</text>
      </view>
    </view>

    <view class="cell" @tap="reportLocation">
      <text class="cell__label">上报当前定位</text>
      <text class="cell__value">{{ lastReport || '未上报' }}</text>
    </view>
    <view class="cell" @tap="showBaseUrl">
      <text class="cell__label">接口地址</text>
      <text class="cell__value">{{ baseUrl }}</text>
    </view>

    <button class="cell cell--btn" @tap="logout">退出登录</button>
  </view>
</template>

<script>
import { riderAuth, getBaseUrl, write, STORAGE_KEYS } from '@xinfei/mini-shared'

export default {
  data() {
    return {
      rider: {},
      lastReport: '',
      baseUrl: getBaseUrl()
    }
  },
  computed: {
    online() {
      return this.rider.status === 1
    },
    initial() {
      const name = this.rider.name || this.rider.phone || '?'
      return name.charAt(0)
    }
  },
  onShow() {
    this.load()
  },
  methods: {
    load() {
      riderAuth.me().then(rider => {
        this.rider = rider || {}
      }).catch(() => {
        this.rider = riderAuth.cached() || {}
      })
    },
    toggle() {
      const wasOnline = this.online
      const action = wasOnline ? riderAuth.offline : riderAuth.online
      action().then(() => {
        uni.showToast({ title: wasOnline ? '已下线' : '已上线', icon: 'none' })
        this.load()
      }).catch(() => {})
    },
    reportLocation() {
      uni.getLocation({
        type: 'gcj02',
        success: res => {
          riderAuth.reportLocation({ longitude: res.longitude, latitude: res.latitude }).then(() => {
            this.lastReport = new Date().toLocaleTimeString()
            // 本地留一份，便于真机调试时确认最近一次上报成功
            write(STORAGE_KEYS.currentRider, Object.assign({}, this.rider, {
              currentLongitude: res.longitude,
              currentLatitude: res.latitude
            }))
            uni.showToast({ title: '定位已上报', icon: 'none' })
          }).catch(() => {})
        },
        fail: () => uni.showToast({ title: '定位失败，检查授权', icon: 'none' })
      })
    },
    showBaseUrl() {
      uni.setClipboardData({ data: this.baseUrl })
    },
    logout() {
      riderAuth.logout().then(() => {
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
  flex: 1;
  margin-left: 24rpx;
  display: flex;
  flex-direction: column;
}

.profile__name {
  font-size: 34rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

.profile__phone {
  margin-top: 8rpx;
  font-size: 26rpx;
  color: var(--color-text-secondary);
}

.profile__badge {
  padding: 12rpx 24rpx;
  border-radius: 32rpx;
  font-size: 24rpx;
  color: var(--color-text-secondary);
  background: var(--bg-card);
}

.profile__badge--on {
  color: var(--color-success-text);
}

.stats {
  display: flex;
  margin: -24rpx 24rpx 0;
  padding: 24rpx 0;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
  box-shadow: var(--shadow-card);
}

.stat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.stat__num {
  font-size: 34rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

.stat__label {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: var(--color-text-hint);
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
