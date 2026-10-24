<template>
  <view class="cs">
    <!-- 顶部视图切换（Web 版在导航栏图标切换，小程序改双 tab） -->
    <view class="tabs">
      <view class="tab" :class="{ 'tab--on': view === 'chat' }" @tap="switchView('chat')">在线客服</view>
      <view class="tab" :class="{ 'tab--on': view === 'complaint' }" @tap="switchView('complaint')">投诉与工单</view>
    </view>

    <!-- ================= 聊天视图 ================= -->
    <block v-if="view === 'chat'">
      <scroll-view class="chat" scroll-y :scroll-into-view="chatAnchor" scroll-with-animation>
        <view class="chat__tip">
          <text>留言后客服会尽快回复，繁忙时可能需要稍等</text>
        </view>

        <!-- 无消息时的快捷问题（点击直接发送） -->
        <view v-if="!messages.length && !loading && !initError" class="chips">
          <view v-for="q in quickQuestions" :key="q" class="chip" hover-class="hover-press" @tap="quickSend(q)">{{ q }}</view>
        </view>

        <view v-if="loading" class="chat__loading"><text>加载中…</text></view>
        <view v-else-if="initError" class="chat__loading">
          <text>客服加载失败</text>
          <text class="chat__retry" @tap="initChat">点击重试</text>
        </view>

        <view v-for="(m, index) in messages" :key="m.id || index">
          <view v-if="msgTimeDivider(index)" class="msg-time">{{ msgTimeDivider(index) }}</view>
          <!-- 系统消息居中 -->
          <view v-if="m.senderType === 3" class="msg-sys"><text>{{ m.content }}</text></view>
          <!-- 客服消息居左 -->
          <view v-else-if="m.senderType === 2" class="msg msg--agent">
            <view class="avatar avatar--agent"><text class="avatar__glyph">客</text></view>
            <view class="bubble bubble--agent">
              <image
                v-if="m.messageType === 2 && m.imageUrl"
                class="bubble__img"
                :src="imgCache[m.imageUrl] || ''"
                mode="aspectFill"
                @tap="previewImage(imgCache[m.imageUrl])"
              />
              <text v-else>{{ m.content }}</text>
            </view>
          </view>
          <!-- 用户消息居右（文本 / 图片） -->
          <view v-else class="msg msg--user">
            <view class="bubble bubble--user">
              <image
                v-if="m.messageType === 2 && m.imageUrl"
                class="bubble__img"
                :src="imgCache[m.imageUrl] || ''"
                mode="aspectFill"
                @tap="previewImage(imgCache[m.imageUrl])"
              />
              <text v-else>{{ m.content }}</text>
            </view>
          </view>
        </view>

        <view v-if="sessionClosed" class="session-end">
          <text>— 会话已结束 —</text>
          <text class="session-end__btn" hover-class="hover-press" @tap="restartSession">重新咨询</text>
        </view>
        <view id="msg-bottom" class="chat__bottom"></view>
      </scroll-view>

      <!-- 输入栏 -->
      <view v-if="!sessionClosed" class="input-bar">
        <text
          class="input-bar__img"
          :class="{ 'input-bar__img--off': sendingImage }"
          hover-class="hover-press"
          @tap="sendImage"
        >图</text>
        <input
          v-model="draft"
          class="input-bar__input"
          maxlength="500"
          confirm-type="send"
          :cursor-spacing="20"
          :adjust-position="true"
          placeholder="请输入您的问题"
          placeholder-class="input-bar__ph"
          @confirm="sendText"
        />
        <text
          class="input-bar__send"
          :class="{ 'input-bar__send--on': canSend && !sending }"
          hover-class="hover-press"
          @tap="sendText"
        >发送</text>
      </view>
      <view v-else class="input-bar input-bar--closed">
        <text>会话已结束，点上方「重新咨询」发起新会话</text>
      </view>
    </block>

    <!-- ================= 投诉视图 ================= -->
    <block v-else>
      <scroll-view class="complaint" scroll-y>
        <view class="card">
          <view class="card__row" hover-class="hover-press" @tap="openMyComplaints">
            <text class="card__row-text">我的投诉记录</text>
            <text class="card__row-arrow">›</text>
          </view>
        </view>

        <view class="card">
          <view class="section-title">投诉类型</view>
          <view class="type-grid">
            <view
              v-for="t in complaintTypes"
              :key="t.v"
              class="type-cell"
              :class="{ 'type-cell--on': form.complaintType === t.v }"
              hover-class="hover-press"
              @tap="form.complaintType = t.v"
            >
              <text class="type-cell__icon">{{ t.glyph }}</text>
              <text class="type-cell__label">{{ t.label }}</text>
            </view>
          </view>
        </view>

        <view class="card">
          <view class="section-title">关联订单（选填）</view>
          <view class="card__row" hover-class="hover-press" @tap="openOrderPick">
            <text class="card__row-text" :class="{ 'card__row-text--ph': !form.orderNumber }">
              {{ form.orderNumber || '选择相关订单，便于更快核实处理' }}
            </text>
            <text v-if="form.orderId" class="card__row-clear" hover-class="hover-press" @tap.stop="clearOrder">×</text>
            <text v-else class="card__row-arrow">›</text>
          </view>
        </view>

        <view class="card">
          <view class="section-title">投诉标题</view>
          <input
            v-model="form.title"
            class="card__input"
            maxlength="200"
            placeholder="一句话概括遇到的问题"
            placeholder-class="card__ph"
          />
        </view>

        <view class="card">
          <view class="section-title">详细描述</view>
          <textarea
            v-model="form.content"
            class="card__textarea"
            maxlength="1000"
            :cursor-spacing="20"
            placeholder="请描述问题发生的时间、经过等（至少5个字）"
            placeholder-class="card__ph"
          />
          <text class="card__count">{{ (form.content || '').length }}/1000</text>
        </view>

        <view class="card">
          <view class="section-title">图片凭证（选填，最多 3 张）</view>
          <view class="imgs">
            <view v-for="(img, ii) in complaintImages" :key="ii" class="imgs__cell">
              <image class="imgs__item" :src="img.local" mode="aspectFill" @tap="previewImage(img.local)" />
              <text v-if="img.uploading" class="imgs__mask">上传中</text>
              <text v-else-if="img.failed" class="imgs__mask imgs__mask--fail">失败</text>
              <text class="imgs__del" hover-class="hover-press" @tap.stop="complaintImages.splice(ii, 1)">×</text>
            </view>
            <view
              v-if="complaintImages.length < 3"
              class="imgs__add"
              hover-class="hover-press"
              @tap="chooseComplaintImage"
            >＋</view>
          </view>
        </view>

        <view class="card">
          <text class="btn btn--main btn--block" :class="{ 'btn--off': submitting }" hover-class="hover-press" @tap="submitComplaint">
            {{ submitting ? '提交中…' : '提交投诉' }}
          </text>
          <view class="tipnote">我们会尽快核实并通过客服或电话与您联系。</view>
        </view>
      </scroll-view>
    </block>

    <!-- 关联订单选择（底部弹层） -->
    <view v-if="orderPickShow" class="popup-mask" @tap="orderPickShow = false">
      <view class="popup" @tap.stop="">
        <text class="popup__title">选择关联订单</text>
        <scroll-view class="popup__scroll" scroll-y>
          <view v-for="o in recentOrders" :key="o.id" class="popup__item" hover-class="hover-press" @tap="pickOrder(o)">
            <text class="popup__item-main">{{ o.number }}</text>
            <text class="popup__item-sub">{{ fmtTime(o.orderTime) }}</text>
          </view>
          <view v-if="!recentOrders.length && !ordersLoading" class="popup__empty"><text>暂无历史订单</text></view>
          <view v-if="ordersLoading" class="popup__empty"><text>加载中…</text></view>
        </scroll-view>
      </view>
    </view>

    <!-- 我的投诉记录（底部弹层） -->
    <view v-if="complaintListShow" class="popup-mask" @tap="complaintListShow = false">
      <view class="popup" @tap.stop="">
        <text class="popup__title">我的投诉记录</text>
        <scroll-view class="popup__scroll" scroll-y>
          <view v-if="complaintListLoading" class="popup__empty"><text>加载中…</text></view>
          <block v-else>
            <view v-if="!myComplaints.length" class="popup__empty"><text>暂无投诉记录</text></view>
            <view v-for="c in myComplaints" :key="c.id" class="crec">
              <view class="crec__top">
                <text class="crec__tag" :class="'crec__tag--s' + c.status">{{ complaintStatusText(c.status) }}</text>
                <text class="crec__type">{{ complaintTypeText(c.complaintType) }}</text>
              </view>
              <text class="crec__title">{{ c.title }}</text>
              <text class="crec__content">{{ c.content }}</text>
              <text class="crec__time">{{ c.complaintNo ? c.complaintNo + ' · ' : '' }}{{ fmtTime(c.createTime) }}</text>
              <view v-if="c.status >= 2 && c.handleResult" class="crec__result">
                <text class="crec__result-label">处理结果</text>
                <text class="crec__result-text">{{ c.handleResult }}</text>
                <text v-if="c.compensationAmount > 0" class="crec__result-comp">
                  赔偿 ¥{{ fmtAmount(c.compensationAmount) }}
                </text>
              </view>
              <view v-if="c.satisfaction" class="crec__rated">
                <text>我的评价 {{ starText(c.satisfaction) }}</text>
                <text v-if="c.userFeedback" class="crec__rated-fb">{{ c.userFeedback }}</text>
              </view>
              <!-- 已处理/已关闭且尚未评价 -->
              <text
                v-else-if="c.status === 2 || c.status === 3"
                class="crec__rate-btn"
                hover-class="hover-press"
                @tap="openRate(c)"
              >评价处理结果</text>
            </view>
          </block>
        </scroll-view>
      </view>
    </view>

    <!-- 满意度评价（底部弹层） -->
    <view v-if="rateShow" class="popup-mask" @tap="rateShow = false">
      <view class="popup" @tap.stop="">
        <text class="popup__title">评价处理结果</text>
        <view class="rate-stars">
          <text
            v-for="n in 5"
            :key="n"
            class="rate-star"
            :class="{ 'rate-star--on': n <= rateScore }"
            @tap="rateScore = n"
          >★</text>
        </view>
        <text class="rate-hint">{{ rateHint(rateScore) }}</text>
        <textarea
          v-model="rateFeedback"
          class="card__textarea"
          maxlength="200"
          :cursor-spacing="20"
          placeholder="补充您的看法（选填）"
          placeholder-class="card__ph"
        />
        <text class="btn btn--main btn--block" :class="{ 'btn--off': rating || rateScore === 0 }" hover-class="hover-press" @tap="submitRate">
          {{ rating ? '提交中…' : '提交评价' }}
        </text>
      </view>
    </view>
  </view>
</template>

<script>
import { http, qs, fullImage, fmtAmount, busyLock, uploadFile, AUTH_HEADER, STORAGE_KEYS, read } from '@xinfei/mini-shared'

const QUICK_QUESTIONS = ['我的订单还要多久送到？', '可以修改收货地址吗？', '餐品少送/错送怎么办？', '如何申请退款？']

// /common/upload 后端约束：jpg/jpeg/png/gif、单张 ≤5MB（CommonController.upload）
const MAX_IMG_SIZE = 5 * 1024 * 1024
const ALLOWED_IMG_EXT = ['jpg', 'jpeg', 'png', 'gif']

function pad2(n) {
  return n < 10 ? '0' + n : '' + n
}

// 兼容 LocalDateTime 字符串 / Jackson 数组 [y,m,d,h,m,s]
function parseDate(t) {
  if (t == null || t === '') return null
  if (Array.isArray(t) && t.length >= 3) return new Date(t[0], t[1] - 1, t[2], t[3] || 0, t[4] || 0, t[5] || 0)
  const d = new Date(String(t).replace(/-/g, '/').replace('T', ' '))
  return isNaN(d.getTime()) ? null : d
}

export default {
  data() {
    return Object.assign(busyLock(), {
      view: 'chat',
      quickQuestions: QUICK_QUESTIONS,
      // ---- 会话与消息 ----
      loading: false,
      initError: false,
      activeSession: null,
      presetOrderId: '',
      messages: [],
      draft: '',
      sending: false,
      sendingImage: false,
      chatAnchor: '',
      // 消息图片缓存：chat 业务图落 private 目录，<image> 无法带 X-Auth-Token 头，
      // 须先 uni.downloadFile 带头下载成本地路径再展示/预览（键=服务器相对路径，值=本地临时路径）
      imgCache: {},
      imgFetching: {},
      // ---- 投诉表单 ----
      form: { complaintType: 1, title: '', content: '', orderId: null, orderNumber: '' },
      complaintImages: [],   // [{ local, path(上传后), uploading, failed }]，最多 3 张（与 Web 版口径一致）
      submitting: false,
      complaintTypes: [
        { v: 1, label: '食品质量', glyph: '食' },
        { v: 2, label: '配送问题', glyph: '配' },
        { v: 3, label: '服务态度', glyph: '态' },
        { v: 4, label: '价格问题', glyph: '价' },
        { v: 5, label: '其他', glyph: '他' }
      ],
      // ---- 关联订单选择 ----
      orderPickShow: false,
      recentOrders: [],
      ordersLoading: false,
      ordersLoaded: false,
      // ---- 我的投诉记录 ----
      complaintListShow: false,
      myComplaints: [],
      complaintListLoading: false,
      // ---- 满意度评价 ----
      rateShow: false,
      ratingComplaint: null,
      rateScore: 0,
      rateFeedback: '',
      rating: false
    })
  },
  computed: {
    canSend() {
      return (this.draft || '').trim().length > 0 && !!this.activeSession
    },
    // 与后台对齐：0=待分配 1=进行中 2/3=已结束（旧数据存在 status=3 脏会话，>=2 一律按已结束）
    sessionClosed() {
      return !!this.activeSession && Number(this.activeSession.status) >= 2
    }
  },
  onLoad(query) {
    // 从订单详情带入 orderId：优先复用该订单的进行中会话，否则创建订单类型会话
    this.presetOrderId = query && query.orderId ? String(query.orderId) : ''
    this.initChat()
  },
  onHide() {
    this.stopPolling()
  },
  onShow() {
    // 回前台续轮询（会话已结束/在投诉视图则不起）
    if (this.view === 'chat' && this.activeSession && !this.sessionClosed) {
      this.pollMessages()
      this.startPolling()
    }
  },
  onUnload() {
    this.stopPolling()
  },
  methods: {
    fullImage,
    fmtAmount,
    fmtTime(t) {
      if (t == null || t === '') return ''
      if (Array.isArray(t)) {
        return t[0] + '-' + pad2(t[1]) + '-' + pad2(t[2]) + ' ' + pad2(t[3] || 0) + ':' + pad2(t[4] || 0)
      }
      return String(t).replace('T', ' ').slice(0, 16)
    },
    previewImage(url) {
      // 本地临时路径（wxfile:// 等）不经 fullImage 拼 baseURL，远端相对路径才拼
      if (!url) return
      const isLocal = /^(https?:)?\/\//.test(url) || url.indexOf('wxfile://') === 0 || url.indexOf('file://') === 0 || url.indexOf('data:') === 0
      uni.previewImage({ urls: [isLocal ? url : fullImage(url)] })
    },

    // ---------- 会话初始化 ----------
    initChat() {
      this.loading = true
      this.initError = false
      // 只有「待分配(0) / 进行中(1)」的会话可以接着聊；已结束(2/3)一律忽略，否则会被钉在死会话里
      const resumable = s => [0, 1].indexOf(Number(s.status)) !== -1
      http.get('/cs/portal/session/list', null, { silent: true }).then(list => {
        const sessions = (Array.isArray(list) ? list : []).filter(resumable)
        let active = null
        if (this.presetOrderId) {
          active = sessions.find(s => s.orderId != null && String(s.orderId) === this.presetOrderId) || null
          if (!active) active = sessions[0] || null
        } else {
          active = sessions[0] || null
        }
        if (active) return active
        // 归属由后端强制校验，这里直接采用；无可用会话则新建
        return http.post(qs('/cs/portal/session/create', {
          sessionType: this.presetOrderId ? 2 : 1,
          orderId: this.presetOrderId || null
        }), null, { silent: true })
      }).then(session => {
        this.activeSession = session || null
        if (!this.activeSession) {
          this.initError = true
          return
        }
        return this.loadMessages().then(() => {
          if (!this.sessionClosed) this.startPolling()
        })
      }).catch(err => {
        if (err && err.notLogin) return
        this.initError = true
      }).then(() => {
        this.loading = false
      })
    },
    // 会话被客服结束后，用户可主动发起新会话继续咨询
    restartSession() {
      if (this.busy('restart')) return
      this.setBusy('restart', true)
      http.post(qs('/cs/portal/session/create', {
        sessionType: this.presetOrderId ? 2 : 1,
        orderId: this.presetOrderId || null
      }), null, { silent: true }).then(session => {
        if (!session) {
          uni.showToast({ title: '发起咨询失败，请稍后重试', icon: 'none' })
          return
        }
        this.activeSession = session
        this.messages = []
        this.draft = ''
        return this.loadMessages().then(() => {
          if (!this.sessionClosed) this.startPolling()
        })
      }).catch(() => {}).then(() => {
        this.setBusy('restart', false)
      })
    },
    loadMessages() {
      return http.get('/cs/portal/message/list/' + this.activeSession.id, null, { silent: true }).then(list => {
        this.messages = Array.isArray(list) ? list : []
        this.warmMessageImages()
        this.scrollBottom()
        // 进入/查看时把客服未读标记已读
        http.post('/cs/portal/message/read/' + this.activeSession.id, null, { silent: true }).catch(() => {})
      })
    },
    // 消息里的图片落 private 目录，<image> 带不了 X-Auth-Token：带头下载到本地后经 imgCache 展示
    warmMessageImages() {
      this.messages.forEach(m => {
        if (m.messageType === 2 && m.imageUrl && !this.imgCache[m.imageUrl]) this.fetchImage(m.imageUrl)
      })
    },
    fetchImage(url) {
      if (this.imgCache[url] || this.imgFetching[url]) return
      this.imgFetching[url] = true
      const header = {}
      const token = read(STORAGE_KEYS.authToken)
      if (token) header[AUTH_HEADER] = token
      uni.downloadFile({
        url: fullImage(url),   // private 相对路径 → /common/download?name=…
        header,
        success: res => {
          if (res.statusCode === 200) this.imgCache[url] = res.tempFilePath
        },
        complete: () => {
          this.imgFetching[url] = false
        }
      })
    },
    pollMessages() {
      if (!this.activeSession) return
      if (Number(this.activeSession.status) >= 2) {
        this.stopPolling()
        return
      }
      // 先同步会话状态：客服在后台关闭会话不写系统消息，只拉消息列表察觉不到
      http.get('/cs/portal/session/' + this.activeSession.id, null, { silent: true }).then(s => {
        if (s) this.activeSession = s
        if (Number(this.activeSession.status) >= 2) {
          this.stopPolling()
          return
        }
        return http.get('/cs/portal/message/list/' + this.activeSession.id, null, { silent: true }).then(list => {
          const next = Array.isArray(list) ? list : []
          const last = next.length ? next[next.length - 1] : null
          const cur = this.messages.length ? this.messages[this.messages.length - 1] : null
          const lastKey = last ? (last.id != null ? last.id : (last.updateTime || '')) : null
          const curKey = cur ? (cur.id != null ? cur.id : (cur.updateTime || '')) : null
          if (lastKey !== curKey || next.length !== this.messages.length) {
            this.messages = next
            this.warmMessageImages()
            this.scrollBottom()
            if (last && last.senderType === 2 && last.isRead === 0) {
              http.post('/cs/portal/message/read/' + this.activeSession.id, null, { silent: true }).catch(() => {})
            }
          }
        })
      }).catch(() => {
        // 轮询失败静默，下一次重试
      })
    },
    startPolling() {
      this.stopPolling()
      this._timer = setInterval(() => { this.pollMessages() }, 4000)
    },
    stopPolling() {
      if (this._timer) {
        clearInterval(this._timer)
        this._timer = null
      }
    },
    scrollBottom() {
      this.$nextTick(() => {
        // 触发 scroll-into-view 落到底部锚点（先移开再置入，保证重复触发生效）
        this.chatAnchor = ''
        setTimeout(() => { this.chatAnchor = 'msg-bottom' }, 50)
      })
    },

    // ---------- 发送 ----------
    switchView(v) {
      if (this.view === v) return
      this.view = v
      // 投诉视图不需要消息轮询，及时停掉避免空转请求
      if (v === 'complaint') {
        this.stopPolling()
      } else if (this.activeSession && !this.sessionClosed) {
        this.pollMessages()
        this.startPolling()
      }
    },
    quickSend(q) {
      this.draft = q
      this.sendText()
    },
    sendText() {
      const text = (this.draft || '').trim()
      if (!text || this.sending || !this.activeSession) return
      this.sending = true
      // messageType：1-文本 2-图片（Controller 契约，图片消息内容走 imageUrl）
      http.post('/cs/portal/message/send', {
        sessionId: this.activeSession.id,
        messageType: 1,
        content: text
      }, { silent: true }).then(() => {
        this.draft = ''
        return this.loadMessages()
      }).catch(err => {
        if (err && err.notLogin) return
        uni.showToast({ title: (err && err.message) || '发送失败', icon: 'none' })
      }).then(() => {
        this.sending = false
      })
    },
    // 发送图片消息：uni.chooseImage → POST /common/upload（bizType=chat）→ POST /message/send（messageType=2，imageUrl=相对路径）
    sendImage() {
      if (this.sendingImage || this.sending) return
      if (!this.activeSession || this.sessionClosed) return
      uni.chooseImage({
        count: 1,
        sizeType: ['compressed'],
        success: res => {
          const file = res.tempFiles && res.tempFiles[0]
          const path = file && (file.path || res.tempFilePaths[0])
          if (!path) return
          if (file && file.size > MAX_IMG_SIZE) {
            uni.showToast({ title: '图片大小不能超过 5MB', icon: 'none' })
            return
          }
          const ext = path.indexOf('.') >= 0 ? path.split('.').pop().toLowerCase() : ''
          if (ALLOWED_IMG_EXT.indexOf(ext) === -1) {
            uni.showToast({ title: '仅支持 JPG / PNG / GIF 格式', icon: 'none' })
            return
          }
          this.doSendImage(path)
        }
      })
    },
    doSendImage(localPath) {
      this.sendingImage = true
      uni.showLoading({ title: '发送中', mask: true })
      uploadFile({ filePath: localPath, bizType: 'chat' }).then(remote => {
        return http.post('/cs/portal/message/send', {
          sessionId: this.activeSession.id,
          messageType: 2,
          imageUrl: remote
        }, { silent: true }).then(msg => {
          // 刚发出的图本地已有，直接种缓存，免去一次回环下载
          if (msg && msg.imageUrl) this.imgCache[msg.imageUrl] = localPath
          return this.loadMessages()
        })
      }).catch(err => {
        if (err && err.notLogin) return
        uni.showToast({ title: (err && err.message) || '图片发送失败', icon: 'none' })
      }).then(() => {
        uni.hideLoading()
        this.sendingImage = false
      })
    },

    // ---------- 投诉 ----------
    clearOrder() {
      this.form.orderId = null
      this.form.orderNumber = ''
    },
    openOrderPick() {
      this.orderPickShow = true
      if (this.ordersLoaded) return
      this.ordersLoading = true
      http.get('/order/userPage', { page: 1, pageSize: 10 }, { silent: true }).then(data => {
        this.recentOrders = (data && data.records) || []
      }).catch(() => {}).then(() => {
        this.ordersLoading = false
        this.ordersLoaded = true
      })
    },
    pickOrder(o) {
      this.form.orderId = o.id
      this.form.orderNumber = o.number
      this.orderPickShow = false
    },
    // 投诉凭证图：选图即传（bizType=chat，与 Web 版口径一致），提交时拼逗号分隔的 imageUrls
    chooseComplaintImage() {
      if (this.submitting) return
      const remain = 3 - this.complaintImages.length
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
          // 顺序逐张上传，本地缩略图先行展示，失败标记可删除重选
          paths.reduce((promise, path) => {
            return promise.then(() => {
              const entry = { local: path, path: '', uploading: true, failed: false }
              this.complaintImages.push(entry)
              return uploadFile({ filePath: path, bizType: 'chat' })
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
          }, Promise.resolve()).catch(() => {})
        }
      })
    },
    submitComplaint() {
      if (this.submitting) return
      const content = (this.form.content || '').trim()
      if (content.length < 5) {
        uni.showToast({ title: '请至少填写5个字的详细描述', icon: 'none' })
        return
      }
      // 与 Web 版同口径：上传中不让提交，失败图须先删除
      if (this.complaintImages.some(x => x.uploading)) {
        uni.showToast({ title: '图片还在上传，请稍候', icon: 'none' })
        return
      }
      if (this.complaintImages.some(x => x.failed)) {
        uni.showToast({ title: '有图片上传失败，请删除后重试', icon: 'none' })
        return
      }
      this.submitting = true
      const urls = this.complaintImages.filter(x => x.path).map(x => x.path)
      const payload = {
        complaintType: this.form.complaintType,
        title: (this.form.title || '').trim() || '投诉',
        content: content,
        // Controller 契约：imageUrls 为逗号分隔的相对路径串（与 Web 版 urls.join(',') 一致）
        imageUrls: urls.length ? urls.join(',') : null
      }
      if (this.form.orderId) {
        payload.orderId = this.form.orderId
        payload.orderNumber = this.form.orderNumber
      }
      http.post('/cs/portal/complaint/create', payload, { silent: true }).then(() => {
        uni.showToast({ title: '投诉已提交，请留意处理进展', icon: 'none' })
        this.form = { complaintType: 1, title: '', content: '', orderId: null, orderNumber: '' }
        this.complaintImages = []
        this.view = 'chat'
      }).catch(err => {
        if (err && err.notLogin) return
        uni.showToast({ title: (err && err.message) || '提交失败', icon: 'none' })
      }).then(() => {
        this.submitting = false
      })
    },

    // ---------- 我的投诉记录 ----------
    openMyComplaints() {
      this.complaintListShow = true
      this.complaintListLoading = true
      http.get('/cs/portal/complaint/list', null, { silent: true }).then(list => {
        this.myComplaints = Array.isArray(list) ? list : []
      }).catch(err => {
        if (err && err.notLogin) return
        uni.showToast({ title: (err && err.message) || '加载失败', icon: 'none' })
      }).then(() => {
        this.complaintListLoading = false
      })
    },
    complaintStatusText(s) {
      const map = { 0: '待处理', 1: '处理中', 2: '已处理', 3: '已关闭' }
      return map[s] || '未知'
    },
    complaintTypeText(t) {
      const hit = this.complaintTypes.find(x => x.v === t)
      return hit ? hit.label : '其他'
    },
    starText(s) {
      const n = Number(s) || 0
      let out = ''
      for (let i = 0; i < n; i++) out += '★'
      return out
    },
    // 仿微信：首条、或与上一条间隔≥5分钟、或跨天时插入时间戳
    msgTimeDivider(index) {
      const cur = parseDate(this.messages[index] && this.messages[index].createTime)
      if (!cur) return ''
      const prev = index > 0 ? parseDate(this.messages[index - 1].createTime) : null
      const sameDay = prev && prev.getFullYear() === cur.getFullYear()
        && prev.getMonth() === cur.getMonth() && prev.getDate() === cur.getDate()
      const needGap = !prev || (cur.getTime() - prev.getTime()) >= 5 * 60 * 1000
      if (!needGap && sameDay) return ''
      const hm = pad2(cur.getHours()) + ':' + pad2(cur.getMinutes())
      if (!prev || !sameDay) {
        const today = new Date()
        const md = pad2(cur.getMonth() + 1) + '-' + pad2(cur.getDate())
        if (cur.getFullYear() !== today.getFullYear()) return cur.getFullYear() + '-' + md + ' ' + hm
        return md + ' ' + hm
      }
      return hm
    },

    // ---------- 满意度评价 ----------
    openRate(c) {
      this.ratingComplaint = c
      this.rateScore = 0
      this.rateFeedback = ''
      this.rateShow = true
    },
    rateHint(s) {
      const map = { 1: '很不满意', 2: '不满意', 3: '一般', 4: '满意', 5: '很满意' }
      return map[s] || '请选择星级'
    },
    submitRate() {
      if (this.rating || this.rateScore === 0 || !this.ratingComplaint) return
      this.rating = true
      // satisfaction/feedback 是 @RequestParam，必须拼 query
      http.post(qs('/cs/portal/complaint/' + this.ratingComplaint.id + '/rate', {
        satisfaction: this.rateScore,
        feedback: (this.rateFeedback || '').trim() || null
      }), null, { silent: true }).then(() => {
        // 本地更新记录，弹层里立即从「去评价」切换为「我的评价」
        const target = this.myComplaints.find(x => x.id === this.ratingComplaint.id)
        if (target) {
          target.satisfaction = this.rateScore
          target.userFeedback = (this.rateFeedback || '').trim()
        }
        this.rateShow = false
        uni.showToast({ title: '评价已提交', icon: 'success' })
      }).catch(err => {
        if (err && err.notLogin) return
        uni.showToast({ title: (err && err.message) || '评价失败', icon: 'none' })
      }).then(() => {
        this.rating = false
      })
    }
  }
}
</script>

<style>
.cs {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg-page);
}

/* ---- 顶部视图切换 ---- */
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

/* ---- 聊天视图 ---- */
.chat {
  flex: 1;
  height: 0;
}

.chat__tip {
  margin: 16rpx 24rpx 0;
  padding: 16rpx 24rpx;
  border-radius: var(--radius-md);
  background: var(--color-primary-tint);
  font-size: 24rpx;
  color: var(--color-primary-text-strong);
}

.chips {
  display: flex;
  flex-wrap: wrap;
  padding: 20rpx 24rpx 0;
}

.chip {
  padding: 12rpx 26rpx;
  margin: 0 16rpx 16rpx 0;
  border-radius: 999rpx;
  font-size: 24rpx;
  color: var(--color-primary-text-strong);
  background: var(--bg-card);
  border: 1rpx solid var(--border-medium);
}

.chat__loading {
  padding: 80rpx 0;
  text-align: center;
  font-size: 26rpx;
  color: var(--color-text-hint);
}

.chat__retry {
  margin-left: 12rpx;
  color: var(--color-primary-text);
}

.msg-time {
  margin: 20rpx 0;
  text-align: center;
  font-size: 22rpx;
  color: var(--color-text-disabled);
}

.msg-sys {
  margin: 12rpx 48rpx;
  text-align: center;
  font-size: 22rpx;
  color: var(--color-text-hint);
}

.msg {
  display: flex;
  align-items: flex-start;
  margin: 16rpx 24rpx;
}

.msg--user {
  justify-content: flex-end;
}

.avatar {
  width: 72rpx;
  height: 72rpx;
  flex-shrink: 0;
  border-radius: var(--radius-round);
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar--agent {
  background: var(--color-primary-tint);
}

.avatar__glyph {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-primary-text-strong);
}

.bubble {
  max-width: 480rpx;
  padding: 18rpx 24rpx;
  border-radius: var(--radius-lg);
  font-size: 28rpx;
  line-height: 40rpx;
  word-break: break-all;
}

.bubble--agent {
  margin-left: 16rpx;
  color: var(--color-text-primary);
  background: var(--bg-card);
}

.bubble--user {
  margin-right: 0;
  color: var(--color-primary-text);
  background: var(--color-primary);
}

.bubble__img {
  width: 240rpx;
  height: 240rpx;
  border-radius: var(--radius-md);
}

.session-end {
  padding: 32rpx 0 16rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  font-size: 24rpx;
  color: var(--color-text-hint);
}

.session-end__btn {
  margin-top: 16rpx;
  padding: 10rpx 36rpx;
  border-radius: 999rpx;
  font-size: 26rpx;
  color: var(--color-primary-text);
  background: var(--color-primary);
}

.chat__bottom {
  height: 24rpx;
}

/* ---- 输入栏 ---- */
.input-bar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  padding: 16rpx 24rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background: var(--bg-card);
  box-shadow: var(--shadow-bar-top);
}

.input-bar__input {
  flex: 1;
  height: 76rpx;
  padding: 0 24rpx;
  box-sizing: border-box;
  border-radius: 999rpx;
  background: var(--bg-input);
  font-size: 28rpx;
}

.input-bar__ph {
  color: var(--color-text-disabled);
}

/* 输入栏图片按钮 */
.input-bar__img {
  flex-shrink: 0;
  width: 64rpx;
  height: 64rpx;
  line-height: 64rpx;
  margin-right: 16rpx;
  text-align: center;
  border-radius: var(--radius-round);
  font-size: 26rpx;
  color: var(--color-text-secondary);
  background: var(--bg-input);
}

.input-bar__img--off {
  opacity: 0.5;
}

.input-bar__send {
  margin-left: 20rpx;
  padding: 14rpx 36rpx;
  border-radius: 999rpx;
  font-size: 26rpx;
  color: var(--color-text-disabled);
  background: var(--bg-input);
}

.input-bar__send--on {
  font-weight: 600;
  color: var(--color-primary-text);
  background: var(--color-primary);
}

.input-bar--closed {
  justify-content: center;
  font-size: 24rpx;
  color: var(--color-text-hint);
}

/* ---- 投诉视图 ---- */
.complaint {
  flex: 1;
  height: 0;
}

.card {
  margin: 16rpx 24rpx 0;
  padding: 24rpx;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
  box-shadow: var(--shadow-card);
}

.section-title {
  margin-bottom: 16rpx;
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

.card__row {
  display: flex;
  align-items: center;
  min-height: 60rpx;
}

.card__row-text {
  flex: 1;
  font-size: 28rpx;
  color: var(--color-text-primary);
}

.card__row-text--ph {
  color: var(--color-text-disabled);
}

.card__row-arrow {
  font-size: 36rpx;
  color: var(--color-text-hint);
}

.card__row-clear {
  padding: 0 12rpx;
  font-size: 36rpx;
  color: var(--color-text-hint);
}

.card__input {
  width: 100%;
  height: 76rpx;
  padding: 0 24rpx;
  box-sizing: border-box;
  border-radius: var(--radius-md);
  background: var(--bg-input);
  font-size: 28rpx;
}

.card__textarea {
  width: 100%;
  height: 200rpx;
  padding: 16rpx 24rpx;
  box-sizing: border-box;
  border-radius: var(--radius-md);
  background: var(--bg-input);
  font-size: 28rpx;
}

.card__ph {
  color: var(--color-text-disabled);
}

.card__count {
  display: block;
  margin-top: 8rpx;
  text-align: right;
  font-size: 22rpx;
  color: var(--color-text-hint);
}

.type-grid {
  display: flex;
  flex-wrap: wrap;
}

.type-cell {
  width: 200rpx;
  margin: 0 16rpx 16rpx 0;
  padding: 20rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  border-radius: var(--radius-md);
  background: var(--bg-input);
  border: 1rpx solid var(--border-medium);
}

.type-cell--on {
  background: var(--color-primary-tint);
  border-color: var(--color-primary);
}

.type-cell__icon {
  font-size: 36rpx;
  font-weight: 600;
  color: var(--color-primary-text-strong);
}

.type-cell__label {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: var(--color-text-secondary);
}

.type-cell--on .type-cell__label {
  font-weight: 600;
  color: var(--color-primary-text);
}

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

.btn--off {
  opacity: 0.5;
}

.tipnote {
  margin-top: 16rpx;
  font-size: 22rpx;
  line-height: 34rpx;
  color: var(--color-text-hint);
}

/* ---- 投诉凭证图片 ---- */
.imgs {
  display: flex;
  flex-wrap: wrap;
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

/* ---- 底部弹层 ---- */
.popup-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 100;
  display: flex;
  align-items: flex-end;
  background: rgba(0, 0, 0, 0.5);
}

.popup {
  width: 100%;
  max-height: 70vh;
  display: flex;
  flex-direction: column;
  padding: 32rpx 24rpx 0;
  padding-bottom: env(safe-area-inset-bottom);
  box-sizing: border-box;
  border-radius: 24rpx 24rpx 0 0;
  background: var(--bg-card);
}

.popup__title {
  margin-bottom: 16rpx;
  text-align: center;
  font-size: 30rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

.popup__scroll {
  max-height: 56vh;
}

.popup__item {
  display: flex;
  align-items: center;
  padding: 24rpx 12rpx;
  border-bottom: 1rpx solid var(--border-light);
}

.popup__item-main {
  flex: 1;
  font-size: 28rpx;
  font-family: monospace;
  color: var(--color-text-primary);
}

.popup__item-sub {
  font-size: 22rpx;
  color: var(--color-text-hint);
}

.popup__empty {
  padding: 60rpx 0;
  text-align: center;
  font-size: 26rpx;
  color: var(--color-text-hint);
}

/* ---- 投诉记录 ---- */
.crec {
  padding: 24rpx 12rpx;
  border-bottom: 1rpx solid var(--border-light);
}

.crec__top {
  display: flex;
  align-items: center;
}

.crec__tag {
  padding: 4rpx 18rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
}

.crec__tag--s0 {
  color: var(--color-warning);
  background: var(--color-primary-tint);
}

.crec__tag--s1 {
  color: var(--color-primary-text-strong);
  background: var(--color-info-tint);
}

.crec__tag--s2 {
  color: var(--color-success-text);
  background: var(--color-success-tint);
}

.crec__tag--s3 {
  color: var(--color-text-hint);
  background: var(--color-neutral-tint);
}

.crec__type {
  margin-left: 16rpx;
  font-size: 24rpx;
  color: var(--color-text-secondary);
}

.crec__title {
  display: block;
  margin-top: 12rpx;
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

.crec__content {
  display: block;
  margin-top: 8rpx;
  font-size: 26rpx;
  line-height: 38rpx;
  color: var(--color-text-secondary);
}

.crec__time {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: var(--color-text-hint);
}

.crec__result {
  margin-top: 12rpx;
  padding: 16rpx 20rpx;
  border-radius: var(--radius-md);
  background: var(--color-success-tint);
}

.crec__result-label {
  display: block;
  font-size: 24rpx;
  font-weight: 600;
  color: var(--color-success-text);
}

.crec__result-text {
  display: block;
  margin-top: 6rpx;
  font-size: 26rpx;
  line-height: 38rpx;
  color: var(--color-text-primary);
}

.crec__result-comp {
  display: block;
  margin-top: 6rpx;
  font-size: 26rpx;
  font-weight: 600;
  color: var(--color-price);
}

.crec__rated {
  margin-top: 12rpx;
  font-size: 24rpx;
  color: var(--color-warning);
}

.crec__rated-fb {
  display: block;
  margin-top: 4rpx;
  color: var(--color-text-hint);
}

.crec__rate-btn {
  display: block;
  margin-top: 16rpx;
  padding: 12rpx 0;
  text-align: center;
  border-radius: 999rpx;
  font-size: 26rpx;
  color: var(--color-primary-text-strong);
  border: 1rpx solid var(--color-primary-text-strong);
}

/* ---- 满意度评价 ---- */
.rate-stars {
  display: flex;
  justify-content: center;
  padding: 12rpx 0;
}

.rate-star {
  padding: 0 16rpx;
  font-size: 56rpx;
  color: var(--border-medium);
}

.rate-star--on {
  color: var(--color-warning);
}

.rate-hint {
  display: block;
  margin-bottom: 16rpx;
  text-align: center;
  font-size: 24rpx;
  color: var(--color-text-hint);
}
</style>
