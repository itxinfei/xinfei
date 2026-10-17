<template>
  <view class="x-empty">
    <view class="x-empty__icon">
      <view class="x-empty__doc"></view>
      <view class="x-empty__line x-empty__line--1"></view>
      <view class="x-empty__line x-empty__line--2"></view>
      <view class="x-empty__line x-empty__line--3"></view>
    </view>
    <text class="x-empty__text">{{ text }}</text>
    <text v-if="sub" class="x-empty__sub">{{ sub }}</text>
  </view>
</template>

<script>
// 纯 CSS 空态插画组件：无需图片设计资源，跨端一致
// easycom 自动注册：components/x-empty/x-empty.vue
export default {
  name: 'x-empty',
  props: {
    text: { type: String, default: '暂无数据' },
    sub: { type: String, default: '' }
  }
}
</script>

<style>
.x-empty {
  padding: 100rpx 48rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.x-empty__icon {
  position: relative;
  width: 120rpx;
  height: 140rpx;
  margin-bottom: 28rpx;
}

.x-empty__doc {
  position: absolute;
  top: 0;
  left: 0;
  width: 120rpx;
  height: 140rpx;
  border: 4rpx solid var(--border-medium);
  border-radius: 12rpx;
  background: var(--bg-card);
  box-sizing: border-box;
}

.x-empty__line {
  position: absolute;
  left: 24rpx;
  height: 6rpx;
  border-radius: 3rpx;
  background: var(--border-medium);
}

.x-empty__line--1 {
  top: 36rpx;
  width: 72rpx;
}

.x-empty__line--2 {
  top: 58rpx;
  width: 52rpx;
}

.x-empty__line--3 {
  top: 80rpx;
  width: 64rpx;
}

.x-empty__text {
  font-size: 26rpx;
  color: var(--color-text-hint);
}

.x-empty__sub {
  margin-top: 12rpx;
  font-size: 24rpx;
  line-height: 36rpx;
  color: var(--color-text-placeholder);
  text-align: center;
}
</style>