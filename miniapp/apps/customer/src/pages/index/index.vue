<template>
  <view class="menu">
    <scroll-view class="menu__side" scroll-y>
      <view
        v-for="c in categories"
        :key="c.id"
        class="side-item"
        :class="{ 'side-item--on': c.id === activeCategoryId }"
        hover-class="hover-press"
        @tap="switchCategory(c.id)"
      >
        <text>{{ c.name }}</text>
      </view>
      <view v-if="!categories.length" class="side-empty">
        <text>加载中…</text>
      </view>
    </scroll-view>

    <!-- 修改点(2026-10-17)：移除 @scrolltolower 空绑定——/dish/list 服务端封顶 200 条，无客户端翻页可触发 -->
    <scroll-view class="menu__main" scroll-y>
      <view v-for="d in dishes" :key="d.id" class="dish">
        <image class="dish__img" :src="img(d.image)" mode="aspectFill" lazy-load />
        <view class="dish__body">
          <text class="dish__name">{{ d.name }}</text>
          <text class="dish__desc">{{ d.description || '暂无描述' }}</text>
          <view class="dish__foot">
            <text class="dish__price">¥{{ fmtAmount(d.price) }}</text>
            <view class="counter">
              <text v-if="countOf(d.id)" class="counter__btn" :class="{ 'counter__btn--off': busy('cart:' + d.id) }" hover-class="hover-press" @tap="sub(d)">−</text>
              <text v-if="countOf(d.id)" class="counter__num">{{ countOf(d.id) }}</text>
              <text class="counter__btn counter__btn--add" :class="{ 'counter__btn--off': busy('cart:' + d.id) }" hover-class="hover-press" @tap="add(d)">＋</text>
            </view>
          </view>
        </view>
      </view>
      <view v-if="loading && !dishes.length" class="menu__loading"><text>加载中…</text></view>
      <view v-if="listError" class="menu__error">
        <text>加载失败</text>
        <text class="menu__retry" @tap="reload()">重试</text>
      </view>
      <view v-if="!dishes.length && !loading && !listError" class="menu__empty">
        <text>该分类下暂无在售菜品</text>
      </view>
    </scroll-view>

    <view class="cartbar" v-if="cartCount > 0">
      <text class="cartbar__count">已选 {{ cartCount }} 件</text>
      <text class="cartbar__amount">¥{{ cartAmount }}</text>
      <text class="cartbar__go" hover-class="hover-press" @tap="goCheckout">去结算</text>
    </view>
  </view>
</template>

<script>
import { http, qs, fullImage, listState, busyLock, fmtAmount } from '@xinfei/mini-shared'

export default {
  data() {
    // 修改点(2026-10-17)：接入列表三态（listError）与购物车加/减 in-flight 锁（busyLock）
    return Object.assign(listState(), busyLock(), {
      categories: [],
      dishes: [],
      activeCategoryId: null,
      cart: [],
      loading: false
    })
  },
  computed: {
    cartCount() {
      return this.cart.reduce((sum, item) => sum + (item.number || 0), 0)
    },
    cartAmount() {
      return this.cart.reduce((sum, item) => sum + Number(item.amount || 0) * (item.number || 0), 0).toFixed(2)
    }
  },
  onLoad() {
    this.loadCategories()
    this.loadCart()
  },
  onPullDownRefresh() {
    Promise.all([this.loadCategories(), this.loadCart()]).then(() => uni.stopPullDownRefresh())
  },
  methods: {
    img: path => fullImage(path),
    fmtAmount,
    loadCategories() {
      // 分类 type=1 菜品；套餐走 /setmeal/list，本轮未迁移
      return http.get(qs('/category/list', { type: 1 })).then(list => {
        this.categories = list || []
        if (this.categories.length && this.activeCategoryId === null) {
          this.activeCategoryId = this.categories[0].id
          return this.loadDishes()
        }
        return null
      }).catch(() => { this.listError = true })
    },
    switchCategory(id) {
      if (id === this.activeCategoryId) return
      this.activeCategoryId = id
      this.loadDishes()
    },
    loadDishes() {
      this.loading = true
      this.listError = false
      return http.get(qs('/dish/list', { categoryId: this.activeCategoryId })).then(list => {
        this.dishes = list || []
      }).catch(() => { this.listError = true }).then(() => {
        this.loading = false
      })
    },
    // 修改点(2026-10-17)：列表失败重试——分类未加载成功时重拉分类，否则重拉当前分类菜品
    reload() {
      if (this.activeCategoryId === null) { this.loadCategories() } else { this.loadDishes() }
    },
    loadCart() {
      return http.get('/shopping-cart/list').then(list => {
        this.cart = list || []
      }).catch(() => {})
    },
    countOf(dishId) {
      const hit = this.cart.find(item => item.dishId === dishId)
      return hit ? hit.number : 0
    },
    add(dish) {
      const key = 'cart:' + dish.id
      if (this.busy(key)) return
      this.setBusy(key, true)
      const exist = this.cart.find(item => item.dishId === dish.id)
      http.post('/shopping-cart/add', { dishId: dish.id, name: dish.name, number: exist ? exist.number + 1 : 1 })
        .then(() => this.loadCart())
        .catch(() => {}).then(() => { this.setBusy(key, false) })
    },
    sub(dish) {
      const key = 'cart:' + dish.id
      if (this.busy(key)) return
      this.setBusy(key, true)
      const exist = this.cart.find(item => item.dishId === dish.id)
      http.post('/shopping-cart/sub', { dishId: dish.id, number: exist ? exist.number : 1 })
        .then(() => this.loadCart())
        .catch(() => {}).then(() => { this.setBusy(key, false) })
    },
    goCheckout() {
      uni.showModal({
        title: '温馨提示',
        content: '在线支付功能升级中，请稍后再试',
        showCancel: false
      })
    }
  }
}
</script>

<style>
.menu {
  display: flex;
  position: relative;
  height: 100%;
  background: var(--bg-page);
}

.menu__side {
  width: 176rpx;
  height: 100%;
  background: var(--bg-card);
}

.side-item {
  padding: 28rpx 16rpx;
  font-size: 26rpx;
  text-align: center;
  color: var(--color-text-secondary);
}

.side-item--on {
  color: var(--color-text-primary);
  font-weight: 600;
  background: var(--color-primary-tint);
}

.side-empty {
  padding: 28rpx 16rpx;
  font-size: 24rpx;
  color: var(--color-text-hint);
  text-align: center;
}

.menu__main {
  flex: 1;
  height: 100%;
  padding-bottom: 120rpx;
  box-sizing: border-box;
}

.dish {
  display: flex;
  padding: 24rpx;
  margin: 16rpx;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
  box-shadow: var(--shadow-card);
}

.dish__img {
  width: 160rpx;
  height: 160rpx;
  border-radius: var(--radius-md);
  flex-shrink: 0;
  background: var(--bg-input);
}

.dish__body {
  flex: 1;
  margin-left: 20rpx;
  display: flex;
  flex-direction: column;
}

.dish__name {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

.dish__desc {
  flex: 1;
  margin-top: 8rpx;
  font-size: 24rpx;
  line-height: 34rpx;
  color: var(--color-text-hint);
}

.dish__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12rpx;
}

.dish__price {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--color-price);
}

.counter {
  display: flex;
  align-items: center;
}

.counter__btn {
  width: 52rpx;
  height: 52rpx;
  line-height: 52rpx;
  text-align: center;
  border-radius: var(--radius-round);
  font-size: 32rpx;
  color: var(--color-text-secondary);
  background: var(--bg-input);
}

.counter__btn--add {
  color: var(--color-primary-text);
  background: var(--color-primary);
}

.counter__num {
  min-width: 48rpx;
  text-align: center;
  font-size: 28rpx;
  color: var(--color-text-primary);
}

.menu__empty,
.menu__error,
.menu__loading {
  padding: 120rpx 0;
  text-align: center;
  font-size: 26rpx;
  color: var(--color-text-hint);
}

.menu__retry {
  margin-left: 12rpx;
  color: var(--color-primary-text);
}

.counter__btn--off {
  opacity: 0.5;
}

.cartbar {
  position: fixed;
  left: 24rpx;
  right: 24rpx;
  bottom: calc(24rpx + env(safe-area-inset-bottom));
  display: flex;
  align-items: center;
  height: 96rpx;
  padding: 0 24rpx;
  border-radius: 48rpx;
  background: var(--bg-card);
  box-shadow: var(--shadow-bar-top);
}

.cartbar__count {
  flex: 1;
  font-size: 26rpx;
  color: var(--color-text-secondary);
}

.cartbar__amount {
  margin-right: 24rpx;
  font-size: 32rpx;
  font-weight: 600;
  color: var(--color-price);
}

.cartbar__go {
  padding: 0 32rpx;
  height: 64rpx;
  line-height: 64rpx;
  border-radius: 32rpx;
  font-size: 28rpx;
  color: var(--color-primary-text);
  background: var(--color-primary);
}
</style>
