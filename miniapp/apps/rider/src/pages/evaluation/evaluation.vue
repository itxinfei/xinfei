<template>
  <view class="evaluation">
    <view class="hero">
      <view class="hero__score">
        <text class="hero__num">{{ stats.avg }}</text>
        <view class="hero__stars">
          <text class="hero__star">{{ starsText(Number(stats.avg)) }}</text>
          <text class="hero__meta">共 {{ stats.cnt }} 条评价</text>
        </view>
      </view>
    </view>

    <view class="section-title"><text>收到的评价</text></view>

    <scroll-view class="list" scroll-y @scrolltolower="loadMore">
      <view v-for="e in records" :key="e.id" class="card">
        <view class="card__head">
          <text class="card__user">{{ e.anonymous === 1 ? '匿名用户' : (e.userName || '顾客') }}</text>
          <text class="card__stars">{{ starsText(e.starRating) }}</text>
        </view>
        <text class="card__time">{{ formatTime(e.createTime) }}</text>
        <text v-if="e.content" class="card__content">{{ e.content }}</text>
        <view v-if="e.tagsParsed && e.tagsParsed.length" class="tags">
          <text v-for="(t, i) in e.tagsParsed" :key="i" class="tag">{{ t }}</text>
        </view>
        <view v-if="e.replyContent" class="reply">
          <text>商家回复：{{ e.replyContent }}</text>
        </view>
      </view>

      <view v-if="loading && !records.length" class="list__loading"><text>加载中…</text></view>
      <view v-if="listError" class="list__error">
        <text>加载失败</text>
        <text class="list__retry" @tap="load()">重试</text>
      </view>
      <x-empty v-if="!records.length && !loading && !listError" text="暂无评价" sub="顾客评价后会展示在这里" />
      <view v-if="!hasMore && records.length" class="list__nomore"><text>没有更多了</text></view>
    </scroll-view>
  </view>
</template>

<script>
import { http, qs, riderAuth, listState } from '@xinfei/mini-shared'

export default {
  data() {
    return Object.assign(listState(), {
      stats: { avg: '0.0', cnt: 0 },
      records: [],
      page: 1,
      size: 10,
      hasMore: true,
      loading: false
    })
  },
  onShow() {
    this.load()
  },
  onPullDownRefresh() {
    this.load().then(() => uni.stopPullDownRefresh())
  },
  methods: {
    /** 评分星星（后端 starRating 整数 1-5，统计 avg 一位小数，按四舍五入取整画星） */
    starsText(score) {
      const n = Math.max(0, Math.min(5, Math.round(Number(score) || 0)))
      return '★★★★★'.slice(0, n) + '☆☆☆☆☆'.slice(0, 5 - n)
    },
    /** 对齐 Web 版 RiderUtil.formatTime：ISO 串去 T 截到分，兼容 Jackson 数组 */
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
      return Promise.all([this.loadStats(), this.loadRecords()])
    },
    /** 评分统计：/rider/{id}/stats 需要骑手 ID，取本地缓存的登录骑手；缓存缺失再拉 /api/rider/me */
    loadStats() {
      const cached = riderAuth.cached() || {}
      const ensureId = cached.id
        ? Promise.resolve(cached.id)
        : riderAuth.me().then(rider => (rider && rider.id) || null).catch(() => null)
      return ensureId.then(riderId => {
        if (!riderId) return
        return http.get('/api/rider-evaluation/rider/' + riderId + '/stats', null, { silent: true }).then(s => {
          s = s || {}
          this.stats = {
            avg: s.avg != null ? Number(s.avg).toFixed(1) : '0.0',
            cnt: s.cnt != null ? s.cnt : 0
          }
        }).catch(() => { /* 统计失败不打断列表 */ })
      })
    },
    loadRecords() {
      if (this.loading || !this.hasMore) return Promise.resolve()
      this.loading = true
      return http.get(qs('/api/rider-evaluation/received', { page: this.page, pageSize: this.size })).then(data => {
        const list = (data && data.records) || []
        list.forEach(e => {
          // tags 是 JSON 数组字符串，如 ["准时送达","态度友好"]，解析失败按无标签展示
          try { e.tagsParsed = e.tags ? JSON.parse(e.tags) : [] }
          catch (err) { e.tagsParsed = [] }
        })
        this.records = this.records.concat(list)
        this.hasMore = list.length === this.size
        this.page += 1
      }).catch(() => { this.listError = true }).then(() => {
        this.loading = false
      })
    },
    loadMore() {
      this.loadRecords()
    }
  }
}
</script>

<style>
.evaluation {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg-page);
}

.hero {
  margin: 24rpx;
  padding: 32rpx;
  border-radius: var(--radius-lg);
  background: var(--color-primary-gradient);
}

.hero__score {
  display: flex;
  align-items: center;
}

.hero__num {
  font-size: 72rpx;
  font-weight: 600;
  line-height: 1;
  color: var(--color-primary-text);
}

.hero__stars {
  margin-left: 24rpx;
  display: flex;
  flex-direction: column;
}

.hero__star {
  font-size: 30rpx;
  color: var(--color-primary-text);
}

.hero__meta {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: var(--color-primary-text);
}

.section-title {
  padding: 0 32rpx 12rpx;
  font-size: 26rpx;
  color: var(--color-text-secondary);
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

.card__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card__user {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

.card__stars {
  font-size: 26rpx;
  color: var(--color-primary-text-strong);
}

.card__time {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: var(--color-text-hint);
}

.card__content {
  display: block;
  margin-top: 12rpx;
  font-size: 28rpx;
  line-height: 40rpx;
  color: var(--color-text-primary);
}

.tags {
  display: flex;
  flex-wrap: wrap;
  margin-top: 12rpx;
}

.tag {
  margin: 8rpx 12rpx 0 0;
  padding: 4rpx 16rpx;
  border-radius: var(--radius-round);
  font-size: 22rpx;
  color: var(--color-primary-text-strong);
  background: var(--color-primary-tint);
}

.reply {
  margin-top: 12rpx;
  padding: 16rpx;
  border-radius: var(--radius-md);
  background: var(--bg-hover);
  font-size: 24rpx;
  line-height: 36rpx;
  color: var(--color-text-secondary);
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
