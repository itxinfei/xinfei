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

    <view class="tip">
      <text class="tip__text">
        员工登录走 /employee/login（用户名 + 密码，密码强度与账号锁定在服务端）。
        返回体为员工资料 Map（id、username、name、role、tenantId 等），tenantId 用于门店状态接口拼路径。
      </text>
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
      submitting: false
    }
  },
  methods: {
    doLogin() {
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
