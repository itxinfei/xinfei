<template>
  <view class="nowifi">
    <!-- 纯 CSS 断网插画：无需图片资源，跨端一致 -->
    <view class="icon">
      <view class="icon__wifi">
        <view class="icon__arc icon__arc--1"></view>
        <view class="icon__arc icon__arc--2"></view>
        <view class="icon__dot"></view>
        <view class="icon__slash"></view>
      </view>
    </view>
    <text class="title">网络连接异常</text>
    <text class="desc">{{ checking ? '正在检测网络…' : (netError ? '网络仍不可用，请检查网络后重试' : '点击重新检测，网络恢复后自动返回') }}</text>
    <text class="btn btn--main" :class="{ 'btn--off': checking }" hover-class="hover-press" @tap="check">
      {{ checking ? '检测中…' : '重新检测' }}
    </text>
    <text class="btn btn--line" hover-class="hover-press" @tap="goHome">回到首页</text>
  </view>
</template>

<script>
export default {
  data() {
    return {
      checking: false,
      netError: false
    }
  },
  onLoad() {
    this.check()
  },
  methods: {
    // 无接口依赖：uni.getNetworkType 判链路，网络恢复即返回上一页
    check() {
      if (this.checking) return
      this.checking = true
      this.netError = false
      uni.getNetworkType({
        success: res => {
          if (res.networkType && res.networkType !== 'none') {
            this.backOrHome()
          } else {
            this.netError = true
          }
        },
        fail: () => {
          this.netError = true
        },
        complete: () => {
          this.checking = false
        }
      })
    },
    // 有历史返回来源页，无历史（深链接直接进入）兜底回首页
    backOrHome() {
      const pages = getCurrentPages()
      if (pages.length > 1) {
        uni.navigateBack()
      } else {
        this.goHome()
      }
    },
    goHome() {
      // 首页在 tabBar 上，navigateTo 打不开
      uni.switchTab({ url: '/pages/index/index' })
    }
  }
}
</script>

<style>
.nowifi {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 180rpx 64rpx 0;
  background: var(--bg-page);
  min-height: 100vh;
  box-sizing: border-box;
}

/* ---- 纯 CSS 断网插画 ---- */
.icon {
  margin-bottom: 40rpx;
}

.icon__wifi {
  position: relative;
  width: 180rpx;
  height: 140rpx;
}

.icon__arc {
  position: absolute;
  left: 50%;
  border: 8rpx solid var(--border-medium);
  border-top-color: transparent;
  border-left-color: transparent;
  border-right-color: transparent;
  border-radius: 50%;
}

.icon__arc--1 {
  top: 8rpx;
  width: 150rpx;
  height: 150rpx;
  margin-left: -75rpx;
}

.icon__arc--2 {
  top: 44rpx;
  width: 90rpx;
  height: 90rpx;
  margin-left: -45rpx;
}

.icon__dot {
  position: absolute;
  left: 50%;
  bottom: 4rpx;
  width: 26rpx;
  height: 26rpx;
  margin-left: -13rpx;
  border-radius: 50%;
  background: var(--border-medium);
}

.icon__slash {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 200rpx;
  height: 8rpx;
  margin-left: -100rpx;
  border-radius: 4rpx;
  background: var(--color-danger);
  transform: rotate(-45deg);
}

.title {
  font-size: 34rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

.desc {
  margin: 20rpx 0 48rpx;
  font-size: 26rpx;
  line-height: 38rpx;
  text-align: center;
  color: var(--color-text-hint);
}

/* ---- 按钮 ---- */
.btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  padding: 22rpx 0;
  box-sizing: border-box;
  border-radius: 999rpx;
  font-size: 30rpx;
}

.btn + .btn {
  margin-top: 24rpx;
}

.btn--main {
  font-weight: 600;
  color: var(--color-primary-text);
  background: var(--color-primary);
}

.btn--line {
  color: var(--color-primary-text-strong);
  border: 1rpx solid var(--color-primary-text-strong);
}

.btn--off {
  opacity: 0.5;
}
</style>
