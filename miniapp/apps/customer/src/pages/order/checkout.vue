<template>
  <view class="checkout">
    <!-- 履约方式：配送上门 / 到店自提（自提豁免地址、配送费 0，后端 preview/submit 已支持） -->
    <view class="mode-tabs">
      <text
        class="mode-tab"
        :class="{ 'mode-tab--on': fulfillmentMode === 'delivery' }"
        hover-class="hover-press"
        @tap="switchMode('delivery')"
      >配送上门</text>
      <text
        class="mode-tab"
        :class="{ 'mode-tab--on': fulfillmentMode === 'pickup' }"
        hover-class="hover-press"
        @tap="switchMode('pickup')"
      >到店自提</text>
    </view>

    <!-- 配送：收货地址；无地址时整卡引导去选择 -->
    <view v-if="fulfillmentMode === 'delivery'" class="card address" hover-class="hover-press" @tap="chooseAddress">
      <template v-if="address">
        <view class="address__main">
          <view class="address__head">
            <text class="address__name">{{ address.consignee || '收货人' }}</text>
            <text class="address__phone">{{ address.phone }}</text>
            <text v-if="address.isDefault === 1" class="address__tag">默认</text>
          </view>
          <text class="address__detail">{{ composeAddress(address) }}</text>
        </view>
        <text class="address__arrow">›</text>
      </template>
      <view v-else class="address__empty">
        <text class="address__empty-text">请选择收货地址</text>
        <text class="address__arrow">›</text>
      </view>
    </view>

    <!-- 自提：门店信息卡（/restaurant/info 带 address 时展示门店地址，无值不渲染该行） -->
    <view v-else class="card pickup">
      <view class="pickup__head">
        <text class="pickup__name">{{ (restaurant && restaurant.name) || '门店自提' }}</text>
        <text v-if="restaurant && restaurant.businessHours" class="pickup__hours">营业时间 {{ restaurant.businessHours }}</text>
      </view>
      <text v-if="restaurant && restaurant.address" class="pickup__addr">门店地址：{{ restaurant.address }}</text>
      <text v-if="restaurant && restaurant.notice" class="pickup__notice">{{ restaurant.notice }}</text>
      <text class="pickup__tip">下单后到店向店员出示取餐码即可取餐，无需配送</text>
    </view>

    <!-- 期望送达时间（O-07）：配送模式可选「尽快」或预约今天/明天/后天时段，与 H5 同口径 -->
    <view v-if="!isPickup" class="card dtime">
      <view class="dtime__row" hover-class="hover-press" @tap="timePanelOpen = !timePanelOpen">
        <text class="dtime__label">期望送达</text>
        <view class="dtime__value">
          <text>{{ deliveryTimeText }}</text>
          <text class="dtime__arrow">{{ timePanelOpen ? '⌃' : '⌄' }}</text>
        </view>
      </view>
      <view v-if="timePanelOpen" class="dtime__panel">
        <view class="dtime__days">
          <text
            v-for="(d, i) in timeDays"
            :key="d.label"
            class="dtime__day"
            :class="{ 'dtime__day--on': timeDayIdx === i }"
            hover-class="hover-press"
            @tap="pickDay(i)"
          >{{ d.label }}</text>
        </view>
        <view
          v-for="(s, i) in timeSlotsForDay"
          :key="s.value"
          class="dtime__opt"
          hover-class="hover-press"
          @tap="pickSlot(i)"
        >
          <view class="dtime__main">
            <text class="dtime__name">{{ s.label }}</text>
            <text v-if="s.time" class="dtime__meta">{{ s.time }}</text>
          </view>
          <view class="radio" :class="{ 'radio--on': timeSlotIdx === i }"></view>
        </view>
      </view>
    </view>

    <!-- 商品明细（数据来自只读核价 preview，与下单同源） -->
    <view class="card">
      <view class="section-title">商品清单</view>
      <view v-if="cartEmpty" class="cart-empty">
        <text>购物车为空，请先选购商品</text>
        <text class="cart-empty__btn" hover-class="hover-press" @tap="goMenu">去点餐</text>
      </view>
      <template v-else>
        <view v-for="(d, i) in details" :key="i" class="goods">
          <image v-if="d.image" class="goods__img" :src="fullImage(d.image)" mode="aspectFill" lazy-load />
          <view v-else class="goods__img goods__img--ph"></view>
          <view class="goods__info">
            <text class="goods__name">{{ d.name }}</text>
            <text v-if="d.flashSaleName" class="goods__tag">{{ d.flashSaleName }}</text>
          </view>
          <text class="goods__qty">×{{ d.quantity }}</text>
          <text class="goods__price">¥{{ fmtAmount(d.lineAmount) }}</text>
        </view>
        <!-- 买赠赠品 -->
        <view v-for="(g, i) in gifts" :key="'g' + i" class="gift">
          <text class="gift__tag">赠</text>
          <text class="gift__name">{{ g.name }}</text>
          <text class="gift__qty">×{{ g.quantity }}</text>
        </view>
      </template>
    </view>

    <!-- 备注 -->
    <view class="card remark-card">
      <text class="section-title">订单备注</text>
      <textarea
        v-model="remark"
        class="remark-card__input"
        maxlength="100"
        placeholder="口味、偏好等要求（选填，100 字以内）"
        placeholder-class="remark-card__ph"
      />
      <text class="remark-card__count">{{ remark.length }}/100</text>
    </view>

    <!-- 支付方式：小程序内仅微信 JSAPI 与货到/到店付款；支付宝无 JSAPI 能力，保持灰显 -->
    <view class="card">
      <view class="section-title">支付方式</view>
      <view class="pay">
        <view class="pay__item" hover-class="hover-press" @tap="payMethod = 2">
          <text class="pay__label">微信支付</text>
          <view class="radio" :class="{ 'radio--on': payMethod === 2 }"></view>
        </view>
        <!-- 会员储值（O-06）：余额>0 才展示；是否够付由服务端下单扣款时校验 -->
        <view v-if="memberBalance > 0" class="pay__item" hover-class="hover-press" @tap="payMethod = 5">
          <text class="pay__label">会员储值（余额 ¥{{ fmtAmount(memberBalance) }}）</text>
          <view class="radio" :class="{ 'radio--on': payMethod === 5 }"></view>
        </view>
        <view class="pay__item" hover-class="hover-press" @tap="payMethod = 6">
          <text class="pay__label">{{ fulfillmentMode === 'pickup' ? '到店付款' : '货到付款' }}</text>
          <view class="radio" :class="{ 'radio--on': payMethod === 6 }"></view>
        </view>
        <view class="pay__item pay__item--off">
          <text class="pay__label">支付宝</text>
          <text class="pay__soon">小程序暂不支持</text>
        </view>
      </view>
    </view>

    <!-- 价格明细 -->
    <view class="card" v-if="pricing">
      <view class="section-title">价格明细</view>
      <view class="row">
        <text class="row__label">商品小计</text>
        <text class="row__value">¥{{ fmtAmount(pricing.goodsAmount) }}</text>
      </view>
      <view v-if="flashSavings > 0" class="row">
        <text class="row__label">秒杀优惠</text>
        <text class="row__value row__value--off">-¥{{ fmtAmount(flashSavings) }}</text>
      </view>
      <view v-if="activityAmount(pricing.fullReduction) > 0" class="row">
        <text class="row__label">满减优惠</text>
        <text class="row__value row__value--off">-¥{{ fmtAmount(activityAmount(pricing.fullReduction)) }}</text>
      </view>
      <view v-if="activityAmount(pricing.newCustomer) > 0" class="row">
        <text class="row__label">新客立减</text>
        <text class="row__value row__value--off">-¥{{ fmtAmount(activityAmount(pricing.newCustomer)) }}</text>
      </view>
      <!-- 优惠券：可用券与抵扣额均以后端按商品额实时计算为准，前端不自行计价 -->
      <view class="row" hover-class="hover-press" @tap="toggleCouponPicker">
        <text class="row__label">优惠券</text>
        <view class="coupon-entry">
          <block v-if="chosenCoupon">
            <text class="coupon-entry__name">{{ chosenCoupon.name }}</text>
            <text class="coupon-entry__pick">更换 ›</text>
          </block>
          <text v-else-if="usableCoupons.length" class="coupon-entry__pick">{{ usableCoupons.length }} 张可用 ›</text>
          <text v-else class="coupon-entry__none">暂无可用 ›</text>
        </view>
      </view>
      <view v-if="couponDiscount > 0" class="row">
        <text class="row__label">优惠券抵扣</text>
        <text class="row__value row__value--off">-¥{{ fmtAmount(couponDiscount) }}</text>
      </view>
      <view v-if="couponPickerOpen" class="coupon-panel">
        <view class="coupon-opt" hover-class="hover-press" @tap="chooseCoupon(null)">
          <text class="coupon-opt__label">不使用优惠券</text>
          <view class="radio" :class="{ 'radio--on': chosenCouponId === null }"></view>
        </view>
        <view
          v-for="c in usableCoupons"
          :key="c.id"
          class="coupon-opt"
          hover-class="hover-press"
          @tap="chooseCoupon(c)"
        >
          <view class="coupon-opt__main">
            <text class="coupon-opt__name">{{ c.name }}</text>
            <text class="coupon-opt__meta">{{ couponCondText(c) }} · 有效期至 {{ couponTimeText(c.expireTime) }}</text>
          </view>
          <view class="coupon-opt__right">
            <text class="coupon-opt__discount">-¥{{ fmtAmount(c.currentDiscount) }}</text>
            <view class="radio" :class="{ 'radio--on': chosenCouponId === c.id }"></view>
          </view>
        </view>
        <text v-if="!usableCoupons.length" class="coupon-panel__empty">本单暂无可用优惠券</text>
      </view>
      <view v-if="fulfillmentMode === 'delivery'" class="row">
        <text class="row__label">配送费</text>
        <text class="row__value">¥{{ fmtAmount(pricing.deliveryFee) }}</text>
      </view>
      <view class="row row--pay">
        <text class="row__label">实付款</text>
        <text class="pay-value">¥{{ fmtAmount(pricing.payAmount) }}</text>
      </view>
    </view>

    <view v-if="previewError" class="tip tip--error">
      <text>价格核算失败：{{ previewError }}</text>
      <text class="tip__btn" hover-class="hover-press" @tap="refreshPreview">重试</text>
    </view>

    <view class="bar-placeholder"></view>

    <!-- 底部提交栏 -->
    <view class="bar">
      <view class="bar__sum">
        <text class="bar__sum-label">合计</text>
        <text class="bar__sum-value">¥{{ pricing ? fmtAmount(pricing.payAmount) : '0.00' }}</text>
      </view>
      <text
        class="bar__submit"
        :class="{ 'bar__submit--off': !canSubmit }"
        hover-class="hover-press"
        @tap="submit"
      >{{ submitting ? '提交中…' : '提交订单' }}</text>
    </view>

    <!-- 不能下单的原因（起送价/配送范围等）浮在提交栏上方 -->
    <view v-if="blockReason" class="bar-block">{{ blockReason }}</view>
  </view>
</template>

<script>
import {
  http, fullImage, busyLock, fmtAmount, STORAGE_KEYS,
  payOrderByWechat, PAY_CANCEL, PAY_PENDING
} from '@xinfei/mini-shared'

function pad2(n) {
  return n < 10 ? '0' + n : '' + n
}

export default {
  data() {
    return Object.assign(busyLock(), {
      // delivery=配送上门（要地址+配送费+范围校验）；pickup=到店自提（地址豁免、配送费 0）
      fulfillmentMode: 'delivery',
      restaurant: null,
      address: null,
      cart: [],
      pricing: null,
      remark: '',
      payMethod: 6,
      // 会员储值余额（O-06）：null=未开通/未登录，>0 展示储值支付项；静默加载失败不影响其他支付方式
      memberBalance: null,
      // 期望送达时间（O-07）：今天+slot0=尽快送达；否则预约（orders.expect_delivery_time 透传落库）
      timePanelOpen: false,
      timeDayIdx: 0,
      timeSlotIdx: 0,
      soonEta: '',
      timeDays: [
        { label: '今天', offset: 0 },
        { label: '明天', offset: 1 },
        { label: '后天', offset: 2 }
      ],
      // 预约时段与 H5 add-order 的 scheduleTimeSlots 同口径同顺序
      scheduleSlots: [
        { value: 'MORNING', label: '早餐', time: '07:30-09:00' },
        { value: 'LUNCH', label: '午餐', time: '11:30-12:30' },
        { value: 'AFTERNOON', label: '下午茶', time: '14:00-16:00' },
        { value: 'DINNER', label: '晚餐', time: '17:30-19:00' }
      ],
      previewLoading: false,
      previewError: '',
      // 结算页选券：usableCoupons 为后端按商品额算出的当前可用券，chosenCouponId 是 coupon_user.id
      usableCoupons: [],
      chosenCouponId: null,
      couponPickerOpen: false,
      // 幂等令牌页面生命周期内固定：网络超时重试不会下出第二单，成功后随页面销毁
      idempotencyKey: 'mp_' + Date.now() + '_' + Math.random().toString(36).slice(2, 10)
    })
  },
  computed: {
    isPickup() {
      return this.fulfillmentMode === 'pickup'
    },
    // 时段选项：「尽快送达」仅今天可选（明天/后天无"尽快"语义）
    timeSlotsForDay() {
      const soon = [{ value: 'SOON', label: '尽快送达', time: '预计 ' + this.soonEta }]
      return this.timeDayIdx === 0 ? soon.concat(this.scheduleSlots) : this.scheduleSlots
    },
    // 是否预约配送（非今天 或 非尽快档）
    isScheduled() {
      return !this.isPickup && !(this.timeDayIdx === 0 && this.timeSlotIdx === 0)
    },
    deliveryTimeText() {
      if (this.isPickup) return ''
      if (!this.isScheduled) return '尽快送达 · 预计 ' + this.soonEta
      const s = this.timeSlotsForDay[this.timeSlotIdx] || {}
      return this.timeDays[this.timeDayIdx].label + ' ' + s.label + '（' + s.time + '）'
    },
    // 提交口径与 H5 对齐：立即=「HH:MM 尽快送达」；预约=「yyyy-MM-dd HH:mm-HH:mm」
    expectDeliveryTimeValue() {
      if (this.isPickup) return null
      if (!this.isScheduled) return this.soonEta + ' 尽快送达'
      const s = this.timeSlotsForDay[this.timeSlotIdx] || {}
      const d = new Date(Date.now() + this.timeDays[this.timeDayIdx].offset * 86400000)
      return d.getFullYear() + '-' + pad2(d.getMonth() + 1) + '-' + pad2(d.getDate()) + ' ' + s.time
    },
    details() {
      return this.pricing ? this.pricing.details || [] : []
    },
    gifts() {
      return this.pricing ? this.pricing.gifts || [] : []
    },
    cartEmpty() {
      return this.cart.length === 0
    },
    flashSavings() {
      return Number(this.pricing && this.pricing.flashSavings || 0)
    },
    // 券抵扣额取 preview.coupon.amount（与提交同源核价），不读 usableCoupons 的试算值
    couponDiscount() {
      return this.activityAmount(this.pricing && this.pricing.coupon)
    },
    chosenCoupon() {
      const id = this.chosenCouponId
      return this.usableCoupons.find(c => c.id === id) || null
    },
    // 起送价/配送范围/无地址/空车任一不满足即禁止提交
    blockReason() {
      if (!this.isPickup && !this.address) return '请先选择收货地址'
      if (this.cartEmpty) return '购物车为空，请先选购商品'
      if (this.previewLoading || this.previewError) return ''
      const p = this.pricing
      if (!p) return ''
      if (p.belowMinOrder) {
        const gap = p.minOrderAmount != null ? '（起送价 ¥' + fmtAmount(p.minOrderAmount) + '）' : ''
        return '订单金额未达到起送价' + gap
      }
      if (p.rangeChecked && !p.inRange) {
        return p.unavailableReason || '收货地址不在配送范围内'
      }
      return ''
    },
    canSubmit() {
      return !this.blockReason && !this.previewLoading && !this.submitting && !!this.pricing
    },
    submitting() {
      return this.busy('submit')
    }
  },
  onLoad() {
    this.bootstrap()
  },
  onShow() {
    // 从地址选择页 navigateBack 回来时 onLoad 不再触发，靠 onShow 消费一次性交接（自提模式无需地址）
    if (this.isPickup) return
    const handedId = uni.getStorageSync(STORAGE_KEYS.customerSelectedAddressId)
    if (handedId) {
      uni.removeStorageSync(STORAGE_KEYS.customerSelectedAddressId)
      this.loadAddressById(handedId)
    }
  },
  methods: {
    fullImage,
    fmtAmount,
    composeAddress(item) {
      // 与地址列表页同口径：省市区去重 + detail（剥离 detail 自带的区划前缀）
      const regions = [item.provinceName, item.cityName, item.districtName]
        .filter((v, i, arr) => v && arr.indexOf(v) === i)
      let detail = item.detail || ''
      regions.forEach(name => {
        if (detail.indexOf(name) === 0) detail = detail.slice(name.length)
      })
      return regions.join('') + detail
    },
    bootstrap() {
      this.loadRestaurant()
      this.loadMemberBalance()
      // 尽快送达 ETA（O-07）：now+40 分钟，与页面停留时长无关，进入页面时算一次
      const eta = new Date(Date.now() + 40 * 60000)
      this.soonEta = pad2(eta.getHours()) + ':' + pad2(eta.getMinutes())
      // 无交接地址时取默认地址（自提模式切回配送也有值）；无论是否有地址都拉购物车（空车给引导）
      const addressReady = this.address
        ? Promise.resolve()
        : http.get('/address-book/default', null, { silent: true })
          .then(a => { this.address = a })
          .catch(() => { this.address = null })
      addressReady.then(() => this.loadCart())
    },
    // 自提门店卡信息：失败静默降级为标题"门店自提"，不阻塞下单
    loadRestaurant() {
      http.get('/restaurant/info', null, { silent: true })
        .then(info => { this.restaurant = info || null })
        .catch(() => { this.restaurant = null })
    },
    switchMode(mode) {
      if (this.fulfillmentMode === mode) return
      this.fulfillmentMode = mode
      this.previewError = ''
      this.refreshPreview()
    },
    loadAddressById(id) {
      http.get('/address-book/' + encodeURIComponent(id), null, { silent: true })
        .then(a => {
          this.address = a
          this.refreshPreview()
        })
        .catch(() => {})
    },
    loadCart() {
      return http.get('/shopping-cart/list', null, { silent: true }).then(list => {
        this.cart = list || []
        if (!this.cartEmpty) this.refreshPreview()
        else this.pricing = null
      }).catch(() => {})
    },
    refreshPreview() {
      if (this.cartEmpty) return Promise.resolve()
      // 配送必须有地址；自提豁免地址校验
      if (!this.isPickup && !this.address) return Promise.resolve()
      this.previewLoading = true
      this.previewError = ''
      // 只读核价，与 /order/submit 同源；自提 addressBookId=null、source=SELF_PICKUP，后端豁免起送价/范围、配送费 0
      return http.post('/api/order/preview', {
        addressBookId: this.isPickup ? null : this.address.id,
        source: this.isPickup ? 'SELF_PICKUP' : 'TAKEOUT',
        usedCouponId: this.chosenCouponId
      }, { silent: true }).then(p => {
        this.pricing = p
        this.loadUsableCoupons()
      }).catch(err => {
        if (err && err.notLogin) return
        this.pricing = null
        this.previewError = (err && err.message) || '请稍后重试'
      }).then(() => {
        this.previewLoading = false
      })
    },
    // 可用券以后端按商品额（不含运费/其他优惠）实时计算为准；非会员/失败一律按无券处理，不打扰下单
    loadUsableCoupons() {
      const base = this.pricing ? Number(this.pricing.goodsAmount || 0) : 0
      return http.get('/front/coupon/usable', { orderAmount: base }, { silent: true })
        .then(list => {
          this.usableCoupons = Array.isArray(list) ? list : []
          // 切地址/模式/金额后已选券可能失效：清掉并重新核价；null 不会再次触发，无循环
          if (this.chosenCouponId != null && !this.usableCoupons.some(c => c.id === this.chosenCouponId)) {
            this.chosenCouponId = null
            this.refreshPreview()
          }
        })
        .catch(() => { this.usableCoupons = [] })
    },
    toggleCouponPicker() {
      this.couponPickerOpen = !this.couponPickerOpen
    },
    chooseCoupon(coupon) {
      const id = coupon ? coupon.id : null
      this.couponPickerOpen = false
      if (id === this.chosenCouponId) return
      this.chosenCouponId = id
      this.refreshPreview()
    },
    couponCondText(c) {
      const n = Number(c.conditionAmount || 0)
      return n > 0 ? '满 ¥' + fmtAmount(c.conditionAmount) + ' 可用' : '无门槛'
    },
    // 与 detail.vue 同口径兼容 LocalDateTime 的数组/字符串两种序列化
    couponTimeText(t) {
      if (t == null || t === '') return '长期有效'
      if (Array.isArray(t)) return t[0] + '-' + pad2(t[1]) + '-' + pad2(t[2])
      return String(t).replace('T', ' ').slice(0, 10)
    },
    activityAmount(hit) {
      return Number(hit && hit.amount || 0)
    },
    chooseAddress() {
      uni.navigateTo({ url: '/pages/address/list?select=1' })
    },
    goMenu() {
      uni.switchTab({ url: '/pages/index/index' })
    },
    submit() {
      if (!this.canSubmit) return
      const key = 'submit'
      if (this.busy(key)) return
      this.setBusy(key, true)
      // /order/submit 是 @RequestBody JSON；货到/到店付款下单即 status=2 待接单，服务端同事务清空购物车
      // 自提 addressBookId=null、source=SELF_PICKUP；与 H5 同口径不传 phone（后端回退 user.phone）
      http.post('/order/submit', {
        addressBookId: this.isPickup ? null : this.address.id,
        source: this.isPickup ? 'SELF_PICKUP' : 'TAKEOUT',
        remark: this.remark.trim(),
        // 期望送达（O-07）：配送模式必传（立即/预约两种口径），自提无配送环节传 null
        expectDeliveryTime: this.expectDeliveryTimeValue,
        payMethod: this.payMethod,
        usedCouponId: this.chosenCouponId,
        idempotencyKey: this.idempotencyKey
      }).then(data => {
        // duplicate=true 是同一幂等键的重复提交，返回的就是已创建订单，同样按成功处理
        if (this.payMethod === 2) return this.handleOnlinePay(data)
        if (this.payMethod === 5) return this.handleBalancePay(data)
        uni.showToast({ title: '下单成功', icon: 'success' })
        setTimeout(() => this.gotoOrderPlaced(data), 600)
      }).catch(() => {}).then(() => { this.setBusy(key, false) })
    },
    // 会员储值支付（O-06）：下单后走 BALANCE 同步渠道（服务端原子扣余额 + 订单 1→2），
    // 失败（未开通/余额不足）订单保持待付款，引导到订单详情可重试或换支付方式
    handleBalancePay(data) {
      if (!data || !data.id) return Promise.resolve()
      uni.showLoading({ title: '正在扣款', mask: true })
      return http.post('/api/payment/pay', { orderId: data.id, channel: 'BALANCE' }, { silent: true })
        .then(res => {
          uni.hideLoading()
          if (!res || res.success === false) {
            throw new Error('储值支付失败，请稍后重试')
          }
          this.memberBalance = Math.max(0, Number(this.memberBalance || 0) - Number(data.amount || 0))
          uni.showToast({ title: '支付成功', icon: 'success' })
          setTimeout(() => this.gotoOrderPlaced(data), 600)
        }).catch(err => {
          uni.hideLoading()
          const msg = (err && err.message) || '储值支付未完成'
          uni.showToast({ title: msg, icon: 'none', duration: 2500 })
          setTimeout(() => this.gotoOrderDetail(data.id), 1200)
        })
    },
    // 会员余额（O-06）：未开通会员/未登录都静默置 null，不展示储值支付项
    loadMemberBalance() {
      http.get('/api/member/member/my-info', null, { silent: true }).then(info => {
        this.memberBalance = info && info.balance != null ? Number(info.balance) : null
      }).catch(() => { this.memberBalance = null })
    },
    // 期望送达（O-07）：切天时若当前档越界（明天/后天无「尽快送达」）回落到第一档
    pickDay(idx) {
      this.timeDayIdx = idx
      if (this.timeSlotIdx >= this.timeSlotsForDay.length) this.timeSlotIdx = 0
    },
    pickSlot(idx) {
      this.timeSlotIdx = idx
      this.timePanelOpen = false
    },
    // 微信 JSAPI：下单成功后立即调起支付，终态以后端支付单回查为准
    handleOnlinePay(data) {
      if (!data || !data.id) return Promise.resolve()
      return payOrderByWechat(data.id, data.amount).then(() => {
        uni.showToast({ title: '支付成功', icon: 'success' })
        setTimeout(() => this.gotoOrderPlaced(data), 600)
      }).catch(err => {
        // 订单已创建：取消/失败/确认超时都落到详情页，不能留在结算页（否则返回会重复下单）
        if (err && err.code === PAY_CANCEL) {
          uni.showToast({ title: '支付已取消，可在订单详情继续支付', icon: 'none' })
          this.gotoOrderDetail(data.id)
          return
        }
        const pending = err && err.code === PAY_PENDING
        uni.showModal({
          title: pending ? '支付结果确认中' : '支付未完成',
          content: pending
            ? '如已扣款，请稍后在订单详情查看支付结果'
            : ((err && err.message) || '可在订单详情重新发起支付'),
          showCancel: false,
          confirmText: '查看订单',
          success: () => this.gotoOrderDetail(data.id)
        })
      })
    },
    gotoOrderPlaced(data) {
      if (this.isPickup && data && data.id) {
        // 自提下单即生成取餐码，直接进详情页出示；redirectTo 关闭结算页防止返回重提
        this.gotoOrderDetail(data.id)
      } else {
        // 一次性交接：订单列表落在「待接单」tab
        uni.setStorageSync('customerOrderStatus', 2)
        uni.reLaunch({ url: '/pages/order/list' })
      }
    },
    gotoOrderDetail(id) {
      uni.redirectTo({ url: '/pages/order/detail?id=' + id })
    }
  }
}
</script>

<style>
.checkout {
  min-height: 100%;
  padding: 8rpx 0 40rpx;
  background: var(--bg-page);
}

.card {
  margin: 16rpx 24rpx;
  padding: 24rpx;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
  box-shadow: var(--shadow-card);
}

.mode-tabs {
  display: flex;
  margin: 16rpx 24rpx;
  padding: 6rpx;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
  box-shadow: var(--shadow-card);
}

.mode-tab {
  flex: 1;
  height: 72rpx;
  line-height: 72rpx;
  text-align: center;
  font-size: 28rpx;
  color: var(--color-text-secondary);
  border-radius: var(--radius-md);
}

.mode-tab--on {
  font-weight: 600;
  color: var(--color-primary-text);
  background: var(--color-primary);
}

.pickup__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pickup__name {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

.pickup__hours {
  font-size: 24rpx;
  color: var(--color-text-hint);
}

.pickup__addr {
  display: block;
  margin-top: 10rpx;
  font-size: 26rpx;
  line-height: 38rpx;
  color: var(--color-text-secondary);
}

.pickup__notice {
  display: block;
  margin-top: 12rpx;
  font-size: 26rpx;
  line-height: 38rpx;
  color: var(--color-text-secondary);
}

.pickup__tip {
  display: block;
  margin-top: 12rpx;
  font-size: 24rpx;
  color: var(--color-primary-text-strong);
}

/* 期望送达时间（O-07） */
.dtime__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.dtime__label {
  font-size: 28rpx;
  color: var(--color-text-primary);
}

.dtime__value {
  display: flex;
  align-items: center;
  gap: 8rpx;
  font-size: 26rpx;
  color: var(--color-text-secondary);
}

.dtime__arrow {
  font-size: 22rpx;
  color: var(--color-text-hint);
}

.dtime__panel {
  margin-top: 16rpx;
  border-top: 1rpx solid var(--border-hairline);
  padding-top: 16rpx;
}

.dtime__days {
  display: flex;
  gap: 16rpx;
  margin-bottom: 12rpx;
}

.dtime__day {
  padding: 8rpx 24rpx;
  font-size: 24rpx;
  color: var(--color-text-secondary);
  background: var(--bg-input);
  border-radius: var(--radius-md);
}

.dtime__day--on {
  color: var(--color-primary-text);
  background: var(--color-primary-tint-strong);
  font-weight: 600;
}

.dtime__opt {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 4rpx;
  border-bottom: 1rpx solid var(--border-hairline);
}

.dtime__opt:last-child {
  border-bottom: none;
}

.dtime__main {
  display: flex;
  align-items: baseline;
  gap: 16rpx;
}

.dtime__name {
  font-size: 26rpx;
  color: var(--color-text-primary);
}

.dtime__meta {
  font-size: 24rpx;
  color: var(--color-text-hint);
}

.address {
  display: flex;
  align-items: center;
}

.address__main {
  flex: 1;
}

.address__head {
  display: flex;
  align-items: center;
}

.address__name {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

.address__phone {
  margin-left: 20rpx;
  font-size: 26rpx;
  color: var(--color-text-secondary);
}

.address__tag {
  margin-left: 16rpx;
  padding: 2rpx 12rpx;
  border-radius: var(--radius-sm);
  font-size: 20rpx;
  color: var(--color-primary-text);
  background: var(--color-primary-tint);
}

.address__detail {
  display: block;
  margin-top: 12rpx;
  font-size: 26rpx;
  line-height: 38rpx;
  color: var(--color-text-secondary);
}

.address__arrow {
  margin-left: 16rpx;
  font-size: 40rpx;
  color: var(--color-text-hint);
}

.address__empty {
  flex: 1;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.address__empty-text {
  font-size: 28rpx;
  color: var(--color-text-secondary);
}

.section-title {
  display: block;
  margin-bottom: 12rpx;
  font-size: 30rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

.cart-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40rpx 0;
}

.cart-empty__btn {
  margin-top: 20rpx;
  padding: 12rpx 48rpx;
  border-radius: 32rpx;
  font-size: 26rpx;
  color: var(--color-primary-text);
  background: var(--color-primary);
}

.goods {
  display: flex;
  align-items: center;
  margin-top: 16rpx;
}

.goods__img {
  width: 88rpx;
  height: 88rpx;
  flex-shrink: 0;
  border-radius: var(--radius-sm);
  background: var(--bg-input);
}

.goods__img--ph {
  background: var(--bg-input);
}

.goods__info {
  flex: 1;
  margin-left: 16rpx;
  display: flex;
  flex-direction: column;
}

.goods__name {
  font-size: 28rpx;
  color: var(--color-text-primary);
}

.goods__tag {
  margin-top: 6rpx;
  font-size: 22rpx;
  color: var(--color-price);
}

.goods__qty {
  margin: 0 20rpx;
  font-size: 26rpx;
  color: var(--color-text-hint);
}

.goods__price {
  width: 120rpx;
  text-align: right;
  font-size: 28rpx;
  color: var(--color-text-primary);
}

.gift {
  display: flex;
  align-items: center;
  margin-top: 16rpx;
}

.gift__tag {
  padding: 2rpx 12rpx;
  border-radius: var(--radius-sm);
  font-size: 20rpx;
  color: var(--color-success-text);
  background: rgba(7, 193, 96, 0.1);
}

.gift__name {
  flex: 1;
  margin-left: 16rpx;
  font-size: 26rpx;
  color: var(--color-text-secondary);
}

.gift__qty {
  font-size: 24rpx;
  color: var(--color-text-hint);
}

.remark-card__input {
  width: 100%;
  height: 140rpx;
  padding: 16rpx;
  box-sizing: border-box;
  border-radius: var(--radius-md);
  background: var(--bg-input);
  font-size: 28rpx;
}

.remark-card__ph {
  color: var(--color-text-disabled);
}

.remark-card__count {
  display: block;
  margin-top: 8rpx;
  text-align: right;
  font-size: 22rpx;
  color: var(--color-text-hint);
}

.pay__item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20rpx 0;
}

.pay__item + .pay__item {
  border-top: 1rpx solid var(--border-light);
}

.pay__item--off {
  opacity: 0.5;
}

.pay__label {
  font-size: 28rpx;
  color: var(--color-text-primary);
}

.pay__soon {
  font-size: 24rpx;
  color: var(--color-text-hint);
}

.radio {
  width: 36rpx;
  height: 36rpx;
  border-radius: var(--radius-round);
  border: 2rpx solid var(--border-medium);
  box-sizing: border-box;
  position: relative;
}

.radio--on {
  border-color: var(--color-primary);
}

.radio--on::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  width: 18rpx;
  height: 18rpx;
  border-radius: var(--radius-round);
  background: var(--color-primary);
  transform: translate(-50%, -50%);
}

.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10rpx 0;
}

.row__label {
  font-size: 26rpx;
  color: var(--color-text-hint);
}

.row__value {
  font-size: 28rpx;
  color: var(--color-text-primary);
}

.row__value--off {
  color: var(--color-price);
}

.row--pay {
  margin-top: 8rpx;
  padding-top: 20rpx;
  border-top: 1rpx solid var(--border-light);
}

.pay-value {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--color-price);
}

/* ---- 优惠券入口与选券面板 ---- */
.coupon-entry {
  display: flex;
  align-items: center;
}

.coupon-entry__name {
  margin-right: 12rpx;
  font-size: 26rpx;
  color: var(--color-text-primary);
}

.coupon-entry__pick {
  font-size: 26rpx;
  color: var(--color-primary-text-strong);
}

.coupon-entry__none {
  font-size: 26rpx;
  color: var(--color-text-hint);
}

.coupon-panel {
  margin-top: 8rpx;
  padding: 8rpx 0;
  border-top: 1rpx solid var(--border-light);
}

.coupon-opt {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16rpx 0;
}

.coupon-opt__label {
  font-size: 28rpx;
  color: var(--color-text-primary);
}

.coupon-opt__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.coupon-opt__name {
  font-size: 28rpx;
  color: var(--color-text-primary);
}

.coupon-opt__meta {
  margin-top: 6rpx;
  font-size: 22rpx;
  color: var(--color-text-hint);
}

.coupon-opt__right {
  display: flex;
  align-items: center;
  margin-left: 24rpx;
}

.coupon-opt__discount {
  margin-right: 16rpx;
  font-size: 26rpx;
  color: var(--color-price);
}

.coupon-panel__empty {
  display: block;
  padding: 24rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: var(--color-text-hint);
}

.tip {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin: 16rpx 24rpx;
  padding: 32rpx;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
  font-size: 26rpx;
  color: var(--color-text-hint);
}

.tip__btn {
  margin-top: 16rpx;
  padding: 10rpx 40rpx;
  border-radius: 32rpx;
  font-size: 26rpx;
  color: var(--color-primary-text);
  background: var(--color-primary);
}

.bar-placeholder {
  height: 140rpx;
}

.bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 10;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16rpx 24rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background: var(--bg-card);
  box-shadow: var(--shadow-bar-top);
}

.bar__sum {
  display: flex;
  align-items: baseline;
}

.bar__sum-label {
  font-size: 26rpx;
  color: var(--color-text-secondary);
}

.bar__sum-value {
  margin-left: 12rpx;
  font-size: 38rpx;
  font-weight: 600;
  color: var(--color-price);
}

.bar__submit {
  padding: 20rpx 56rpx;
  border-radius: 44rpx;
  font-size: 30rpx;
  color: var(--color-primary-text);
  background: var(--color-primary);
}

.bar__submit--off {
  opacity: 0.5;
}

.bar-block {
  position: fixed;
  left: 24rpx;
  right: 24rpx;
  bottom: calc(120rpx + env(safe-area-inset-bottom));
  z-index: 9;
  padding: 12rpx 24rpx;
  border-radius: var(--radius-md);
  font-size: 24rpx;
  color: var(--color-danger-text);
  background: rgba(233, 78, 60, 0.08);
  text-align: center;
}
</style>
