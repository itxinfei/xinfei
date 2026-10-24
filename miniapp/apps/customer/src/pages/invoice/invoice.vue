<template>
  <view class="inv">
    <view v-if="booting" class="tip"><text>加载中…</text></view>

    <template v-else>
      <!-- ============ 未选订单：从可开票订单中选 ============ -->
      <block v-if="!orderId">
        <view class="card">
          <view class="section-title">选择要开票的订单</view>
          <text class="section-tip">仅已完成订单可申请开票</text>
        </view>
        <view v-if="ordersLoading" class="list__more"><text>加载中…</text></view>
        <view v-else-if="ordersError" class="list__error">
          <text>加载失败</text>
          <text class="list__retry" @tap="loadOrders">重试</text>
        </view>
        <template v-else>
          <view
            v-for="o in orders"
            :key="o.id"
            class="ocard"
            hover-class="hover-press"
            @tap="pickOrder(o)"
          >
            <view class="ocard__head">
              <text class="ocard__no">{{ o.number }}</text>
              <text class="ocard__time">{{ fmtTime(o.orderTime) }}</text>
            </view>
            <view class="ocard__foot">
              <text class="ocard__amount">¥{{ fmtAmount(o.amount) }}</text>
              <text class="ocard__go">去开票 ›</text>
            </view>
          </view>
          <x-empty v-if="!orders.length" text="暂无可开票订单" sub="订单完成后可在此申请开票" />
        </template>
      </block>

      <!-- ============ 已选订单：发票信息 / 抬头与申请 ============ -->
      <block v-else>
        <view class="card">
          <view class="row"><text class="row__label">订单号</text><text class="row__value row__mono">{{ orderNo || '—' }}</text></view>
          <view class="row"><text class="row__label">订单金额</text><text class="row__value row__price">¥{{ fmtAmount(amount) }}</text></view>
        </view>

        <!-- 已有发票记录：展示状态与票面信息 -->
        <view v-if="record" class="card">
          <view class="rec-head">
            <text class="rec-head__title">{{ statusText(record.status) }}</text>
            <text class="rec-head__tag" :class="'rec-head__tag--s' + record.status">{{ statusText(record.status) }}</text>
          </view>
          <view v-if="record.invoiceNo" class="row"><text class="row__label">发票号码</text><text class="row__value row__mono">{{ record.invoiceNo }}</text></view>
          <view v-if="record.invoiceCode" class="row"><text class="row__label">发票代码</text><text class="row__value row__mono">{{ record.invoiceCode }}</text></view>
          <view v-if="record.title" class="row"><text class="row__label">抬头</text><text class="row__value">{{ record.title }}</text></view>
          <view v-if="record.amount" class="row"><text class="row__label">开票金额</text><text class="row__value">¥{{ fmtAmount(record.amount) }}</text></view>
          <view v-if="record.applyTime" class="row"><text class="row__label">申请时间</text><text class="row__value">{{ fmtTime(record.applyTime) }}</text></view>
          <!-- 已作废：允许重新申请（后端仅拦截非作废记录） -->
          <view v-if="record.status === 3" class="rec-ops">
            <text class="btn btn--main" hover-class="hover-press" @tap="reapply">重新申请开票</text>
          </view>
          <view v-if="record.status === 3" class="tipnote">该发票已作废，可重新提交开票申请</view>
        </view>

        <!-- 未申请：抬头选择 + 新增 + 申请 -->
        <template v-else>
          <view class="card">
            <view class="section-title">选择发票抬头</view>
            <view v-if="titlesLoading" class="list__more"><text>加载中…</text></view>
            <template v-else>
              <view
                v-for="t in titles"
                :key="t.id"
                class="titem"
                hover-class="hover-press"
                @tap="selectedTitleId = t.id"
              >
                <view class="titem__radio" :class="{ 'titem__radio--on': selectedTitleId === t.id }"></view>
                <view class="titem__main">
                  <text class="titem__title">{{ t.title }}</text>
                  <text v-if="t.taxNumber" class="titem__tax">{{ t.taxNumber }} · {{ t.type === 2 ? '企业' : '个人' }}</text>
                </view>
                <text class="titem__op" hover-class="hover-press" @tap.stop="deleteTitle(t)">删除</text>
              </view>
              <x-empty v-if="!titles.length" text="暂无发票抬头" sub="请先在下方新增抬头" />
            </template>
          </view>

          <view class="card">
            <view class="section-title">新增抬头</view>
            <view class="frow">
              <text class="frow__label">抬头类型</text>
              <view class="chips">
                <view class="chip" :class="{ 'chip--on': form.type === 1 }" hover-class="hover-press" @tap="form.type = 1">个人</view>
                <view class="chip" :class="{ 'chip--on': form.type === 2 }" hover-class="hover-press" @tap="form.type = 2">企业</view>
              </view>
            </view>
            <view class="frow">
              <text class="frow__label">抬头名称</text>
              <input
                v-model="form.title"
                class="frow__input"
                maxlength="50"
                :placeholder="form.type === 2 ? '请输入公司名称' : '个人填姓名'"
                placeholder-class="frow__ph"
              />
            </view>
            <view v-if="form.type === 2" class="frow">
              <text class="frow__label">税号</text>
              <input
                v-model="form.taxNumber"
                class="frow__input"
                maxlength="30"
                placeholder="纳税人识别号（15/17/18/20位）"
                placeholder-class="frow__ph"
              />
            </view>
            <text v-if="formError" class="form-error">{{ formError }}</text>
            <text class="btn btn--line" :class="{ 'btn--off': busy('saveTitle') }" hover-class="hover-press" @tap="saveTitle">
              {{ busy('saveTitle') ? '保存中…' : '保存抬头' }}
            </text>
          </view>

          <view class="card">
            <text class="btn btn--main btn--block" :class="{ 'btn--off': busy('apply') || !selectedTitleId }" hover-class="hover-press" @tap="apply">
              {{ busy('apply') ? '提交中…' : '申请开票' }}
            </text>
            <view class="tipnote">仅已完成订单可申请开票，提交后商家将在 3 个工作日内为您开具电子发票。</view>
          </view>
        </template>
      </block>
    </template>
  </view>
</template>

<script>
import { http, busyLock, fmtAmount } from '@xinfei/mini-shared'

const STATUS_TEXT = { 0: '待申请', 1: '已申请，待开具', 2: '已开具', 3: '已作废' }

function pad2(n) {
  return n < 10 ? '0' + n : '' + n
}

// 兼容 Jackson 两种序列化：字符串 "2026-10-22 12:00:00" / 数组 [2026,10,22,12,0]
function fmtTime(t) {
  if (t == null || t === '') return ''
  if (Array.isArray(t)) {
    return t[0] + '-' + pad2(t[1]) + '-' + pad2(t[2]) + ' ' + pad2(t[3] || 0) + ':' + pad2(t[4] || 0)
  }
  return String(t).replace('T', ' ').slice(0, 16)
}

export default {
  data() {
    return Object.assign(busyLock(), {
      booting: true,
      // 目标订单：order/detail 跳入时由 query.id 指定；直接进入则先选单
      orderId: '',
      orderNo: '',
      amount: '',
      // 可开票订单（status=4 已完成）
      orders: [],
      ordersLoading: false,
      ordersError: false,
      // 发票记录（已申请/已开具/已作废）
      record: null,
      // 抬头
      titles: [],
      titlesLoading: false,
      selectedTitleId: null,
      // 保存抬头后按 名称+税号 自动选中（后端 save 不返回新 id）
      pendingSelect: null,
      // 新增抬头表单（type：1=个人 2=企业，与 InvoiceTitle.type 对齐）
      form: { type: 1, title: '', taxNumber: '' },
      formError: ''
    })
  },
  onLoad(query) {
    if (query && query.id) {
      this.orderId = String(query.id)
      this.orderNo = query.orderNo ? decodeURIComponent(query.orderNo) : ''
      this.amount = query.amount ? Number(query.amount) : ''
      // 订单号/金额以接口回查为准：入口只传 orderId 时兜底补齐
      if (!this.orderNo || !query.amount) this.fillOrderInfo()
    }
    this.boot()
  },
  onShow() {
    // 从「我的发票」跳回时刷新记录；首次 onShow 由 boot 承担，跳过
  },
  methods: {
    fmtAmount,
    fmtTime,
    statusText(s) {
      return STATUS_TEXT[s] != null ? STATUS_TEXT[s] : '未知'
    },
    boot() {
      if (this.orderId) {
        this.loadInvoice()
        this.loadTitles()
        this.booting = false
      } else {
        this.loadOrders().then(() => { this.booting = false })
      }
    },
    // ---- 订单回查：orderNo/金额以详情接口为准 ----
    fillOrderInfo() {
      http.get('/order/' + encodeURIComponent(this.orderId), null, { silent: true }).then(o => {
        if (o) {
          this.orderNo = o.number || this.orderNo
          if (!this.amount) this.amount = o.amount
        }
      }).catch(() => {})
    },
    // ---- 可开票订单列表（仅已完成） ----
    loadOrders() {
      this.ordersLoading = true
      this.ordersError = false
      return http.get('/order/userPage', { page: 1, pageSize: 20, status: 4 }, { silent: true }).then(data => {
        this.orders = (data && data.records) || []
      }).catch(err => {
        if (err && err.notLogin) return
        this.ordersError = true
      }).then(() => {
        this.ordersLoading = false
      })
    },
    pickOrder(o) {
      this.orderId = String(o.id)
      this.orderNo = o.number || ''
      this.amount = o.amount
      this.record = null
      this.loadInvoice()
      this.loadTitles()
    },
    // ---- 发票记录 ----
    loadInvoice() {
      http.get('/invoice/order/' + encodeURIComponent(this.orderId), null, { silent: true }).then(data => {
        this.record = data || null
      }).catch(() => {})
    },
    // ---- 抬头 ----
    loadTitles() {
      this.titlesLoading = true
      return http.get('/invoice/title/list', null, { silent: true }).then(list => {
        this.titles = Array.isArray(list) ? list : []
        if (this.pendingSelect) {
          const hit = this.titles.find(t =>
            t.title === this.pendingSelect.title &&
            (t.taxNumber || '') === (this.pendingSelect.taxNumber || ''))
          if (hit) this.selectedTitleId = hit.id
          this.pendingSelect = null
        } else if (this.titles.length && this.selectedTitleId == null) {
          this.selectedTitleId = this.titles[0].id
        }
      }).catch(err => {
        if (err && err.notLogin) return
        uni.showToast({ title: (err && err.message) || '加载抬头失败', icon: 'none' })
      }).then(() => {
        this.titlesLoading = false
      })
    },
    // 企业税号：15/17/18/20 位字母或数字，合法返回大写值，非法返回 null
    normalizeTaxNumber(v) {
      const s = (v || '').trim().toUpperCase()
      return /^(?:[0-9A-Z]{15}|[0-9A-Z]{17}|[0-9A-Z]{18}|[0-9A-Z]{20})$/.test(s) ? s : null
    },
    validateForm(form) {
      const title = (form.title || '').trim()
      if (!title) return { msg: '请填写抬头名称' }
      let taxNumber = ''
      if (form.type === 2) {
        taxNumber = this.normalizeTaxNumber(form.taxNumber)
        if (!taxNumber) return { msg: '请填写正确的企业税号（15/17/18/20位字母或数字）' }
      }
      return { title, taxNumber }
    },
    saveTitle() {
      if (this.busy('saveTitle')) return
      const v = this.validateForm(this.form)
      if (v.msg) {
        this.formError = v.msg
        return
      }
      this.formError = ''
      this.setBusy('saveTitle', true)
      http.post('/invoice/title/save', { type: this.form.type, title: v.title, taxNumber: v.taxNumber }, { silent: true })
        .then(() => {
          uni.showToast({ title: '抬头已保存', icon: 'success' })
          this.pendingSelect = { title: v.title, taxNumber: v.taxNumber }
          this.form.title = ''
          this.form.taxNumber = ''
          this.loadTitles()
        })
        .catch(err => {
          if (err && err.notLogin) return
          uni.showToast({ title: (err && err.message) || '保存失败', icon: 'none' })
        })
        .then(() => { this.setBusy('saveTitle', false) })
    },
    deleteTitle(t) {
      uni.showModal({
        title: '删除抬头',
        content: '确定删除抬头「' + t.title + '」吗？',
        confirmText: '删除',
        success: r => {
          if (!r.confirm) return
          http.del('/invoice/title/' + t.id, null, { silent: true }).then(() => {
            uni.showToast({ title: '已删除', icon: 'success' })
            if (this.selectedTitleId === t.id) this.selectedTitleId = null
            this.loadTitles()
          }).catch(() => {})
        }
      })
    },
    // ---- 申请开票：body 为 InvoiceRecord 形状（titleId/title/taxNumber/type） ----
    apply() {
      if (this.busy('apply')) return
      if (!this.selectedTitleId) {
        uni.showToast({ title: '请选择发票抬头', icon: 'none' })
        return
      }
      const t = this.titles.find(x => x.id === this.selectedTitleId)
      if (!t) {
        uni.showToast({ title: '抬头不存在，请重新选择', icon: 'none' })
        return
      }
      this.setBusy('apply', true)
      http.post('/invoice/apply/' + encodeURIComponent(this.orderId), {
        titleId: t.id,
        title: t.title,
        taxNumber: t.taxNumber || '',
        type: t.type || 1
      }, { silent: true }).then(record => {
        uni.showModal({
          title: '申请成功',
          content: '发票申请已提交，商家将在 3 个工作日内为您开具。',
          showCancel: false,
          success: () => {
            this.record = record || { status: 1 }
            this.loadInvoice()
          }
        })
      }).catch(err => {
        if (err && err.notLogin) return
        uni.showToast({ title: (err && err.message) || '申请失败', icon: 'none' })
      }).then(() => { this.setBusy('apply', false) })
    },
    // 已作废 → 回到抬头选择/申请流程（后端允许作废后重新申请）
    reapply() {
      this.record = null
    }
  }
}
</script>

<style>
.inv {
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

.tip {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 200rpx 0;
  font-size: 28rpx;
  color: var(--color-text-hint);
}

.section-title {
  margin-bottom: 8rpx;
  font-size: 30rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

.section-tip {
  font-size: 24rpx;
  color: var(--color-text-hint);
}

.row {
  display: flex;
  align-items: center;
  padding: 12rpx 0;
}

.row__label {
  width: 150rpx;
  flex-shrink: 0;
  font-size: 26rpx;
  color: var(--color-text-hint);
}

.row__value {
  flex: 1;
  font-size: 28rpx;
  color: var(--color-text-primary);
  word-break: break-all;
}

.row__mono {
  font-family: monospace;
}

.row__price {
  font-weight: 600;
  color: var(--color-price);
}

/* ---- 可开票订单卡 ---- */
.ocard {
  margin: 16rpx 24rpx;
  padding: 24rpx;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
  box-shadow: var(--shadow-card);
}

.ocard__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.ocard__no {
  font-size: 26rpx;
  font-family: monospace;
  color: var(--color-text-primary);
}

.ocard__time {
  font-size: 22rpx;
  color: var(--color-text-hint);
}

.ocard__foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16rpx;
}

.ocard__amount {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--color-price);
}

.ocard__go {
  font-size: 24rpx;
  color: var(--color-primary-text-strong);
}

/* ---- 发票记录状态 ---- */
.rec-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8rpx;
}

.rec-head__title {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

.rec-head__tag {
  padding: 4rpx 18rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
}

.rec-head__tag--1 {
  color: var(--color-warning);
  background: var(--color-primary-tint);
}

.rec-head__tag--2 {
  color: var(--color-success-text);
  background: var(--color-success-tint);
}

.rec-head__tag--3 {
  color: var(--color-text-hint);
  background: var(--color-neutral-tint);
}

.rec-ops {
  display: flex;
  justify-content: center;
  margin-top: 20rpx;
}

/* ---- 抬头选择 ---- */
.titem {
  display: flex;
  align-items: center;
  padding: 20rpx 0;
}

.titem + .titem {
  border-top: 1rpx solid var(--border-light);
}

.titem__radio {
  width: 32rpx;
  height: 32rpx;
  margin-right: 20rpx;
  flex-shrink: 0;
  box-sizing: border-box;
  border: 3rpx solid var(--border-medium);
  border-radius: var(--radius-round);
}

.titem__radio--on {
  border: 10rpx solid var(--color-primary);
  background: var(--bg-card);
}

.titem__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.titem__title {
  font-size: 28rpx;
  color: var(--color-text-primary);
}

.titem__tax {
  margin-top: 6rpx;
  font-size: 22rpx;
  color: var(--color-text-hint);
}

.titem__op {
  flex-shrink: 0;
  margin-left: 16rpx;
  padding: 8rpx 22rpx;
  border-radius: 28rpx;
  font-size: 24rpx;
  color: var(--color-danger-text);
  background: var(--color-danger-tint);
}

/* ---- 新增抬头表单 ---- */
.frow {
  display: flex;
  align-items: center;
  padding: 16rpx 0;
}

.frow__label {
  width: 150rpx;
  flex-shrink: 0;
  font-size: 26rpx;
  color: var(--color-text-secondary);
}

.frow__input {
  flex: 1;
  height: 72rpx;
  padding: 0 20rpx;
  box-sizing: border-box;
  border-radius: var(--radius-md);
  background: var(--bg-input);
  font-size: 28rpx;
  color: var(--color-text-primary);
}

.frow__ph {
  color: var(--color-text-disabled);
}

.chips {
  display: flex;
}

.chip {
  padding: 10rpx 32rpx;
  margin-right: 16rpx;
  border-radius: 999rpx;
  font-size: 26rpx;
  color: var(--color-text-secondary);
  background: var(--bg-input);
  border: 1rpx solid var(--border-medium);
}

.chip--on {
  font-weight: 600;
  color: var(--color-primary-text);
  background: var(--color-primary-tint);
  border-color: var(--color-primary);
}

.form-error {
  display: block;
  padding: 4rpx 0;
  font-size: 24rpx;
  color: var(--color-danger);
}

/* ---- 按钮 ---- */
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 18rpx 40rpx;
  border-radius: 999rpx;
  font-size: 28rpx;
}

.btn--block {
  display: flex;
  width: 100%;
  box-sizing: border-box;
}

.btn--main {
  font-weight: 600;
  color: var(--color-primary-text);
  background: var(--color-primary);
}

.btn--line {
  color: var(--color-primary-text-strong);
  border: 1rpx solid var(--color-primary-text-strong);
}

.btn--off {
  opacity: 0.5;
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

.tipnote {
  margin-top: 16rpx;
  font-size: 22rpx;
  line-height: 34rpx;
  color: var(--color-text-hint);
}
</style>
