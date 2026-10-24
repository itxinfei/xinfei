<template>
  <view class="mp">
    <!-- 会员卡：非会员显示开通引导，会员显示等级/资产/升级进度 -->
    <view v-if="!pageError" class="mc">
      <block v-if="isMember">
        <view class="mc__head">
          <view class="mc__level">
            <text class="mc__level-name">{{ levelName }}</text>
            <text v-if="discountText" class="mc__level-tag">{{ discountText }}</text>
          </view>
          <text class="mc__brand">心飞会员</text>
        </view>

        <view class="mc__stats">
          <view class="mc__stat">
            <text class="mc__stat-num">{{ member.points || 0 }}</text>
            <text class="mc__stat-label">积分</text>
          </view>
          <view class="mc__stat">
            <text class="mc__stat-num">¥{{ fmtAmount(member.balance) }}</text>
            <text class="mc__stat-label">储值余额</text>
          </view>
          <view class="mc__stat" hover-class="hover-press" @tap="toCoupon">
            <text class="mc__stat-num">{{ couponCount }}</text>
            <text class="mc__stat-label">优惠券 ›</text>
          </view>
        </view>

        <view class="mc__prog">
          <view class="mc__prog-bar">
            <view class="mc__prog-inner" :style="{ width: progressPct + '%' }"></view>
          </view>
          <text class="mc__prog-text">{{ progressText }}</text>
        </view>
      </block>

      <block v-else>
        <text class="mc__guest-title">开通会员</text>
        <text class="mc__guest-desc">消费得积分 · 等级享折扣 · 积分兑好券</text>
        <button class="mc__open" :disabled="busy('open')" @tap="openMember">
          {{ busy('open') ? '开通中…' : '立即开通（免费）' }}
        </button>
      </block>
    </view>

    <!-- 在线充值：微信 JSAPI 支付，回调成功后余额自动入账；仅会员有储值账户 -->
    <view v-if="isMember" class="recharge" hover-class="hover-press" @tap="openRecharge">
      <text class="recharge__label">在线充值</text>
      <text class="recharge__go">充值 ›</text>
    </view>

    <view class="tabs">
      <view class="tab" :class="{ 'tab--on': tab === 'points' }" @tap="switchTab('points')">积分明细</view>
      <view class="tab" :class="{ 'tab--on': tab === 'recharges' }" @tap="switchTab('recharges')">充值记录</view>
    </view>

    <scroll-view class="list" scroll-y @scrolltolower="loadMore">
      <view v-if="pageError" class="list__error">
        <text>加载失败</text>
        <text class="list__retry" @tap="bootstrap">重试</text>
      </view>

      <block v-else>
        <block v-if="tab === 'points'">
          <view v-for="r in records" :key="'p' + r.id" class="rec">
            <view class="rec__main">
              <text class="rec__title">{{ r.remark || '积分变动' }}</text>
              <text class="rec__time">{{ r.createdTime }}</text>
            </view>
            <text
              class="rec__num"
              :class="pointsRecordSign(r.type) === '+' ? 'rec__num--in' : 'rec__num--out'"
            >{{ pointsRecordSign(r.type) }}{{ r.points }}</text>
          </view>
        </block>

        <block v-else>
          <view v-for="r in records" :key="'r' + r.id" class="rec">
            <view class="rec__main">
              <view class="rec__titlerow">
                <text class="rec__title">账户充值</text>
                <text v-if="Number(r.giftAmount) > 0" class="rec__gift">赠 ¥{{ fmtAmount(r.giftAmount) }}</text>
              </view>
              <text class="rec__time">{{ r.createdTime }}</text>
            </view>
            <text class="rec__num rec__num--in">+¥{{ fmtAmount(r.amount) }}</text>
          </view>
        </block>

        <x-empty v-if="!isMember && !loading" text="开通会员后可查看相关记录" sub="会员开通免费" />
        <x-empty
          v-else-if="!records.length && !loading && !listError"
          :text="tab === 'points' ? '暂无积分明细' : '暂无充值记录'"
        />
        <view v-if="listError" class="list__error">
          <text>加载失败</text>
          <text class="list__retry" @tap="load(true)">重试</text>
        </view>
        <view v-if="loading" class="list__more"><text>加载中…</text></view>
        <view v-if="!hasMore && records.length" class="list__more"><text>没有更多了</text></view>
      </block>
    </scroll-view>

    <!-- 充值弹层：快捷金额或自定义金额，微信 JSAPI 支付，成功后刷新余额与记录 -->
    <view v-if="rechargeVisible" class="modal-mask" @tap="closeRecharge">
      <view class="rmodal" @tap.stop="">
        <text class="rmodal__title">账户充值</text>
        <view class="ramounts">
          <view
            v-for="a in quickAmounts"
            :key="a"
            class="ramount"
            :class="{ 'ramount--on': !customAmount && rechargeAmount === a }"
            hover-class="hover-press"
            @tap="pickAmount(a)"
          >{{ a }} 元</view>
        </view>
        <input
          v-model="customAmount"
          class="rmodal__input"
          type="digit"
          maxlength="9"
          placeholder="自定义金额（1~5000 元）"
          placeholder-class="rmodal__ph"
        >
        <text class="rmodal__balance">当前余额 ¥{{ fmtAmount(member && member.balance) }}</text>
        <view class="rmodal__ops">
          <text class="rbtn" hover-class="hover-press" @tap="closeRecharge">取消</text>
          <text
            class="rbtn rbtn--main"
            :class="{ 'rbtn--off': busy('recharge') }"
            hover-class="hover-press"
            @tap="confirmRecharge"
          >{{ busy('recharge') ? '支付中…' : '确认充值' }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import {
  http, listState, busyLock, fmtAmount, pointsRecordSign,
  payRechargeByWechat, PAY_CANCEL, PAY_PENDING
} from '@xinfei/mini-shared'

export default {
  data() {
    return Object.assign(listState(), busyLock(), {
      ready: false,       // 首次 bootstrap 完成（onShow 据此避免与 onLoad 双发）
      pageError: false,  // my-info 拉取失败：整页错误态
      isMember: false,
      member: null,
      level: null,
      couponCount: 0,
      levels: [],
      tab: 'points',     // points | recharges
      records: [],
      page: 1,
      pageSize: 10,
      hasMore: true,
      loading: false,
      // 在线充值弹层
      rechargeVisible: false,
      quickAmounts: [50, 100, 200, 500],
      rechargeAmount: 100, // 快捷金额选中值
      customAmount: ''     // 非空时以自定义金额为准（字符串，来自 input）
    })
  },
  computed: {
    levelName() {
      return (this.level && this.level.name) || '会员'
    },
    // MemberLevel.discount 是比率（≤1，0.95 = 9.5 折；=1 等于不打折，不展示徽章）
    discountText() {
      const rate = this.level ? Number(this.level.discount) : NaN
      if (!rate || isNaN(rate) || rate >= 1) return ''
      return Number((rate * 10).toFixed(1)) + ' 折'
    },
    // 当前等级：优先 levelId 精确匹配，匹配不到再按积分门槛兜底
    currentLevel() {
      if (!this.levels.length) return this.level
      if (this.level) {
        const hit = this.levels.find(l => l.id === this.level.id)
        if (hit) return hit
      }
      const pts = Number((this.member && this.member.points) || 0)
      let current = null
      this.levels.forEach(l => {
        if (Number(l.minPoints || 0) <= pts) current = l
      })
      return current
    },
    nextLevel() {
      if (!this.isMember || !this.levels.length) return null
      const pts = Number((this.member && this.member.points) || 0)
      return this.levels.find(l => Number(l.minPoints || 0) > pts) || null
    },
    progressPct() {
      if (!this.isMember) return 0
      if (!this.nextLevel) return 100
      const pts = Number((this.member && this.member.points) || 0)
      const curMin = Number((this.currentLevel && this.currentLevel.minPoints) || 0)
      const nextMin = Number(this.nextLevel.minPoints || 0)
      if (nextMin <= curMin) return 100
      const pct = Math.round(((pts - curMin) / (nextMin - curMin)) * 100)
      return Math.max(0, Math.min(100, pct))
    },
    progressText() {
      if (!this.nextLevel) return '已达最高等级，继续保持'
      const pts = Number((this.member && this.member.points) || 0)
      const need = Number(this.nextLevel.minPoints) - pts
      return '距「' + this.nextLevel.name + '」还需 ' + need + ' 积分'
    }
  },
  onLoad() {
    this.bootstrap()
  },
  onShow() {
    // 从领券中心返回时静默刷新券数等资产，不动列表翻页状态
    if (this.ready) this.refreshInfo()
  },
  methods: {
    fmtAmount,
    pointsRecordSign,
    bootstrap() {
      this.pageError = false
      return Promise.all([
        http.get('/api/member/member/my-info', null, { silent: true }),
        // 等级表失败不阻塞会员卡主流程，进度条区域降级为不展示
        http.get('/api/member/member/my-levels', null, { silent: true }).catch(() => [])
      ]).then(([info, levels]) => {
        const data = info || {}
        this.isMember = !!data.isMember
        this.member = data.member || null
        this.level = data.level || null
        this.couponCount = data.couponCount || 0
        this.levels = Array.isArray(levels) ? levels : []
        this.ready = true
        return this.load(true)
      }).catch(() => {
        this.pageError = true
      })
    },
    // 只刷新会员卡资产（my-info），供 onShow 轻量更新
    refreshInfo() {
      http.get('/api/member/member/my-info', null, { silent: true }).then(data => {
        if (!data) return
        this.isMember = !!data.isMember
        this.member = data.member || null
        this.level = data.level || null
        this.couponCount = data.couponCount || 0
      }).catch(() => {})
    },
    switchTab(t) {
      if (this.tab === t) return
      this.tab = t
      this.load(true)
    },
    load(reset) {
      if (reset) {
        this.page = 1
        this.records = []
        this.hasMore = true
        this.listError = false
      } else if (this.loading || !this.hasMore) {
        return Promise.resolve()
      }
      // 非会员后端会直接抛业务错误，前端不发请求，展示开通引导空态
      if (!this.isMember) return Promise.resolve()
      this.loading = true
      const url = this.tab === 'points'
        ? '/api/member/member/my-points'
        : '/api/member/member/my-recharges'
      return http.get(url, { page: this.page, pageSize: this.pageSize }, { silent: true }).then(data => {
        const list = (data && data.records) || []
        this.records = reset ? list : this.records.concat(list)
        this.hasMore = list.length === this.pageSize
        this.page += 1
      }).catch(() => {
        this.listError = true
      }).then(() => {
        this.loading = false
      })
    },
    loadMore() {
      this.load(false)
    },
    openMember() {
      if (this.busy('open')) return
      this.setBusy('open', true)
      http.post('/api/member/portal/open', null).then(() => {
        uni.showToast({ title: '会员开通成功', icon: 'success' })
        this.bootstrap()
      }).catch(() => {
        // 业务失败原因由请求层统一 toast（如已开通/限流）
      }).then(() => {
        this.setBusy('open', false)
      })
    },
    toCoupon() {
      uni.navigateTo({ url: '/pages/coupon/coupon?tab=mine' })
    },
    openRecharge() {
      this.rechargeVisible = true
    },
    // 支付进行中禁止点遮罩关闭，避免半途丢了弹层
    closeRecharge() {
      if (this.busy('recharge')) return
      this.rechargeVisible = false
    },
    pickAmount(a) {
      this.customAmount = ''
      this.rechargeAmount = a
    },
    // 归一化充值金额：null=格式非法，-1=超出 1~5000 区间，否则返回最多两位小数的金额
    resolveRechargeAmount() {
      const raw = this.customAmount == null ? '' : String(this.customAmount).trim()
      const usingQuick = raw === ''
      const text = usingQuick ? String(this.rechargeAmount) : raw
      if (!/^\d+(\.\d{1,2})?$/.test(text)) return null
      const n = Number(text)
      if (!isFinite(n) || isNaN(n)) return null
      if (n < 1 || n > 5000) return -1
      return Math.round(n * 100) / 100
    },
    confirmRecharge() {
      if (this.busy('recharge')) return
      const amount = this.resolveRechargeAmount()
      if (amount === null) {
        uni.showToast({ title: '请输入正确的金额（最多两位小数）', icon: 'none' })
        return
      }
      if (amount === -1) {
        uni.showToast({ title: '充值金额需在 1~5000 元之间', icon: 'none' })
        return
      }
      this.setBusy('recharge', true)
      // 1.建 PENDING 充值单 2.JSAPI 支付 3.轮询充值单 SUCCESS（回调已联动余额入账）
      http.post('/api/member/portal/recharge/create', { amount, paymentMethod: 'WECHAT' }, { silent: true })
        .then(res => {
          const no = res && res.rechargeNo
          if (!no) throw new Error('充值单创建失败，请稍后重试')
          return payRechargeByWechat(no, amount)
        })
        .then(() => {
          uni.showToast({ title: '充值成功', icon: 'success' })
          this.rechargeVisible = false
          this.customAmount = ''
          this.bootstrap()
        })
        .catch(err => {
          // 用户取消：旧 PENDING 单由后端超时关单，弹层保留可直接换金额重试
          if (err && err.code === PAY_CANCEL) {
            uni.showToast({ title: '支付已取消', icon: 'none' })
            return
          }
          if (err && err.code === PAY_PENDING) {
            uni.showModal({
              title: '充值结果确认中',
              content: '如已扣款，余额稍后到账，可在充值记录中查看',
              showCancel: false,
              success: () => {
                this.rechargeVisible = false
                this.bootstrap()
              }
            })
            return
          }
          // PAY_FAIL 或后端业务拒绝（未绑微信/未配置渠道）：弹层保留，展示原文案
          uni.showToast({ title: (err && err.message) || '充值失败，请稍后重试', icon: 'none' })
        })
        .then(() => { this.setBusy('recharge', false) })
    }
  }
}
</script>

<style>
.mp {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg-page);
}

/* ---- 会员卡 ---- */
.mc {
  flex-shrink: 0;
  margin: 24rpx;
  padding: 32rpx;
  border-radius: var(--radius-xl);
  background: var(--color-primary-gradient);
  box-shadow: var(--shadow-card);
}

.mc__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.mc__level {
  display: flex;
  align-items: center;
}

.mc__level-name {
  font-size: 36rpx;
  font-weight: 600;
  color: var(--color-primary-text);
}

.mc__level-tag {
  margin-left: 16rpx;
  padding: 2rpx 14rpx;
  font-size: 22rpx;
  color: var(--color-primary-text);
  border: 1rpx solid var(--color-primary-text);
  border-radius: var(--radius-round);
}

.mc__brand {
  font-size: 22rpx;
  color: var(--color-primary-text);
  opacity: 0.65;
}

.mc__stats {
  display: flex;
  margin-top: 36rpx;
}

.mc__stat {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.mc__stat-num {
  font-size: 34rpx;
  font-weight: 600;
  color: var(--color-primary-text);
}

.mc__stat-label {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: var(--color-primary-text);
  opacity: 0.7;
}

.mc__prog {
  margin-top: 32rpx;
}

.mc__prog-bar {
  height: 14rpx;
  border-radius: var(--radius-round);
  background: var(--bg-card);
  overflow: hidden;
}

.mc__prog-inner {
  height: 100%;
  border-radius: var(--radius-round);
  background: var(--color-primary-text);
  transition: width var(--transition-fast);
}

.mc__prog-text {
  display: block;
  margin-top: 12rpx;
  font-size: 22rpx;
  color: var(--color-primary-text);
  opacity: 0.75;
}

.mc__guest-title {
  display: block;
  font-size: 36rpx;
  font-weight: 600;
  color: var(--color-primary-text);
}

.mc__guest-desc {
  display: block;
  margin-top: 12rpx;
  font-size: 24rpx;
  color: var(--color-primary-text);
  opacity: 0.8;
}

.mc__open {
  margin: 36rpx 0 0;
  height: 84rpx;
  line-height: 84rpx;
  border-radius: var(--radius-round);
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-primary-text);
  background: var(--bg-card);
}

.mc__open[disabled] {
  opacity: 0.6;
}

/* ---- 充值入口 ---- */
.recharge {
  flex-shrink: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 0 24rpx;
  padding: 26rpx 24rpx;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
  box-shadow: var(--shadow-card);
}

.recharge__label {
  font-size: 28rpx;
  color: var(--color-text-primary);
}

.recharge__go {
  font-size: 26rpx;
  color: var(--color-primary-text-strong);
}

/* ---- 充值弹层 ---- */
.modal-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 48rpx;
  background: rgba(0, 0, 0, 0.5);
}

.rmodal {
  width: 100%;
  padding: 32rpx;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
}

.rmodal__title {
  display: block;
  font-size: 32rpx;
  font-weight: 600;
  text-align: center;
  color: var(--color-text-primary);
}

.ramounts {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  margin-top: 28rpx;
}

.ramount {
  width: 48%;
  margin-bottom: 16rpx;
  padding: 22rpx 0;
  text-align: center;
  font-size: 30rpx;
  color: var(--color-text-primary);
  background: var(--bg-input);
  border: 1rpx solid transparent;
  border-radius: var(--radius-md);
  box-sizing: border-box;
}

.ramount--on {
  color: var(--color-primary-text-strong);
  border-color: var(--color-primary);
  background: var(--color-primary-tint);
}

.rmodal__input {
  margin-top: 8rpx;
  height: 84rpx;
  padding: 0 24rpx;
  font-size: 28rpx;
  color: var(--color-text-primary);
  background: var(--bg-input);
  border-radius: var(--radius-md);
  box-sizing: border-box;
}

.rmodal__ph {
  color: var(--color-text-hint);
  font-size: 26rpx;
}

.rmodal__balance {
  display: block;
  margin-top: 16rpx;
  font-size: 24rpx;
  color: var(--color-text-hint);
}

.rmodal__ops {
  display: flex;
  justify-content: space-between;
  margin-top: 28rpx;
}

.rbtn {
  flex: 1;
  margin: 0 12rpx;
  padding: 20rpx 0;
  text-align: center;
  font-size: 28rpx;
  color: var(--color-text-primary);
  background: var(--bg-input);
  border-radius: var(--radius-round);
}

.rbtn--main {
  font-weight: 600;
  color: var(--color-primary-text);
  background: var(--color-primary);
}

.rbtn--off {
  opacity: 0.6;
}

/* ---- Tab ---- */
.tabs {
  flex-shrink: 0;
  display: flex;
  margin-top: 24rpx;
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

/* ---- 流水列表 ---- */
.list {
  flex: 1;
}

.rec {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 16rpx 24rpx 0;
  padding: 24rpx;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
  box-shadow: var(--shadow-card);
}

.rec__main {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}

.rec__titlerow {
  display: flex;
  align-items: center;
}

.rec__title {
  font-size: 28rpx;
  color: var(--color-text-primary);
}

.rec__gift {
  margin-left: 12rpx;
  font-size: 22rpx;
  color: var(--color-primary-text-strong);
}

.rec__time {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: var(--color-text-hint);
}

.rec__num {
  margin-left: 24rpx;
  font-size: 32rpx;
  font-weight: 600;
}

.rec__num--in {
  color: var(--color-success-text);
}

.rec__num--out {
  color: var(--color-danger-text);
}

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
