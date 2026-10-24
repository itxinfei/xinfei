<template>
  <view class="ai">
    <!-- 顶栏：新对话 -->
    <view class="head">
      <text class="head__title">点餐小助手</text>
      <text v-if="messages.length" class="head__new" hover-class="hover-press" @tap="newConversation">新对话</text>
    </view>

    <scroll-view class="chat" scroll-y :scroll-into-view="chatAnchor" scroll-with-animation>
      <!-- 欢迎语 -->
      <view class="msg msg--agent">
        <view class="avatar avatar--agent"><text class="avatar__glyph">AI</text></view>
        <view class="bubble bubble--agent">
          <text>{{ welcome || '你好呀！我是点餐小助手，可以帮你推荐菜品、介绍口味和份量，有什么想吃的尽管问我～' }}</text>
        </view>
      </view>

      <!-- 快捷问题 -->
      <view v-if="!messages.length && quickQuestions.length" class="chips">
        <view v-for="q in quickQuestions" :key="q" class="chip" hover-class="hover-press" @tap="quickSend(q)">{{ q }}</view>
      </view>

      <!-- 消息体 -->
      <view v-for="(m, i) in messages" :key="i">
        <view v-if="m.role === 'user'" class="msg msg--user">
          <view class="bubble bubble--user"><text>{{ m.content }}</text></view>
        </view>
        <view v-else class="msg msg--agent">
          <view class="avatar avatar--agent"><text class="avatar__glyph">AI</text></view>
          <view class="bubble bubble--agent">
            <text v-if="m.content">{{ m.content }}</text>
            <text v-else class="bubble__pending">{{ sending ? '正在思考…' : '' }}</text>
            <!-- 推荐菜品卡（点餐场景专用，来自响应体 dishes） -->
            <view v-if="m.dishes && m.dishes.length" class="dishes">
              <view v-for="(d, j) in m.dishes" :key="j" class="dish" hover-class="hover-press" @tap="goOrder">
                <image v-if="d.image" class="dish__img" :src="fullImage(d.image)" mode="aspectFill" lazy-load />
                <view v-else class="dish__img dish__img--ph"></view>
                <view class="dish__info">
                  <text class="dish__name">{{ d.name }}</text>
                  <text v-if="d.reason" class="dish__reason">{{ d.reason }}</text>
                  <text class="dish__price">¥{{ fmtAmount(d.price) }}</text>
                </view>
              </view>
            </view>
          </view>
        </view>
      </view>

      <view v-if="listError" class="chat__loading">
        <text>加载历史对话失败</text>
        <text class="chat__retry" @tap="restoreHistory">重试</text>
      </view>
      <view id="msg-bottom" class="chat__bottom"></view>
    </scroll-view>

    <!-- 输入区（适配软键盘：adjust-position + cursor-spacing） -->
    <view class="input-bar">
      <input
        v-model="draft"
        class="input-bar__input"
        maxlength="500"
        confirm-type="send"
        :cursor-spacing="20"
        :adjust-position="true"
        placeholder="想吃什么？告诉我吧"
        placeholder-class="input-bar__ph"
        @confirm="send"
      />
      <text
        class="input-bar__send"
        :class="{ 'input-bar__send--on': canSend && !sending }"
        hover-class="hover-press"
        @tap="send"
      >发送</text>
    </view>
  </view>
</template>

<script>
import { http, fullImage, fmtAmount } from '@xinfei/mini-shared'

/** 本页本地交接键：继续上次对话（shared STORAGE_KEYS 不含业务态，不放全局） */
const CONV_KEY = 'customerAiConversationId'

export default {
  data() {
    return {
      // 消息模型：{role:'user'|'assistant', content, dishes?, pending?}
      messages: [],
      conversationId: '',
      welcome: '',
      quickQuestions: [],
      draft: '',
      sending: false,
      chatAnchor: '',
      listError: false
    }
  },
  computed: {
    canSend() {
      return (this.draft || '').trim().length > 0 && !this.sending
    }
  },
  onLoad() {
    this.loadSceneConfig()
    this.conversationId = uni.getStorageSync(CONV_KEY) || ''
    if (this.conversationId) this.restoreHistory()
  },
  methods: {
    fullImage,
    fmtAmount,
    // 场景配置：C 端收敛为 order_assistant，后端会强制钳制
    loadSceneConfig() {
      http.get('/api/ai/scene-config', { scene: 'order_assistant' }, { silent: true }).then(cfg => {
        if (!cfg) return
        if (cfg.welcome) this.welcome = cfg.welcome
        if (Array.isArray(cfg.quickQuestions)) this.quickQuestions = cfg.quickQuestions
      }).catch(() => {})
    },
    // 恢复上次对话的消息历史；对话已失效（清库/越权）时静默作废本地 conversationId
    restoreHistory() {
      this.listError = false
      http.get('/api/ai/conversations/' + encodeURIComponent(this.conversationId), null, { silent: true })
        .then(list => {
          const records = Array.isArray(list) ? list : []
          this.messages = records
            .filter(r => r.role === 'user' || r.role === 'assistant')
            .map(r => ({ role: r.role, content: r.content || '' }))
          this.scrollBottom()
        })
        .catch(err => {
          const msg = (err && err.message) || ''
          if (msg.indexOf('不存在') !== -1 || msg.indexOf('无权') !== -1) {
            this.conversationId = ''
            uni.removeStorageSync(CONV_KEY)
          } else {
            this.listError = true
          }
        })
    },
    newConversation() {
      this.conversationId = ''
      this.messages = []
      this.listError = false
      uni.removeStorageSync(CONV_KEY)
    },
    quickSend(q) {
      this.draft = q
      this.send()
    },
    send() {
      const text = (this.draft || '').trim()
      if (!text || this.sending) return
      this.sending = true
      this.draft = ''
      this.messages.push({ role: 'user', content: text })
      const slot = { role: 'assistant', content: '', dishes: null }
      this.messages.push(slot)
      this.scrollBottom()
      // 非流式通道：uni.request 不支持 SSE 流式消费（Web 版 /chat/stream 为 POST SSE），
      // 走同一对话链路的非流式接口 /api/ai/chat（Web 端自身也保留它作流式失败降级）
      http.post('/api/ai/chat', {
        message: text,
        scene: 'order_assistant',
        conversationId: this.conversationId || null
      }, { silent: true, timeout: 60000 }).then(resp => {
        slot.content = (resp && resp.content) || '（AI 未返回内容，请重试）'
        slot.dishes = (resp && Array.isArray(resp.dishes) && resp.dishes.length) ? resp.dishes : null
        const cid = resp && resp.data ? resp.data.conversationId : null
        if (cid && cid !== this.conversationId) {
          this.conversationId = cid
          uni.setStorageSync(CONV_KEY, cid)
        }
        this.sending = false
        this.scrollBottom()
      }).catch(err => {
        // 移除占位气泡，把错误回填为助手消息便于重试
        const idx = this.messages.indexOf(slot)
        if (idx !== -1) this.messages.splice(idx, 1)
        this.sending = false
        if (err && err.notLogin) return
        this.messages.push({ role: 'assistant', content: (err && err.message) || '请求失败，请稍后重试' })
        this.scrollBottom()
      })
    },
    goOrder() {
      // 菜品卡点击：回点餐首页自助搜索下单（AI 直达购物车链路 Web 版才有）
      uni.switchTab({ url: '/pages/index/index' })
    },
    scrollBottom() {
      this.$nextTick(() => {
        this.chatAnchor = ''
        setTimeout(() => { this.chatAnchor = 'msg-bottom' }, 50)
      })
    }
  }
}
</script>

<style>
.ai {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg-page);
}

.head {
  flex-shrink: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20rpx 32rpx;
  background: var(--bg-card);
  border-bottom: 1rpx solid var(--border-light);
}

.head__title {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

.head__new {
  padding: 8rpx 28rpx;
  border-radius: 999rpx;
  font-size: 24rpx;
  color: var(--color-primary-text-strong);
  background: var(--color-primary-tint);
}

.chat {
  flex: 1;
  height: 0;
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

.msg {
  display: flex;
  align-items: flex-start;
  margin: 20rpx 24rpx;
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
  background: var(--color-primary-tint);
}

.avatar__glyph {
  font-size: 26rpx;
  font-weight: 700;
  color: var(--color-primary-text-strong);
}

.bubble {
  max-width: 500rpx;
  padding: 18rpx 24rpx;
  border-radius: var(--radius-lg);
  font-size: 28rpx;
  line-height: 42rpx;
  word-break: break-all;
}

.bubble--agent {
  margin-left: 16rpx;
  color: var(--color-text-primary);
  background: var(--bg-card);
}

.bubble--user {
  color: var(--color-primary-text);
  background: var(--color-primary);
}

.bubble__pending {
  color: var(--color-text-hint);
}

/* ---- 推荐菜品卡 ---- */
.dishes {
  margin-top: 16rpx;
}

.dish {
  display: flex;
  align-items: center;
  padding: 16rpx;
  margin-bottom: 12rpx;
  border-radius: var(--radius-md);
  background: var(--bg-page);
}

.dish:last-child {
  margin-bottom: 0;
}

.dish__img {
  width: 96rpx;
  height: 96rpx;
  flex-shrink: 0;
  border-radius: var(--radius-sm);
  background: var(--bg-input);
}

.dish__info {
  flex: 1;
  min-width: 0;
  margin-left: 16rpx;
  display: flex;
  flex-direction: column;
}

.dish__name {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text-primary);
}

.dish__reason {
  margin-top: 4rpx;
  font-size: 22rpx;
  line-height: 32rpx;
  color: var(--color-text-hint);
}

.dish__price {
  margin-top: 4rpx;
  font-size: 26rpx;
  font-weight: 600;
  color: var(--color-price);
}

.chat__loading {
  padding: 40rpx 0;
  text-align: center;
  font-size: 26rpx;
  color: var(--color-text-hint);
}

.chat__retry {
  margin-left: 12rpx;
  color: var(--color-primary-text);
}

.chat__bottom {
  height: 24rpx;
}

/* ---- 输入区 ---- */
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
</style>
