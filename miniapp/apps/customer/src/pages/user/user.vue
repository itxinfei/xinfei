<template>
  <view class="me">
    <view class="profile">
      <image v-if="avatar" class="profile__avatar" :src="avatar" mode="aspectFill" />
      <view v-else class="profile__avatar profile__avatar--text">
        <text>{{ initial }}</text>
      </view>
      <view class="profile__text">
        <text class="profile__name">{{ user.name || '未设置昵称' }}</text>
        <text class="profile__phone">{{ user.phone || '' }}</text>
      </view>
    </view>

    <view class="stats">
      <view v-for="s in stats" :key="s.status" class="stat" @tap="toOrders(s.status)">
        <text class="stat__num">{{ s.count }}</text>
        <text class="stat__label">{{ s.label }}</text>
      </view>
    </view>

    <view class="cell" @tap="toAddress">
      <text class="cell__label">收货地址</text>
      <text class="cell__value">{{ addressCount }} 个</text>
    </view>
    <view class="cell" @tap="toFavorite">
      <text class="cell__label">我的收藏</text>
      <text class="cell__value">查看</text>
    </view>
    <view class="cell" @tap="showBaseUrl">
      <text class="cell__label">接口地址</text>
      <text class="cell__value">{{ baseUrl }}</text>
    </view>

    <button class="cell cell--btn" @tap="logout">退出登录</button>
  </view>
</template>

<script>
import { customerAuth, http, getBaseUrl, fullImage } from '@xinfei/mini-shared'

export default {
  data() {
    return {
      user: {},
      stats: [
        { status: 1, label: '待付款', count: '-' },
        { status: 2, label: '待接单', count: '-' },
        { status: 3, label: '配送中', count: '-' },
        { status: 4, label: '已完成', count: '-' }
      ],
      addressCount: 0,
      baseUrl: getBaseUrl()
    }
  },
  onShow() {
    this.load()
  },
  computed: {
    avatar() {
      return fullImage(this.user.avatar)
    },
    initial() {
      const name = this.user.name || this.user.phone || '?'
      return name.charAt(0)
    }
  },
  methods: {
    load() {
      customerAuth.me().then(user => {
        this.user = user || {}
      }).catch(() => {})
      this.stats.forEach(s => {
        http.get('/order/userPage', { page: 1, pageSize: 1, status: s.status }, { silent: true })
          .then(data => { s.count = (data && data.total) || 0 })
          .catch(() => {})
      })
      http.get('/address-book/list', null, { silent: true }).then(list => {
        this.addressCount = (list || []).length
      }).catch(() => {})
    },
    toOrders(status) {
      // 订单页在 tabBar 上，navigateTo 打不开、switchTab 不带 query，用本地存储交接筛选状态
      if (status) uni.setStorageSync('customerOrderStatus', String(status))
      uni.switchTab({ url: '/pages/order/list' })
    },
    toAddress() {
      uni.showToast({ title: '地址簿页待迁移', icon: 'none' })
    },
    toFavorite() {
      uni.showToast({ title: '收藏页待迁移', icon: 'none' })
    },
    showBaseUrl() {
      uni.setClipboardData({ data: this.baseUrl })
    },
    logout() {
      customerAuth.logout().then(() => {
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
  width: 120rpx;
  height: 120rpx;
  border-radius: var(--radius-round);
  background: var(--bg-card);
}

.profile__avatar--text {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 48rpx;
  font-weight: 600;
  color: var(--color-primary-text);
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

.profile__phone {
  margin-top: 8rpx;
  font-size: 26rpx;
  color: var(--color-text-secondary);
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
