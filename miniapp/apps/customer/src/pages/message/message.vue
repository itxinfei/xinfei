<template>
  <view class="msg">
    <!-- 顶栏右侧「全部已读」：有未读才显示 -->
    <view class="toolbar">
      <text class="toolbar__unread">未读 {{ unreadCount }} 条</text>
      <text
        v-if="unreadCount > 0"
        class="toolbar__readall"
        :class="{ 'toolbar__readall--off': busy('readall') }"
        hover-class="hover-press"
        @tap="markAllRead"
      >全部已读</text>
    </view>

    <scroll-view class="list" scroll-y @scrolltolower="loadMore">
      <view
        v-for="msg in messages"
        :key="msg.id"
        class="card"
        :class="{ 'card--unread': msg.status === 1 }"
        hover-class="hover-press"
        @tap="openDetail(msg)"
      >
        <view v-if="msg.status === 1" class="card__dot"></view>
        <view class="card__head">
          <text class="card__title">{{ msg.title || '系统消息' }}</text>
          <text class="card__tag" :class="'card__tag--t' + msg.pushType">{{ pushTypeName(msg.pushType) }}</text>
        </view>
        <text class="card__content">{{ msg.content }}</text>
        <text class="card__time">{{ formatTime(msg.createTime) }}</text>
      </view>

      <view v-if="listError" class="list__error">
        <text>加载失败</text>
        <text class="list__retry" @tap="reload">重试</text>
      </view>
      <x-empty v-if="!messages.length && !loading && !listError" text="暂无消息" sub="订单和优惠通知会显示在这里" />
      <view v-if="loading" class="list__more"><text>加载中…</text></view>
      <view v-if="!hasMore && messages.length" class="list__more"><text>没有更多了</text></view>
    </scroll-view>

    <!-- 消息详情弹层：点开即标记已读 -->
    <view v-if="detail.show" class="mask" @tap="closeDetail">
      <view class="dialog" @tap.stop>
        <text class="dialog__title">{{ detail.title }}</text>
        <scroll-view class="dialog__body" scroll-y>
          <text class="dialog__content">{{ detail.content || '（无内容）' }}</text>
        </scroll-view>
        <text class="dialog__time">{{ formatTime(detail.createTime) }}</text>
        <view class="dialog__actions">
          <text class="dabtn" hover-class="hover-press" @tap="goFromDetail('order')">查看订单</text>
          <text class="dabtn dabtn--main" hover-class="hover-press" @tap="goFromDetail('index')">
            {{ detail.pushType === 4 ? '去点餐使用' : '去点餐' }}
          </text>
        </view>
        <text class="dialog__close" hover-class="hover-press" @tap="closeDetail">关闭</text>
      </view>
    </view>
  </view>
</template>

<script>
import { http, qs, listState, busyLock } from '@xinfei/mini-shared'

export default {
  data() {
    return Object.assign(listState(), busyLock(), {
      messages: [],
      page: 1,
      pageSize: 15,
      hasMore: true,
      loading: false,
      unreadCount: 0,
      detail: { show: false, title: '', content: '', createTime: '', pushType: null }
    })
  },
  onLoad() {
    this.load(true)
    this.fetchUnreadCount()
  },
  onPullDownRefresh() {
    this.load(true).then(() => {
      uni.stopPullDownRefresh()
      this.fetchUnreadCount()
    })
  },
  methods: {
    load(reset) {
      if (this.loading) return Promise.resolve()
      if (reset) {
        this.page = 1
        this.hasMore = true
      }
      this.loading = true
      if (reset) this.listError = false
      return http.get(qs('/recommend/messages', { page: this.page, pageSize: this.pageSize }), null, { silent: true })
        .then(data => {
          const records = (data && data.records) || []
          this.messages = reset ? records : this.messages.concat(records)
          this.hasMore = records.length >= this.pageSize
          // 页码仅在成功且确有下一页后推进，失败重试仍落在同一页
          if (this.hasMore) this.page += 1
        })
        .catch(() => { this.listError = true })
        .then(() => { this.loading = false })
    },
    loadMore() {
      if (this.loading || !this.hasMore) return
      this.load(false)
    },
    reload() {
      this.load(true)
    },
    fetchUnreadCount() {
      http.get('/recommend/messages/unread-count', null, { silent: true })
        .then(count => { this.unreadCount = Number(count) || 0 })
        .catch(() => {})
    },
    openDetail(msg) {
      this.detail = {
        show: true,
        title: msg.title || '系统消息',
        content: msg.content || '',
        createTime: msg.createTime,
        pushType: msg.pushType
      }
      if (msg.status === 1) {
        this.markRead(msg)
      }
    },
    // 单条已读：PUT /recommend/messages/{id}/read，本地回填状态并递减未读角标
    markRead(msg) {
      http.put('/recommend/messages/' + msg.id + '/read', null, { silent: true }).then(() => {
        msg.status = 2
        if (this.unreadCount > 0) this.unreadCount -= 1
      }).catch(() => {})
    },
    markAllRead() {
      const key = 'readall'
      if (this.busy(key)) return
      const unread = this.messages.filter(m => m.status === 1)
      if (!unread.length) {
        uni.showToast({ title: '暂无未读消息', icon: 'none' })
        return
      }
      uni.showModal({
        title: '全部已读',
        content: '确定将所有未读消息标记为已读？',
        success: r => {
          if (!r.confirm) return
          this.setBusy(key, true)
          // 单次批量已读接口（Web 端 2026-09-17 已从 N+1 单条改为 read-all）
          http.put('/recommend/messages/read-all').then(() => {
            this.messages.forEach(m => { if (m.status === 1) m.status = 2 })
            this.unreadCount = 0
            uni.showToast({ title: '已全部标记为已读', icon: 'success' })
          }).catch(() => {}).then(() => { this.setBusy(key, false) })
        }
      })
    },
    closeDetail() {
      this.detail.show = false
    },
    goFromDetail(target) {
      this.detail.show = false
      // tabBar 页必须 switchTab：去点餐回首页凑单/用券，查看订单回订单列表
      uni.switchTab({ url: target === 'order' ? '/pages/order/list' : '/pages/index/index' })
    },
    pushTypeName(type) {
      const map = { 1: '弹窗', 2: '通知', 3: '短信', 4: '优惠券' }
      return map[type] || '其他'
    },
    // 今天/昨天归并显示；iOS 无法解析「yyyy-MM-dd HH:mm:ss」，统一 - → / 再解析
    formatTime(dateStr) {
      if (!dateStr) return ''
      const d = new Date(String(dateStr).replace(/-/g, '/'))
      if (isNaN(d.getTime())) return String(dateStr)
      const pad = n => String(n).padStart(2, '0')
      const date = d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate())
      const now = new Date()
      const today = now.getFullYear() + '-' + pad(now.getMonth() + 1) + '-' + pad(now.getDate())
      const yesterday = new Date(now.getTime() - 86400000)
      const yest = yesterday.getFullYear() + '-' + pad(yesterday.getMonth() + 1) + '-' + pad(yesterday.getDate())
      const time = pad(d.getHours()) + ':' + pad(d.getMinutes())
      if (date === today) return '今天 ' + time
      if (date === yest) return '昨天 ' + time
      return date + ' ' + time
    }
  }
}
</script>

<style>
.msg {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg-page);
}

/* ---------------- 顶栏 ---------------- */
.toolbar {
  flex-shrink: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20rpx 32rpx;
  background: var(--bg-card);
  border-bottom: 1rpx solid var(--border-light);
}

.toolbar__unread {
  font-size: 24rpx;
  color: var(--color-text-hint);
}

.toolbar__readall {
  font-size: 26rpx;
  font-weight: 600;
  color: var(--color-primary-text);
}

.toolbar__readall--off {
  opacity: 0.5;
}

.list {
  flex: 1;
}

/* ---------------- 消息卡 ---------------- */
.card {
  position: relative;
  margin: 16rpx 24rpx 0;
  padding: 24rpx;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
  box-shadow: var(--shadow-card);
}

.card--unread {
  border: 1rpx solid var(--color-primary-tint-strong);
}

.card__dot {
  position: absolute;
  top: 28rpx;
  right: 28rpx;
  width: 16rpx;
  height: 16rpx;
  border-radius: var(--radius-round);
  background: var(--color-danger);
}

.card__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-right: 32rpx;
}

.card__title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text-primary);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

/* 推送类型标签：1 弹窗 / 2 通知 / 3 短信 / 4 优惠券 */
.card__tag {
  flex-shrink: 0;
  padding: 2rpx 14rpx;
  border-radius: var(--radius-sm);
  font-size: 20rpx;
  color: var(--color-text-secondary);
  background: var(--color-neutral-tint);
}

.card__tag--t4 {
  color: var(--color-danger-text);
  background: var(--color-danger-tint);
}

.card__tag--t2 {
  color: var(--color-primary-text-strong);
  background: var(--color-primary-tint);
}

.card__content {
  margin-top: 12rpx;
  font-size: 26rpx;
  line-height: 38rpx;
  color: var(--color-text-secondary);
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

.card__time {
  display: block;
  margin-top: 12rpx;
  font-size: 22rpx;
  color: var(--color-text-hint);
}

/* ---------------- 详情弹层 ---------------- */
.mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.55);
}

.dialog {
  width: 600rpx;
  max-height: 70vh;
  display: flex;
  flex-direction: column;
  padding: 40rpx 36rpx 28rpx;
  border-radius: var(--radius-xl);
  background: var(--bg-card);
  box-sizing: border-box;
}

.dialog__title {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--color-text-primary);
  text-align: center;
}

.dialog__body {
  flex: 1;
  margin-top: 24rpx;
  max-height: 40vh;
}

.dialog__content {
  font-size: 28rpx;
  line-height: 44rpx;
  color: var(--color-text-secondary);
}

.dialog__time {
  margin-top: 20rpx;
  font-size: 22rpx;
  color: var(--color-text-hint);
  text-align: right;
}

.dialog__actions {
  display: flex;
  gap: 20rpx;
  margin-top: 32rpx;
}

.dabtn {
  flex: 1;
  height: 80rpx;
  line-height: 78rpx;
  text-align: center;
  font-size: 26rpx;
  color: var(--color-text-secondary);
  border: 1rpx solid var(--border-medium);
  border-radius: var(--radius-round);
  box-sizing: border-box;
}

.dabtn--main {
  font-weight: 600;
  color: var(--color-primary-text);
  background: var(--color-primary-gradient);
  border-color: transparent;
}

.dialog__close {
  margin-top: 24rpx;
  text-align: center;
  font-size: 26rpx;
  color: var(--color-text-hint);
}

/* ---------------- 列表状态 ---------------- */
.list__error {
  padding: 120rpx 0;
  text-align: center;
  font-size: 26rpx;
  color: var(--color-text-hint);
}

.list__retry {
  margin-left: 12rpx;
  color: var(--color-primary-text);
}

.list__more {
  padding: 24rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: var(--color-text-hint);
}
</style>
