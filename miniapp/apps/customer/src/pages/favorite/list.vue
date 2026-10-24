<template>
  <view class="page">
    <scroll-view class="favlist" scroll-y>
      <view v-for="item in records" :key="item.targetId" class="fav">
        <!-- 列表内都是已收藏：金心，点一下取消（可逆，不做二次确认） -->
        <text
          class="fav__heart"
          :class="{ 'fav__heart--off': busy('rm:' + item.targetId) }"
          hover-class="hover-press"
          @tap.stop="remove(item)"
        >♥</text>

        <image
          v-if="item.image"
          class="fav__img"
          :class="{ 'fav__img--dim': !item.onSale }"
          :src="img(item.image)"
          mode="aspectFill"
          lazy-load
        />
        <view v-else class="fav__ph"><text class="fav__ph-text">失效</text></view>

        <view class="fav__body">
          <text class="fav__name" :class="{ 'fav__name--dim': !item.available || !item.onSale }">{{ item.name }}</text>
          <view class="fav__tags">
            <text v-if="item.available && !item.onSale" class="fav__tag">已停售</text>
            <text v-if="!item.available" class="fav__tag fav__tag--dead">已失效</text>
          </view>
          <view class="fav__foot">
            <view v-if="item.available" class="fav__pricebox">
              <text class="fav__yen">¥</text>
              <text class="fav__price">{{ fmtAmount(item.price) }}</text>
            </view>
            <view v-else></view>
            <text
              v-if="item.onSale"
              class="fav__add"
              :class="{ 'fav__add--off': busy('add:' + item.targetId) }"
              hover-class="hover-press"
              @tap.stop="addCart(item)"
            >加入购物车</text>
            <text
              v-if="!item.available"
              class="fav__clean"
              hover-class="hover-press"
              @tap.stop="remove(item)"
            >移除</text>
          </view>
        </view>
      </view>

      <view v-if="listError" class="favlist__state">
        <text>加载失败</text>
        <text class="favlist__retry" @tap="load(true)">重试</text>
      </view>
      <x-empty v-if="!records.length && !loading && !listError" text="还没有收藏的菜品" sub="点餐时点菜品旁的 ♥ 即可收藏" />
      <view v-if="loading" class="favlist__state"><text>加载中…</text></view>
    </scroll-view>
  </view>
</template>

<script>
import { http, qs, fullImage, listState, busyLock, fmtAmount } from '@xinfei/mini-shared'

// 收藏类型：1=菜品（后端 UserFavorite.TYPE_DISH；店铺收藏未开放）
const TARGET_TYPE_DISH = 1

export default {
  data() {
    return Object.assign(listState(), busyLock(), {
      records: [],
      loadedOnce: false
    })
  },
  onLoad() {
    this.load(true)
  },
  onShow() {
    // 首次由 onLoad 加载；从下单链路返回时重拉，同步停售/失效状态
    if (this.loadedOnce) this.load(true)
  },
  onPullDownRefresh() {
    this.load(true).then(() => uni.stopPullDownRefresh())
  },
  methods: {
    fmtAmount,
    img: path => fullImage(path),
    load(reset) {
      if (!reset && this.loading) return Promise.resolve()
      this.loading = true
      this.listError = false
      return http.get(qs('/api/favorite/list', { targetType: TARGET_TYPE_DISH }), null, { silent: true })
        .then(list => { this.records = list || [] })
        .catch(() => { this.listError = true })
        .then(() => {
          this.loading = false
          this.loadedOnce = true
        })
    },
    addCart(item) {
      const key = 'add:' + item.targetId
      if (this.busy(key)) return
      this.setBusy(key, true)
      // name/image/amount 服务端按 DB 回填，与首页加购同一接口同一口径
      http.post('/shopping-cart/add', { dishId: item.targetId, name: item.name, number: 1 }, { silent: true })
        .then(() => uni.showToast({ title: '已加入购物车', icon: 'none' }))
        .catch(() => {})
        .then(() => { this.setBusy(key, false) })
    },
    // 取消收藏：失效商品（菜品已删除）也可取消，后端取消路径不校验菜品存在性
    remove(item) {
      const key = 'rm:' + item.targetId
      if (this.busy(key)) return
      this.setBusy(key, true)
      http.post('/api/favorite/toggle', { targetType: TARGET_TYPE_DISH, targetId: item.targetId }, { silent: true })
        .then(res => {
          if (res && res.favorited === false) {
            this.records = this.records.filter(r => String(r.targetId) !== String(item.targetId))
            uni.showToast({ title: '已取消收藏', icon: 'none' })
          }
        })
        .catch(() => {})
        .then(() => { this.setBusy(key, false) })
    }
  }
}
</script>

<style>
.page {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg-page);
}

.favlist {
  flex: 1;
}

.fav {
  position: relative;
  display: flex;
  margin: 16rpx 24rpx;
  padding: 24rpx;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
  box-shadow: var(--shadow-card);
}

.fav__heart {
  position: absolute;
  top: 16rpx;
  right: 20rpx;
  z-index: 1;
  font-size: 34rpx;
  line-height: 1;
  color: var(--color-primary);
}

.fav__heart--off {
  opacity: 0.5;
}

.fav__img {
  width: 160rpx;
  height: 160rpx;
  border-radius: var(--radius-md);
  flex-shrink: 0;
  background: var(--bg-input);
}

.fav__img--dim {
  opacity: 0.45;
}

.fav__ph {
  width: 160rpx;
  height: 160rpx;
  border-radius: var(--radius-md);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-input);
}

.fav__ph-text {
  font-size: 24rpx;
  color: var(--color-text-hint);
}

.fav__body {
  flex: 1;
  min-width: 0;
  margin-left: 20rpx;
  display: flex;
  flex-direction: column;
}

.fav__name {
  display: block;
  padding-right: 48rpx;
  font-size: 30rpx;
  font-weight: 600;
  color: var(--color-text-primary);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.fav__name--dim {
  color: var(--color-text-hint);
}

.fav__tags {
  margin-top: 10rpx;
  display: flex;
}

.fav__tag {
  padding: 2rpx 12rpx;
  border-radius: var(--radius-sm);
  font-size: 22rpx;
  color: var(--color-text-secondary);
  background: var(--color-primary-tint);
}

.fav__tag--dead {
  color: var(--color-text-hint);
  background: var(--bg-input);
}

.fav__foot {
  margin-top: auto;
  padding-top: 12rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.fav__pricebox {
  display: flex;
  align-items: baseline;
}

.fav__yen {
  font-size: 24rpx;
  font-weight: 700;
  color: var(--color-price);
}

.fav__price {
  font-size: 38rpx;
  font-weight: 800;
  color: var(--color-price);
}

.fav__add {
  padding: 10rpx 28rpx;
  border-radius: 32rpx;
  font-size: 26rpx;
  color: var(--color-primary-text);
  background: var(--color-primary-gradient);
  box-shadow: var(--shadow-primary-sm);
}

.fav__add--off {
  opacity: 0.5;
}

.fav__clean {
  padding: 8rpx 28rpx;
  border-radius: 32rpx;
  font-size: 26rpx;
  color: var(--color-text-secondary);
  border: 1rpx solid var(--border-medium);
}

.favlist__state {
  padding: 120rpx 0;
  text-align: center;
  font-size: 26rpx;
  color: var(--color-text-hint);
}

.favlist__retry {
  margin-left: 12rpx;
  color: var(--color-primary-text);
}
</style>
