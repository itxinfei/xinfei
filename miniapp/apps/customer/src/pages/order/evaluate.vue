<template>
  <view class="eval-page">
    <!-- 加载/失败/不可评价三态 -->
    <view v-if="loading" class="state"><text>加载中…</text></view>
    <view v-else-if="loadError" class="state">
      <text>加载失败</text>
      <text class="state__btn" hover-class="hover-press" @tap="bootstrap">重新加载</text>
    </view>
    <view v-else-if="notCompleted" class="state"><text>订单未完成，无法评价</text></view>
    <view v-else-if="!items.length" class="state"><text>该订单没有可评价的商品</text></view>

    <template v-else>
      <!-- 菜品评价：每道菜一条（同菜不同风味合并，后端按 orderId+dishId 去重） -->
      <view class="card">
        <text class="card__title">商品评价</text>
        <view v-for="item in items" :key="item.key" class="dish">
          <view class="dish__head">
            <text class="dish__name">{{ item.name }}</text>
            <text class="dish__qty">×{{ item.qty }}</text>
          </view>
          <text v-if="item.flavors.length" class="dish__flavor">{{ item.flavors.join(' / ') }}</text>

          <!-- 已评：只读回显 -->
          <view v-if="item.submitted" class="done">
            <view class="stars stars--readonly">
              <text v-for="n in 5" :key="n" class="star" :class="{ 'star--on': n <= item.star }">★</text>
            </view>
            <text v-if="item.content" class="done__text">{{ item.content }}</text>
            <view v-if="item.doneImages.length" class="imgs">
              <image
                v-for="(img, ii) in item.doneImages"
                :key="ii"
                class="imgs__item"
                :src="fullImage(img)"
                mode="aspectFill"
                @tap="previewImage(item.doneImages, ii)"
              />
            </view>
            <text class="done__tag">{{ item.auditText }}</text>
          </view>

          <!-- 未评 -->
          <template v-else>
            <view class="stars">
              <text
                v-for="n in 5"
                :key="n"
                class="star"
                :class="{ 'star--on': n <= item.star }"
                hover-class="hover-press"
                @tap="setStar(item, n)"
              >★</text>
              <text class="stars__required">* 必评</text>
            </view>
            <view class="chips">
              <text
                v-for="tag in dishTags"
                :key="tag"
                class="chip"
                :class="{ 'chip--on': item.tags.indexOf(tag) !== -1 }"
                hover-class="hover-press"
                @tap="toggleTag(item, tag)"
              >{{ tag }}</text>
            </view>
            <textarea
              v-model="item.text"
              class="dish__input"
              maxlength="200"
              placeholder="说说这道菜怎么样（选填，200 字以内）"
              placeholder-class="dish__ph"
            />
            <!-- 评价传图（每道菜最多3张，与 Web 版 my-evaluations.html 口径一致）：先传 /common/upload 拿相对路径，提交时随评价写入 images -->
            <view class="imgs">
              <view v-for="(img, ii) in item.images" :key="ii" class="imgs__cell">
                <image class="imgs__item" :src="img.local" mode="aspectFill" @tap="previewImage(item.images.map(x => x.local), ii)" />
                <text v-if="img.uploading" class="imgs__mask">上传中</text>
                <text v-else-if="img.failed" class="imgs__mask imgs__mask--fail">失败</text>
                <text class="imgs__del" hover-class="hover-press" @tap.stop="removeImage(item, ii)">×</text>
              </view>
              <view
                v-if="item.images.length < MAX_EVAL_IMAGES"
                class="imgs__add"
                :class="{ 'imgs__add--off': uploadingKey }"
                hover-class="hover-press"
                @tap="chooseImages(item)"
              >＋</view>
            </view>
            <text v-if="item.error" class="dish__error">{{ item.error }}</text>
          </template>
        </view>

        <!-- 整单一个匿名开关，作用于全部菜品评价（与 H5 一致） -->
        <view class="anon">
          <text class="anon__label">匿名评价</text>
          <!-- 微信 switch 的 color 只接受 hexColor、不支持 CSS 变量，此处显式写品牌金 -->
          <switch :checked="anonymous" color="#ffc200" @change="onAnonymousChange" />
        </view>
      </view>

      <!-- 骑手评价：仅自营骑手配送单（order.riderId 非空） -->
      <view v-if="order.riderId" class="card">
        <text class="card__title">骑手评价</text>
        <view v-if="riderEval" class="done">
          <view class="stars stars--readonly">
            <text v-for="n in 5" :key="n" class="star" :class="{ 'star--on': n <= riderEval.starRating }">★</text>
          </view>
          <view v-if="riderTags.length" class="chips chips--readonly">
            <text v-for="tag in riderTags" :key="tag" class="chip chip--on">{{ tag }}</text>
          </view>
          <text v-if="riderEval.content" class="done__text">{{ riderEval.content }}</text>
          <text class="done__tag">已评价</text>
        </view>
        <template v-else>
          <view class="stars">
            <text
              v-for="n in 5"
              :key="n"
              class="star"
              :class="{ 'star--on': n <= rider.star }"
              hover-class="hover-press"
              @tap="rider.star = n"
            >★</text>
          </view>
          <view class="chips">
            <text
              v-for="tag in riderPresetTags"
              :key="tag"
              class="chip"
              :class="{ 'chip--on': rider.tags.indexOf(tag) !== -1 }"
              hover-class="hover-press"
              @tap="toggleRiderTag(tag)"
            >{{ tag }}</text>
          </view>
          <textarea
            v-model="rider.content"
            class="dish__input"
            maxlength="200"
            placeholder="骑手服务怎么样（选填，200 字以内）"
            placeholder-class="dish__ph"
          />
          <view class="anon">
            <text class="anon__label">匿名评价</text>
            <!-- 微信 switch 的 color 只接受 hexColor、不支持 CSS 变量，此处显式写品牌金 -->
            <switch :checked="rider.anonymous" color="#ffc200" @change="onRiderAnonymousChange" />
          </view>
          <text
            class="rider-submit"
            :class="{ 'rider-submit--off': busy('rider') || riderDone }"
            hover-class="hover-press"
            @tap="submitRider"
          >{{ busy('rider') ? '提交中…' : '提交骑手评价' }}</text>
        </template>
      </view>

      <view class="bar-placeholder"></view>

      <!-- 菜品批量提交：逐菜 POST（后端无批量端点），成功一项转只读一项 -->
      <view v-if="pendingItems.length" class="bar">
        <text
          class="bar__submit"
          :class="{ 'bar__submit--off': busy('dish') }"
          hover-class="hover-press"
          @tap="submitDishes"
        >{{ busy('dish') ? '提交中…' : '提交菜品评价（' + pendingItems.length + '）' }}</text>
      </view>
    </template>
  </view>
</template>

<script>
import { http, qs, busyLock, uploadFile, fullImage } from '@xinfei/mini-shared'

// 快捷标签后端无字典接口，与 H5 同口径写死；菜品标签随 content 落库（dish_evaluation 无 tags 列）
const DISH_TAGS = ['味道好', '分量足', '配送快', '包装好', '食材新鲜', '性价比高']
const RIDER_TAGS = ['准时送达', '态度友好', '包装完好', '骑行平稳', '沟通顺畅']

// 评价传图：每道菜最多 3 张（Web 版 my-evaluations.html van-uploader :max-count="3"）；
// /common/upload 后端约束：jpg/jpeg/png/gif、单张 ≤5MB（CommonController.upload）
const MAX_EVAL_IMAGES = 3
const MAX_IMG_SIZE = 5 * 1024 * 1024
const ALLOWED_IMG_EXT = ['jpg', 'jpeg', 'png', 'gif']

export default {
  data() {
    return Object.assign(busyLock(), {
      orderId: '',
      order: {},
      items: [],
      dishTags: DISH_TAGS,
      riderPresetTags: RIDER_TAGS,
      riderTags: [],
      anonymous: false,
      rider: { star: 5, tags: [], content: '', anonymous: false },
      riderEval: null,
      riderDone: false,
      loading: true,
      loadError: false,
      notCompleted: false,
      // 正在上传的评价图片所属菜品 key（同一时间只允许一处选图/上传，避免 busyLock key 爆炸）
      uploadingKey: '',
      MAX_EVAL_IMAGES
    })
  },
  computed: {
    pendingItems() {
      return this.items.filter(i => !i.submitted)
    }
  },
  onLoad(query) {
    this.orderId = query && query.id ? String(query.id) : ''
    this.bootstrap()
  },
  methods: {
    fullImage,
    bootstrap() {
      if (!this.orderId) {
        this.loading = false
        this.notCompleted = true
        return
      }
      this.loading = true
      this.loadError = false
      http.get('/order/' + encodeURIComponent(this.orderId), null, { silent: true }).then(order => {
        this.order = order || {}
        if (Number(this.order.status) !== 4) {
          this.notCompleted = true
          this.loading = false
          return
        }
        this.buildItems()
        // 两个评价查询互不依赖，并发；各自失败只影响回显，不阻塞提交（提交页面对错误文案兜底）
        return Promise.all([this.loadDishEvals(), this.order.riderId ? this.loadRiderEval() : null])
      }).catch(err => {
        if (err && err.notLogin) return
        this.loadError = true
      }).then(() => {
        this.loading = false
      })
    },
    // orderDetails 同菜多风味可能多行：按商品 key 合并数量/风味，否则第二行会被后端重复校验拒掉
    buildItems() {
      const grouped = {}
      ;(this.order.orderDetails || []).forEach(d => {
        const key = d.dishId ? 'd_' + d.dishId : 's_' + d.setmealId
        if (!grouped[key]) {
          grouped[key] = {
            key,
            dishId: d.dishId || null,
            setmealId: d.setmealId || null,
            name: d.name,
            qty: 0,
            flavors: [],
            star: 0,
            tags: [],
            text: '',
            images: [],        // 待提交：[{ local, path(上传后), uploading, failed }]
            doneImages: [],    // 已评回显：服务器相对路径数组
            error: '',
            submitted: false,
            content: '',
            auditText: '已评价'
          }
        }
        const item = grouped[key]
        item.qty += Number(d.number || 0)
        if (d.dishFlavor && item.flavors.indexOf(d.dishFlavor) === -1) item.flavors.push(d.dishFlavor)
      })
      this.items = Object.keys(grouped).map(k => grouped[k])
    },
    loadDishEvals() {
      return http.get('/api/dish-evaluation/order/' + encodeURIComponent(this.orderId), null, { silent: true })
        .then(list => this.markSubmitted(list || [])).catch(() => {})
    },
    markSubmitted(evals) {
      evals.forEach(ev => {
        const key = ev.dishId ? 'd_' + ev.dishId : 's_' + ev.setmealId
        const item = this.items.find(i => i.key === key)
        if (!item) return
        item.submitted = true
        item.star = Number(ev.starRating || 0)
        item.content = ev.content || ''
        item.doneImages = this.parseImages(ev.images)
        item.auditText = Number(ev.status) === 0 ? '审核中' : (Number(ev.status) === 2 ? '未通过' : '已评价')
      })
    },
    loadRiderEval() {
      return http.get(qs('/api/rider-evaluation/order/' + encodeURIComponent(this.orderId), { riderId: this.order.riderId }), null, { silent: true })
        .then(ev => {
          this.riderEval = ev || null
          if (ev) {
            this.riderTags = this.parseTags(ev.tags)
            this.riderDone = true
          }
        }).catch(() => {})
    },
    parseTags(raw) {
      if (!raw) return []
      try {
        const arr = JSON.parse(raw)
        return Array.isArray(arr) ? arr : []
      } catch (e) {
        return []
      }
    },
    setStar(item, n) {
      item.star = n
      item.error = ''
    },
    toggleTag(item, tag) {
      const idx = item.tags.indexOf(tag)
      if (idx === -1) item.tags.push(tag)
      else item.tags.splice(idx, 1)
    },
    toggleRiderTag(tag) {
      const idx = this.rider.tags.indexOf(tag)
      if (idx === -1) this.rider.tags.push(tag)
      else this.rider.tags.splice(idx, 1)
    },
    onAnonymousChange(e) {
      this.anonymous = !!(e.detail && e.detail.value)
    },
    onRiderAnonymousChange(e) {
      this.rider.anonymous = !!(e.detail && e.detail.value)
    },
    // ---------- 评价图片 ----------
    // images 字段为 JSON 数组字符串（DishEvaluation.images 为 String 列，与 Web 版口径一致）
    parseImages(raw) {
      if (!raw) return []
      if (Array.isArray(raw)) return raw
      try {
        const arr = JSON.parse(raw)
        return Array.isArray(arr) ? arr : [raw]
      } catch (e) {
        return [raw]
      }
    },
    previewImage(urls, index) {
      if (!urls || !urls.length) return
      uni.previewImage({ urls: urls.map(u => fullImage(u)), current: fullImage(urls[index || 0]) })
    },
    chooseImages(item) {
      if (this.uploadingKey) return
      const remain = MAX_EVAL_IMAGES - item.images.length
      if (remain <= 0) return
      uni.chooseImage({
        count: remain,
        sizeType: ['compressed'],
        success: res => {
          const paths = res.tempFilePaths || []
          const files = res.tempFiles || []
          let bad = ''
          for (let i = 0; i < paths.length; i++) {
            const size = files[i] && files[i].size
            const ext = paths[i].indexOf('.') >= 0 ? paths[i].split('.').pop().toLowerCase() : ''
            if (size > MAX_IMG_SIZE) { bad = '图片大小不能超过 5MB'; break }
            if (ALLOWED_IMG_EXT.indexOf(ext) === -1) { bad = '仅支持 JPG / PNG / GIF 格式'; break }
          }
          if (bad) {
            uni.showToast({ title: bad, icon: 'none' })
            return
          }
          if (!paths.length) return
          this.uploadImages(item, paths)
        }
      })
    },
    uploadImages(item, paths) {
      // 顺序上传（uni.uploadFile 无并发必要，逐张补 path），全部结束后释放 uploadingKey
      this.uploadingKey = item.key
      const chain = paths.reduce((promise, path) => {
        return promise.then(() => {
          const entry = { local: path, path: '', uploading: true, failed: false }
          item.images.push(entry)
          return uploadFile({ filePath: path, bizType: 'evaluation' })
            .then(remote => {
              entry.path = remote
              entry.uploading = false
            })
            .catch(err => {
              if (err && err.notLogin) throw err
              entry.uploading = false
              entry.failed = true
            })
        })
      }, Promise.resolve())
      chain.catch(() => {}).then(() => {
        // 上传失败的留在本地可删；未失败的参与提交
        this.uploadingKey = ''
      })
    },
    removeImage(item, index) {
      item.images.splice(index, 1)
    },
    // 标签与文字组合成 content 落库（对齐 H5「标签入 content」口径；后端 content 上限 500，文本限 200 不会超）
    composeContent(item) {
      const text = (item.text || '').trim()
      const label = item.tags.join('，')
      if (label && text) return label + '。' + text
      return label || text
    },
    submitDishes() {
      const key = 'dish'
      if (this.busy(key)) return
      // 先全量校验星级：任一未评星则内联标错并中止，一条都不发
      let missing = false
      this.pendingItems.forEach(item => {
        if (!item.star) {
          item.error = '请为这道菜品评分'
          missing = true
        } else {
          item.error = ''
        }
      })
      if (missing) {
        uni.showToast({ title: '还有商品未评分', icon: 'none' })
        return
      }
      // 有图片还在上传时禁止提交（与 Web 版"图片还在上传，请稍候"同口径）
      if (this.uploadingKey) {
        uni.showToast({ title: '图片还在上传，请稍候', icon: 'none' })
        return
      }
      if (this.pendingItems.some(item => item.images.some(x => x.failed))) {
        uni.showToast({ title: '有图片上传失败，请删除后重试', icon: 'none' })
        return
      }
      this.setBusy(key, true)
      const targets = this.pendingItems.slice()
      const failed = []
      // 顺序逐条提交：后端无批量端点；成功一条转只读，失败的保留可重试
      const chain = targets.reduce((promise, item) => {
        return promise.then(() => {
          const uploaded = item.images.filter(x => x.path && !x.failed).map(x => x.path)
          return http.post('/api/dish-evaluation', {
            orderId: this.order.id,
            dishId: item.dishId,
            setmealId: item.setmealId,
            dishName: item.name,
            starRating: item.star,
            content: this.composeContent(item),
            // images 后端为 String（存 JSON 数组），与 Web 版 my-evaluations.html 口径一致
            images: uploaded.length ? JSON.stringify(uploaded) : null,
            anonymous: this.anonymous ? 1 : 0
          }, { silent: true }).then(() => {
            item.submitted = true
            item.content = this.composeContent(item)
            item.doneImages = uploaded
            item.auditText = '审核中'
          }).catch(() => {
            failed.push(item.name)
          })
        })
      }, Promise.resolve())
      chain.then(() => {
        if (!failed.length) {
          uni.showToast({ title: '评价提交成功，感谢您的反馈', icon: 'none' })
        } else {
          uni.showToast({ title: '「' + failed.join('、') + '」提交失败，请重试', icon: 'none', duration: 2500 })
        }
      }).finally(() => { this.setBusy(key, false) })
    },
    submitRider() {
      const key = 'rider'
      if (this.busy(key)) return
      this.setBusy(key, true)
      http.post('/api/rider-evaluation', {
        orderId: this.order.id,
        riderId: this.order.riderId,
        starRating: this.rider.star,
        content: (this.rider.content || '').trim(),
        tags: JSON.stringify(this.rider.tags),
        anonymous: this.rider.anonymous ? 1 : 0
      }, { silent: true }).then(ev => {
        this.riderEval = ev || {
          starRating: this.rider.star,
          content: (this.rider.content || '').trim(),
          tags: JSON.stringify(this.rider.tags)
        }
        this.riderTags = this.rider.tags.slice()
        this.riderDone = true
        uni.showToast({ title: '评价成功', icon: 'success' })
      }).catch(() => {}).then(() => { this.setBusy(key, false) })
    }
  }
}
</script>

<style>
.eval-page {
  min-height: 100%;
  padding: 8rpx 0 40rpx;
  background: var(--bg-page);
}

.state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 160rpx 0;
  font-size: 26rpx;
  color: var(--color-text-hint);
}

.state__btn {
  margin-top: 20rpx;
  padding: 12rpx 48rpx;
  border-radius: 32rpx;
  font-size: 26rpx;
  color: var(--color-primary-text);
  background: var(--color-primary);
}

.card {
  margin: 16rpx 24rpx;
  padding: 24rpx;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
  box-shadow: var(--shadow-card);
}

.card__title {
  display: block;
  margin-bottom: 16rpx;
  font-size: 30rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

.dish {
  padding: 20rpx 0;
  border-top: 1rpx solid var(--border-light);
}

.dish:first-of-type {
  border-top: none;
}

.dish__head {
  display: flex;
  align-items: center;
}

.dish__name {
  flex: 1;
  font-size: 28rpx;
  color: var(--color-text-primary);
}

.dish__qty {
  font-size: 26rpx;
  color: var(--color-text-hint);
}

.dish__flavor {
  display: block;
  margin-top: 6rpx;
  font-size: 24rpx;
  color: var(--color-text-hint);
}

.stars {
  display: flex;
  align-items: center;
  margin-top: 14rpx;
}

.star {
  margin-right: 12rpx;
  font-size: 44rpx;
  line-height: 1;
  color: var(--border-medium);
}

.star--on {
  color: var(--color-primary);
}

.stars__required {
  margin-left: 8rpx;
  font-size: 22rpx;
  color: var(--color-danger);
}

.stars--readonly .star {
  margin-right: 8rpx;
  font-size: 36rpx;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  margin-top: 14rpx;
}

.chip {
  margin: 0 14rpx 14rpx 0;
  padding: 8rpx 22rpx;
  border-radius: var(--radius-round);
  font-size: 24rpx;
  color: var(--color-text-secondary);
  background: var(--bg-input);
}

.chip--on {
  color: var(--color-primary-text);
  background: var(--color-primary-tint);
}

.chips--readonly {
  margin-top: 10rpx;
}

.dish__input {
  width: 100%;
  height: 130rpx;
  padding: 16rpx;
  box-sizing: border-box;
  border-radius: var(--radius-md);
  background: var(--bg-input);
  font-size: 26rpx;
}

.dish__ph {
  color: var(--color-text-disabled);
}

.dish__error {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: var(--color-danger);
}

/* ---- 评价图片 ---- */
.imgs {
  display: flex;
  flex-wrap: wrap;
  margin-top: 14rpx;
}

.imgs__cell {
  position: relative;
  margin: 0 14rpx 14rpx 0;
}

.imgs__item {
  width: 152rpx;
  height: 152rpx;
  border-radius: var(--radius-md);
  background: var(--bg-input);
}

.imgs__mask {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-md);
  font-size: 22rpx;
  color: var(--color-primary-text);
  background: rgba(0, 0, 0, 0.45);
}

.imgs__mask--fail {
  color: var(--color-danger-text);
}

.imgs__del {
  position: absolute;
  top: -14rpx;
  right: -14rpx;
  width: 40rpx;
  height: 40rpx;
  line-height: 38rpx;
  text-align: center;
  border-radius: var(--radius-round);
  font-size: 28rpx;
  color: var(--color-primary-text);
  background: var(--color-danger);
}

.imgs__add {
  width: 152rpx;
  height: 152rpx;
  line-height: 148rpx;
  text-align: center;
  border: 1rpx dashed var(--border-medium);
  border-radius: var(--radius-md);
  font-size: 48rpx;
  color: var(--color-text-hint);
  background: var(--bg-input);
  box-sizing: border-box;
}

.imgs__add--off {
  opacity: 0.5;
}

.done {
  margin-top: 12rpx;
}

.done__text {
  display: block;
  margin-top: 10rpx;
  font-size: 26rpx;
  line-height: 38rpx;
  color: var(--color-text-primary);
}

.done__tag {
  display: inline-block;
  margin-top: 10rpx;
  padding: 2rpx 14rpx;
  border-radius: var(--radius-sm);
  font-size: 20rpx;
  color: var(--color-text-secondary);
  background: var(--bg-input);
}

.anon {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid var(--border-light);
}

.anon__label {
  font-size: 26rpx;
  color: var(--color-text-secondary);
}

.rider-submit {
  display: block;
  margin-top: 20rpx;
  height: 80rpx;
  line-height: 80rpx;
  text-align: center;
  border-radius: 40rpx;
  font-size: 28rpx;
  color: var(--color-primary-text);
  background: var(--color-primary);
}

.rider-submit--off {
  opacity: 0.5;
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
  padding: 16rpx 24rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background: var(--bg-card);
  box-shadow: var(--shadow-bar-top);
}

.bar__submit {
  display: block;
  height: 84rpx;
  line-height: 84rpx;
  text-align: center;
  border-radius: 42rpx;
  font-size: 30rpx;
  color: var(--color-primary-text);
  background: var(--color-primary);
}

.bar__submit--off {
  opacity: 0.5;
}
</style>
