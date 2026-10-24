<template>
  <view class="message">
    <view class="bar">
      <text class="bar__tip">共 {{ total }} 条，未读 {{ unread }} 条</text>
      <text
        class="bar__btn"
        :class="{ 'bar__btn--off': !unread || readAllBusy }"
        hover-class="hover-press"
        @tap="readAll"
      >全部已读</text>
    </view>

    <scroll-view class="list" scroll-y @scrolltolower="loadMore">
      <view
        v-for="m in records"
        :key="m.id"
        class="card"
        :class="{ 'card--unread': m.isRead === 0 }"
        hover-class="hover-press"
        @tap="markRead(m)"
      >
        <view class="card__head">
          <text class="card__title">{{ m.title }}</text>
          <text v-if="m.isRead === 0" class="card__dot">未读</text>
        </view>
        <text class="card__content">{{ m.content }}</text>
        <view class="card__foot">
          <text class="card__type">{{ typeText(m.type) }}</text>
          <text class="card__time">{{ formatTime(m.createTime) }}</text>
        </view>
      </view>

      <view v-if="loading && !records.length" class="list__loading"><text>加载中…</text></view>
      <view v-if="listError" class="list__error">
        <text>加载失败</text>
        <text class="list__retry" @tap="load()">重试</text>
      </view>
      <x-empty v-if="!records.length && !loading && !listError" text="暂无消息" sub="派单、催单与公告会在这里通知你" />
      <view v-if="!hasMore && records.length" class="list__nomore"><text>没有更多了</text></view>
    </scroll-view>
  </view>
</template>

<script>
import { http, qs, listState } from '@xinfei/mini-shared'

// 消息类型：1派单提醒 2催单提醒 3异常处理 4系统公告 5其他（RiderMessage.TYPE_*，与 Web 版映射一致）
const MSG_TYPE = { 1: '派单提醒', 2: '催单提醒', 3: '异常处理', 4: '系统公告', 5: '其他' }

export default {
  data() {
    return Object.assign(listState(), {
      records: [],
      total: 0,
      unread: 0,
      page: 1,
      size: 10,
      hasMore: true,
      loading: false,
      readAllBusy: false
    })
  },
  onShow() {
    this.load()
  },
  onPullDownRefresh() {
    this.load().then(() => uni.stopPullDownRefresh())
  },
  methods: {
    typeText(t) {
      return MSG_TYPE[t] || '消息'
    },
    /** 对齐 Web 版 RiderUtil.formatTime：ISO 串去 T 截到分，兼容 Jackson 数组（已读消息的 @JsonFormat 已是字符串） */
    formatTime(t) {
      if (t == null || t === '') return ''
      if (typeof t === 'string') return t.replace('T', ' ').substring(0, 16)
      if (Array.isArray(t)) {
        const pad = v => (v < 10 ? '0' + v : '' + v)
        return t[0] + '-' + pad(t[1]) + '-' + pad(t[2]) + ' ' + pad(t[3] || 0) + ':' + pad(t[4] || 0)
      }
      return String(t)
    },
    load() {
      this.page = 1
      this.records = []
      this.hasMore = true
      this.listError = false
      this.loadUnread()
      return this.loadRecords()
    },
    loadUnread() {
      // 未读数来自专用接口（不是本地数 records 过滤：分页后本地数会偏小）
      http.get('/api/rider-message/unread-count', null, { silent: true }).then(data => {
        this.unread = (data && data.count) || 0
      }).catch(() => {})
    },
    loadRecords() {
      if (this.loading || !this.hasMore) return Promise.resolve()
      this.loading = true
      return http.get(qs('/api/rider-message/mine', { page: this.page, size: this.size })).then(data => {
        const list = (data && data.records) || []
        this.records = this.records.concat(list)
        this.total = (data && data.total) || this.records.length
        this.hasMore = list.length === this.size
        this.page += 1
      }).catch(() => { this.listError = true }).then(() => {
        this.loading = false
      })
    },
    loadMore() {
      this.loadRecords()
    },
    markRead(m) {
      if (!m || m.isRead === 1) return
      http.put('/api/rider-message/' + m.id + '/read').then(() => {
        m.isRead = 1
        if (this.unread > 0) this.unread -= 1
      }).catch(() => {})
    },
    readAll() {
      if (!this.unread || this.readAllBusy) return
      this.readAllBusy = true
      http.put('/api/rider-message/read-all').then(() => {
        uni.showToast({ title: '已全部标记已读', icon: 'none' })
        this.unread = 0
        return this.load()
      }).catch(() => {}).then(() => {
        this.readAllBusy = false
      })
    }
  }
}
</script>

<style>
.message {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg-page);
}

.bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20rpx 24rpx;
}

.bar__tip {
  font-size: 24rpx;
  color: var(--color-text-secondary);
}

.bar__btn {
  padding: 8rpx 24rpx;
  border-radius: var(--radius-round);
  font-size: 24rpx;
  color: var(--color-primary-text);
  border: 2rpx solid var(--color-primary);
}

.bar__btn--off {
  color: var(--color-text-disabled);
  border-color: var(--border-light);
}

.list {
  flex: 1;
}

.card {
  margin: 0 24rpx 16rpx;
  padding: 24rpx;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
  box-shadow: var(--shadow-card);
}

.card--unread {
  background: var(--color-primary-tint);
}

.card__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card__title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

.card__dot {
  flex-shrink: 0;
  padding: 4rpx 14rpx;
  border-radius: var(--radius-round);
  font-size: 20rpx;
  color: var(--color-danger-text);
  background: var(--color-danger-tint);
}

.card__content {
  display: block;
  margin-top: 12rpx;
  font-size: 26rpx;
  line-height: 38rpx;
  color: var(--color-text-secondary);
}

.card__foot {
  display: flex;
  justify-content: space-between;
  margin-top: 16rpx;
}

.card__type {
  font-size: 22rpx;
  color: var(--color-primary-text-strong);
}

.card__time {
  font-size: 22rpx;
  color: var(--color-text-hint);
}

.list__empty,
.list__error,
.list__loading {
  padding: 80rpx 0;
  text-align: center;
  font-size: 26rpx;
  color: var(--color-text-hint);
}

.list__nomore {
  padding: 24rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: var(--color-text-hint);
}

.list__retry {
  margin-left: 12rpx;
  color: var(--color-primary-text);
}
</style>
