<template>
  <view class="menu">
    <!-- 金色门店头部（navigationStyle: custom，避让状态栏与微信胶囊） -->
    <view class="hero" :style="{ paddingTop: headPadTop + 'px' }">
      <view class="shop">
        <image class="shop__logo" :src="img(restaurant.logo)" mode="aspectFill" />
        <view class="shop__main">
          <text class="shop__name">{{ restaurant.name || '门店信息加载中…' }}</text>
          <view class="shop__meta">
            <text v-if="restaurant.stars" class="shop__stars">★ {{ restaurant.stars }}</text>
            <text v-if="restaurant.stars && restaurant.monthlySales" class="shop__dot">|</text>
            <text v-if="restaurant.monthlySales" class="shop__sales">月售{{ restaurant.monthlySales }}</text>
            <text v-if="restaurant.deliveryEnabled === false" class="shop__closed">暂停配送</text>
          </view>
          <view class="shop__info">
            <text v-if="minOrderAmount > 0">¥{{ fmtAmount(minOrderAmount) }} 起送</text>
            <text v-if="minOrderAmount > 0 && restaurant.businessHours" class="shop__dot">·</text>
            <text>{{ restaurant.businessHours || '营业时间以门店为准' }}</text>
          </view>
        </view>
      </view>

            <!-- P1-2 菜品搜索：跨分类本地过滤（名称/描述），清空恢复分类视图 -->
      <view class="searchbar">
        <input
          class="searchbar__input"
          type="text"
          :value="searchKw"
          placeholder="搜索菜品"
          confirm-type="search"
          @input="onSearchInput"
        />
        <text v-if="searchKw" class="searchbar__clear" hover-class="hover-press" @tap="clearSearch">×</text>
      </view>
<view v-if="restaurant.notice" class="notice" hover-class="hover-press" @tap="showNotice">
        <text class="notice__tag">公告</text>
        <text class="notice__text">{{ restaurant.notice }}</text>
      </view>
    </view>

    <view class="menu__body">
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
        <!-- 套餐在前：整行点开明细弹层，＋直接加购（@tap.stop 阻止冒泡） -->
        <view v-for="sm in displayedSetmeals" :key="'s' + sm.id" class="setmeal" hover-class="hover-press" @tap="openSetmeal(sm)">
          <view class="setmeal__imgbox">
            <image class="setmeal__img" :src="img(sm.image)" mode="aspectFill" lazy-load />
            <text class="setmeal__tag">套餐</text>
          </view>
          <view class="setmeal__body">
            <text class="setmeal__name">{{ sm.name }}</text>
            <text class="setmeal__desc">{{ sm.description || '超值组合 · 点击看套餐内容' }}</text>
            <view class="setmeal__foot">
              <view class="setmeal__pricebox">
                <text class="setmeal__yen">¥</text>
                <text class="setmeal__price">{{ fmtAmount(sm.price) }}</text>
              </view>
              <view class="counter">
                <text v-if="countOfSetmeal(sm.id)" class="counter__btn counter__btn--sub" :class="{ 'counter__btn--off': busy('carts:' + sm.id) }" hover-class="hover-press" @tap.stop="subSetmeal(sm)">−</text>
                <text v-if="countOfSetmeal(sm.id)" class="counter__num">{{ countOfSetmeal(sm.id) }}</text>
                <text class="counter__btn counter__btn--add" :class="{ 'counter__btn--off': busy('carts:' + sm.id) }" hover-class="hover-press" @tap.stop="addSetmeal(sm)">＋</text>
              </view>
            </view>
          </view>
        </view>

        <view v-if="setmeals.length && dishes.length" class="menu__divider"><text class="menu__divider-text">单点菜品</text></view>

        <view v-for="d in displayedDishes" :key="'d' + (d._hit !== undefined ? d.id + 's' : d.id)" class="dish">
          <!-- 菜品收藏：金色实心=已收藏；未登录由全局 401 拦截跳登录页 -->
          <text
            class="dish__fav"
            :class="{ 'dish__fav--on': isFavoriteDish(d.id), 'dish__fav--off': busy('fav:' + d.id) }"
            hover-class="hover-press"
            @tap.stop="toggleFavoriteDish(d)"
          >♥</text>
          <image class="dish__img" :src="img(d.image)" mode="aspectFill" lazy-load />
          <view class="dish__body">
            <text class="dish__name">{{ d.name }}</text>
            <text class="dish__desc">{{ d.description || '暂无描述' }}</text>
            <view class="dish__foot">
              <view class="dish__pricebox">
                <text class="dish__yen">¥</text>
                <text class="dish__price">{{ fmtAmount(d.price) }}</text>
              </view>
              <view class="counter">
                <text v-if="countOfDish(d.id)" class="counter__btn counter__btn--sub" :class="{ 'counter__btn--off': busy('cartd:' + d.id) }" hover-class="hover-press" @tap="subDish(d)">−</text>
                <text v-if="countOfDish(d.id)" class="counter__num">{{ countOfDish(d.id) }}</text>
                <text v-if="hasFlavor(d)" class="counter__spec" :class="{ 'counter__spec--off': busy('cartd:' + d.id) }" hover-class="hover-press" @tap="openFlavor(d)">选规格</text>
                <text v-else class="counter__btn counter__btn--add" :class="{ 'counter__btn--off': busy('cartd:' + d.id) }" hover-class="hover-press" @tap="addDish(d)">＋</text>
              </view>
            </view>
          </view>
        </view>
        <view v-if="loading && !dishes.length && !setmeals.length" class="menu__loading"><text>加载中…</text></view>
        <view v-if="listError" class="menu__error">
          <text>加载失败</text>
          <text class="menu__retry" @tap="reload()">重试</text>
        </view>
        <x-empty v-if="!dishes.length && !setmeals.length && !loading && !listError" :text="searchActive ? '没有找到相关菜品，换个词试试' : '该分类下暂无在售商品'" />
      </scroll-view>
    </view>


    <!-- 口味选择弹层（P0-4）：带口味菜品加购必经；语义对齐 H5 front 口味弹窗——每组单选、逗号拼串 -->
    <view v-if="fsheet.visible" class="msheet-mask" @tap="closeFlavor">
      <view class="fsheet" @tap.stop="noop">
        <view class="fsheet__head">
          <image v-if="fsheet.dish && fsheet.dish.image" class="fsheet__img" :src="img(fsheet.dish.image)" mode="aspectFill" />
          <view class="fsheet__t">
            <text class="fsheet__name">{{ fsheet.dish ? fsheet.dish.name : '' }}</text>
            <view class="fsheet__pricebox">
              <text class="fsheet__yen">¥</text>
              <text class="fsheet__price">{{ fsheet.dish ? fmtAmount(fsheet.dish.price) : '0.00' }}</text>
            </view>
          </view>
          <text class="fsheet__close" hover-class="hover-press" @tap="closeFlavor">×</text>
        </view>
        <scroll-view scroll-y class="fsheet__body">
          <view v-for="g in fsheet.groups" :key="g.id" class="fg">
            <view class="fg__name">{{ g.name }}</view>
            <view class="fg__opts">
              <text
                v-for="opt in g.options"
                :key="opt"
                class="fg__opt"
                :class="{ 'fg__opt--on': g.picked === opt }"
                hover-class="hover-press"
                @tap="pickFlavor(g, opt)"
              >{{ opt }}</text>
            </view>
          </view>
          <view v-if="!fsheet.groups.length" class="fsheet__empty">该菜品暂无可选口味</view>
        </scroll-view>
        <view class="fsheet__bar">
          <view class="fsheet__bar-pricebox">
            <text class="fsheet__yen">¥</text>
            <text class="fsheet__price">{{ fsheet.dish ? fmtAmount(fsheet.dish.price) : '0.00' }}</text>
          </view>
          <text
            class="fsheet__bar-add"
            :class="{ 'fsheet__bar-add--off': busy('cartd:' + (fsheet.dish && fsheet.dish.id)) }"
            hover-class="hover-press"
            @tap="addFromFlavorSheet"
          >加入购物车</text>
        </view>
      </view>
    </view>

    <!-- 深色购物车悬浮栏：小程序贴 webview 底（原生 tabBar 已占安全区），H5 条件编译抬到 tabBar 上方 -->
    <view class="cartbar">
      <view class="cartbar__icon" :class="{ 'cartbar__icon--on': cartCount > 0 }">
        <image
          class="cartbar__img"
          :src="cartCount > 0 ? '/static/brand/cart-active.png' : '/static/brand/cart.png'"
          mode="aspectFit"
        />
        <view v-if="cartCount > 0" class="cartbar__badge">{{ cartCount > 99 ? '99+' : cartCount }}</view>
      </view>

      <view class="cartbar__info">
        <block v-if="cartCount > 0">
          <view class="cartbar__amountline">
            <text class="cartbar__yen">¥</text>
            <text class="cartbar__amount">{{ cartAmount }}</text>
          </view>
          <text v-if="diffAmount > 0" class="cartbar__hint">还差¥{{ diffAmount.toFixed(2) }}起送</text>
        </block>
        <text v-else class="cartbar__empty">购物车空空如也</text>
      </view>

      <text
        v-if="cartCount > 0"
        class="cartbar__go"
        :class="{ 'cartbar__go--off': !canCheckout }"
        hover-class="hover-press"
        @tap="goCheckout"
      >{{ canCheckout ? '去结算' : '¥' + fmtAmount(minOrderAmount) + '起送' }}</text>
    </view>

    <!-- 套餐明细底部弹层：头图 + 套餐菜品清单 + 底部加购条 -->
    <view v-if="sheet.visible" class="msheet-mask" @tap="closeSetmeal">
      <view class="msheet" @tap.stop="noop">
        <view class="msheet__hero">
          <image v-if="sheet.data && sheet.data.image" class="msheet__img" :src="img(sheet.data.image)" mode="aspectFill" />
          <view class="msheet__shade"></view>
          <text class="msheet__close" hover-class="hover-press" @tap="closeSetmeal">×</text>
          <view class="msheet__titlebox">
            <text class="msheet__name">{{ sheet.data ? sheet.data.name : '' }}</text>
            <text v-if="sheet.data && sheet.data.description" class="msheet__desc">{{ sheet.data.description }}</text>
          </view>
        </view>
        <scroll-view scroll-y class="msheet__list">
          <view class="msheet__sub">套餐内容<text v-if="sheet.dishes.length">（{{ sheet.dishes.length }} 道）</text></view>
          <view v-for="sd in sheet.dishes" :key="sd.dishId || sd.id" class="mrow">
            <text class="mrow__name">{{ sd.name }}</text>
            <text class="mrow__copies">×{{ sd.copies }}</text>
          </view>
          <view v-if="!sheet.dishes.length" class="msheet__empty">明细加载中…</view>
        </scroll-view>
        <view class="msheet__bar">
          <view class="msheet__pricebox">
            <text class="msheet__yen">¥</text>
            <text class="msheet__price">{{ sheet.data ? fmtAmount(sheet.data.price) : '0.00' }}</text>
          </view>
          <view class="msheet__counter">
            <text v-if="sheetCount" class="counter__btn counter__btn--sub" hover-class="hover-press" @tap="subSetmeal(sheet.data)">−</text>
            <text v-if="sheetCount" class="counter__num">{{ sheetCount }}</text>
          </view>
          <text class="msheet__add" :class="{ 'msheet__add--off': sheet.data && busy('carts:' + sheet.data.id) }" hover-class="hover-press" @tap="joinFromSheet">加入购物车</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { http, qs, fullImage, listState, busyLock, fmtAmount } from '@xinfei/mini-shared'

export default {
  data() {
    // 修改点(2026-10-17)：接入列表三态（listError）与购物车加/减 in-flight 锁（busyLock）
    return Object.assign(listState(), busyLock(), {
      // 分类不传 type：菜品分类(1)与套餐分类(2)同栏展示，选中后右侧并行拉套餐+菜品
      categories: [],
      dishes: [],
      setmeals: [],
      activeCategoryId: null,
      cart: [],
      // 当前用户已收藏菜品 ID（GET /api/favorite/ids?targetType=1），控制行内心形显隐
      favoriteIds: [],
      loading: false,
      // 套餐明细底部弹层
      sheet: { visible: false, data: null, dishes: [] },
      // 口味选择弹层（P0-4）：groups 每项 { id, name, options[], picked }
      fsheet: { visible: false, dish: null, groups: [] },
      // 门店头信息（GET /restaurant/info，匿名可访问；失败静默不阻塞点餐）
      restaurant: {},
      // custom 导航：内容区上内边距（状态栏 + 导航条高度），px 为系统 API 返回单位
      headPadTop: 12
    })
  },
  computed: {
    // P1-2：搜索态（有关键字且已拉全量）→ 跨分类过滤；否则回分类视图
    searchActive() {
      return !!(this.searchKw && this.searchKw.trim() && this.allDishes)
    },
    displayedSetmeals() {
      return this.searchActive ? [] : this.setmeals
    },
    displayedDishes() {
      if (!this.searchActive) return this.dishes
      const kw = this.searchKw.trim().toLowerCase()
      return this.allDishes.filter(d =>
        (d.name || '').toLowerCase().indexOf(kw) !== -1 ||
        (d.description || '').toLowerCase().indexOf(kw) !== -1)
    },
    cartCount() {
      return this.cart.reduce((sum, item) => sum + (item.number || 0), 0)
    },
    cartAmount() {
      return this.cart.reduce((sum, item) => sum + Number(item.amount || 0) * (item.number || 0), 0).toFixed(2)
    },
    // 起送价（元）；接口未返回或 0 表示不限起送
    minOrderAmount() {
      const v = Number(this.restaurant.minOrder)
      return Number.isFinite(v) && v > 0 ? v : 0
    },
    diffAmount() {
      return Math.max(0, this.minOrderAmount - Number(this.cartAmount))
    },
    canCheckout() {
      return this.cartCount > 0 && (this.minOrderAmount <= 0 || Number(this.cartAmount) >= this.minOrderAmount)
    },
    // 弹层当前套餐在购物车中的份数
    sheetCount() {
      if (!this.sheet.data) return 0
      return this.countOfSetmeal(this.sheet.data.id)
    }
  },
  onLoad() {
    this.initCustomNav()
    this.loadCategories()
    this.loadCart()
    this.loadRestaurant()
    this.loadFavoriteIds()
  },
  onShow() {
    // 下单成功后服务端已清空购物车，tab 页只 onLoad 一次会残留旧数量，每次显示重拉
    if (this.categories.length) this.loadCart()
    // 从登录页/收藏页返回时同步心形状态
    this.loadFavoriteIds()
  },
  onPullDownRefresh() {
    Promise.all([this.loadCategories(), this.loadCart(), this.loadRestaurant(), this.loadFavoriteIds()]).then(() => uni.stopPullDownRefresh())
  },
  methods: {
    img: path => fullImage(path),
    fmtAmount,
    initCustomNav() {
      try {
        const info = uni.getWindowInfo ? uni.getWindowInfo() : uni.getSystemInfoSync()
        const statusBarHeight = info.statusBarHeight || 0
        // #ifdef MP-WEIXIN
        // 胶囊按钮 top/height 推算导航条高度，门店卡整体落在胶囊下方
        const cap = uni.getMenuButtonBoundingClientRect()
        const navBarHeight = cap && cap.height ? (cap.top - statusBarHeight) * 2 + cap.height : 44
        this.headPadTop = statusBarHeight + navBarHeight + 8
        // #endif
        // #ifndef MP-WEIXIN
        // H5 无状态栏与胶囊，只留一条小间隙
        this.headPadTop = statusBarHeight + 12
        // #endif
      } catch (e) {
        this.headPadTop = 12
      }
    },
    loadRestaurant() {
      return http.get('/restaurant/info').then(info => {
        this.restaurant = info || {}
      }).catch(() => {})
    },
    showNotice() {
      uni.showModal({ title: '店铺公告', content: this.restaurant.notice || '', showCancel: false })
    },
    loadCategories() {
      // 不传 type：菜品分类(1)+套餐分类(2)全部返回，按 sort 升序
      return http.get('/category/list').then(list => {
        this.categories = list || []
        if (this.categories.length && this.activeCategoryId === null) {
          this.activeCategoryId = this.categories[0].id
          return this.loadMenu()
        }
        return null
      }).catch(() => { this.listError = true })
    },
    // P1-2 搜索：首次输入懒拉全店菜品（/dish/list 不带 categoryId 即全量，后端封顶 200 条）
    onSearchInput(e) {
      this.searchKw = (e && e.detail && e.detail.value) || ''
      if (this.searchKw.trim() && !this.allDishes) {
        http.get('/dish/list').then(list => { this.allDishes = list || [] }).catch(() => {})
      }
    },
    clearSearch() {
      this.searchKw = ''
    },
    switchCategory(id) {
      if (id === this.activeCategoryId) return
      this.activeCategoryId = id
      this.loadMenu()
    },
    // 选中分类后并行加载套餐（在售）与菜品，套餐排在菜品前
    loadMenu() {
      this.loading = true
      this.listError = false
      return Promise.all([
        http.get(qs('/setmeal/list', { categoryId: this.activeCategoryId, status: 1 })).catch(() => []),
        http.get(qs('/dish/list', { categoryId: this.activeCategoryId })).catch(() => [])
      ]).then(([setmeals, dishes]) => {
        // 任一请求失败由 catch 吞成 []，两路皆败才显错误态，避免半胜显全错
        this.setmeals = setmeals || []
        this.dishes = dishes || []
      }).then(() => {
        this.loading = false
      })
    },
    // 修改点(2026-10-17)：列表失败重试——分类未加载成功时重拉分类，否则重拉当前分类菜单
    reload() {
      if (this.activeCategoryId === null) { this.loadCategories() } else { this.loadMenu() }
    },
    loadCart() {
      return http.get('/shopping-cart/list').then(list => {
        this.cart = list || []
      }).catch(() => {})
    },
    // 已收藏菜品 ID（仅菜品 targetType=1；接口失败静默为空，不阻塞点餐）
    loadFavoriteIds() {
      return http.get(qs('/api/favorite/ids', { targetType: 1 }), null, { silent: true })
        .then(ids => { this.favoriteIds = Array.isArray(ids) ? ids : [] })
        .catch(() => {})
    },
    isFavoriteDish(id) {
      return this.favoriteIds.some(fid => String(fid) === String(id))
    },
    toggleFavoriteDish(dish) {
      const key = 'fav:' + dish.id
      if (this.busy(key)) return
      this.setBusy(key, true)
      const wasFav = this.isFavoriteDish(dish.id)
      // 乐观更新，失败回滚（与旧 H5 首页同策略）
      this.favoriteIds = wasFav
        ? this.favoriteIds.filter(fid => String(fid) !== String(dish.id))
        : this.favoriteIds.concat([dish.id])
      http.post('/api/favorite/toggle', { targetType: 1, targetId: dish.id }, { silent: true })
        .then(res => {
          const favorited = !!(res && res.favorited)
          this.favoriteIds = favorited
            ? (this.isFavoriteDish(dish.id) ? this.favoriteIds : this.favoriteIds.concat([dish.id]))
            : this.favoriteIds.filter(fid => String(fid) !== String(dish.id))
          uni.showToast({ title: favorited ? '已收藏' : '已取消收藏', icon: 'none' })
        }).catch(() => {
          this.favoriteIds = wasFav
            ? (this.isFavoriteDish(dish.id) ? this.favoriteIds : this.favoriteIds.concat([dish.id]))
            : this.favoriteIds.filter(fid => String(fid) !== String(dish.id))
        }).then(() => { this.setBusy(key, false) })
    },
    countOfDish(dishId) {
      const hit = this.cart.find(item => item.dishId === dishId && !item.setmealId)
      return hit ? hit.number : 0
    },
    countOfSetmeal(setmealId) {
      const hit = this.cart.find(item => item.setmealId === setmealId)
      return hit ? hit.number : 0
    },
    addDish(dish, flavor) {
      const key = 'cartd:' + dish.id
      if (this.busy(key)) return Promise.resolve(false)
      this.setBusy(key, true)
      // P0-4：多口味菜同 dishId 在购物车按口味分行——exist 必须按「同口味」匹配
      const fv = flavor || ''
      const exist = this.cart.find(item => item.dishId === dish.id && !item.setmealId && (item.dishFlavor || '') === fv)
      // name/image/amount 服务端按 DB 回填，前端传值会被覆盖
      const payload = { dishId: dish.id, name: dish.name, number: exist ? exist.number + 1 : 1 }
      if (fv) payload.dishFlavor = fv
      return http.post('/shopping-cart/add', payload)
        .then(() => { this.loadCart(); return true })
        .catch(() => false)
        .then(ok => { this.setBusy(key, false); return ok })
    },
    subDish(dish) {
      const key = 'cartd:' + dish.id
      if (this.busy(key)) return
      this.setBusy(key, true)
      // P0-4：多口味菜取该菜品首条购物车行——后端 /sub 按 dishId+dishFlavor 精确减，
      // 无口味入参时只匹配空口味行，口味行会减不掉
      const exist = this.cart.find(item => item.dishId === dish.id && !item.setmealId)
      const payload = { dishId: dish.id, number: exist ? exist.number : 1 }
      if (exist && exist.dishFlavor) payload.dishFlavor = exist.dishFlavor
      http.post('/shopping-cart/sub', payload)
        .then(() => this.loadCart())
        .catch(() => {}).then(() => { this.setBusy(key, false) })
    },
    addSetmeal(sm) {
      const key = 'carts:' + sm.id
      if (this.busy(key)) return
      this.setBusy(key, true)
      const exist = this.cart.find(item => item.setmealId === sm.id)
      http.post('/shopping-cart/add', { setmealId: sm.id, name: sm.name, number: exist ? exist.number + 1 : 1 })
        .then(() => this.loadCart())
        .catch(() => {}).then(() => {
          this.setBusy(key, false)
          // 弹层开着时同步份数（loadCart 后 cart 已刷新，countOfSetmeal 响应式更新）
        })
    },
    subSetmeal(sm) {
      const key = 'carts:' + sm.id
      if (this.busy(key)) return
      this.setBusy(key, true)
      const exist = this.cart.find(item => item.setmealId === sm.id)
      http.post('/shopping-cart/sub', { setmealId: sm.id, number: exist ? exist.number : 1 })
        .then(() => this.loadCart())
        .catch(() => {}).then(() => { this.setBusy(key, false) })
    },
    openSetmeal(sm) {
      // 列表行点击：拉套餐明细（setmealDishes）后开弹层
      this.sheet = { visible: true, data: sm, dishes: [] }
      http.get('/setmeal/' + sm.id, null, { silent: true }).then(dto => {
        // 防止异步返回时弹层已切到别的套餐
        if (this.sheet.visible && this.sheet.data && String(this.sheet.data.id) === String(sm.id)) {
          this.sheet.data = Object.assign({}, sm, dto || {})
          this.sheet.dishes = (dto && dto.setmealDishes) || []
        }
      }).catch(() => {
        uni.showToast({ title: '套餐明细加载失败', icon: 'none' })
      })
    },
    closeSetmeal() {
      this.sheet.visible = false
    },
    // 弹层内容区吞点击，防止点内容触发遮罩关闭
    noop() {},
    joinFromSheet() {
      if (this.sheet.data) this.addSetmeal(this.sheet.data)
    },
    // ── P0-4 口味弹层 ──
    hasFlavor(d) {
      return !!(d && d.flavors && d.flavors.length)
    },
    openFlavor(d) {
      // value 是 JSON 字符串（如 '["微辣","中辣"]'），逐组解析；非法值该组降级为空不中断弹层
      const groups = (d.flavors || []).map(g => {
        let options = []
        try {
          const arr = JSON.parse(g.value)
          if (Array.isArray(arr)) options = arr
        } catch (e) { /* 非法 JSON：该组无选项，提交时会被「请选择」拦住 */ }
        return { id: g.id, name: g.name, options: options, picked: null }
      })
      this.fsheet = { visible: true, dish: d, groups: groups }
    },
    closeFlavor() {
      this.fsheet.visible = false
    },
    pickFlavor(g, opt) {
      g.picked = opt
    },
    addFromFlavorSheet() {
      const dish = this.fsheet.dish
      if (!dish) return
      const unselected = this.fsheet.groups.find(g => !g.picked && g.options.length)
      if (unselected) {
        uni.showToast({ title: '请选择' + unselected.name, icon: 'none' })
        return
      }
      const flavor = this.fsheet.groups.filter(g => g.picked).map(g => g.picked).join(',')
      // 仅成功后关弹层（失败保持已选口味可直接重试，与 H5 口径一致）
      this.addDish(dish, flavor).then(ok => { if (ok) this.fsheet.visible = false })
    },
    goCheckout() {
      if (!this.canCheckout) {
        uni.showToast({ title: '还差¥' + this.diffAmount.toFixed(2) + '起送', icon: 'none' })
        return
      }
      uni.navigateTo({ url: '/pages/order/checkout' })
    }
  }
}
</script>

<style>
.menu {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg-page);
}

/* ---------------- 金色门店头部 ---------------- */
.hero {
  flex-shrink: 0;
  padding: 0 24rpx 24rpx;
  background: var(--color-hero-gradient);
}

.shop {
  display: flex;
  padding: 24rpx 28rpx;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
  box-shadow: var(--shadow-md);
}

.shop__logo {
  width: 112rpx;
  height: 112rpx;
  border-radius: var(--radius-md);
  background: var(--bg-input);
  flex-shrink: 0;
}

.shop__main {
  flex: 1;
  min-width: 0;
  margin-left: 24rpx;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.shop__name {
  font-size: 34rpx;
  font-weight: 700;
  color: var(--color-text-primary);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.shop__meta {
  display: flex;
  align-items: center;
  margin-top: 8rpx;
  font-size: 24rpx;
}

.shop__stars {
  font-weight: 700;
  color: var(--color-tab-active);
}

.shop__sales {
  color: var(--color-text-secondary);
}

.shop__dot {
  margin: 0 12rpx;
  color: var(--color-text-hint);
}

.shop__closed {
  margin-left: 12rpx;
  padding: 2rpx 12rpx;
  border-radius: var(--radius-sm);
  font-size: 22rpx;
  color: var(--color-danger-text);
  background: var(--color-danger-tint);
}

.shop__info {
  display: flex;
  align-items: center;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: var(--color-text-hint);
}

.searchbar {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin: 16rpx 24rpx 0;
  background: var(--bg-card);
  border-radius: var(--radius-round);
  padding: 10rpx 24rpx;
}
.searchbar__input {
  flex: 1;
  font-size: 26rpx;
  color: var(--color-text-primary);
  height: 52rpx;
}
.searchbar__clear {
  font-size: 36rpx;
  color: var(--color-text-hint);
  padding: 0 8rpx;
}
.notice {
  display: flex;
  align-items: center;
  margin-top: 20rpx;
  padding: 14rpx 24rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.85);
}

.notice__tag {
  flex-shrink: 0;
  margin-right: 16rpx;
  font-size: 24rpx;
  font-weight: 600;
  color: var(--color-primary-text);
}

.notice__text {
  flex: 1;
  min-width: 0;
  font-size: 24rpx;
  color: var(--color-text-primary);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

/* ---------------- 分类 + 菜品 ---------------- */
.menu__body {
  flex: 1;
  display: flex;
  min-height: 0;
}

.menu__side {
  width: 160rpx;
  height: 100%;
  padding-bottom: 120rpx;
  box-sizing: border-box;
  background: var(--bg-page);
}

.side-item {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 116rpx;
  padding: 20rpx 12rpx;
  box-sizing: border-box;
  font-size: 24rpx;
  text-align: center;
  color: var(--color-text-secondary);
}

.side-item--on {
  background: var(--bg-card);
  color: var(--color-primary-text);
  font-weight: 600;
}

/* 选中项左侧 3×16 金色指示条 */
.side-item--on::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 6rpx;
  height: 32rpx;
  border-radius: 0 6rpx 6rpx 0;
  background: var(--color-primary);
}

.side-empty {
  padding: 40rpx 12rpx;
  font-size: 24rpx;
  color: var(--color-text-hint);
  text-align: center;
}

.menu__main {
  flex: 1;
  height: 100%;
  padding-bottom: 120rpx;
  box-sizing: border-box;
  background: var(--bg-card);
}

.dish {
  position: relative;
  display: flex;
  padding: 20rpx 24rpx;
}

/* 行分隔发丝线，左起 110px 对齐文字区 */
.dish::after {
  content: '';
  position: absolute;
  left: 220rpx;
  right: 0;
  bottom: 0;
  height: 1rpx;
  background: var(--border-hairline);
}

.dish__img {
  width: 176rpx;
  height: 176rpx;
  border-radius: var(--radius-md);
  flex-shrink: 0;
  background: var(--bg-input);
}

.dish__body {
  flex: 1;
  min-width: 0;
  margin-left: 20rpx;
  display: flex;
  flex-direction: column;
}

.dish__name {
  display: block;
  /* 给右上收藏心形让位，避免长菜名压到 ♥ */
  padding-right: 48rpx;
  font-size: 32rpx;
  font-weight: 600;
  color: var(--color-text-primary);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

/* 菜品收藏心形：未收藏灰、已收藏金；仅支持菜品（套餐后端未开放） */
.dish__fav {
  position: absolute;
  top: 16rpx;
  right: 20rpx;
  z-index: 1;
  font-size: 34rpx;
  line-height: 1;
  color: var(--color-text-hint);
}

.dish__fav--on {
  color: var(--color-primary);
}

.dish__fav--off {
  opacity: 0.5;
}

.dish__desc {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  margin-top: 8rpx;
  font-size: 24rpx;
  line-height: 34rpx;
  color: var(--color-text-hint);
  overflow: hidden;
}

.dish__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
  padding-top: 12rpx;
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
  font-size: 40rpx;
  font-weight: 800;
  color: var(--color-price);
}

.counter {
  display: flex;
  align-items: center;
}

.counter__btn {
  width: 48rpx;
  height: 48rpx;
  line-height: 46rpx;
  text-align: center;
  border-radius: var(--radius-round);
  font-size: 32rpx;
  box-sizing: border-box;
}

.counter__btn--add {
  color: var(--color-text-white);
  background: var(--color-primary-gradient);
  box-shadow: var(--shadow-primary-sm);
}

.counter__btn--sub {
  color: var(--color-primary-text);
  background: var(--bg-card);
  border: 1rpx solid var(--color-primary);
}

.counter__num {
  min-width: 48rpx;
  text-align: center;
  font-size: 28rpx;
  font-weight: 700;
  color: var(--color-text-primary);
}

.counter__btn--off {
  opacity: 0.5;
}

/* ── P0-4 选规格入口与口味弹层 ── */
.counter__spec {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 108rpx;
  height: 52rpx;
  padding: 0 20rpx;
  border-radius: var(--radius-round);
  font-size: 24rpx;
  font-weight: 600;
  color: var(--color-primary-text);
  background: var(--color-primary-gradient);
  box-shadow: var(--shadow-primary-sm);
  box-sizing: border-box;
}
.counter__spec--off { opacity: 0.5; }

.fsheet {
  position: absolute;
  left: 0; right: 0; bottom: 0;
  background: var(--bg-page);
  border-radius: var(--radius-lg) var(--radius-lg) 0 0;
  max-height: 70vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.fsheet__head {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 24rpx;
  background: var(--bg-card);
}
.fsheet__img { width: 120rpx; height: 120rpx; border-radius: var(--radius-md); flex-shrink: 0; }
.fsheet__t { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 8rpx; }
.fsheet__name { font-size: 30rpx; font-weight: 700; color: var(--color-text-primary); }
.fsheet__pricebox { display: flex; align-items: baseline; }
.fsheet__yen { font-size: 24rpx; color: var(--color-primary-text-strong); }
.fsheet__price { font-size: 34rpx; font-weight: 700; color: var(--color-primary-text-strong); }
.fsheet__close {
  width: 56rpx; height: 56rpx; border-radius: var(--radius-round);
  background: var(--bg-page); color: var(--color-text-hint);
  display: flex; align-items: center; justify-content: center;
  font-size: 40rpx; flex-shrink: 0;
}
.fsheet__body { max-height: 46vh; padding: 8rpx 24rpx; box-sizing: border-box; }
.fg { padding: 20rpx 0; }
.fg__name { font-size: 27rpx; font-weight: 600; color: var(--color-text-primary); margin-bottom: 16rpx; }
.fg__opts { display: flex; flex-wrap: wrap; gap: 16rpx; }
.fg__opt {
  padding: 10rpx 32rpx;
  border-radius: var(--radius-round);
  font-size: 25rpx;
  color: var(--color-text-secondary);
  background: var(--bg-card);
  border: 1rpx solid var(--color-border);
}
.fg__opt--on {
  color: var(--color-primary-text);
  background: var(--color-primary-tint);
  border-color: var(--color-primary);
  font-weight: 600;
}
.fsheet__empty { padding: 60rpx 0; text-align: center; font-size: 26rpx; color: var(--color-text-hint); }
.fsheet__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 24rpx calc(20rpx + env(safe-area-inset-bottom));
  background: var(--bg-card);
  border-top: 1rpx solid var(--color-border-light);
}
.fsheet__bar-pricebox { display: flex; align-items: baseline; }
.fsheet__bar-add {
  padding: 16rpx 48rpx;
  border-radius: var(--radius-round);
  font-size: 27rpx;
  font-weight: 700;
  color: var(--color-primary-text);
  background: var(--color-primary-gradient);
  box-shadow: var(--shadow-primary-sm);
}
.fsheet__bar-add--off { opacity: 0.5; }


.menu__loading,
.menu__error {
  padding: 120rpx 0;
  text-align: center;
  font-size: 26rpx;
  color: var(--color-text-hint);
}

.menu__retry {
  margin-left: 12rpx;
  color: var(--color-primary-text);
}

/* ---------------- 套餐行（与菜品同族，金色角标区分） ---------------- */
.setmeal {
  position: relative;
  display: flex;
  padding: 20rpx 24rpx;
}

.setmeal::after {
  content: '';
  position: absolute;
  left: 220rpx;
  right: 0;
  bottom: 0;
  height: 1rpx;
  background: var(--border-hairline);
}

.setmeal__imgbox {
  position: relative;
  width: 176rpx;
  height: 176rpx;
  flex-shrink: 0;
}

.setmeal__img {
  width: 176rpx;
  height: 176rpx;
  border-radius: var(--radius-md);
  background: var(--bg-input);
}

.setmeal__tag {
  position: absolute;
  left: 0;
  top: 0;
  padding: 2rpx 14rpx;
  border-radius: var(--radius-md) 0 var(--radius-md) 0;
  background: var(--color-primary-gradient);
  color: var(--color-primary-text);
  font-size: 20rpx;
  font-weight: 700;
  line-height: 1.7;
}

.setmeal__body {
  flex: 1;
  min-width: 0;
  margin-left: 20rpx;
  display: flex;
  flex-direction: column;
}

.setmeal__name {
  display: block;
  font-size: 32rpx;
  font-weight: 600;
  color: var(--color-text-primary);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.setmeal__desc {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  margin-top: 8rpx;
  font-size: 24rpx;
  line-height: 34rpx;
  color: var(--color-text-hint);
  overflow: hidden;
}

.setmeal__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
  padding-top: 12rpx;
}

.setmeal__pricebox {
  display: flex;
  align-items: baseline;
}

.setmeal__yen {
  font-size: 24rpx;
  font-weight: 700;
  color: var(--color-price);
}

.setmeal__price {
  font-size: 40rpx;
  font-weight: 800;
  color: var(--color-price);
}

/* 套餐与单点菜品之间的小标题分隔 */
.menu__divider {
  padding: 20rpx 24rpx 8rpx;
}

.menu__divider-text {
  font-size: 22rpx;
  color: var(--color-text-hint);
  letter-spacing: 2rpx;
}

/* ---------------- 套餐明细底部弹层 ---------------- */
.msheet-mask {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  z-index: 100;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: flex-end;
}

.msheet {
  width: 100%;
  max-height: 82vh;
  display: flex;
  flex-direction: column;
  background: var(--bg-card);
  border-radius: 32rpx 32rpx 0 0;
  overflow: hidden;
}

.msheet__hero {
  position: relative;
  flex-shrink: 0;
  height: 280rpx;
  background: var(--bg-input);
}

.msheet__img {
  width: 100%;
  height: 280rpx;
}

.msheet__shade {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 160rpx;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0) 0%, rgba(0, 0, 0, 0.55) 100%);
}

.msheet__close {
  position: absolute;
  top: 20rpx;
  right: 20rpx;
  width: 56rpx;
  height: 56rpx;
  line-height: 52rpx;
  text-align: center;
  border-radius: var(--radius-round);
  background: rgba(0, 0, 0, 0.4);
  color: var(--color-white, #fff);
  font-size: 36rpx;
}

.msheet__titlebox {
  position: absolute;
  left: 28rpx;
  right: 28rpx;
  bottom: 20rpx;
  display: flex;
  flex-direction: column;
}

.msheet__name {
  font-size: 36rpx;
  font-weight: 700;
  color: var(--color-white, #fff);
}

.msheet__desc {
  margin-top: 6rpx;
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.85);
}

.msheet__list {
  flex: 1;
  min-height: 0;
  max-height: 480rpx;
}

.msheet__sub {
  padding: 24rpx 28rpx 8rpx;
  font-size: 26rpx;
  font-weight: 600;
  color: var(--color-text-hint);
}

.mrow {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 22rpx 28rpx;
  position: relative;
}

.mrow + .mrow::before {
  content: '';
  position: absolute;
  top: 0;
  left: 28rpx;
  right: 0;
  height: 1rpx;
  background: var(--border-hairline);
}

.mrow__name {
  font-size: 28rpx;
  color: var(--color-text-primary);
}

.mrow__copies {
  font-size: 26rpx;
  color: var(--color-text-hint);
}

.msheet__empty {
  padding: 60rpx 0;
  text-align: center;
  font-size: 26rpx;
  color: var(--color-text-hint);
}

.msheet__bar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  padding: 16rpx 28rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  border-top: 1rpx solid var(--border-light);
}

.msheet__pricebox {
  display: flex;
  align-items: baseline;
}

.msheet__yen {
  font-size: 26rpx;
  font-weight: 700;
  color: var(--color-price);
}

.msheet__price {
  font-size: 44rpx;
  font-weight: 800;
  color: var(--color-price);
}

.msheet__counter {
  display: flex;
  align-items: center;
  margin-left: 24rpx;
}

.msheet__add {
  margin-left: auto;
  padding: 0 48rpx;
  height: 80rpx;
  line-height: 80rpx;
  border-radius: 999rpx;
  background: var(--color-primary-gradient);
  box-shadow: var(--shadow-primary-sm);
  color: var(--color-primary-text);
  font-size: 28rpx;
  font-weight: 700;
}

.msheet__add--off {
  opacity: 0.5;
}

/* ---------------- 深色购物车悬浮栏 ---------------- */
.cartbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  height: 96rpx;
  padding: 0 24rpx;
  box-sizing: border-box;
  background: var(--color-bar);
  box-shadow: var(--shadow-bar-top);
}

/* #ifdef H5 */
/* H5 的 tabBar 是页面内元素，悬浮栏需抬到 tabBar（100rpx + 安全区）之上 */
.cartbar {
  bottom: calc(100rpx + env(safe-area-inset-bottom));
}
.menu__main,
.menu__side {
  padding-bottom: 220rpx;
}
/* #endif */

.cartbar__icon {
  position: relative;
  width: 96rpx;
  height: 96rpx;
  margin-top: -36rpx;
  border: 6rpx solid var(--color-bar);
  border-radius: var(--radius-round);
  background: var(--color-bar);
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cartbar__img {
  width: 64rpx;
  height: 64rpx;
}

.cartbar__badge {
  position: absolute;
  top: -8rpx;
  right: -12rpx;
  min-width: 32rpx;
  height: 32rpx;
  padding: 0 8rpx;
  border-radius: 999rpx;
  background: var(--color-price);
  font-size: 20rpx;
  font-weight: 700;
  line-height: 32rpx;
  text-align: center;
  color: var(--color-text-white);
  box-sizing: border-box;
}

.cartbar__info {
  flex: 1;
  min-width: 0;
  margin-left: 20rpx;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.cartbar__amountline {
  display: flex;
  align-items: baseline;
}

.cartbar__yen {
  font-size: 24rpx;
  font-weight: 700;
  color: var(--color-text-white);
  opacity: 0.75;
}

.cartbar__amount {
  font-size: 36rpx;
  font-weight: 700;
  color: var(--color-text-white);
}

.cartbar__hint {
  margin-top: 2rpx;
  font-size: 22rpx;
  color: var(--color-text-white);
  opacity: 0.8;
}

.cartbar__empty {
  font-size: 26rpx;
  color: var(--color-text-hint);
}

.cartbar__go {
  flex-shrink: 0;
  width: 200rpx;
  height: 72rpx;
  line-height: 72rpx;
  border-radius: var(--radius-md);
  text-align: center;
  font-size: 28rpx;
  font-weight: 700;
  color: var(--color-primary-text);
  background: var(--color-primary-gradient);
  box-shadow: var(--shadow-primary-sm);
}

/* 未达起送：深栏上的禁用态（半透明白，不引入新色值） */
.cartbar__go--off {
  color: rgba(255, 255, 255, 0.5);
  background: rgba(255, 255, 255, 0.14);
  box-shadow: none;
}
</style>
