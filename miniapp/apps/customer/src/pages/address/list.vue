<template>
  <view class="page">
    <scroll-view class="list" scroll-y>
      <view v-for="item in records" :key="item.id" class="card">
        <view class="card__main" hover-class="hover-press" @tap="handleItemTap(item)">
          <view class="card__line">
            <text class="card__name">{{ item.consignee }}</text>
            <text v-if="sexText(item)" class="card__sex">{{ sexText(item) }}</text>
            <text class="card__phone">{{ item.phone }}</text>
            <text v-if="item.isDefault === 1" class="tag tag--default">默认</text>
            <text v-if="item.label && item.label !== '无'" class="tag">{{ item.label }}</text>
          </view>
          <text class="card__addr">{{ composeAddress(item) }}</text>
        </view>

        <view class="card__ops">
          <text
            class="op"
            :class="{ 'op--off': item.isDefault === 1 || busy('default:' + item.id) }"
            hover-class="hover-press"
            @tap="handleSetDefault(item)"
          >{{ item.isDefault === 1 ? '默认地址' : '设为默认' }}</text>
          <text class="op" hover-class="hover-press" @tap="toEdit(item)">编辑</text>
          <text class="op op--danger" hover-class="hover-press" @tap="handleDelete(item)">删除</text>
        </view>
      </view>

      <view v-if="listError" class="list__state">
        <text>加载失败</text>
        <text class="list__retry" @tap="load(true)">重试</text>
      </view>
      <x-empty v-if="!records.length && !loading && !listError" text="暂无收货地址" sub="添加地址后下单更便捷" />
      <view v-if="loading" class="list__state"><text>加载中…</text></view>
    </scroll-view>

    <view class="footer">
      <button class="footer__btn" hover-class="hover-press" @tap="toAdd">+ 新增收货地址</button>
    </view>
  </view>
</template>

<script>
import { http, qs, listState, busyLock, STORAGE_KEYS } from '@xinfei/mini-shared'

export default {
  data() {
    return Object.assign(listState(), busyLock(), {
      // 选择模式：来源页（结算页）通过 ?select=1 进入，点卡片回传地址 ID 后立即返回
      selectMode: false,
      records: [],
      loading: false,
      loadedOnce: false
    })
  },
  onLoad(query) {
    this.selectMode = !!(query && query.select === '1')
    this.load(true)
  },
  onShow() {
    // 首次进入由 onLoad 加载；从编辑页返回时重拉，覆盖 onLoad 未触发导致的列表过期
    if (this.loadedOnce) this.load(true)
  },
  onPullDownRefresh() {
    this.load(true).then(() => uni.stopPullDownRefresh())
  },
  methods: {
    load(reset) {
      if (!reset && this.loading) return Promise.resolve()
      this.loading = true
      this.listError = false
      return http.get('/address-book/list', null, { silent: true }).then(list => {
        this.records = list || []
      }).catch(() => {
        this.listError = true
      }).then(() => {
        this.loading = false
        this.loadedOnce = true
      })
    },
    sexText(item) {
      if (item.sex === 0) return '女士'
      if (item.sex === 1) return '先生'
      return ''
    },
    // 移植 H5 composeAddressText：省市区去重拼接 + detail；detail 自带区划前缀时剥离，兼容 legacy 数据
    composeAddress(item) {
      const regions = [item.provinceName, item.cityName, item.districtName]
        .filter((v, i, arr) => v && arr.indexOf(v) === i)
      let detail = item.detail || ''
      regions.forEach(name => {
        if (detail.indexOf(name) === 0) detail = detail.slice(name.length)
      })
      return regions.join('') + detail
    },
    handleItemTap(item) {
      if (!this.selectMode) {
        this.toEdit(item)
        return
      }
      // 一次性交接：写 ID 后来源页 onShow 读取并删除
      uni.setStorageSync(STORAGE_KEYS.customerSelectedAddressId, String(item.id))
      uni.navigateBack()
    },
    handleSetDefault(item) {
      if (item.isDefault === 1) return
      const key = 'default:' + item.id
      if (this.busy(key)) return
      this.setBusy(key, true)
      http.put('/address-book/default', { id: item.id }).then(() => {
        uni.showToast({ title: '已设为默认', icon: 'none' })
        return this.load(true)
      }).catch(() => {}).then(() => { this.setBusy(key, false) })
    },
    handleDelete(item) {
      uni.showModal({
        title: '删除地址',
        content: '确定删除该收货地址？',
        confirmColor: '#e64340', // 原生组件参数不认 CSS 变量（合规例外）
        success: res => {
          if (!res.confirm) return
          // 后端为 @RequestParam List<Long> ids，必须走 query；DELETE 的 body 不会被绑定
          http.del(qs('/address-book', { ids: item.id })).then(() => {
            uni.showToast({ title: '已删除', icon: 'none' })
            return this.load(true)
          }).catch(() => {})
        }
      })
    },
    toAdd() {
      const suffix = this.selectMode ? '?select=1' : ''
      uni.navigateTo({ url: '/pages/address/edit' + suffix })
    },
    toEdit(item) {
      const suffix = this.selectMode ? '&select=1' : ''
      uni.navigateTo({ url: '/pages/address/edit?id=' + item.id + suffix })
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

.list {
  flex: 1;
}

.card {
  margin: 16rpx 24rpx;
  padding: 24rpx;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
  box-shadow: var(--shadow-card);
}

.card__line {
  display: flex;
  align-items: center;
}

.card__name {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

.card__sex {
  margin-left: 12rpx;
  font-size: 26rpx;
  color: var(--color-text-secondary);
}

.card__phone {
  margin-left: 20rpx;
  font-size: 28rpx;
  color: var(--color-text-secondary);
}

.tag {
  margin-left: 12rpx;
  padding: 2rpx 12rpx;
  border-radius: var(--radius-sm);
  font-size: 22rpx;
  color: var(--color-primary-text-strong);
  background: var(--color-primary-tint);
}

.tag--default {
  color: var(--color-primary-text);
  background: var(--color-primary);
}

.card__addr {
  margin-top: 12rpx;
  font-size: 26rpx;
  line-height: 38rpx;
  color: var(--color-text-secondary);
}

.card__ops {
  display: flex;
  justify-content: flex-end;
  margin-top: 16rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid var(--border-light);
}

.op {
  margin-left: 20rpx;
  padding: 8rpx 28rpx;
  border-radius: 32rpx;
  font-size: 26rpx;
  color: var(--color-text-secondary);
  border: 1rpx solid var(--border-medium);
}

.op--danger {
  color: var(--color-danger-text);
}

.op--off {
  opacity: 0.5;
}

.list__state {
  padding: 120rpx 0;
  text-align: center;
  font-size: 26rpx;
  color: var(--color-text-hint);
}

.list__retry {
  margin-left: 12rpx;
  color: var(--color-primary-text);
}

.footer {
  flex-shrink: 0;
  padding: 16rpx 24rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background: var(--bg-card);
  box-shadow: var(--shadow-bar-top);
}

.footer__btn {
  height: 88rpx;
  line-height: 88rpx;
  border-radius: 44rpx;
  font-size: 32rpx;
  color: var(--color-primary-text);
  background: var(--color-primary-gradient);
}
</style>
