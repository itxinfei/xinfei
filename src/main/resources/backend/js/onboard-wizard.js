/**
 * 新商户引导向导（P0-3，O-45）—— 仅 dashboard/overview.html 引入。
 *
 * 触发条件（loadAllData 后判定一次/会话）：
 *   未配置营业时间 || 无菜品分类 || 无在售菜品  → 认为是「新商户」，弹向导；
 *   localStorage 'rg_onboard_done' = '1'（完成或勾选不再提示）则不再打扰。
 *
 * 五步（每步：说明 + 跳转对应页面，向导保持打开，回来后「我已完成」勾选下一步）：
 *   1 店铺信息与营业时间 → 门店管理
 *   2 创建菜品分类       → 菜品管理（分类弹窗）
 *   3 上架首个菜品       → 菜品管理
 *   4 （堂食可选）打印桌码 → 桌台管理
 *   5 上线营业           → 门店状态自检清单
 *
 * 设计约束：纯前端检测（复用工作台已拉的数据 + 两个轻查询），零后端改动；
 * 向导绝不自动改任何业务数据——所有动作都由商家在目标页完成。
 */
;(function () {
  var win = window
  var XinfeiComponentsV3 = win.XinfeiComponentsV3 || (win.XinfeiComponentsV3 = {})

  var DONE_KEY = 'rg_onboard_done'

  XinfeiComponentsV3['onboard-wizard'] = {
    name: 'OnboardWizard',
    props: {
      visible: { type: Boolean, default: false },
      // 检测结果快照（父页传入）：{ noHours, noCategory, noDish }
      checks: { type: Object, default: function () { return {} } }
    },
    emits: ['close'],
    data: function () {
      return {
        step: 1,
        // 各步完成态（进入时按 checks 预勾已达标项）
        done: { hours: false, category: false, dish: false, qrcode: false, open: false },
        skipForever: false,
        steps: [
          { key: 'hours', icon: 'ri-store-2-line', title: '完善店铺信息', desc: '填写店名与营业时间——打烊时段将自动暂停接单', url: 'page/store/shop-settings.html', cta: '去设置' },
          { key: 'category', icon: 'ri-apps-line', title: '创建菜品分类', desc: '如「热菜 / 饮品 / 小吃」，顾客按分类浏览菜单', url: 'page/food/list.html', cta: '去创建' },
          { key: 'dish', icon: 'ri-restaurant-line', title: '上架首个菜品', desc: '一张图 + 价格即可开卖，口味规格可后续补充', url: 'page/food/list.html', cta: '去上架' },
          { key: 'qrcode', icon: 'ri-qr-code-line', title: '（可选）打印桌码', desc: '堂食顾客扫码点餐；只做外卖可跳过', url: 'page/dining/table-list.html', cta: '去生成' },
          { key: 'open', icon: 'ri-rocket-line', title: '上线营业', desc: '确认门店未暂停接单，坐等第一单进来', url: 'page/store/list.html', cta: '去检查' }
        ]
      }
    },
    computed: {
      currentStep: function () {
        return this.steps[this.step - 1] || this.steps[0]
      },
      progressPercent: function () {
        return Math.round((this.step / this.steps.length) * 100)
      }
    },
    watch: {
      visible: function (v) {
        if (v) {
          // 打开时按检测结果预勾达标步骤
          this.done.hours = !this.checks.noHours
          this.done.category = !this.checks.noCategory
          this.done.dish = !this.checks.noDish
          this.step = 1
        }
      }
    },
    methods: {
      markDone: function (key) {
        this.done[key] = true
        // 自动进入下一步
        var idx = this.steps.findIndex(function (s) { return s.key === key })
        if (idx >= 0 && idx < this.steps.length - 1) this.step = idx + 2
      },
      goStep: function (url) {
        // iframe 架构：通知壳层打开菜单页（向导保留状态，回来继续）
        win.parent.postMessage({ type: 'XINFEI_NAVIGATE', url: url }, '*')
      },
      finish: function () {
        try { win.localStorage.setItem(DONE_KEY, '1') } catch (e) { /* 隐私模式静默 */ }
        this.$emit('close')
        XinfeiUI.success('引导完成，祝生意兴隆！')
      },
      closeLater: function () {
        // 「稍后再说」：本次关闭，下次进工作台若仍未达标会再弹
        this.$emit('close')
      },
      closeNever: function () {
        try { win.localStorage.setItem(DONE_KEY, '1') } catch (e) { /* 同上 */ }
        this.$emit('close')
      }
    },
    template:
      '<el-dialog :model-value="visible" title="欢迎入驻 · 开店五步走" width="520px" append-to-body :close-on-click-modal="false" :show-close="false">' +
        '<div class="obw">' +
          '<div class="obw__progress">' +
            '<div class="obw__progress-bar" :style="{ width: progressPercent + \'%\' }"></div>' +
          '</div>' +
          '<div class="obw__steps">' +
            '<div v-for="(s, i) in steps" :key="s.key" class="obw__step" :class="{ \'is-active\': step === i + 1, \'is-done\': done[s.key] }" @click="step = i + 1">' +
              '<i :class="[s.icon, \'obw__step-icon\']"></i>' +
              '<span class="obw__step-title">{{ i + 1 }}. {{ s.title }}</span>' +
              '<i v-if="done[s.key]" class="ri-check-line obw__step-check"></i>' +
            '</div>' +
          '</div>' +
          '<div class="obw__panel">' +
            '<div class="obw__panel-title"><i :class="currentStep.icon"></i> {{ currentStep.title }}</div>' +
            '<div class="obw__panel-desc">{{ currentStep.desc }}</div>' +
            '<div class="obw__panel-actions">' +
              '<el-button type="primary" size="small" @click="goStep(currentStep.url)">{{ currentStep.cta }}</el-button>' +
              '<el-button size="small" @click="markDone(currentStep.key)">我已完成</el-button>' +
            '</div>' +
          '</div>' +
          '<div class="obw__foot">' +
            '<el-checkbox v-model="skipForever" size="small">不再提示</el-checkbox>' +
            '<span class="obw__spacer"></span>' +
            '<el-button text size="small" @click="closeLater">稍后再说</el-button>' +
            '<el-button type="success" size="small" @click="finish">完成引导</el-button>' +
          '</div>' +
        '</div>' +
      '</el-dialog>'
  }

  // 窄屏样式增强（向导主要在桌面用，简单兜底）
  var style = document.createElement('style')
  style.textContent =
    '.obw{padding:0 4px}' +
    '.obw__progress{height:6px;background:var(--el-fill-color-light);border-radius:3px;margin-bottom:16px}' +
    '.obw__progress-bar{height:100%;background:var(--color-brand-500);border-radius:3px;transition:width .3s}' +
    '.obw__steps{display:flex;flex-direction:column;gap:6px;margin-bottom:16px}' +
    '.obw__step{display:flex;align-items:center;gap:10px;padding:9px 12px;border-radius:var(--radius-md);cursor:pointer;border:1px solid transparent}' +
    '.obw__step:hover{background:var(--el-fill-color-light)}' +
    '.obw__step.is-active{border-color:var(--color-brand-500);background:var(--color-brand-50)}' +
    '.obw__step.is-done{opacity:.65}' +
    '.obw__step-icon{font-size:18px;color:var(--color-brand-600)}' +
    '.obw__step-title{flex:1;font-size:14px}' +
    '.obw__step-check{color:var(--color-success);font-weight:700}' +
    '.obw__panel{padding:14px;border:1px dashed var(--el-border-color);border-radius:var(--radius-md);margin-bottom:14px}' +
    '.obw__panel-title{font-weight:600;font-size:15px;margin-bottom:6px}' +
    '.obw__panel-title i{color:var(--color-brand-600);margin-right:4px}' +
    '.obw__panel-desc{font-size:13px;color:var(--text-secondary);margin-bottom:12px}' +
    '.obw__foot{display:flex;align-items:center;gap:8px}' +
    '.obw__spacer{flex:1}'
  document.head.appendChild(style)

  // 导出完成标记键供宿主页使用
  win.XINFEI_ONBOARD_DONE_KEY = DONE_KEY
})()
