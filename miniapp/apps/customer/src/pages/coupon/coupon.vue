<template>
  <view class="cp">
    <view class="tabs">
      <view class="tab" :class="{ 'tab--on': tab === 'available' }" @tap="switchTab('available')">可领取</view>
      <view class="tab" :class="{ 'tab--on': tab === 'mine' }" @tap="switchTab('mine')">我的券</view>
    </view>

    <scroll-view class="list" scroll-y>
      <!-- ============ 可领取 ============ -->
      <block v-if="tab === 'available'">
        <!-- 超值券包（O-18）：打包售卖，微信支付购买成功自动发放到「我的券」 -->
        <view v-for="p in packages" :key="'pkg' + p.id" class="coupon coupon--pkg" hover-class="hover-press">
          <view class="coupon__face coupon__face--pkg">
            <text class="coupon__amount">¥{{ fmtAmount(p.price) }}</text>
            <text class="coupon__cond">券包价</text>
          </view>
          <view class="coupon__body">
            <view class="coupon__main">
              <view class="coupon__titlerow">
                <text class="coupon__name">{{ p.name }}</text>
                <text class="coupon__new" v-if="p.originalValue">价值 ¥{{ fmtAmount(p.originalValue) }}</text>
              </view>
              <text class="coupon__meta">{{ pkgItemsText(p) }}</text>
            </view>
            <view class="coupon__foot">
              <text class="coupon__type">券包</text>
              <text
                class="cbtn cbtn--main"
                :class="{ 'cbtn--off': busy('pkg:' + p.id) }"
                hover-class="hover-press"
                @tap="buyPackage(p)"
              >{{ busy('pkg:' + p.id) ? '支付中…' : '购买' }}</text>
            </view>
          </view>
        </view>

        <view v-for="t in available" :key="t.id" class="coupon">
          <view class="coupon__face">
            <text class="coupon__amount">{{ faceText(t) }}</text>
            <text class="coupon__cond">{{ condText(t) }}</text>
          </view>
          <view class="coupon__body">
            <view class="coupon__main">
              <view class="coupon__titlerow">
                <text class="coupon__name">{{ t.name }}</text>
                <text v-if="t.type === 'NEW_MEMBER'" class="coupon__new">新客专享</text>
              </view>
              <text class="coupon__meta">领取后 {{ t.validDays }} 天有效 · 剩余 {{ t.remainCount }} 张</text>
            </view>
            <view class="coupon__foot">
              <text class="coupon__type">{{ couponTypeText(t.type) }}</text>
              <!-- 同模板每会员限领 1 张（后端 uk_member_template 兜底），mine 命中即置灰 -->
              <text v-if="claimedIds[t.id]" class="cbtn cbtn--off">已领取</text>
              <text
                v-else-if="Number(t.pointsPrice) > 0"
                class="cbtn cbtn--line"
                :class="{ 'cbtn--off': points < Number(t.pointsPrice) || busy('exchange:' + t.id) }"
                hover-class="hover-press"
                @tap="exchange(t)"
              >{{ busy('exchange:' + t.id) ? '兑换中…' : t.pointsPrice + ' 积分' }}</text>
              <text
                v-else
                class="cbtn cbtn--main"
                :class="{ 'cbtn--off': busy('claim:' + t.id) }"
                hover-class="hover-press"
                @tap="claim(t)"
              >{{ busy('claim:' + t.id) ? '领取中…' : '免费领取' }}</text>
            </view>
          </view>
        </view>

        <x-empty v-if="!availLoading && !availError && !available.length" text="暂无可领取的优惠券" />
        <view v-if="availError" class="list__error">
          <text>加载失败</text>
          <text class="list__retry" @tap="loadAvailable">重试</text>
        </view>
        <view v-if="availLoading" class="list__more"><text>加载中…</text></view>
      </block>

      <!-- ============ 我的券 ============ -->
      <block v-else>
        <view v-if="notMember" class="guide">
          <x-empty text="开通会员后领取和管理优惠券" sub="会员开通免费" />
          <button class="guide__btn" @tap="toMember">立即开通</button>
        </view>

        <block v-else>
          <view class="subtabs">
            <view
              v-for="f in filters"
              :key="f.key"
              class="subtab"
              :class="{ 'subtab--on': mineFilter === f.key }"
              @tap="mineFilter = f.key"
            >{{ f.label }}（{{ counts[f.key] || 0 }}）</view>
          </view>

          <view
            v-for="c in mineFiltered"
            :key="c.id"
            class="coupon"
            :class="{ 'coupon--off': c.status !== 'unused' }"
          >
            <view class="coupon__face">
              <text class="coupon__amount">{{ faceText(c) }}</text>
              <text class="coupon__cond">{{ condText(c) }}</text>
            </view>
            <view class="coupon__body">
              <view class="coupon__main">
                <view class="coupon__titlerow">
                  <text class="coupon__name">{{ c.name || '优惠券' }}</text>
                  <text v-if="c.expiringSoon" class="coupon__soon">即将过期</text>
                  <text v-if="c.status !== 'unused'" class="coupon__status">{{ couponStatusText(c.status) }}</text>
                </view>
                <text class="coupon__meta">{{ mineTimeText(c) }}</text>
              </view>
              <view class="coupon__foot">
                <text class="coupon__type">{{ couponTypeText(c.type) }}</text>
                <text v-if="c.status === 'unused'" class="cbtn cbtn--main" hover-class="hover-press" @tap="useCoupon">去使用</text>
              </view>
            </view>
          </view>

          <x-empty v-if="!mineLoading && !mineError && !mineFiltered.length" text="暂无优惠券" />
          <view v-if="mineError" class="list__error">
            <text>加载失败</text>
            <text class="list__retry" @tap="loadMine">重试</text>
          </view>
          <view v-if="mineLoading" class="list__more"><text>加载中…</text></view>
        </block>
      </block>
    </scroll-view>
  </view>
</template>

<script>
import { http, busyLock, fmtAmount, couponTypeText, couponStatusText, payCouponPackageByWechat, PAY_CANCEL } from '@xinfei/mini-shared'

export default {
  data() {
    return Object.assign(busyLock(), {
      tab: 'available',      // available | mine（onLoad 支持 ?tab=mine）
      notMember: false,
      points: 0,             // 当前积分，兑换前本地预检
      // 可领取（接口无分页，全量）
      available: [],
      availLoading: false,
      availError: false,
      // 超值券包（O-18）：上架券包，购买后自动发放
      packages: [],
      // 我的券（接口无分页，前端分三段）
      mine: [],
      mineLoading: false,
      mineError: false,
      mineFilter: 'unused',
      filters: [
        { key: 'unused', label: '未使用' },
        { key: 'used', label: '已使用' },
        { key: 'expired', label: '已过期' }
      ]
    })
  },
  computed: {
    // 已领模板去重表（每模板限 1 张，按 templateId 判即可）
    claimedIds() {
      const map = {}
      this.mine.forEach(c => {
        if (c.templateId != null) map[c.templateId] = true
      })
      return map
    },
    mineFiltered() {
      const list = this.mine.filter(c => c.status === this.mineFilter)
      // P1-3：unused 态下临期券（3 天内到期）置顶，并打 expiringSoon 标供角标渲染
      if (this.mineFilter === 'unused') {
        list.forEach(c => { c.expiringSoon = this.isExpiringSoon(c) })
        list.sort((a, b) => (b.expiringSoon ? 1 : 0) - (a.expiringSoon ? 1 : 0))
      }
      return list
    },
    counts() {
      const r = { unused: 0, used: 0, expired: 0 }
      this.mine.forEach(c => {
        if (r[c.status] !== undefined) r[c.status] += 1
      })
      return r
    }
  },
  onLoad(query) {
    if (query && query.tab === 'mine') this.tab = 'mine'
  },
  // 领券/开通会员返回本页都需要刷新，统一在 onShow 全量拉取
  onShow() {
    this.refresh()
  },
  methods: {
    fmtAmount,
    couponTypeText,
    couponStatusText,
    refresh() {
      this.loadAvailable()
      this.loadMemberThenMine()
      this.loadPackages()
    },
    // 超值券包（O-18）：匿名可看，购买需登录（未登录 request 层自动回登录页）
    loadPackages() {
      http.get('/api/coupon-package/list', null, { silent: true }).then(list => {
        this.packages = Array.isArray(list) ? list : []
      }).catch(() => { this.packages = [] })
    },
    pkgItemsText(p) {
      const items = (p.items || []).map(i => (i.templateSummary || '') + (i.templateName || ''))
        .filter(s => s)
      return items.length ? items.join(' · ') : (p.description || '多张优惠券打包')
    },
    buyPackage(p) {
      const key = 'pkg:' + p.id
      if (this.busy(key)) return
      this.setBusy(key, true)
      http.post('/api/coupon-package/buy?packageId=' + p.id, null, { silent: true })
        .then(order => payCouponPackageByWechat(order.orderNo, p.price))
        .then(() => {
          uni.showToast({ title: '购买成功，券已到账', icon: 'success' })
          this.refresh()
        })
        .catch(err => {
          if (err && err.code === PAY_CANCEL) {
            uni.showToast({ title: '支付已取消', icon: 'none' })
            return
          }
          uni.showToast({ title: (err && err.message) || '购买未完成', icon: 'none', duration: 2500 })
        })
        .then(() => { this.setBusy(key, false) })
    },
    loadAvailable() {
      this.availLoading = true
      this.availError = false
      http.get('/front/coupon/available', null, { silent: true }).then(list => {
        this.available = Array.isArray(list) ? list : []
      }).catch(() => {
        this.availError = true
      }).then(() => {
        this.availLoading = false
      })
    },
    loadMemberThenMine() {
      // 会员身份以 my-info 为权威；接口失败时保守按会员处理，让 /my 的业务文案再兜底一次
      http.get('/api/member/member/my-info', null, { silent: true }).then(info => {
        this.notMember = !(info && info.isMember)
        this.points = info && info.member ? Number(info.member.points || 0) : 0
      }).catch(() => {
        this.notMember = false
      }).then(() => {
        if (this.notMember) {
          this.mine = []
          return
        }
        this.loadMine()
      })
    },
    loadMine() {
      this.mineLoading = true
      this.mineError = false
      http.get('/front/coupon/my', null, { silent: true }).then(list => {
        this.mine = Array.isArray(list) ? list : []
      }).catch(err => {
        // 非会员：HTTP 200 + code:0 + "尚未开通会员，请先注册会员"
        if (err && err.message && err.message.indexOf('开通会员') !== -1) {
          this.notMember = true
          this.mine = []
        } else {
          this.mineError = true
        }
      }).then(() => {
        this.mineLoading = false
      })
    },
    switchTab(t) {
      this.tab = t
    },
    claim(t) {
      const key = 'claim:' + t.id
      if (this.busy(key) || this.claimedIds[t.id]) return
      if (this.notMember) return this.guideOpen()
      this.setBusy(key, true)
      // silent：业务失败（如非会员文案）要在本页弹开通引导，不能只靠统一 toast
      http.post('/front/coupon/claim/' + t.id, null, { silent: true }).then(() => {
        uni.showToast({ title: '领取成功', icon: 'success' })
        this.afterAcquired()
      }).catch(err => {
        if (err && err.message && err.message.indexOf('开通会员') !== -1) {
          this.guideOpen()
        } else {
          uni.showToast({ title: (err && err.message) || '领取失败', icon: 'none' })
        }
      }).then(() => {
        this.setBusy(key, false)
      })
    },
    exchange(t) {
      const key = 'exchange:' + t.id
      if (this.busy(key) || this.claimedIds[t.id]) return
      if (this.notMember) return this.guideOpen()
      const price = Number(t.pointsPrice || 0)
      if (this.points < price) {
        uni.showToast({ title: '积分不足，还差 ' + (price - this.points) + ' 积分', icon: 'none' })
        return
      }
      uni.showModal({
        title: '积分兑换',
        content: '确认使用 ' + price + ' 积分兑换「' + t.name + '」？',
        confirmText: '兑换',
        success: r => {
          if (r.confirm) this.doExchange(t, key)
        }
      })
    },
    doExchange(t, key) {
      this.setBusy(key, true)
      http.post('/front/coupon/exchange/' + t.id, null, { silent: true }).then(() => {
        uni.showToast({ title: '兑换成功', icon: 'success' })
        this.afterAcquired()
      }).catch(err => {
        // 请求层已透出 422 响应体里的业务 msg（如"积分不足"），直接展示即可
        uni.showToast({ title: (err && err.message) || '兑换失败', icon: 'none' })
      }).then(() => {
        this.setBusy(key, false)
      })
    },
    // 领取/兑换后库存、积分、我的券都可能变化，三份数据一起重拉
    afterAcquired() {
      this.loadAvailable()
      this.loadMemberThenMine()
    },
    guideOpen() {
      uni.showModal({
        title: '提示',
        content: '领券需先开通会员（免费），是否前往开通？',
        confirmText: '去开通',
        success: r => {
          if (r.confirm) this.toMember()
        }
      })
    },
    toMember() {
      uni.navigateTo({ url: '/pages/member/member' })
    },
    useCoupon() {
      // 本期券不接结算页抵扣，"去使用"先引导回点餐首页凑单
      uni.switchTab({ url: '/pages/index/index' })
    },
    // 券面主文案：折扣券显 X 折；满减/新客显 ¥金额；异常数据兜底
    faceText(c) {
      if (c.type === 'DISCOUNT' && c.discountRate != null && Number(c.discountRate) > 0) {
        return Number((Number(c.discountRate) * 10).toFixed(1)) + ' 折'
      }
      if (c.discountAmount != null && Number(c.discountAmount) > 0) {
        return '¥' + fmtAmount(c.discountAmount)
      }
      return '优惠券'
    },
    // P1-3：expireTime 距今 <3 天视为临期（解析失败按不临期，保守不打扰）
    isExpiringSoon(c) {
      if (!c || !c.expireTime || c.status !== 'unused') return false
      try {
        const t = new Date(String(c.expireTime).replace(/-/g, '/')).getTime()
        if (isNaN(t)) return false
        return t - Date.now() < 3 * 24 * 3600 * 1000
      } catch (e) { return false }
    },
    condText(c) {
      const n = Number(c.conditionAmount || 0)
      return n > 0 ? '满 ¥' + fmtAmount(c.conditionAmount) + ' 可用' : '无门槛'
    },
    mineTimeText(c) {
      if (c.status === 'used') return c.usedTime ? '已于 ' + c.usedTime + ' 使用' : '已使用'
      if (c.status === 'expired') return c.expireTime ? '已于 ' + c.expireTime + ' 过期' : '已过期'
      return c.expiringSoon ? c.expireTime + ' 到期，抓紧使用' : (c.expireTime ? '有效期至 ' + c.expireTime : '长期有效')
    }
  }
}
</script>

<style>
.cp {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg-page);
}

/* ---- 顶部 Tab ---- */
.tabs {
  flex-shrink: 0;
  display: flex;
  background: var(--bg-card);
  border-bottom: 1rpx solid var(--border-light);
}

.tab {
  position: relative;
  flex: 1;
  padding: 24rpx 0;
  text-align: center;
  font-size: 28rpx;
  color: var(--color-text-secondary);
}

.tab--on {
  font-weight: 600;
  color: var(--color-text-primary);
}

.tab--on::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 0;
  width: 64rpx;
  height: 6rpx;
  margin-left: -32rpx;
  border-radius: var(--radius-round);
  background: var(--color-primary);
}

.list {
  flex: 1;
}

/* ---- 我的券：状态子筛选 ---- */
.subtabs {
  display: flex;
  margin: 16rpx 24rpx 0;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
  overflow: hidden;
}

.subtab {
  flex: 1;
  padding: 20rpx 0;
  text-align: center;
  font-size: 26rpx;
  color: var(--color-text-secondary);
}

.subtab--on {
  font-weight: 600;
  color: var(--color-primary-text);
  background: var(--color-primary-tint);
}

/* ---- 券卡（左票根 + 右信息） ---- */
.coupon {
  display: flex;
  margin: 16rpx 24rpx 0;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
  box-shadow: var(--shadow-card);
  overflow: hidden;
}

.coupon--off {
  opacity: 0.55;
}

.coupon__face {
  width: 200rpx;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24rpx 12rpx;
  background: var(--color-primary-tint);
  border-right: 2rpx dashed var(--border-medium);
}

/* 券包卡（O-18）：主色实底突出售卖属性 */
.coupon__face--pkg {
  background: var(--color-primary-gradient);
}

.coupon--pkg .coupon__cond {
  color: var(--color-primary-text);
}

.coupon__amount {
  font-size: 42rpx;
  font-weight: 700;
  color: var(--color-price);
}

.coupon__cond {
  margin-top: 8rpx;
  font-size: 20rpx;
  color: var(--color-text-hint);
}

.coupon__body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 20rpx 24rpx;
}

.coupon__titlerow {
  display: flex;
  align-items: center;
}

.coupon__name {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

.coupon__new {
  margin-left: 12rpx;
  padding: 2rpx 10rpx;
  font-size: 20rpx;
  color: var(--color-danger-text);
  border: 1rpx solid var(--color-danger);
  border-radius: var(--radius-sm);
}

.coupon__soon {
  font-size: 20rpx;
  color: var(--color-danger, #e64340);
  background: rgba(230, 67, 64, 0.1);
  border-radius: var(--radius-round);
  padding: 2rpx 12rpx;
}
.coupon__status {
  margin-left: 12rpx;
  font-size: 22rpx;
  color: var(--color-text-hint);
}

.coupon__meta {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: var(--color-text-hint);
}

.coupon__foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 14rpx;
}

.coupon__type {
  font-size: 22rpx;
  color: var(--color-text-hint);
}

/* ---- 操作按钮（text 元素） ---- */
.cbtn {
  padding: 10rpx 28rpx;
  font-size: 24rpx;
  border-radius: var(--radius-round);
}

.cbtn--main {
  font-weight: 600;
  color: var(--color-primary-text);
  background: var(--color-primary);
}

.cbtn--line {
  color: var(--color-primary-text-strong);
  border: 1rpx solid var(--color-primary-text-strong);
}

.cbtn--off {
  color: var(--color-text-disabled);
  background: var(--bg-input);
  border: none;
}

/* ---- 非会员开通引导 ---- */
.guide {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 40rpx;
}

.guide__btn {
  margin-top: 16rpx;
  width: 320rpx;
  height: 80rpx;
  line-height: 80rpx;
  border-radius: var(--radius-round);
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-primary-text);
  background: var(--color-primary);
}

/* ---- 列表状态 ---- */
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
