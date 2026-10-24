<template>
  <view class="pf">
    <!-- 头像区：点按换头像（uni.chooseImage + uni.uploadFile 走 token 通道） -->
    <view class="avatar-sec" @tap="changeAvatar">
      <image v-if="avatarSrc" class="avatar" :src="avatarSrc" mode="aspectFill" />
      <view v-else class="avatar avatar--text"><text>{{ initial }}</text></view>
      <text class="avatar__hint">{{ uploading ? '上传中…' : '点击更换头像' }}</text>
    </view>

    <!-- 表单卡片 -->
    <view class="form">
      <view class="row">
        <text class="row__label">昵称</text>
        <input
          v-model="name"
          class="row__input"
          maxlength="20"
          placeholder="请输入昵称"
          placeholder-class="row__ph"
        />
      </view>
      <view class="row">
        <text class="row__label">性别</text>
        <view class="sex">
          <text
            v-for="g in genders"
            :key="g.value"
            class="sex__opt"
            :class="{ 'sex__opt--on': sex === g.value }"
            hover-class="hover-press"
            @tap="sex = g.value"
          >{{ g.label }}</text>
        </view>
      </view>
      <view class="row">
        <text class="row__label">手机号</text>
        <text class="row__value">{{ maskedPhone }}</text>
      </view>
      <view class="row row--hint">
        <text class="row__tip">手机号为登录账号，暂不支持在小程序内修改</text>
      </view>
    </view>

    <button class="save" :class="{ 'save--off': saving || uploading }" :disabled="saving || uploading" @tap="save">
      {{ saving ? '保存中…' : '保存' }}
    </button>
  </view>
</template>

<script>
import { http, getBaseUrl, STORAGE_KEYS, CSRF_HEADER, AUTH_HEADER, fullImage, read, write } from '@xinfei/mini-shared'

// /common/upload 后端约束：jpg/jpeg/png/gif、单张 ≤5MB（CommonController.upload）
const MAX_AVATAR_SIZE = 5 * 1024 * 1024
const ALLOWED_EXT = ['jpg', 'jpeg', 'png', 'gif']

export default {
  data() {
    return {
      user: {},
      name: '',
      sex: '',          // User.sex 为 String："1"男 "0"女 ""保密
      genders: [
        { label: '男', value: '1' },
        { label: '女', value: '0' },
        { label: '保密', value: '' }
      ],
      saving: false,
      uploading: false
    }
  },
  computed: {
    avatarSrc() {
      return fullImage(this.user.avatar)
    },
    initial() {
      const name = this.user.name || this.user.phone || '?'
      return name.charAt(0)
    },
    // GET /user/info 默认已脱敏（138****8000），仅展示
    maskedPhone() {
      return this.user.phone || '未绑定'
    }
  },
  onShow() {
    this.load()
  },
  methods: {
    load() {
      http.get('/user/info', null, { silent: true }).then(user => {
        this.user = user || {}
        this.name = this.user.name || ''
        this.sex = this.user.sex === null || this.user.sex === undefined ? '' : String(this.user.sex)
      }).catch(() => {})
    },
    save() {
      if (this.saving || this.uploading) return
      const name = (this.name || '').trim()
      if (!name) {
        uni.showToast({ title: '昵称不能为空', icon: 'none' })
        return
      }
      this.saving = true
      // PUT /user/info 仅更新 name / sex / avatar 三个非敏感字段，id 由后端强制绑定会话用户
      http.put('/user/info', { name, sex: this.sex }).then(user => {
        if (user) this.user = user
        this.name = name
        this.syncCache()
        uni.showToast({ title: '资料已保存', icon: 'success' })
      }).catch(() => {}).then(() => { this.saving = false })
    },
    changeAvatar() {
      if (this.uploading || this.saving) return
      uni.chooseImage({
        count: 1,
        sizeType: ['compressed'],
        success: res => {
          const file = res.tempFiles && res.tempFiles[0]
          const path = file && (file.path || res.tempFilePaths[0])
          if (!path) return
          const v = this.validateAvatar(path, file.size)
          if (!v.ok) {
            uni.showToast({ title: v.msg, icon: 'none' })
            return
          }
          this.uploadAvatar(path)
        }
      })
    },
    validateAvatar(path, size) {
      if (size > MAX_AVATAR_SIZE) return { ok: false, msg: '图片大小不能超过 5MB' }
      const ext = path.indexOf('.') >= 0 ? path.split('.').pop().toLowerCase() : ''
      if (ALLOWED_EXT.indexOf(ext) === -1) return { ok: false, msg: '仅支持 JPG / PNG / GIF 格式' }
      return { ok: true }
    },
    uploadAvatar(filePath) {
      this.uploading = true
      // 登录态走 token 通道（5.1 已落地）；CSRF 头若已下发也一并回带（uni.uploadFile 不经过 request.js）
      const header = {}
      const token = read(STORAGE_KEYS.authToken)
      if (token) header[AUTH_HEADER] = token
      const csrf = read(STORAGE_KEYS.csrfToken)
      if (csrf) header[CSRF_HEADER] = csrf

      uni.uploadFile({
        url: getBaseUrl() + '/common/upload',
        filePath,
        name: 'file',
        formData: { bizType: 'avatar' },
        header,
        success: res => {
          let body = null
          try { body = JSON.parse(res.data) } catch (e) { /* 非 JSON 响应按失败处理 */ }
          if (res.statusCode !== 200 || !body || body.code !== 1 || !body.data) {
            uni.showToast({ title: (body && body.msg) || '上传失败，请重试', icon: 'none' })
            return
          }
          this.applyAvatar(body.data)
        },
        fail: () => {
          uni.showToast({ title: '网络不可用，上传失败', icon: 'none' })
        },
        complete: () => {
          this.uploading = false
        }
      })
    },
    // 上传成功后持久化到用户资料（与 Web 端 user.html 两步同构：upload → PUT /info）
    applyAvatar(path) {
      http.put('/user/info', { avatar: path }).then(user => {
        if (user) this.user = user
        this.syncCache()
        uni.showToast({ title: '头像已更新', icon: 'success' })
      }).catch(() => {})
    },
    // 同步本地登录态缓存，避免「我的」页还显示旧昵称/旧头像
    syncCache() {
      const cached = read(STORAGE_KEYS.currentUser)
      if (cached) {
        write(STORAGE_KEYS.currentUser, Object.assign({}, cached, {
          name: this.user.name,
          avatar: this.user.avatar
        }))
      }
    }
  }
}
</script>

<style>
.pf {
  min-height: 100%;
  padding-bottom: 64rpx;
  background: var(--bg-page);
}

/* ---------------- 头像区 ---------------- */
.avatar-sec {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 56rpx 0 40rpx;
  background: var(--color-primary-gradient);
}

.avatar {
  width: 152rpx;
  height: 152rpx;
  border-radius: var(--radius-round);
  border: 4rpx solid rgba(255, 255, 255, 0.95);
  background: var(--bg-card);
  box-shadow: 0 4rpx 16rpx rgba(190, 110, 0, 0.25);
}

.avatar--text {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 56rpx;
  font-weight: 600;
  color: var(--color-primary-text);
}

.avatar__hint {
  margin-top: 16rpx;
  font-size: 24rpx;
  color: var(--color-text-primary);
  opacity: 0.75;
}

/* ---------------- 表单 ---------------- */
.form {
  margin: 24rpx 24rpx 0;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
  box-shadow: var(--shadow-card);
  overflow: hidden;
}

.row {
  position: relative;
  display: flex;
  align-items: center;
  min-height: 104rpx;
  padding: 0 28rpx;
  box-sizing: border-box;
}

.row + .row::after {
  content: '';
  position: absolute;
  top: 0;
  left: 28rpx;
  right: 0;
  height: 1rpx;
  background: var(--border-light);
}

.row__label {
  flex-shrink: 0;
  width: 140rpx;
  font-size: 28rpx;
  color: var(--color-text-primary);
}

.row__input {
  flex: 1;
  height: 104rpx;
  font-size: 28rpx;
  color: var(--color-text-primary);
}

.row__ph {
  color: var(--color-text-placeholder);
}

.row__value {
  flex: 1;
  font-size: 28rpx;
  color: var(--color-text-secondary);
}

.row--hint {
  min-height: 0;
  padding: 4rpx 28rpx 20rpx;
}

.row__tip {
  font-size: 22rpx;
  color: var(--color-text-hint);
}

/* 性别三选 */
.sex {
  display: flex;
  flex: 1;
  gap: 16rpx;
}

.sex__opt {
  flex: 1;
  padding: 12rpx 0;
  text-align: center;
  font-size: 26rpx;
  color: var(--color-text-secondary);
  border: 1rpx solid var(--border-medium);
  border-radius: var(--radius-round);
}

.sex__opt--on {
  font-weight: 600;
  color: var(--color-primary-text);
  background: var(--color-primary-tint);
  border-color: var(--color-primary);
}

/* ---------------- 保存按钮 ---------------- */
.save {
  margin: 48rpx 24rpx 0;
  height: 92rpx;
  line-height: 92rpx;
  border-radius: var(--radius-round);
  font-size: 30rpx;
  font-weight: 600;
  color: var(--color-primary-text);
  background: var(--color-primary-gradient);
  box-shadow: var(--shadow-primary-sm);
}

.save--off {
  opacity: 0.5;
}
</style>
