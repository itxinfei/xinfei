<template>
  <view v-if="visible" class="x-modal">
    <view class="x-modal__mask" @tap="cancel"></view>
    <view class="x-modal__box">
      <text class="x-modal__title">{{ title }}</text>
      <text v-if="desc" class="x-modal__desc">{{ desc }}</text>
      <input
        class="x-modal__input"
        v-model="inner"
        :placeholder="placeholder"
        placeholder-class="x-modal__placeholder"
        :maxlength="200"
        :focus="visible"
      />
      <view class="x-modal__btns">
        <text class="x-modal__btn x-modal__btn--cancel" hover-class="hover-press" @tap="cancel">取消</text>
        <text class="x-modal__btn x-modal__btn--ok" hover-class="hover-press" @tap="confirm">确定</text>
      </view>
    </view>
  </view>
</template>

<script>
export default {
  name: 'x-input-modal',
  props: {
    visible: { type: Boolean, default: false },
    title: { type: String, default: '提示' },
    desc: { type: String, default: '' },
    placeholder: { type: String, default: '' }
  },
  emits: ['confirm', 'cancel'],
  data() {
    return { inner: '' }
  },
  watch: {
    visible(v) {
      if (v) this.inner = ''
    }
  },
  methods: {
    confirm() {
      this.$emit('confirm', this.inner)
    },
    cancel() {
      this.$emit('cancel')
    }
  }
}
</script>

<style>
.x-modal {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 999;
  display: flex;
  align-items: center;
  justify-content: center;
}

.x-modal__mask {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
}

.x-modal__box {
  position: relative;
  width: 600rpx;
  padding: 40rpx 32rpx 0;
  border-radius: var(--radius-xl);
  background: var(--bg-card);
}

.x-modal__title {
  display: block;
  text-align: center;
  font-size: 32rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

.x-modal__desc {
  display: block;
  margin-top: 16rpx;
  text-align: center;
  font-size: 26rpx;
  line-height: 38rpx;
  color: var(--color-text-hint);
}

.x-modal__input {
  margin-top: 32rpx;
  padding: 20rpx 24rpx;
  border-radius: var(--radius-md);
  font-size: 28rpx;
  background: var(--bg-input);
  color: var(--color-text-primary);
}

.x-modal__placeholder {
  color: var(--color-text-placeholder);
}

.x-modal__btns {
  display: flex;
  margin-top: 32rpx;
  border-top: 1rpx solid var(--border-light);
}

.x-modal__btn {
  flex: 1;
  padding: 26rpx 0;
  text-align: center;
  font-size: 30rpx;
}

.x-modal__btn--cancel {
  color: var(--color-text-secondary);
  border-right: 1rpx solid var(--border-light);
}

.x-modal__btn--ok {
  color: var(--color-primary-text-strong);
  font-weight: 600;
}
</style>