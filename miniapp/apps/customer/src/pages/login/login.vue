<template>
  <view class="login">
    <view class="brand">
      <text class="brand__title">心飞外卖</text>
      <text class="brand__slogan">手机号验证码登录，未注册自动注册</text>
    </view>

    <view class="field">
      <text class="field__label">手机号</text>
      <input
        class="field__input"
        type="number"
        maxlength="11"
        placeholder="请输入手机号"
        placeholder-class="field__placeholder"
        v-model="phone"
      />
    </view>

    <view class="field">
      <text class="field__label">验证码</text>
      <input
        class="field__input"
        type="number"
        maxlength="6"
        placeholder="请输入 6 位验证码"
        placeholder-class="field__placeholder"
        v-model="code"
      />
      <text class="field__side" :class="{ 'field__side--off': counting }" @tap="sendCode">
        {{ counting ? countdown + 's' : '获取验证码' }}
      </text>
    </view>

    <button class="btn-primary" :loading="submitting" @tap="doLogin">登录</button>

    <view class="agree">
      <checkbox :checked="agreed" color="#ffc200" @tap="agreed = !agreed" />
      <text class="agree__text">我已阅读并同意</text>
      <text class="agree__link" @tap="showAgreement('user')">《用户协议》</text>
      <text class="agree__link" @tap="showAgreement('privacy')">《隐私政策》</text>
    </view>

    <view class="tip">
      <text class="tip__text">未注册的手机号验证后将自动创建账号</text>
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
      timer: null
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
      customerAuth.login(this.phone, this.code).then(() => {
        uni.switchTab({ url: '/pages/index/index' })
      }).catch(() => {}).then(() => {
        this.submitting = false
      })
    }
  }
}
</script>

<style>
.login {
  padding: 120rpx 48rpx 48rpx;
  min-height: 100vh;
  background: var(--bg-page);
}

.brand {
  display: flex;
  flex-direction: column;
  margin-bottom: 64rpx;
}

.brand__title {
  font-size: 56rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

.brand__slogan {
  margin-top: 16rpx;
  font-size: 26rpx;
  color: var(--color-text-hint);
}

.field {
  display: flex;
  align-items: center;
  padding: 24rpx 0;
  border-bottom: 1rpx solid var(--border-light);
}

.field__label {
  width: 120rpx;
  font-size: 28rpx;
  color: var(--color-text-secondary);
}

.field__input {
  flex: 1;
  font-size: 30rpx;
  color: var(--color-text-primary);
}

.field__placeholder {
  color: var(--color-text-placeholder);
}

.field__side {
  padding-left: 24rpx;
  font-size: 26rpx;
  color: var(--color-primary-text-strong);
}

.field__side--off {
  color: var(--color-text-disabled);
}

.btn-primary {
  margin-top: 64rpx;
  height: 88rpx;
  line-height: 88rpx;
  border-radius: 44rpx;
  font-size: 32rpx;
  color: var(--color-primary-text);
  background: var(--color-primary-gradient);
}

.agree {
  display: flex;
  align-items: center;
  margin-top: 32rpx;
  font-size: 24rpx;
}

.agree__text {
  color: var(--color-text-hint);
}

.agree__link {
  color: var(--color-primary-text-strong);
}

.tip {
  margin-top: 48rpx;
  padding: 24rpx;
  border-radius: var(--radius-md);
  background: var(--color-primary-tint);
}

.tip__text {
  font-size: 22rpx;
  line-height: 34rpx;
  color: var(--color-text-secondary);
}
</style>
