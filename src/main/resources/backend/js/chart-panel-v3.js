/**
 * 报表页通用组件 —— Vue 3 / Element Plus 版
 *
 * 后台是 iframe 单页架构，每个页面自己 new 一个 app；旧 Vue 2 版 chart-panel.js
 * 已删除（git 历史可查），报表页现统一使用本文件。
 *
 * 组件列表（与 v2 版一一对应，模板与 props 未改）：
 *   <chart-panel>  — 图表面板（统一圆角、阴影、标题栏、hover 效果）
 *   <kpi-card>     — KPI 指标卡片（统一颜色主题、图标布局）
 *   <page-shell>   — 报表页外壳（统一 filter-bar + stats-cards 布局）
 *
 * 注册方式的变化：Vue 2 里 Vue.component() 直接挂全局；Vue 3 没有全局注册口，
 * 所以这里只把组件塞进 window.XinfeiComponentsV3 注册表，由 app-v3.js 的
 * createXinfeiApp() 统一 app.component() 注册。用「有则复用、无则新建」的写法，
 * 使本文件与 components-v3.js 的加载先后顺序无关（页面普遍在报表组件之后才引入）。
 *
 * 使用方式：
 *   1. 引入样式：<link rel="stylesheet" href="../../styles/chart-panel.css" />
 *   2. 引入组件：<script src="../../js/chart-panel-v3.js"></script>
 *   3. 页面用 createXinfeiApp({ ... }) 启动，模板里直接写 <chart-panel> 等标签
 *
 * @author Xinfei Team
 * @since 2026-10-21
 */

(function () {
  var XinfeiComponentsV3 = window.XinfeiComponentsV3 || (window.XinfeiComponentsV3 = {})

  /** icon 兼容两种写法：以 ri-/el-icon- 开头的当作字体图标 class，否则当 emoji/文本渲染 */
  function isIconClass (icon) {
    return typeof icon === 'string' && /^(ri-|el-icon-)/.test(icon)
  }

  // ============================================================
  // 组件1：chart-panel — 图表面板
  // ============================================================
  XinfeiComponentsV3['chart-panel'] = {
    props: {
      /** 面板标题 */
      title: {
        type: String,
        default: ''
      },
      /** 标题前缀图标（RemixIcon class，如 ri-bar-chart-2-line；兼容 emoji/文本） */
      icon: {
        type: String,
        default: ''
      },
      /** 面板内边距 */
      padding: {
        type: String,
        default: '24px'
      },
      /** 底部间距 */
      marginBottom: {
        type: String,
        default: '20px'
      }
    },
    methods: {
      isIconClass: isIconClass
    },
    template:
      '<div class="chart-panel" :style="{ padding: padding, marginBottom: marginBottom }">' +
        '<div v-if="title || $slots.title" class="chart-panel__title">' +
          '<slot name="title">' +
            '<i v-if="isIconClass(icon)" :class="icon" class="chart-panel__icon" aria-hidden="true"></i>' +
            '<span v-else-if="icon" class="chart-panel__icon" v-text="icon"></span>' +
            '<span>{{ title }}</span>' +
          '</slot>' +
        '</div>' +
        '<div class="chart-panel__body">' +
          '<slot></slot>' +
        '</div>' +
      '</div>'
  }

  // ============================================================
  // 组件2：kpi-card — KPI 指标卡片
  // ============================================================
  XinfeiComponentsV3['kpi-card'] = {
    props: {
      /** 数值 */
      value: {
        type: [Number, String],
        default: 0
      },
      /** 标签 */
      label: {
        type: String,
        default: ''
      },
      /** 颜色主题：primary / success / warning / danger / info / purple */
      color: {
        type: String,
        default: 'primary'
      },
      /** 单位 */
      unit: {
        type: String,
        default: ''
      },
      /** 图标（RemixIcon class，如 ri-user-line；兼容 emoji/文本） */
      icon: {
        type: String,
        default: ''
      },
      /** 是否使用 flex 行布局（图标在左，文字在右） */
      flex: {
        type: Boolean,
        default: false
      },
      /** 附加提示文字 */
      subText: {
        type: String,
        default: ''
      }
    },
    computed: {
      cardClass: function () {
        return 'kpi-card kpi-card--' + (this.color || 'primary')
      }
    },
    methods: {
      isIconClass: isIconClass
    },
    template:
      '<div :class="cardClass">' +
        '<div v-if="icon" class="kpi-card__icon">' +
          '<i v-if="isIconClass(icon)" :class="icon" aria-hidden="true"></i>' +
          '<span v-else v-text="icon"></span>' +
        '</div>' +
        '<div class="kpi-card__content">' +
          '<div class="kpi-card__label">{{ label }}</div>' +
          '<div class="kpi-card__value">' +
            '{{ value != null ? value : 0 }}' +
            '<small v-if="unit" class="kpi-card__unit">{{ unit }}</small>' +
          '</div>' +
          '<div v-if="subText" class="kpi-card__sub">{{ subText }}</div>' +
        '</div>' +
      '</div>'
  }

  // ============================================================
  // 组件3：page-shell — 报表页外壳
  // ============================================================
  XinfeiComponentsV3['page-shell'] = {
    props: {
      /** 页面标题 */
      title: {
        type: String,
        default: ''
      },
      /** KPI 卡片配置 [{ value, label, color, icon, unit }] */
      cards: {
        type: Array,
        default: function () { return [] }
      },
      /** 卡片列数：3 或 4（默认 4） */
      cardCols: {
        type: Number,
        default: 4
      }
    },
    computed: {
      cardsColClass: function () {
        return this.cardCols === 3 ? 'page-shell__cards--col-3' : ''
      }
    },
    template:
      '<div class="page-shell">' +
        '<div v-if="title" class="page-shell__header">{{ title }}</div>' +
        '<div class="page-shell__toolbar"><slot name="toolbar"></slot></div>' +
        '<div v-if="cards.length > 0" class="page-shell__cards" :class="cardsColClass">' +
          '<kpi-card v-for="card in cards" :key="card.label || card.value"' +
            ' :value="card.value" :label="card.label" :color="card.color"' +
            ' :icon="card.icon" :unit="card.unit || \'\'"' +
            ' :sub-text="card.subText || \'\'"' +
            ' :flex="!!card.flex" />' +
        '</div>' +
        '<div class="page-shell__content"><slot></slot></div>' +
      '</div>'
  }
})()
