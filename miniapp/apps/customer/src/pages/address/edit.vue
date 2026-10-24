<template>
  <view class="page">
    <view class="form">
      <view class="row">
        <text class="row__label">联系人</text>
        <input
          v-model="form.consignee"
          class="row__input"
          maxlength="10"
          placeholder="请输入联系人姓名"
          placeholder-class="row__ph"
        />
      </view>

      <view class="row">
        <text class="row__label">性别</text>
        <view class="chips">
          <view
            v-for="s in sexOptions"
            :key="s.value"
            class="chip"
            :class="{ 'chip--on': form.sex === s.value }"
            hover-class="hover-press"
            @tap="form.sex = s.value"
          >{{ s.label }}</view>
        </view>
      </view>

      <view class="row">
        <text class="row__label">手机号</text>
        <input
          v-model="form.phone"
          class="row__input"
          type="number"
          maxlength="11"
          placeholder="请输入 11 位手机号"
          placeholder-class="row__ph"
        />
      </view>

      <picker
        class="picker"
        mode="selector"
        :range="provinces"
        range-key="name"
        :value="provinceIndex < 0 ? 0 : provinceIndex"
        @change="handleProvinceChange"
      >
        <view class="row">
          <text class="row__label">省份</text>
          <text class="row__value" :class="{ 'row__value--ph': !form.provinceName }">{{ form.provinceName || '请选择省份' }}</text>
        </view>
      </picker>

      <picker
        class="picker"
        mode="selector"
        :range="cities"
        range-key="name"
        :value="cityIndex < 0 ? 0 : cityIndex"
        @change="handleCityChange"
      >
        <view class="row">
          <text class="row__label">城市</text>
          <text class="row__value" :class="{ 'row__value--ph': !form.cityName }">{{ form.cityName || '请选择城市' }}</text>
        </view>
      </picker>

      <picker
        class="picker"
        mode="selector"
        :range="districts"
        range-key="name"
        :value="districtIndex < 0 ? 0 : districtIndex"
        @change="handleDistrictChange"
      >
        <view class="row">
          <text class="row__label">区/县</text>
          <text class="row__value" :class="{ 'row__value--ph': !form.districtName }">{{ form.districtName || '请选择区/县' }}</text>
        </view>
      </picker>
    </view>

    <view class="form">
      <view class="row">
        <text class="row__label">小区/大厦</text>
        <input
          v-model="form.community"
          class="row__input"
          maxlength="100"
          placeholder="如：心飞花园 / 环球大厦"
          placeholder-class="row__ph"
        />
      </view>
      <view class="row">
        <text class="row__label">楼栋</text>
        <input
          v-model="form.building"
          class="row__input"
          maxlength="20"
          placeholder="如：3 栋"
          placeholder-class="row__ph"
        />
      </view>
      <view class="row">
        <text class="row__label">单元</text>
        <input
          v-model="form.unit"
          class="row__input"
          maxlength="20"
          placeholder="选填"
          placeholder-class="row__ph"
        />
      </view>
      <view class="row">
        <text class="row__label">楼层</text>
        <input
          v-model="form.floor"
          class="row__input"
          maxlength="20"
          placeholder="选填"
          placeholder-class="row__ph"
        />
      </view>
      <view class="row">
        <text class="row__label">门牌号</text>
        <input
          v-model="form.roomNo"
          class="row__input"
          maxlength="20"
          placeholder="如：1503"
          placeholder-class="row__ph"
        />
      </view>
      <view class="row">
        <text class="row__label">街道</text>
        <input
          v-model="form.streetName"
          class="row__input"
          maxlength="50"
          placeholder="选填"
          placeholder-class="row__ph"
        />
      </view>

      <view class="row">
        <text class="row__label">标签</text>
        <view class="chips">
          <view
            v-for="lb in labelOptions"
            :key="lb"
            class="chip"
            :class="{ 'chip--on': form.label === lb }"
            hover-class="hover-press"
            @tap="form.label = lb"
          >{{ lb }}</view>
        </view>
      </view>
    </view>

    <view class="footer">
      <button v-if="id" class="footer__del" hover-class="hover-press" @tap="handleDelete">删除此地址</button>
      <button class="footer__save" :loading="saving" hover-class="hover-press" @tap="handleSave">保存</button>
    </view>
  </view>
</template>

<script>
import { http, qs, STORAGE_KEYS } from '@xinfei/mini-shared'

const PHONE_RE = /^1[3-9]\d{9}$/

function emptyForm() {
  return {
    consignee: '',
    sex: 1,
    phone: '',
    provinceCode: '', provinceName: '',
    cityCode: '', cityName: '',
    districtCode: '', districtName: '',
    streetName: '', community: '', building: '',
    unit: '', floor: '', roomNo: '',
    label: '公司'
  }
}

export default {
  data() {
    return {
      id: null,
      // 选择模式（来源为结算页）：保存成功后把地址 ID 交接回来源页
      selectMode: false,
      saving: false,
      form: emptyForm(),
      // legacy 地址（六列结构化字段为空、仅有后端 detail）详情值，供地址可定位校验放行
      legacyDetail: '',
      provinces: [],
      cities: [],
      districts: [],
      provinceIndex: -1,
      cityIndex: -1,
      districtIndex: -1,
      sexOptions: [
        { label: '先生', value: 1 },
        { label: '女士', value: 0 }
      ],
      labelOptions: ['无', '公司', '家', '学校']
    }
  },
  onLoad(query) {
    this.id = query && query.id ? query.id : null
    this.selectMode = !!(query && query.select === '1')
    uni.setNavigationBarTitle({ title: this.id ? '编辑收货地址' : '新增收货地址' })
    this.loadRegions(0).then(list => {
      this.provinces = list
      if (this.id) this.loadDetail()
    })
  },
  methods: {
    loadRegions(parentId) {
      return http.get(qs('/region/children', { parentId }), null, { silent: true })
        .then(list => list || []).catch(() => [])
    },
    loadDetail() {
      http.get('/address-book/' + this.id).then(detail => {
        if (!detail) return
        const form = emptyForm()
        Object.keys(form).forEach(k => {
          if (detail[k] !== null && detail[k] !== undefined) form[k] = detail[k]
        })
        // sex 可空：回填必须特判 0，否则会被 || 默认值吞成"先生"
        form.sex = detail.sex === 0 || detail.sex === 1 ? detail.sex : 1
        this.form = form
        this.legacyDetail = detail.detail || ''
        this.restoreRegionPickers(detail)
      }).catch(() => {})
    },
    // 详情不返回 region 主键 id，只有 code/name：按 code 逐级反查 picker 下标
    restoreRegionPickers(detail) {
      const pIdx = this.provinces.findIndex(p => p.code === detail.provinceCode)
      if (pIdx < 0) return
      this.provinceIndex = pIdx
      this.loadRegions(this.provinces[pIdx].id).then(cities => {
        this.cities = cities
        const cIdx = cities.findIndex(c => c.code === detail.cityCode)
        if (cIdx < 0) return
        this.cityIndex = cIdx
        return this.loadRegions(cities[cIdx].id)
      }).then(districts => {
        if (!districts) return
        this.districts = districts
        const dIdx = districts.findIndex(d => d.code === detail.districtCode)
        if (dIdx >= 0) this.districtIndex = dIdx
      })
    },
    resetCityDistrict() {
      this.cities = []
      this.districts = []
      this.cityIndex = -1
      this.districtIndex = -1
      this.form.cityCode = ''
      this.form.cityName = ''
      this.form.districtCode = ''
      this.form.districtName = ''
    },
    handleProvinceChange(e) {
      const idx = Number(e.detail.value)
      const province = this.provinces[idx]
      this.provinceIndex = idx
      this.form.provinceCode = province.code
      this.form.provinceName = province.name
      this.resetCityDistrict()
      this.loadRegions(province.id).then(list => { this.cities = list })
    },
    handleCityChange(e) {
      const idx = Number(e.detail.value)
      const city = this.cities[idx]
      this.cityIndex = idx
      this.form.cityCode = city.code
      this.form.cityName = city.name
      this.districts = []
      this.districtIndex = -1
      this.form.districtCode = ''
      this.form.districtName = ''
      this.loadRegions(city.id).then(list => { this.districts = list })
    },
    handleDistrictChange(e) {
      const idx = Number(e.detail.value)
      const district = this.districts[idx]
      this.districtIndex = idx
      this.form.districtCode = district.code
      this.form.districtName = district.name
    },
    validate() {
      const f = this.form
      if (!f.consignee.trim()) return '请填写联系人姓名'
      if (!PHONE_RE.test(f.phone)) return '请输入正确的 11 位手机号'
      if (!f.provinceName || !f.cityName || !f.districtName) return '请选择完整的省/市/区'
      // 与 H5、后端同口径：有小区/大厦，或同时填楼栋+门牌号；
      // 六格全空但旧 detail 存在（legacy 数据）放行，允许只改联系人/电话
      const structuredEmpty = !f.community && !f.building && !f.unit && !f.floor && !f.roomNo && !f.streetName
      if (structuredEmpty) {
        if (this.legacyDetail) return ''
        return '请填写小区/大厦，或同时填写楼栋和门牌号'
      }
      if (!f.community && !(f.building && f.roomNo)) {
        return '请填写小区/大厦，或同时填写楼栋和门牌号'
      }
      return ''
    },
    handleSave() {
      if (this.saving) return
      const error = this.validate()
      if (error) {
        uni.showToast({ title: error, icon: 'none' })
        return
      }
      const f = this.form
      // 显式白名单字段：detail/经纬度/租户/用户 id 等一律由后端处理，不信任前端传值
      const payload = {
        consignee: f.consignee.trim(),
        sex: Number(f.sex),
        phone: f.phone,
        provinceCode: f.provinceCode,
        provinceName: f.provinceName,
        cityCode: f.cityCode,
        cityName: f.cityName,
        districtCode: f.districtCode,
        districtName: f.districtName,
        streetName: f.streetName,
        community: f.community,
        building: f.building,
        unit: f.unit,
        floor: f.floor,
        roomNo: f.roomNo,
        label: f.label
      }
      this.saving = true
      const request = this.id
        ? http.put('/address-book', Object.assign({ id: this.id }, payload))
        : http.post('/address-book', payload)
      request.then(data => {
        if (this.selectMode) {
          // 栈：来源页 → 地址列表 → 本页，delta 2 回来源页；新建 id 取响应，编辑用当前 id
          const addressId = this.id || (data && data.id)
          uni.setStorageSync(STORAGE_KEYS.customerSelectedAddressId, String(addressId))
        }
        uni.showToast({ title: '保存成功', icon: 'success' })
        setTimeout(() => uni.navigateBack({ delta: this.selectMode ? 2 : 1 }), 400)
      }).catch(() => {}).then(() => {
        this.saving = false
      })
    },
    handleDelete() {
      uni.showModal({
        title: '删除地址',
        content: '确定删除该收货地址？',
        confirmColor: '#e64340',
        success: res => {
          if (!res.confirm) return
          // 后端为 @RequestParam List<Long> ids，必须走 query
          http.del(qs('/address-book', { ids: this.id })).then(() => {
            uni.navigateBack()
          }).catch(() => {})
        }
      })
    }
  }
}
</script>

<style>
.page {
  min-height: 100%;
  padding-bottom: 200rpx;
  background: var(--bg-page);
}

.form {
  margin-top: 24rpx;
  padding: 0 24rpx;
  background: var(--bg-card);
}

.row {
  display: flex;
  align-items: center;
  min-height: 96rpx;
  padding: 16rpx 0;
  border-bottom: 1rpx solid var(--border-light);
}

.picker {
  display: block;
}

.row__label {
  width: 160rpx;
  flex-shrink: 0;
  font-size: 28rpx;
  color: var(--color-text-primary);
}

.row__input {
  flex: 1;
  font-size: 28rpx;
  color: var(--color-text-primary);
}

.row__value {
  flex: 1;
  font-size: 28rpx;
  color: var(--color-text-primary);
}

.row__value--ph,
.row__ph {
  color: var(--color-text-placeholder);
}

.chips {
  flex: 1;
  display: flex;
  flex-wrap: wrap;
}

.chip {
  margin-right: 16rpx;
  margin-bottom: 8rpx;
  padding: 8rpx 28rpx;
  border-radius: 32rpx;
  font-size: 26rpx;
  color: var(--color-text-secondary);
  border: 1rpx solid var(--border-medium);
  background: var(--bg-card);
}

.chip--on {
  color: var(--color-primary-text);
  background: var(--color-primary-tint);
  border-color: var(--color-primary);
}

.footer {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 16rpx 24rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background: var(--bg-card);
  box-shadow: var(--shadow-bar-top);
}

.footer__save {
  height: 88rpx;
  line-height: 88rpx;
  border-radius: 44rpx;
  font-size: 32rpx;
  color: var(--color-primary-text);
  background: var(--color-primary-gradient);
}

.footer__del {
  height: 80rpx;
  line-height: 80rpx;
  margin-bottom: 16rpx;
  border-radius: 40rpx;
  font-size: 28rpx;
  color: var(--color-danger-text);
  background: var(--bg-card);
  border: 1rpx solid var(--border-medium);
}
</style>
