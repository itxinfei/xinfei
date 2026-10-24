<template>
  <view class="bh">
    <scroll-view class="list" scroll-y>
      <view v-for="h in records" :key="h.id" class="row" hover-class="hover-press" @tap="goBrowse">
        <view class="row__icon" :class="{ 'row__icon--setmeal': h.targetType === 2 }">
          <text>{{ h.targetType === 2 ? '套' : '菜' }}</text>
        </view>
        <view class="row__mid">
          <text class="row__name">{{ h.targetName || (h.targetType === 2 ? '套餐' : '菜品') }}</text>
          <text class="row__time">{{ fmtTime(h.createTime) }}</text>
        </view>
        <text class="row__tag" :class="actionClass(h.actionType)">{{ actionText(h.actionType) }}</text>
      </view>

      <view v-if="listError" class="list__error">
        <text>加载失败</text>
        <text class="list__retry" @tap="load">重试</text>
      </view>
      <x-empty v-if="!records.length && !loading && !listError" text="暂无浏览足迹" sub="去首页看看好吃的吧" />
      <view v-if="loading" class="list__more"><text>加载中…</text></view>
    </scroll-view>

    <!-- 底部「去逛逛」：Web 版无独立菜品详情页，点足迹也是回首页重新浏览 -->
    <view class="footer">
      <button class="gobtn" @tap="goBrowse">去逛逛</button>
    </view>
  </view>
</template>

<script>
// 浏览记录：GET /recommend/browse-history（接口无分页，limit 一次拉满，对齐 Web browse-history.html）
// BrowseHistory 字段：targetType(1 菜品 / 2 套餐)、targetId、targetName、actionType、createTime
import { http, qs, listState } from '@xinfei/mini-shared'

const HISTORY_LIMIT = 30

export default {
  data() {
    return Object.assign(listState(), {
      records: [],
      loading: false
    })
  },
  onLoad() {
    this.load()
  },
  onPullDownRefresh() {
    this.load().then(() => uni.stopPullDownRefresh())
  },
  methods: {
    load() {
      this.loading = true
      this.listError = false
      return http.get(qs('/recommend/browse-history', { limit: HISTORY_LIMIT }), null, { silent: true })
        .then(list => {
          this.records = Array.isArray(list) ? list : []
        })
        .catch(() => { this.listError = true })
        .then(() => { this.loading = false })
    },
    // 行为标签：1 浏览 / 2 收藏 / 3 加购 / 4 分享（与 Web actionText/actionType 一致）
    actionText(a) {
      if (a === 2) return '收藏'
      if (a === 3) return '加购'
      if (a === 4) return '分享'
      return '浏览'
    },
    actionClass(a) {
      if (a === 2) return 'row__tag--fav'
      if (a === 3) return 'row__tag--cart'
      if (a === 4) return 'row__tag--share'
      return ''
    },
    fmtTime(s) {
      if (!s) return ''
      return String(s).replace('T', ' ').substring(0, 16)
    },
    goBrowse() {
      // tabBar 页必须 switchTab；小程序无 history.back 语义，与 Web「点击回首页」行为一致
      uni.switchTab({ url: '/pages/index/index' })
    }
  }
}
</script>

<style>
.bh {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg-page);
}

.list {
  flex: 1;
}

/* ---------------- 足迹行 ---------------- */
.row {
  display: flex;
  align-items: center;
  margin: 16rpx 24rpx 0;
  padding: 24rpx;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
  box-shadow: var(--shadow-card);
}

.row__icon {
  width: 80rpx;
  height: 80rpx;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-round);
  font-size: 30rpx;
  font-weight: 600;
  color: var(--color-primary-text-strong);
  background: var(--color-primary-tint);
}

.row__icon--setmeal {
  color: var(--color-warning-text);
  background: var(--color-neutral-tint);
}

.row__mid {
  flex: 1;
  min-width: 0;
  margin: 0 20rpx;
  display: flex;
  flex-direction: column;
}

.row__name {
  font-size: 28rpx;
  color: var(--color-text-primary);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.row__time {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: var(--color-text-hint);
}

/* 行为标签（描边胶囊）：2 收藏红 / 3 加购金 / 4 分享主色 / 默认浏览灰 */
.row__tag {
  flex-shrink: 0;
  padding: 4rpx 18rpx;
  font-size: 22rpx;
  border-radius: var(--radius-round);
  color: var(--color-text-secondary);
  border: 1rpx solid var(--border-medium);
}

.row__tag--fav {
  color: var(--color-danger-text);
  border-color: var(--color-danger);
}

.row__tag--cart {
  color: var(--color-primary-text-strong);
  border-color: var(--color-primary-text-strong);
}

.row__tag--share {
  color: var(--color-primary-text-strong);
  border-color: var(--color-primary-text-strong);
}

/* ---------------- 底部去逛逛 ---------------- */
.footer {
  flex-shrink: 0;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background: var(--bg-card);
  border-top: 1rpx solid var(--border-light);
}

.gobtn {
  height: 88rpx;
  line-height: 88rpx;
  border-radius: var(--radius-round);
  font-size: 30rpx;
  font-weight: 600;
  color: var(--color-primary-text);
  background: var(--color-primary-gradient);
  box-shadow: var(--shadow-primary-sm);
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
