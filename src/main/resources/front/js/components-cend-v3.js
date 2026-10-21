/* ============================================================
 * 心飞外卖 C 端 — 跨页面复用组件（Vue 3 / Vant 4 / 无构建）
 *
 * 【迁移期分叉】本文件由 front/js/components-cend.js（Vue 2 + Vant 2）移植而来。
 * 全站 C 页面按页面粒度灰度切换：页面把 components-cend.js 换成本文件 + app-v3-vant.js。
 * 因此 components-cend.js 在迁移期内冻结，全站切完后删除它、本文件改回全局注册即可。
 *
 * 注册方式的变化：Vue 2 时代 Vue.component() 直接生效；Vue 3 必须先拿到 app 实例，
 * 故本文件只导出注册表 window.XinfeiComponentsCendV3，由 createVantApp() 统一注册。
 *
 * 组件清单：
 *   <cend-shell>      页面壳：导航栏 + 内容槽 + 底部 Tab
 *   <cend-empty>      空态：图标 + 文本 + 副文本 + 操作按钮
 *
 * 导航主题：light（白底轻导航，对标美团二级页，默认）/ brand（品牌金，首页类）
 *
 * Vant 2 → Vant 4 的硬性差异（本文件已处理，勿回退）：
 *   - van-tabbar 的 value 改名 model-value（v-model 也跟着变）
 *   - slot="icon" 元素形态 → <template #icon>
 *
 * 引入方式（在 vant.min.js 之后、createVantApp 之前）：
 *   <script src="../js/components-cend-v3.js"></script>
 *
 * @since 2026-10-21
 * ============================================================ */
(function () {
  'use strict';

  if (!window.Vue || !window.Vue.createApp) {
    throw new Error('components-cend-v3.js 需要 Vue 3 全局构建，且必须先于本文件加载')
  }

  /* ==========================================================
   * <cend-shell> — 页面壳
   * ==========================================================
   * Props：
   *   title        [string] 标题（默认空）
   *   light        [bool]   true=白底轻导航(默认) false=品牌金导航
   *   show-back    [bool]   是否显示左返回箭头（默认 true）
   *   show-home    [bool]   是否显示右侧首页图标（默认 false）
   *   show-tabbar  [bool]   是否显示底部 Tab（默认 false）
   *   tab-active   [number] 当前 Tab 索引（0=点餐/1=订单/2=我的，默认 0）
   *
   * Events：@back / @home / @tab-change
   * Slot：default — 主内容区域；right — 导航栏右侧
   */
  var CendShell = {
    template: [
      '<div class="cend-page">',
      '  <!-- 导航栏：独立 .cend-navbar，不复用各页 .divHead 金色作用域样式 -->',
      '  <div v-if="title || showBack || showHome || $slots.right" class="cend-navbar" :class="light ? \'is-light\' : \'is-brand\'">',
      '    <i v-if="showBack" class="cend-navbar__btn cend-navbar__back ri-arrow-left-s-line" @click="onBack"></i>',
      '    <span class="cend-navbar__title">{{ title }}</span>',
      '    <i v-if="showHome && !$slots.right" class="cend-navbar__btn cend-navbar__home ri-home-4-line" @click="onHome"></i>',
      '    <span v-if="$slots.right" class="cend-navbar__right"><slot name="right"></slot></span>',
      '  </div>',
      '  <!-- 主内容（页面自行控制滚动和间距） -->',
      '  <slot></slot>',
      '  <!-- 底部 Tab（Vant 自带 fixed 定位 + 安全区）',
      '       用 <component :is> 而不是静态 <van-tabbar>：Vue 3 把静态标签的 resolveComponent 提到渲染函数',
      '       顶部执行，v-if 为假也照跑，于是没装 Vant 的页面（no-wifi 纯静态提示页）会刷两条',
      '       「Failed to resolve component: van-tabbar」；动态 :is 的解析发生在真正渲染的那一次，不显示就不解析。 -->',
      '  <component v-if="showTabbar" :is="\'van-tabbar\'" :model-value="tabActive" :safe-area-inset="true" @change="onTabChange" class="app-tabbar">',
      '    <component v-for="t in tabs" :key="t.label" :is="\'van-tabbar-item\'"><span>{{ t.label }}</span><template #icon><i :class="t.icon"></i></template></component>',
      '  </component>',
      '</div>'
    ].join(''),
    data: function () {
      return {
        tabs: [
          { label: '点餐', icon: 'ri-restaurant-line' },
          { label: '订单', icon: 'ri-file-list-line' },
          { label: '我的', icon: 'ri-user-3-line' }
        ]
      };
    },
    props: {
      title:      { type: String, default: '' },
      light:      { type: Boolean, default: true },
      showBack:   { type: Boolean, default: true },
      showHome:   { type: Boolean, default: false },
      showTabbar: { type: Boolean, default: false },
      tabActive:  { type: Number, default: 0 }
    },
    emits: ['back', 'home', 'tab-change'],
    methods: {
      onBack: function () {
        // 优先使用全局兜底，避免页面自定义方法覆盖导致行为不统一
        if (typeof window.goBack === 'function') {
          window.goBack();
        } else if (history.length > 1) {
          history.go(-1);
        } else {
          window.location.href = '/front/index.html';
        }
        this.$emit('back');
      },
      onHome: function () {
        window.location.href = '/front/index.html';
        this.$emit('home');
      },
      onTabChange: function (active) {
        if (active === this.tabActive) return;
        var urlMap = { 0: '/front/index.html', 1: '/front/page/order.html', 2: '/front/page/user.html' };
        var url = urlMap[active];
        if (url) {
          this.$emit('tab-change', active);
          window.location.href = url;
        }
      }
    }
  };

  /* ==========================================================
   * <cend-empty> — 空态
   * ==========================================================
   * Props：
   *   icon  [string] RemixIcon 类名 或 图片 URL（自动判断）
   *   text  [string] 主文案（默认 "暂无数据"）
   *   sub   [string] 副文案（可选）
   *   action-text [string] 操作按钮文案（可选，不传不显示按钮）
   * Events：@action
   */
  var CendEmpty = {
    template: [
      '<div class="empty-state has-tabbar">',
      '  <img v-if="isImg" :src="icon" :alt="text"/>',
      '  <i v-else :class="icon"></i>',
      '  <p class="empty-text">{{ text }}</p>',
      '  <span class="empty-sub">{{ sub }}</span>',
      '  <div v-if="actionText" class="empty-action" @click="onAction">{{ actionText }}</div>',
      '</div>'
    ].join(''),
    props: {
      icon:       { type: String, default: 'ri-inbox-line' },
      text:       { type: String, default: '暂无数据' },
      sub:        { type: String, default: '' },
      actionText: { type: String, default: '' }
    },
    emits: ['action'],
    computed: {
      isImg: function () {
        return this.icon && (this.icon.indexOf('/') === 0 || this.icon.indexOf('http') === 0 || this.icon.indexOf('./') === 0);
      }
    },
    methods: {
      onAction: function () {
        this.$emit('action');
      }
    }
  };

  window.XinfeiComponentsCendV3 = {
    'cend-shell': CendShell,
    'cend-empty': CendEmpty
  };
})();
