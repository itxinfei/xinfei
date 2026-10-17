<template>
  <view class="login">
    <view class="brand">
      <text class="brand__title">心飞门店</text>
      <text class="brand__slogan">员工账号登录 · 用户名 + 密码</text>
    </view>

    <view class="field">
      <text class="field__label">用户名</text>
      <input class="field__input" v-model="username" placeholder="请输入员工用户名" placeholder-class="field__placeholder" />
    </view>

    <view class="field">
      <text class="field__label">密码</text>
      <input class="field__input" password v-model="password" placeholder="请输入密码" placeholder-class="field__placeholder" />
    </view>

    <button class="btn-primary" :loading="submitting" @tap="doLogin">登录</button>

    <view class="agree">
      <checkbox :checked="agreed" color="#ffc200" @tap="agreed = !agreed" />
      <text class="agree__text">我已阅读并同意</text>
      <text class="agree__link" @tap="showAgreement('user')">《用户协议》</text>
      <text class="agree__link" @tap="showAgreement('privacy')">《隐私政策》</text>
    </view>

    <view class="tip">
      <text class="tip__text">员工账号由门店管理员分配，如需开号或重置密码请联系店长</text>
    </view>
  </view>
</template>

<script>
import { employeeAuth } from '@xinfei/mini-shared'

export default {
  data() {
    return {
      username: '',
      password: '',
      submitting: false,
      agreed: false
    }
  },
  methods: {
    showAgreement(kind) {
      const title = kind === 'user' ? '用户协议' : '隐私政策'
      const content = kind === 'user'
        ? '欢迎使用心飞门店。使用本服务即表示您同意遵守平台门店操作规范：及时处理订单、如实更新门店状态、妥善保管账号密码。'
        : '我们重视您的个人信息保护。本应用仅收集员工账号信息用于登录与门店管理操作。未经您授权，我们不会向第三方共享您的个人信息。'
      uni.showModal({ title, content, showCancel: false })
    },
    doLogin() {
      if (!this.agreed) {
        uni.showToast({ title: '请先勾选同意用户协议与隐私政策', icon: 'none' })
        return
      }
      if (!this.username) {
        uni.showToast({ title: '请输入用户名', icon: 'none' })
        return
      }
      if (!this.password) {
        uni.showToast({ title: '请输入密码', icon: 'none' })
        return
      }
      this.submitting = true
      employeeAuth.login(this.username, this.password).then(() => {
        uni.switchTab({ url: '/pages/workbench/workbench' })
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
  width: 140rpx;
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
