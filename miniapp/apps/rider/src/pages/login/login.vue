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
      <label class="remember" @tap="rememberMe = !rememberMe">
        <checkbox :checked="rememberMe" color="#ffc200" />
        <text>记住我</text>
      </label>
    </view>

    <button class="btn-primary" :loading="submitting" @tap="doLogin">登录</button>

    <view class="tip">
      <text class="tip__text">
        骑手端登录接口为 /api/rider/login（RiderAuthController），勾选「记住我」时后端下发持久 cookie；
        小程序无 cookie 容器，该分支同样依赖后端的 token 会话通道改造。
      </text>
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
      submitting: false
    }
  },
  methods: {
    doLogin() {
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
