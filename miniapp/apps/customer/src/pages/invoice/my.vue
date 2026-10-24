<template>
  <view class="myinv">
    <view
      v-for="item in records"
      :key="item.id"
      class="card"
      hover-class="hover-press"
      @tap="goInvoice(item)"
    >
      <view class="card__head">
        <text class="card__title">{{ item.title || '未填写抬头' }}</text>
        <text class="card__tag" :class="'card__tag--s' + item.status">{{ statusText(item.status) }}</text>
      </view>
      <view class="row">
        <text class="row__label">订单号</text>
        <text class="row__value row__mono">{{ item.orderNo || '--' }}</text>
      </view>
      <view class="row">
        <text class="row__label">申请时间</text>
        <text class="row__value">{{ fmtTime(item.applyTime) }}</text>
      </view>
      <view class="row">
        <text class="row__label">开票金额</text>
        <text class="row__value row__price">¥{{ fmtAmount(item.amount) }}</text>
      </view>
      <!-- 已开具且有票面文件：下载 PDF 后打开（发票文件落 private 目录，须带 X-Auth-Token 走 /common/download） -->
      <view v-if="Number(item.status) === 2 && item.invoiceUrl" class="card__ops">
        <text
          class="card__view"
          :class="{ 'card__view--off': openingId }"
          hover-class="hover-press"
          @tap.stop="viewInvoice(item)"
        >{{ openingId === item.id ? '打开中…' : '查看发票' }}</text>
      </view>
    </view>

    <view v-if="listError" class="list__error">
      <text>加载失败，请稍后重试</text>
      <text class="list__retry" @tap="load(true)">重新加载</text>
    </view>
    <x-empty v-else-if="!records.length && !loading" text="暂无发票记录" sub="下单后可在此开具发票" />
    <view v-if="loading" class="list__more"><text>加载中…</text></view>
    <view v-if="!hasMore && records.length" class="list__more"><text>没有更多了</text></view>
  </view>
</template>

<script>
import { http, listState, fmtAmount, fullImage, AUTH_HEADER, STORAGE_KEYS, read } from '@xinfei/mini-shared'

const STATUS_TEXT = { 0: '待申请', 1: '已申请，待开具', 2: '已开具', 3: '已作废' }

function pad2(n) {
  return n < 10 ? '0' + n : '' + n
}

// 兼容 LocalDateTime 的 ISO 带 T 格式与 Jackson 数组序列化
function fmtTime(t) {
  if (t == null || t === '') return '-'
  if (Array.isArray(t)) {
    return t[0] + '-' + pad2(t[1]) + '-' + pad2(t[2]) + ' ' + pad2(t[3] || 0) + ':' + pad2(t[4] || 0)
  }
  return String(t).replace('T', ' ').slice(0, 16)
}

export default {
  data() {
    return Object.assign(listState(), {
      records: [],
      total: 0,
      page: 1,
      pageSize: 10,
      hasMore: true,
      loading: false,
      openingId: null
    })
  },
  onLoad() {
    this.load(true)
  },
  onPullDownRefresh() {
    this.load(true).then(() => uni.stopPullDownRefresh())
  },
  onReachBottom() {
    this.load(false)
  },
  onShow() {
    // 开发票页申请成功后返回时刷新列表
    if (this.records.length || this.total) this.load(true)
  },
  methods: {
    fmtAmount,
    fmtTime,
    statusText(s) {
      return STATUS_TEXT[s] != null ? STATUS_TEXT[s] : '未知'
    },
    load(reset) {
      if (reset) {
        this.page = 1
        this.records = []
        this.hasMore = true
      } else if (this.loading || !this.hasMore) {
        return Promise.resolve()
      }
      this.loading = true
      this.listError = false
      // pageSize 上限 50（@Max(50)），保持 10 与 Web 版一致
      return http.get('/invoice/my/page', { page: this.page, pageSize: this.pageSize }, { silent: true })
        .then(data => {
          const list = (data && data.records) || []
          this.total = (data && data.total) || 0
          this.records = reset ? list : this.records.concat(list)
          this.hasMore = this.records.length < this.total && list.length > 0
          if (!reset) this.page += 1
        })
        .catch(err => {
          if (err && err.notLogin) return
          this.listError = true
        })
        .then(() => {
          this.loading = false
        })
    },
    goInvoice(item) {
      // 携带订单号/金额，供开发票页回显（接口只回查 orderId 也兜得住）
      const q = 'id=' + encodeURIComponent(item.orderId || '')
        + '&orderNo=' + encodeURIComponent(item.orderNo || '')
        + '&amount=' + encodeURIComponent(item.amount != null ? item.amount : '')
      uni.navigateTo({ url: '/pages/invoice/invoice?' + q })
    },
    // 已开具（status=2，InvoiceRecord.STATUS_ISSUED）且有 invoiceUrl：带头下载 PDF 再 openDocument 打开
    viewInvoice(item) {
      if (this.openingId) return
      if (!item.invoiceUrl) return
      this.openingId = item.id
      const header = {}
      const token = read(STORAGE_KEYS.authToken)
      if (token) header[AUTH_HEADER] = token
      uni.downloadFile({
        url: fullImage(item.invoiceUrl),   // private 相对路径 → /common/download?name=…
        header,
        success: res => {
          if (res.statusCode === 200) {
            uni.openDocument({
              filePath: res.tempFilePath,
              fileType: 'pdf',
              showMenu: true,
              fail: () => uni.showToast({ title: '发票文件打开失败', icon: 'none' })
            })
          } else if (res.statusCode === 401) {
            uni.showToast({ title: '登录已过期，请重新登录', icon: 'none' })
          } else {
            uni.showToast({ title: '发票下载失败', icon: 'none' })
          }
        },
        fail: () => uni.showToast({ title: '网络不可用，发票下载失败', icon: 'none' }),
        complete: () => { this.openingId = null }
      })
    }
  }
}
</script>

<style>
.myinv {
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

.card__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8rpx;
}

.card__title {
  flex: 1;
  min-width: 0;
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card__tag {
  flex-shrink: 0;
  margin-left: 16rpx;
  padding: 4rpx 18rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
}

.card__tag--1 {
  color: var(--color-warning);
  background: var(--color-primary-tint);
}

.card__tag--2 {
  color: var(--color-success-text);
  background: var(--color-success-tint);
}

.card__tag--3 {
  color: var(--color-text-hint);
  background: var(--color-neutral-tint);
}

.row {
  display: flex;
  align-items: center;
  padding: 8rpx 0;
}

.row__label {
  width: 150rpx;
  flex-shrink: 0;
  font-size: 26rpx;
  color: var(--color-text-hint);
}

.row__value {
  flex: 1;
  font-size: 26rpx;
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

.card__ops {
  display: flex;
  justify-content: center;
  margin-top: 16rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid var(--border-light);
}

.card__view {
  padding: 10rpx 48rpx;
  border-radius: var(--radius-round);
  font-size: 26rpx;
  color: var(--color-primary-text-strong);
  border: 1rpx solid var(--color-primary-text-strong);
}

.card__view--off {
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
</style>
