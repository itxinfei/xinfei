<template>
  <view class="fq">
    <!-- 快捷说明头 -->
    <view class="head">
      <view class="head__icon"><text>★</text></view>
      <view class="head__text">
        <text class="head__title">您的常购清单</text>
        <text class="head__desc">快速复购您喜爱的菜品</text>
      </view>
    </view>

    <!-- 分类标签：全部/菜品/套餐（仅一类时不显示） -->
    <view v-if="categories.length > 1" class="cats">
      <view
        v-for="cat in categories"
        :key="cat"
        class="cat"
        :class="{ 'cat--on': activeCategory === cat }"
        hover-class="hover-press"
        @tap="activeCategory = cat"
      >{{ cat }}</view>
    </view>

    <block v-if="filteredItems.length">
      <text class="grouptitle">按点餐频率排序</text>
      <view v-for="item in filteredItems" :key="item.key" class="dish">
        <image v-if="item.image" class="dish__img" :src="img(item.image)" mode="aspectFill" lazy-load />
        <view v-else class="dish__img dish__img--ph"><text>菜</text></view>
        <view class="dish__body">
          <text class="dish__name">{{ item.name }}<text v-if="item.dishFlavor" class="dish__flavor">（{{ item.dishFlavor }}）</text></text>
          <view class="dish__meta">
            <text class="dish__badge">点过{{ item.count }}次</text>
            <text v-if="lastTimeText(item.lastOrderTime)" class="dish__time">{{ lastTimeText(item.lastOrderTime) }}</text>
          </view>
          <view class="dish__foot">
            <view class="dish__pricebox">
              <text class="dish__yen">¥</text>
              <text class="dish__price">{{ fmtAmount(item.price) }}</text>
            </view>
            <text
              class="dish__add"
              :class="{ 'dish__add--off': busy('add:' + item.key) }"
              hover-class="hover-press"
              @tap="reorder(item)"
            >{{ busy('add:' + item.key) ? '加入中…' : '再加一单' }}</text>
          </view>
        </view>
      </view>
      <text class="hint">基于您的历史订单生成（最近 3 页订单聚合）</text>
    </block>

    <view v-else-if="listError" class="state">
      <text>加载失败</text>
      <text class="state__retry" @tap="initData">重新加载</text>
    </view>
    <x-empty
      v-else-if="!loading && !items.length"
      text="暂无常购菜品"
      sub="去点餐后，这里会展示您的常购清单"
    />
    <x-empty v-else-if="!loading" text="该分类下暂无常购菜品" />
    <view v-if="loading" class="list__more"><text>加载中…</text></view>
  </view>
</template>

<script>
// 常购清单：后端无专用接口，与 Web frequent-orders.html 同构——
// 复用 GET /order/userPage 拉最近 3 页订单（每页 100，后端 PageUtils 上限），
// 前端按「菜品/套餐 + ID」聚合计次；单菜复购走 POST /shopping-cart/add（服务端按 DB 回填真实价格）。
import { http, qs, fullImage, listState, busyLock, fmtAmount } from '@xinfei/mini-shared'

const MAX_PAGES = 3
const PAGE_SIZE = 100

export default {
  data() {
    return Object.assign(listState(), busyLock(), {
      items: [],
      activeCategory: '全部',
      categories: ['全部'],
      loading: false
    })
  },
  computed: {
    filteredItems() {
      if (this.activeCategory === '全部') return this.items
      return this.items.filter(item => item.category === this.activeCategory)
    }
  },
  onLoad() {
    this.initData()
  },
  methods: {
    fmtAmount,
    img: path => fullImage(path),
    initData() {
      this.loading = true
      this.listError = false
      this.aggregate().catch(() => { this.listError = true }).then(() => {
        this.loading = false
      })
    },
    async aggregate() {
      const itemMap = new Map()
      const categorySet = new Set(['全部'])
      let page = 1
      let hasMore = true

      while (hasMore && page <= MAX_PAGES) {
        const data = await http.get(qs('/order/userPage', { page, pageSize: PAGE_SIZE }), null, { silent: true })
        const records = (data && data.records) || []
        if (!records.length) break
        for (const order of records) {
          const details = order.orderDetails || []
          for (const d of details) {
            // 菜品与套餐 ID 可能同值，必须带类型前缀（d_ / s_ / x_ 兜底），否则会误合并
            // P0-4：聚合 key 带口味串——同菜不同口味是不同常购项（如 微辣/特辣），误合并会丢规格
            const key = d.setmealId != null ? 's_' + d.setmealId
              : (d.dishId != null ? 'd_' + d.dishId + (d.dishFlavor ? '|' + d.dishFlavor : '') : 'x_' + d.id)
            const cat = d.setmealId != null ? '套餐' : '菜品'
            categorySet.add(cat)
            const existing = itemMap.get(key)
            if (existing) {
              existing.count += d.number || 1
              if (order.orderTime && (!existing.lastOrderTime || order.orderTime > existing.lastOrderTime)) {
                existing.lastOrderTime = order.orderTime
                existing.price = d.amount
              }
            } else {
              itemMap.set(key, {
                key,
                name: d.name,
                price: d.amount,
                count: d.number || 1,
                category: cat,
                lastOrderTime: order.orderTime,
                image: d.image || '',
                dishId: d.dishId || null,
                setmealId: d.setmealId || null,
                dishFlavor: d.dishFlavor || ''
              })
            }
          }
        }
        hasMore = page < (data && data.pages ? data.pages : 1)
        page += 1
      }

      this.items = Array.from(itemMap.values()).sort((a, b) => b.count - a.count).slice(0, 20)
      this.categories = Array.from(categorySet)
    },
    lastTimeText(t) {
      if (!t) return ''
      const d = new Date(String(t).replace(/-/g, '/').replace('T', ' '))
      if (isNaN(d.getTime())) return ''
      return (d.getMonth() + 1) + '月' + d.getDate() + '日'
    },
    // 一键再加购：单菜语义（非整单 /order/again），POST /shopping-cart/add
    // amount/name/image 由服务端按 DB 校验回填，仅作展示兜底；P0-4：dishFlavor 从历史明细透传——精确还原该口味行
    reorder(item) {
      const key = 'add:' + item.key
      if (this.busy(key)) return
      if (!item.dishId && !item.setmealId) {
        uni.showToast({ title: '该菜品暂不支持单独复购', icon: 'none' })
        return
      }
      this.setBusy(key, true)
      const params = {
        number: 1,
        amount: parseFloat(Number(item.price || 0).toFixed(2)),
        name: item.name,
        image: item.image || ''
      }
      if (item.setmealId != null) params.setmealId = item.setmealId
      else {
        params.dishId = item.dishId
        // 空 dishFlavor 不传：后端空口味行与口味行是两条购物车记录
        if (item.dishFlavor) params.dishFlavor = item.dishFlavor
      }
      http.post('/shopping-cart/add', params).then(() => {
        uni.showToast({ title: '已加入购物车', icon: 'none' })
      }).catch(() => {}).then(() => { this.setBusy(key, false) })
    }
  }
}
</script>

<style>
.fq {
  min-height: 100%;
  padding: 24rpx 24rpx 48rpx;
  box-sizing: border-box;
  background: var(--bg-page);
}

/* ---------------- 快捷说明头 ---------------- */
.head {
  display: flex;
  align-items: center;
  padding: 28rpx;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
  box-shadow: var(--shadow-card);
}

.head__icon {
  width: 88rpx;
  height: 88rpx;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-round);
  font-size: 44rpx;
  color: var(--color-primary);
  background: var(--color-primary-tint);
}

.head__text {
  margin-left: 20rpx;
  display: flex;
  flex-direction: column;
}

.head__title {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

.head__desc {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: var(--color-text-hint);
}

/* ---------------- 分类标签 ---------------- */
.cats {
  display: flex;
  gap: 16rpx;
  margin-top: 20rpx;
}

.cat {
  padding: 12rpx 36rpx;
  font-size: 26rpx;
  color: var(--color-text-secondary);
  border-radius: var(--radius-round);
  background: var(--bg-card);
  border: 1rpx solid var(--border-medium);
}

.cat--on {
  font-weight: 600;
  color: var(--color-primary-text);
  background: var(--color-primary-tint);
  border-color: var(--color-primary);
}

.grouptitle {
  display: block;
  margin: 28rpx 4rpx 0;
  font-size: 26rpx;
  font-weight: 600;
  color: var(--color-text-secondary);
}

/* ---------------- 菜品卡 ---------------- */
.dish {
  display: flex;
  margin-top: 16rpx;
  padding: 24rpx;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
  box-shadow: var(--shadow-card);
}

.dish__img {
  width: 160rpx;
  height: 160rpx;
  flex-shrink: 0;
  border-radius: var(--radius-md);
  background: var(--bg-input);
}

.dish__img--ph {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30rpx;
  color: var(--color-text-hint);
}

.dish__body {
  flex: 1;
  min-width: 0;
  margin-left: 20rpx;
  display: flex;
  flex-direction: column;
}

.dish__name {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--color-text-primary);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.dish__meta {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-top: 10rpx;
}

.dish__flavor { font-size: 22rpx; color: var(--color-text-hint); font-weight: 400; }
.dish__badge {
  padding: 2rpx 14rpx;
  font-size: 20rpx;
  color: var(--color-primary-text-strong);
  background: var(--color-primary-tint);
  border-radius: var(--radius-sm);
}

.dish__time {
  font-size: 22rpx;
  color: var(--color-text-hint);
}

.dish__foot {
  margin-top: auto;
  padding-top: 12rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.dish__pricebox {
  display: flex;
  align-items: baseline;
}

.dish__yen {
  font-size: 24rpx;
  font-weight: 700;
  color: var(--color-price);
}

.dish__price {
  font-size: 36rpx;
  font-weight: 800;
  color: var(--color-price);
}

.dish__add {
  padding: 10rpx 28rpx;
  border-radius: var(--radius-round);
  font-size: 26rpx;
  color: var(--color-primary-text);
  background: var(--color-primary-gradient);
  box-shadow: var(--shadow-primary-sm);
}

.dish__add--off {
  opacity: 0.5;
}

.hint {
  display: block;
  margin-top: 28rpx;
  text-align: center;
  font-size: 22rpx;
  color: var(--color-text-hint);
}

/* ---------------- 状态区 ---------------- */
.state {
  padding: 120rpx 0;
  text-align: center;
  font-size: 26rpx;
  color: var(--color-text-hint);
}

.state__retry {
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
