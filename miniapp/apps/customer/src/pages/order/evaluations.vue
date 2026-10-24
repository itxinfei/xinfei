<template>
  <view class="ev">
    <scroll-view class="list" scroll-y @scrolltolower="loadMore">
      <view v-for="item in records" :key="item.id" class="card" @tap="openOrder(item)">
        <view class="card__head">
          <view class="card__dishinfo">
            <text class="card__dish">{{ item.dishName || (item.setmealId ? '套餐' : '未知菜品') }}</text>
            <view class="card__meta">
              <view class="stars">
                <text
                  v-for="i in 5"
                  :key="i"
                  class="star"
                  :class="{ 'star--on': i <= (item.starRating || 0) }"
                >★</text>
              </view>
              <text class="card__time">{{ formatTime(item.createTime) }}</text>
              <text v-if="Number(item.anonymous) === 1" class="card__anony">匿名</text>
            </view>
          </view>
          <text class="card__status" :class="'card__status--s' + item.status">{{ statusText(item.status) }}</text>
        </view>

        <text class="card__content">{{ item.content || '（无文字评价）' }}</text>

        <!-- 评价图片：images 后端为 JSON 字符串（或数组），统一解析后拼绝对地址预览 -->
        <view v-if="imageList(item.images).length" class="imgs">
          <image
            v-for="(pic, idx) in imageList(item.images)"
            :key="idx"
            class="imgs__item"
            :src="img(pic)"
            mode="aspectFill"
            lazy-load
            @tap.stop="preview(item, idx)"
          />
        </view>

        <!-- 商家回复 -->
        <view v-if="item.replyContent" class="reply">
          <text class="reply__label">商家回复：</text>
          <text class="reply__text">{{ item.replyContent }}</text>
          <text class="reply__time">{{ formatTime(item.replyTime) }}</text>
        </view>

        <view v-if="item.orderId" class="card__foot">
          <text class="card__order">查看原订单 ›</text>
        </view>
      </view>

      <view v-if="listError" class="list__error">
        <text>加载失败</text>
        <text class="list__retry" @tap="load(true)">重试</text>
      </view>
      <x-empty v-if="!records.length && !loading && !listError" text="暂无评价记录" sub="订单完成后可在订单页对菜品评价" />
      <view v-if="loading" class="list__more"><text>加载中…</text></view>
      <view v-if="!hasMore && records.length" class="list__more"><text>没有更多了</text></view>
    </scroll-view>
  </view>
</template>

<script>
// 我的评价：GET /api/dish-evaluation/user/my（分页，DishEvaluation 实体直出，无菜品图片字段）。
// 与 Web my-evaluations.html 列表态对齐；提交/删除评价入口保留在订单详情/评价页，本页只读展示。
import { http, qs, fullImage, listState } from '@xinfei/mini-shared'

export default {
  data() {
    return Object.assign(listState(), {
      records: [],
      page: 1,
      pageSize: 10,
      total: 0,
      hasMore: true,
      loading: false
    })
  },
  onLoad() {
    this.load(true)
  },
  onPullDownRefresh() {
    this.load(true).then(() => uni.stopPullDownRefresh())
  },
  methods: {
    img: path => fullImage(path),
    load(reset) {
      if (this.loading) return Promise.resolve()
      if (reset) {
        this.page = 1
        this.hasMore = true
      }
      this.loading = true
      if (reset) this.listError = false
      return http.get(qs('/api/dish-evaluation/user/my', { page: this.page, pageSize: this.pageSize }), null, { silent: true })
        .then(data => {
          const list = (data && data.records) || []
          this.total = (data && data.total) || 0
          this.records = reset ? list : this.records.concat(list)
          // pageSize 上限 50（后端 @Max(50)），按返回条数判断收尾
          this.hasMore = list.length >= this.pageSize && this.records.length < this.total
          if (this.hasMore) this.page += 1
        })
        .catch(() => { this.listError = true })
        .then(() => { this.loading = false })
    },
    loadMore() {
      if (this.loading || !this.hasMore) return
      this.load(false)
    },
    // images 为 String（JSON 数组），兼容数组/字符串/非法 JSON 三态
    imageList(images) {
      if (!images) return []
      if (Array.isArray(images)) return images
      try {
        const parsed = JSON.parse(images)
        return Array.isArray(parsed) ? parsed : [images]
      } catch (e) {
        return [images]
      }
    },
    preview(item, index) {
      const urls = this.imageList(item.images).map(p => fullImage(p))
      if (!urls.length) return
      uni.previewImage({ urls, current: urls[index] })
    },
    openOrder(item) {
      if (!item.orderId) return
      uni.navigateTo({ url: '/pages/order/detail?id=' + item.orderId })
    },
    statusText(status) {
      const map = { 0: '待审核', 1: '已通过', 2: '已拒绝' }
      return map[status] || '未知'
    },
    formatTime(dateStr) {
      if (!dateStr) return ''
      const d = new Date(String(dateStr).replace(/-/g, '/'))
      if (isNaN(d.getTime())) return String(dateStr).substring(0, 16).replace('T', ' ')
      const pad = n => String(n).padStart(2, '0')
      return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' +
        pad(d.getHours()) + ':' + pad(d.getMinutes())
    }
  }
}
</script>

<style>
.ev {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg-page);
}

.list {
  flex: 1;
}

/* ---------------- 评价卡 ---------------- */
.card {
  margin: 16rpx 24rpx 0;
  padding: 24rpx;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
  box-shadow: var(--shadow-card);
}

.card__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.card__dishinfo {
  flex: 1;
  min-width: 0;
}

.card__dish {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text-primary);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.card__meta {
  display: flex;
  align-items: center;
  gap: 14rpx;
  margin-top: 10rpx;
}

/* 星级：实心金色 / 空心灰 */
.stars {
  display: flex;
}

.star {
  font-size: 26rpx;
  line-height: 1;
  color: var(--border-medium);
}

.star--on {
  color: var(--color-primary);
}

.card__time {
  font-size: 22rpx;
  color: var(--color-text-hint);
}

.card__anony {
  padding: 0 12rpx;
  font-size: 20rpx;
  color: var(--color-text-secondary);
  background: var(--color-neutral-tint);
  border-radius: var(--radius-sm);
}

/* 审核状态：0 待审核金 / 1 已通过绿 / 2 已拒绝红 */
.card__status {
  flex-shrink: 0;
  margin-left: 16rpx;
  padding: 4rpx 18rpx;
  border-radius: var(--radius-round);
  font-size: 22rpx;
  font-weight: 600;
}

.card__status--s0 {
  color: var(--color-warning-text);
  background: var(--color-neutral-tint);
}

.card__status--s1 {
  color: var(--color-success-text);
  background: var(--color-success-tint);
}

.card__status--s2 {
  color: var(--color-danger-text);
  background: var(--color-danger-tint);
}

.card__content {
  display: block;
  margin-top: 16rpx;
  font-size: 26rpx;
  line-height: 40rpx;
  color: var(--color-text-secondary);
  word-break: break-all;
}

/* ---------------- 评价图片九宫 ---------------- */
.imgs {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 16rpx;
}

.imgs__item {
  width: 200rpx;
  height: 200rpx;
  border-radius: var(--radius-md);
  background: var(--bg-input);
}

/* ---------------- 商家回复 ---------------- */
.reply {
  margin-top: 16rpx;
  padding: 16rpx 20rpx;
  border-radius: var(--radius-md);
  background: var(--bg-warm);
}

.reply__label {
  font-size: 24rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

.reply__text {
  font-size: 24rpx;
  line-height: 38rpx;
  color: var(--color-text-secondary);
}

.reply__time {
  display: block;
  margin-top: 8rpx;
  font-size: 20rpx;
  color: var(--color-text-hint);
}

.card__foot {
  margin-top: 16rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid var(--border-light);
  display: flex;
  justify-content: flex-end;
}

.card__order {
  font-size: 24rpx;
  color: var(--color-primary-text);
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
