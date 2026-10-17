<template>
  <view class="login">
    <view class="brand">
      <text class="brand__title">心飞骑手</text>
      <text class="brand__slogan">手机号 + 密码登录</text>
    </view>

    <view class="field">
      <text class="field__label">手机号</text>
      <input class="field__input" type="number" maxlength="11" v-model="phone" placeholder="请输入手机号" placeholder-class="field__placeholder" />
    </view>

    <view class="field">
      <text class="field__label">密码</text>
      <input class="field__input" password v-model="password" placeholder="请输入密码" placeholder-class="field__placeholder" />
    </view>

    <view class="row">
      <view class="remember">
        <x-checkbox :checked="rememberMe" @change="rememberMe = $event" />
        <text @tap="rememberMe = !rememberMe">记住我</text>
      </view>
    </view>

    <button class="btn-primary" :loading="submitting" @tap="doLogin">登录</button>

    <view class="agree">
      <x-checkbox :checked="agreed" @change="agreed = $event" />
      <text class="agree__text">我已阅读并同意</text>
      <text class="agree__link" @tap="showAgreement('user')">《用户协议》</text>
      <text class="agree__link" @tap="showAgreement('privacy')">《隐私政策》</text>
    </view>

    <view class="tip">
      <text class="tip__text">骑手账号由站点管理员开通，如需开号或重置密码请联系站长</text>
    </view>
  </view>
</template>

<script>
import { riderAuth } from '@xinfei/mini-shared'

const PHONE_RE = /^1[3-9]\d{9}$/

export default {
  data() {
    return {
      phone: '',
      password: '',
      rememberMe: false,
      submitting: false,
      agreed: false
    }
  },
  methods: {
    showAgreement(kind) {
      const title = kind === 'user' ? '用户协议' : '隐私政策'
      const content = kind === 'user'
        ? '欢迎使用心飞骑手。使用本服务即表示您同意遵守平台配送规则：按时取餐送达、规范配送行为、不私自取消订单。违规行为将影响接单权限。'
        : '我们重视您的个人信息保护。本应用仅收集手机号用于登录、实时位置用于接单配送与导航。未经您授权，我们不会向第三方共享您的个人信息。'
      uni.showModal({ title, content, showCancel: false })
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
      if (!this.password) {
        uni.showToast({ title: '请输入密码', icon: 'none' })
        return
      }
      this.submitting = true
      riderAuth.login(this.phone, this.password, this.rememberMe).then(() => {
        uni.switchTab({ url: '/pages/hall/hall' })
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

.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 24rpx;
}

.remember {
  display: flex;
  align-items: center;
  font-size: 26rpx;
  color: var(--color-text-secondary);
}

.btn-primary {
  margin-top: 48rpx;
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
