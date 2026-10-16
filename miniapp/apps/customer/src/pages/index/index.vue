<template>
  <view class="menu">
    <scroll-view class="menu__side" scroll-y>
      <view
        v-for="c in categories"
        :key="c.id"
        class="side-item"
        :class="{ 'side-item--on': c.id === activeCategoryId }"
        @tap="switchCategory(c.id)"
      >
        <text>{{ c.name }}</text>
      </view>
      <view v-if="!categories.length" class="side-empty">
        <text>加载中…</text>
      </view>
    </scroll-view>

    <scroll-view class="menu__main" scroll-y @scrolltolower="loadMore">
      <view v-for="d in dishes" :key="d.id" class="dish">
        <image class="dish__img" :src="img(d.image)" mode="aspectFill" />
        <view class="dish__body">
          <text class="dish__name">{{ d.name }}</text>
          <text class="dish__desc">{{ d.description || '暂无描述' }}</text>
          <view class="dish__foot">
            <text class="dish__price">¥{{ d.price }}</text>
            <view class="counter">
              <text v-if="countOf(d.id)" class="counter__btn" @tap="sub(d)">−</text>
              <text v-if="countOf(d.id)" class="counter__num">{{ countOf(d.id) }}</text>
              <text class="counter__btn counter__btn--add" @tap="add(d)">＋</text>
            </view>
          </view>
        </view>
      </view>
      <view v-if="!dishes.length && !loading" class="menu__empty">
        <text>该分类下暂无在售菜品</text>
      </view>
    </scroll-view>

    <view class="cartbar" v-if="cartCount > 0">
      <text class="cartbar__count">已选 {{ cartCount }} 件</text>
      <text class="cartbar__amount">¥{{ cartAmount }}</text>
      <text class="cartbar__go" @tap="goCheckout">去结算</text>
    </view>
  </view>
</template>

<script>
import { http, qs, fullImage } from '@xinfei/mini-shared'

export default {
  data() {
    return {
      categories: [],
      dishes: [],
      activeCategoryId: null,
      cart: [],
      loading: false
    }
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
    loadCategories() {
      // 分类 type=1 菜品；套餐走 /setmeal/list，本轮未迁移
      return http.get(qs('/category/list', { type: 1 })).then(list => {
        this.categories = list || []
        if (this.categories.length && this.activeCategoryId === null) {
          this.activeCategoryId = this.categories[0].id
          return this.loadDishes()
        }
        return null
      }).catch(() => {})
    },
    switchCategory(id) {
      if (id === this.activeCategoryId) return
      this.activeCategoryId = id
      this.loadDishes()
    },
    loadDishes() {
      this.loading = true
      return http.get(qs('/dish/list', { categoryId: this.activeCategoryId })).then(list => {
        this.dishes = list || []
      }).catch(() => {}).then(() => {
        this.loading = false
      })
    },
    loadMore() {
      // /dish/list 服务端已封顶 200 条，无需客户端翻页
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
      const exist = this.cart.find(item => item.dishId === dish.id)
      http.post('/shopping-cart/add', { dishId: dish.id, name: dish.name, number: exist ? exist.number + 1 : 1 })
        .then(() => this.loadCart())
        .catch(() => {})
    },
    sub(dish) {
      const exist = this.cart.find(item => item.dishId === dish.id)
      http.post('/shopping-cart/sub', { dishId: dish.id, number: exist ? exist.number : 1 })
        .then(() => this.loadCart())
        .catch(() => {})
    },
    goCheckout() {
      // 结算/支付链路依赖微信支付 JSAPI 与地址簿页，属下一轮迁移
      uni.showModal({
        title: '提示',
        content: '地址簿与支付页尚未迁移到小程序端，可先在「订单」页查看历史订单',
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
  height: 100vh;
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

.menu__empty {
  padding: 120rpx 0;
  text-align: center;
  font-size: 26rpx;
  color: var(--color-text-hint);
}

.cartbar {
  position: fixed;
  left: 24rpx;
  right: 24rpx;
  bottom: 24rpx;
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
