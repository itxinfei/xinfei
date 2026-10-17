<template>
  <view class="x-switch" :class="{ 'x-switch--on': checked }" @tap="toggle">
    <view class="x-switch__knob"></view>
  </view>
</template>

<script>
// view 实现的开关：原生 switch 的 color 属性不支持 CSS 变量，
// 改用本组件后开启色走 var(--color-primary)，随主题令牌联动
// easycom 自动注册：components/x-switch/x-switch.vue
export default {
  name: 'x-switch',
  props: {
    checked: { type: Boolean, default: false }
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
.x-switch {
  width: 88rpx;
  height: 48rpx;
  border-radius: 24rpx;
  background: var(--bg-input);
  border: 1rpx solid var(--border-medium);
  position: relative;
  flex-shrink: 0;
  box-sizing: border-box;
  transition: background 0.2s, border-color 0.2s;
}

.x-switch--on {
  background: var(--color-primary);
  border-color: var(--color-primary);
}

.x-switch__knob {
  position: absolute;
  top: 3rpx;
  left: 3rpx;
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  background: #ffffff;
  box-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.15);
  transition: transform 0.2s;
}

.x-switch--on .x-switch__knob {
  transform: translateX(40rpx);
}
</style>