<template>
  <view class="me">
    <!-- 金色身份区（点击进编辑资料） -->
    <view class="profile" hover-class="hover-press" @tap="toProfile">
      <image v-if="avatar" class="profile__avatar" :src="avatar" mode="aspectFill" />
      <view v-else class="profile__avatar profile__avatar--text">
        <text>{{ initial }}</text>
      </view>
      <view class="profile__text">
        <text class="profile__name">{{ user.name || '未设置昵称' }}</text>
        <text class="profile__phone">{{ maskedPhone }}</text>
      </view>
    </view>

    <!-- 实体会员卡：整块进会员中心；my-info 失败时整块不渲染，不打扰主流程 -->
    <view v-if="memberLoaded" class="mcard" hover-class="hover-press" @tap="toMember">
      <view class="mcard__top">
        <text class="mcard__level">{{ memberInfo ? memberLevelText : '开通会员' }}</text>
        <text class="mcard__entry">{{ memberInfo ? '会员中心 ›' : '立即开通 ›' }}</text>
      </view>
      <text v-if="!memberInfo" class="mcard__tip">消费得积分 · 等级享折扣 · 积分兑好券</text>
      <view class="mcard__stats">
        <view class="mcard__item">
          <text class="mcard__num">{{ memberPoints }}</text>
          <text class="mcard__label">积分</text>
        </view>
        <view class="mcard__item">
          <text class="mcard__num">{{ memberBalance }}</text>
          <text class="mcard__label">余额(元)</text>
        </view>
        <view class="mcard__item">
          <text class="mcard__num">{{ memberCoupons }}</text>
          <text class="mcard__label">优惠券</text>
        </view>
      </view>
    </view>

    <!-- 我的订单 -->
    <view class="ocard">
      <view class="ocard__head">
        <text class="ocard__title">我的订单</text>
        <text class="ocard__all" hover-class="hover-press" @tap="toAllOrders">全部订单 ›</text>
      </view>
      <view class="stats">
        <view v-for="s in stats" :key="s.status" class="stat" hover-class="hover-press" @tap="toOrders(s.status)">
          <text class="stat__num">{{ s.count }}</text>
          <text class="stat__label">{{ s.label }}</text>
        </view>
      </view>
    </view>

    <!-- 常用功能分组 -->
    <view class="group">
      <view class="grow" hover-class="hover-press" @tap="toAddress">
        <text class="grow__label">收货地址</text>
        <text class="grow__value">{{ addressCount }} 个</text>
        <text class="grow__arrow">›</text>
      </view>
      <view class="grow" hover-class="hover-press" @tap="toMember">
        <text class="grow__label">会员中心</text>
        <text class="grow__value">{{ memberInfo ? memberPoints + ' 积分' : '查看' }}</text>
        <text class="grow__arrow">›</text>
      </view>
      <view class="grow" hover-class="hover-press" @tap="toCoupon">
        <text class="grow__label">领券中心</text>
        <text class="grow__value">{{ memberInfo && memberCoupons ? memberCoupons + ' 张可用' : '查看' }}</text>
        <text class="grow__arrow">›</text>
      </view>
      <view class="grow" hover-class="hover-press" @tap="toFavorite">
        <text class="grow__label">我的收藏</text>
        <text class="grow__value">查看</text>
        <text class="grow__arrow">›</text>
      </view>
      <view class="grow" hover-class="hover-press" @tap="toPage('/pages/order/frequent')">
        <text class="grow__label">常购清单</text>
        <text class="grow__value">快速再加购</text>
        <text class="grow__arrow">›</text>
      </view>
      <view class="grow" hover-class="hover-press" @tap="toPage('/pages/order/evaluations')">
        <text class="grow__label">我的评价</text>
        <text class="grow__value">查看</text>
        <text class="grow__arrow">›</text>
      </view>
      <view class="grow" hover-class="hover-press" @tap="toPage('/pages/history/browse')">
        <text class="grow__label">浏览记录</text>
        <text class="grow__value">查看</text>
        <text class="grow__arrow">›</text>
      </view>
      <view class="grow" hover-class="hover-press" @tap="toPage('/pages/message/message')">
        <text class="grow__label">消息中心</text>
        <text class="grow__value">查看</text>
        <text class="grow__arrow">›</text>
      </view>
      <view class="grow" hover-class="hover-press" @tap="toPage('/pages/invoice/my')">
        <text class="grow__label">我的发票</text>
        <text class="grow__value">查看</text>
        <text class="grow__arrow">›</text>
      </view>
      <view class="grow" hover-class="hover-press" @tap="toPage('/pages/queue/queue')">
        <text class="grow__label">排队取号</text>
        <text class="grow__value">堂食排队</text>
        <text class="grow__arrow">›</text>
      </view>
      <view class="grow" hover-class="hover-press" @tap="toPage('/pages/reservation/reservation')">
        <text class="grow__label">预约订座</text>
        <text class="grow__value">查看</text>
        <text class="grow__arrow">›</text>
      </view>
      <view class="grow" hover-class="hover-press" @tap="toPage('/pages/service/service')">
        <text class="grow__label">客服中心</text>
        <text class="grow__value">在线客服/投诉</text>
        <text class="grow__arrow">›</text>
      </view>
      <view class="grow" hover-class="hover-press" @tap="toPage('/pages/ai/assistant')">
        <text class="grow__label">AI 助手</text>
        <text class="grow__value">不知道吃什么？</text>
        <text class="grow__arrow">›</text>
      </view>
    </view>

    <view class="logout" hover-class="hover-press" @tap="logout">
      <text>退出登录</text>
    </view>
  </view>
</template>

<script>
import { customerAuth, http, fullImage, fmtAmount } from '@xinfei/mini-shared'

export default {
  data() {
    return {
      user: {},
      stats: [
        { status: 1, label: '待付款', count: 0 },
        { status: 2, label: '待接单', count: 0 },
        { status: 3, label: '配送中', count: 0 },
        { status: 4, label: '已完成', count: 0 }
      ],
      addressCount: 0,
      memberLoaded: false,  // my-info 首次返回（含非会员）；失败保持 false，会员卡不渲染
      memberInfo: null      // null=非会员；会员时为 { member, level, couponCount }
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
    },
    // 手机号脱敏：138****1234
    maskedPhone() {
      const p = this.user.phone || ''
      return p.length === 11 ? p.slice(0, 3) + '****' + p.slice(7) : p
    },
    memberPoints() {
      return this.memberInfo ? (this.memberInfo.member.points || 0) : 0
    },
    memberBalance() {
      return this.memberInfo ? this.fmtAmount(this.memberInfo.member.balance) : '0.00'
    },
    memberCoupons() {
      return this.memberInfo ? (this.memberInfo.couponCount || 0) : 0
    },
    // 会员卡上的等级文案：等级名（+折扣徽章，discount 是 ≤1 的比率，0.95=9.5 折）
    memberLevelText() {
      const level = this.memberInfo && this.memberInfo.level
      if (!level) return '会员'
      const rate = Number(level.discount)
      if (rate && rate > 0 && rate < 1) {
        return level.name + ' · ' + Number((rate * 10).toFixed(1)) + ' 折'
      }
      return level.name
    }
  },
  methods: {
    fmtAmount,
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
      // 会员卡数据静默加载，失败不渲染、不打扰
      http.get('/api/member/member/my-info', null, { silent: true }).then(data => {
        this.memberLoaded = true
        this.memberInfo = data && data.isMember ? data : null
      }).catch(() => {})
    },
    toMember() {
      uni.navigateTo({ url: '/pages/member/member' })
    },
    toCoupon() {
      uni.navigateTo({ url: '/pages/coupon/coupon' })
    },
    toOrders(status) {
      // 订单页在 tabBar 上，navigateTo 打不开、switchTab 不带 query，用本地存储交接筛选状态
      if (status) uni.setStorageSync('customerOrderStatus', String(status))
      uni.switchTab({ url: '/pages/order/list' })
    },
    toAllOrders() {
      // 'all' 为全部 tab 的显式交接值（订单页 applyHandoff 识别）
      uni.setStorageSync('customerOrderStatus', 'all')
      uni.switchTab({ url: '/pages/order/list' })
    },
    toAddress() {
      uni.navigateTo({ url: '/pages/address/list' })
    },
    toFavorite() {
      uni.navigateTo({ url: '/pages/favorite/list' })
    },
    toProfile() {
      uni.navigateTo({ url: '/pages/user/profile' })
    },
    toPage(url) {
      uni.navigateTo({ url })
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
  min-height: 100%;
  padding-bottom: 48rpx;
  background: var(--bg-page);
}

/* ---------------- 金色身份区 ---------------- */
.profile {
  display: flex;
  align-items: center;
  padding: 44rpx 32rpx 48rpx;
  background: var(--color-primary-gradient);
}

.profile__avatar {
  width: 112rpx;
  height: 112rpx;
  border-radius: var(--radius-round);
  border: 4rpx solid rgba(255, 255, 255, 0.95);
  background: var(--bg-card);
  box-shadow: 0 4rpx 16rpx rgba(190, 110, 0, 0.25);
}

.profile__avatar--text {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 44rpx;
  font-weight: 600;
  color: var(--color-primary-text);
}

.profile__text {
  margin-left: 24rpx;
  display: flex;
  flex-direction: column;
}

.profile__name {
  font-size: 36rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

.profile__phone {
  margin-top: 10rpx;
  font-size: 26rpx;
  color: var(--color-text-primary);
  opacity: 0.75;
}

/* ---------------- 实体会员卡 ---------------- */
.mcard {
  position: relative;
  margin: -24rpx 24rpx 0;
  padding: 28rpx 28rpx 32rpx;
  border-radius: 32rpx;
  background: var(--color-gold-card-gradient);
  box-shadow: var(--shadow-md);
  overflow: hidden;
}

/* 右上角径向高光 */
.mcard::before {
  content: '';
  position: absolute;
  top: -80rpx;
  right: -80rpx;
  width: 280rpx;
  height: 280rpx;
  border-radius: var(--radius-round);
  background: radial-gradient(circle, rgba(255, 255, 255, 0.5) 0%, rgba(255, 255, 255, 0) 70%);
  pointer-events: none;
}

.mcard__top {
  position: relative;
  z-index: 1;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.mcard__level {
  padding: 6rpx 22rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.5);
  font-size: 24rpx;
  font-weight: 700;
  color: var(--color-text-primary);
}

.mcard__entry {
  font-size: 24rpx;
  color: var(--color-text-primary);
  opacity: 0.8;
}

.mcard__tip {
  display: block;
  position: relative;
  z-index: 1;
  margin-top: 16rpx;
  font-size: 22rpx;
  color: var(--color-text-primary);
  opacity: 0.65;
}

.mcard__stats {
  position: relative;
  z-index: 1;
  display: flex;
  margin-top: 24rpx;
}

.mcard__item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
}

.mcard__item + .mcard__item::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 1rpx;
  height: 44rpx;
  background: rgba(51, 51, 51, 0.15);
}

.mcard__num {
  font-size: 40rpx;
  font-weight: 700;
  color: var(--color-text-primary);
}

.mcard__label {
  margin-top: 6rpx;
  font-size: 22rpx;
  color: var(--color-text-primary);
  opacity: 0.65;
}

/* ---------------- 我的订单白卡 ---------------- */
.ocard {
  margin: 24rpx 24rpx 0;
  padding: 24rpx 0 28rpx;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
  box-shadow: var(--shadow-card);
}

.ocard__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 28rpx 20rpx;
}

.ocard__title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

.ocard__all {
  font-size: 24rpx;
  color: var(--color-text-hint);
}

.stats {
  display: flex;
}

.stat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.stat__num {
  font-size: 40rpx;
  font-weight: 700;
  color: var(--color-text-primary);
}

.stat__label {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: var(--color-text-hint);
}

/* ---------------- 常用功能分组 ---------------- */
.group {
  margin: 24rpx 24rpx 0;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
  box-shadow: var(--shadow-card);
  overflow: hidden;
}

.grow {
  position: relative;
  display: flex;
  align-items: center;
  height: 102rpx;
  padding: 0 28rpx;
  box-sizing: border-box;
}

.grow + .grow::after {
  content: '';
  position: absolute;
  top: 0;
  left: 28rpx;
  right: 0;
  height: 1rpx;
  background: var(--border-light);
}

.grow__label {
  font-size: 28rpx;
  color: var(--color-text-primary);
}

.grow__value {
  margin-left: auto;
  font-size: 26rpx;
  color: var(--color-text-hint);
}

.grow__arrow {
  margin-left: 10rpx;
  font-size: 30rpx;
  color: var(--color-text-hint);
}

/* ---------------- 退出登录 ---------------- */
.logout {
  margin-top: 48rpx;
  text-align: center;
  font-size: 26rpx;
  color: var(--color-text-hint);
}
</style>
