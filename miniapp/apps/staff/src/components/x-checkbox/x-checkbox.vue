<template>
  <view class="x-checkbox" :class="{ 'x-checkbox--on': checked }" hover-class="hover-press" @tap="toggle">
    <view class="x-checkbox__box">
      <view v-if="checked" class="x-checkbox__tick"></view>
    </view>
    <slot></slot>
  </view>
</template>

<script>
// view 实现的复选框：原生 checkbox 的 color 属性不支持 CSS 变量，
// 改用本组件后选中色走 var(--color-primary)，随主题令牌联动
// easycom 自动注册：components/x-checkbox/x-checkbox.vue
export default {
  name: 'x-checkbox',
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

.x-checkbox__tick {
  width: 16rpx;
  height: 9rpx;
  border-left: 3rpx solid #ffffff;
  border-bottom: 3rpx solid #ffffff;
  transform: rotate(-45deg);
  margin-top: -4rpx;
}
</style>