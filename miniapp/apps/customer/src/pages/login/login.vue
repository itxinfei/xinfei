<template>
  <view class="login">
    <!-- 金色品牌 hero -->
    <view class="hero" :style="{ paddingTop: (statusBarHeight + 24) + 'px' }">
      <view class="hero__ring hero__ring--a"></view>
      <view class="hero__ring hero__ring--b"></view>
      <view class="hero__spark hero__spark--1"></view>
      <view class="hero__spark hero__spark--2"></view>
      <view class="hero__spark hero__spark--3"></view>
      <view class="hero__spark hero__spark--4"></view>

      <view class="hero__brand">
        <view class="hero__badge">
          <image class="hero__logo" src="/static/brand/logo.png" mode="aspectFit" />
        </view>
        <text class="hero__name">心飞外卖</text>
        <text class="hero__slogan">美味，如期而至</text>
      </view>

      <!-- 暖色弧形过渡（CSS 椭圆模拟旧版波浪，微信小程序不支持内联 SVG） -->
      <view class="hero__arc"></view>
    </view>

    <!-- 登录表单 -->
    <view class="form">
      <text class="form__title">手机号登录</text>
      <text class="form__sub">未注册的手机号验证后将自动创建账号</text>

      <view class="input" :class="{ 'input--on': phoneFocused }">
        <input
          class="input__el"
          type="number"
          maxlength="11"
          placeholder="请输入手机号"
          placeholder-class="input__ph"
          v-model="phone"
          @focus="phoneFocused = true"
          @blur="phoneFocused = false"
        />
      </view>

      <view class="input" :class="{ 'input--on': codeFocused }">
        <input
          class="input__el"
          type="number"
          maxlength="6"
          placeholder="请输入 6 位验证码"
          placeholder-class="input__ph"
          v-model="code"
          @focus="codeFocused = true"
          @blur="codeFocused = false"
        />
        <text class="input__btn" :class="{ 'input__btn--off': counting }" @tap="sendCode">
          {{ counting ? countdown + 's' : '获取验证码' }}
        </text>
      </view>

      <button class="submit" :loading="submitting" @tap="doLogin">登录</button>

      <!-- #ifdef MP-WEIXIN -->
      <button class="wx" :loading="wxLogging" @tap="doWxLogin">微信一键登录</button>
      <!-- #endif -->

      <view class="agree">
        <view class="agree__check">
          <x-checkbox round :checked="agreed" @change="agreed = $event" />
        </view>
        <text class="agree__text">我已阅读并同意</text>
        <text class="agree__link" @tap="showAgreement('user')">《用户协议》</text>
        <text class="agree__link" @tap="showAgreement('privacy')">《隐私政策》</text>
      </view>
    </view>
  </view>
</template>

<script>
import { customerAuth } from '@xinfei/mini-shared'

const PHONE_RE = /^1[3-9]\d{9}$/

export default {
  data() {
    return {
      phone: '',
      code: '',
      countdown: 60,
      counting: false,
      submitting: false,
      agreed: false,
      timer: null,
      // 微信一键登录 needBind 场景下的一次性绑定凭据（5 分钟有效，登录成功即绑定 openid）
      wxTicket: '',
      wxLogging: false,
      // 输入框聚焦态（驱动金色描边）与状态栏高度（hero 顶到状态栏下）
      phoneFocused: false,
      codeFocused: false,
      statusBarHeight: 0
    }
  },
  onLoad() {
    try {
      const info = uni.getWindowInfo ? uni.getWindowInfo() : uni.getSystemInfoSync()
      this.statusBarHeight = info.statusBarHeight || 0
    } catch (e) {
      this.statusBarHeight = 0
    }
  },
  onUnload() {
    this.clearTimer()
  },
  methods: {
    clearTimer() {
      if (this.timer) {
        clearInterval(this.timer)
        this.timer = null
      }
    },
    startCounting() {
      this.counting = true
      this.countdown = 60
      this.timer = setInterval(() => {
        this.countdown -= 1
        if (this.countdown <= 0) {
          this.counting = false
          this.clearTimer()
        }
      }, 1000)
    },
    showAgreement(kind) {
      const title = kind === 'user' ? '用户协议' : '隐私政策'
      const content = kind === 'user'
        ? '欢迎使用心飞外卖。使用本服务即表示您同意遵守本平台的使用规则：合法使用账号、不发布违规内容、不恶意下单。平台有权对违规账号进行限制或封禁。'
        : '我们重视您的个人信息保护。本应用仅收集手机号用于注册登录、收货地址用于配送、订单信息用于履约。未经您授权，我们不会向第三方共享您的个人信息。'
      uni.showModal({ title, content, showCancel: false })
    },
    sendCode() {
      if (this.counting) return
      if (!this.agreed) {
        uni.showToast({ title: '请先勾选同意用户协议与隐私政策', icon: 'none' })
        return
      }
      if (!PHONE_RE.test(this.phone)) {
        uni.showToast({ title: '手机号格式不正确', icon: 'none' })
        return
      }
      customerAuth.sendCode(this.phone).then(() => {
        uni.showToast({ title: '验证码已发送', icon: 'none' })
        this.startCounting()
      }).catch(() => {})
    },
    doLogin() {
      if (!this.agreed) {
        uni.showToast({ title: '请先勾选同意用户协议与隐私政策', icon: 'none' })
        return
      }
      if (!PHONE_RE.test(this.phone)) {
        uni.showToast({ title: '请输入正确手机号', icon: 'none' })
        return
      }
      if (!this.code) {
        uni.showToast({ title: '请输入验证码', icon: 'none' })
        return
      }
      this.submitting = true
      customerAuth.login(this.phone, this.code, this.wxTicket || undefined).then(() => {
        this.wxTicket = ''
        uni.switchTab({ url: '/pages/index/index' })
      }).catch(() => {}).then(() => {
        this.submitting = false
      })
    },
    doWxLogin() {
      if (!this.agreed) {
        uni.showToast({ title: '请先勾选同意用户协议与隐私政策', icon: 'none' })
        return
      }
      this.wxLogging = true
      customerAuth.wxLogin().then(data => {
        if (data && data.needBind) {
          // 未绑定：暂存一次性凭据，引导手机号验证码登录后完成绑定
          this.wxTicket = data.wxTicket
          uni.showToast({ title: '请用手机号验证码登录完成绑定', icon: 'none', duration: 3000 })
          return
        }
        uni.switchTab({ url: '/pages/index/index' })
      }).catch(err => {
        if (err && err.cancelled) return
        // 仅 uni.login 调用失败需自行提示；后端业务错误（code!=1）已由 request.js 统一 toast
        if (err && err.wxLoginFail) {
          uni.showToast({ title: '微信登录失败，请重试', icon: 'none' })
        }
      }).then(() => {
        this.wxLogging = false
      })
    }
  }
}
</script>

<style>
.login {
  min-height: 100vh;
  background: var(--bg-warm);
}

/* ---------------- 金色 hero ---------------- */
.hero {
  position: relative;
  overflow: hidden;
  padding-bottom: 96rpx;
  background: var(--color-login-hero-gradient);
}

/* 两束半透明高光 + 装饰圆环、星点（旧 H5 同语言） */
.hero::before {
  content: '';
  position: absolute;
  top: -120rpx;
  left: 0;
  right: 0;
  height: 320rpx;
  background: radial-gradient(140% 55% at 50% 0%, rgba(255, 255, 255, 0.42), rgba(255, 255, 255, 0) 60%);
  pointer-events: none;
}

.hero__ring {
  position: absolute;
  border: 4rpx solid rgba(255, 255, 255, 0.28);
  border-radius: var(--radius-round);
  pointer-events: none;
}

.hero__ring--a {
  width: 380rpx;
  height: 380rpx;
  top: 80rpx;
  right: -130rpx;
}

.hero__ring--b {
  width: 300rpx;
  height: 300rpx;
  bottom: 40rpx;
  left: -120rpx;
  border-color: rgba(255, 255, 255, 0.2);
}

.hero__spark {
  position: absolute;
  width: 12rpx;
  height: 12rpx;
  border-radius: var(--radius-round);
  background: rgba(255, 255, 255, 0.85);
  box-shadow: 0 0 16rpx rgba(255, 255, 255, 0.8);
  animation: sparkTwinkle 3.4s ease-in-out infinite;
}

.hero__spark--1 { top: 130rpx; left: 96rpx; }
.hero__spark--2 { top: 200rpx; right: 120rpx; width: 16rpx; height: 16rpx; animation-delay: 0.6s; }
.hero__spark--3 { top: 300rpx; left: 150rpx; width: 10rpx; height: 10rpx; animation-delay: 1.2s; }
.hero__spark--4 { top: 110rpx; right: 240rpx; width: 10rpx; height: 10rpx; animation-delay: 1.8s; }

.hero__brand {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 24rpx;
}

.hero__badge {
  width: 176rpx;
  height: 176rpx;
  border-radius: var(--radius-round);
  background: var(--bg-card);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 12rpx 36rpx rgba(190, 110, 0, 0.22), 0 36rpx 88rpx rgba(255, 150, 0, 0.28);
  animation: logoFloat 3.2s ease-in-out infinite;
}

.hero__logo {
  width: 140rpx;
  height: 140rpx;
}

.hero__name {
  margin-top: 28rpx;
  font-size: 48rpx;
  font-weight: 700;
  letter-spacing: 8rpx;
  color: var(--color-primary-text);
}

.hero__slogan {
  margin-top: 14rpx;
  font-size: 26rpx;
  letter-spacing: 10rpx;
  color: var(--color-primary-text-strong);
}

/* 底部弧形过渡：暖底椭圆上压 hero 底边 */
.hero__arc {
  position: absolute;
  left: -30%;
  bottom: -40rpx;
  width: 160%;
  height: 80rpx;
  border-radius: 50%;
  background: var(--bg-warm);
}

@keyframes logoFloat {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-10rpx); }
}

@keyframes sparkTwinkle {
  0%, 100% { opacity: 0.35; transform: scale(0.8); }
  50% { opacity: 1; transform: scale(1.15); }
}

/* ---------------- 表单 ---------------- */
.form {
  position: relative;
  padding: 48rpx 56rpx 64rpx;
}

.form__title {
  display: block;
  text-align: center;
  font-size: 40rpx;
  font-weight: 700;
  color: var(--color-primary-text);
}

.form__sub {
  display: block;
  margin: 16rpx 0 56rpx;
  text-align: center;
  font-size: 24rpx;
  line-height: 36rpx;
  color: rgba(90, 62, 16, 0.75);
}

.input {
  display: flex;
  align-items: center;
  height: 104rpx;
  margin-bottom: 32rpx;
  padding: 0 36rpx;
  border: 3rpx solid transparent;
  border-radius: 32rpx;
  background: var(--bg-input-warm);
  transition: border-color var(--transition-fast), background-color var(--transition-fast), box-shadow var(--transition-fast);
}

.input--on {
  background: var(--bg-card);
  border-color: var(--color-primary);
  box-shadow: 0 0 0 8rpx rgba(255, 194, 0, 0.1);
}

.input__el {
  flex: 1;
  height: 100%;
  font-size: 34rpx;
  font-weight: 500;
  color: var(--color-primary-text);
}

.input__ph {
  font-size: 32rpx;
  font-weight: 400;
  color: var(--text-secondary);
}

.input__btn {
  flex-shrink: 0;
  margin-left: 24rpx;
  padding: 0 28rpx;
  height: 72rpx;
  line-height: 72rpx;
  border-radius: 999rpx;
  background: var(--color-primary-tint);
  font-size: 26rpx;
  font-weight: 600;
  color: var(--color-primary-text);
}

.input__btn--off {
  color: var(--text-secondary);
}

.submit {
  margin-top: 40rpx;
  height: 96rpx;
  line-height: 96rpx;
  border-radius: 999rpx;
  font-size: 34rpx;
  font-weight: 600;
  letter-spacing: 6rpx;
  color: var(--color-primary-text-strong-active);
  background: var(--color-primary-gradient);
  box-shadow: var(--shadow-primary);
}

.submit:active {
  transform: scale(0.98);
}

.wx {
  margin-top: 28rpx;
  height: 96rpx;
  line-height: 96rpx;
  border-radius: 999rpx;
  font-size: 32rpx;
  font-weight: 500;
  color: var(--color-primary-text-strong);
  background: var(--color-primary-tint);
}

.wx:active {
  transform: scale(0.98);
}

.agree {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 44rpx;
  font-size: 24rpx;
  line-height: 36rpx;
}

.agree__check {
  margin-right: 12rpx;
  display: flex;
  align-items: center;
}

.agree__text {
  color: rgba(90, 62, 16, 0.85);
}

.agree__link {
  color: var(--color-primary-text);
  font-weight: 600;
}
</style>
