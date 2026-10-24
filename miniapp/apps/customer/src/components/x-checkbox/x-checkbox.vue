<template>
  <view class="x-checkbox" :class="{ 'x-checkbox--on': checked, 'x-checkbox--round': round }" hover-class="hover-press" @tap="toggle">
    <view class="x-checkbox__box">
      <view v-if="checked" class="x-checkbox__tick"></view>
    </view>
    <slot></slot>
  </view>
</template>

<script>
// view 实现的复选框：原生 checkbox 的 color 属性不支持 CSS 变量，
// 改用本组件后选中色走 var(--color-primary)，随主题令牌联动
// round=true 为登录页用的圆形暖金描边款；默认方框用于其他页面
// easycom 自动注册：components/x-checkbox/x-checkbox.vue
export default {
  name: 'x-checkbox',
  props: {
    checked: { type: Boolean, default: false },
    round: { type: Boolean, default: false }
  },
  emits: ['change'],
  methods: {
    toggle() {
      this.$emit('change', !this.checked)
    }
  }
}
</script>

<style>
.x-checkbox {
  display: inline-flex;
  align-items: center;
}

.x-checkbox__box {
  width: 34rpx;
  height: 34rpx;
  border: 2rpx solid var(--border-medium);
  border-radius: 6rpx;
  background: var(--bg-card);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-sizing: border-box;
}

.x-checkbox--on .x-checkbox__box {
  background: var(--color-primary);
  border-color: var(--color-primary);
}

/* 圆形款（登录页协议）：暖金描边，选中后走主色渐变 */
.x-checkbox--round .x-checkbox__box {
  width: 36rpx;
  height: 36rpx;
  border-radius: var(--radius-round);
  border-color: var(--border-warm);
}

.x-checkbox--on.x-checkbox--round .x-checkbox__box {
  background: var(--color-primary-gradient);
  border-color: var(--color-primary);
  box-shadow: 0 4rpx 12rpx rgba(255, 209, 0, 0.35);
}

.x-checkbox__tick {
  width: 16rpx;
  height: 9rpx;
  border-left: 3rpx solid #ffffff;
  border-bottom: 3rpx solid #ffffff;
  transform: rotate(-45deg);
  margin-top: -4rpx;
}
</style>