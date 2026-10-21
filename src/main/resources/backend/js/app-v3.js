/* ============================================================
 * Vue 3 装配层（迁移期与 Vue 2 的 new Vue 并存）
 * ============================================================
 * 页面侧只需把 `new Vue({ el: '#x', ... })` 改成 `createXinfeiApp({ el: '#x', ... })`，
 * 其余不动：el 从 options 里取出来在装配完成后自行 mount，这样每页的差异面
 * 缩到一处标识符替换，转换脚本能机械完成，回滚也只是改回这一个标识符。
 *
 * 装配内容（顺序即依赖）：
 *   ElementPlus 安装 → 中文语言包 → 图标组件 → 共享业务组件
 * 语言包必须显式传：Element Plus 默认英文，旧 element-ui 内置中文，
 * 漏了会把分页/日期面板/MessageBox 按钮悄悄换成 OK、Cancel、January。
 * ============================================================ */
(function () {
  'use strict'

  if (!window.Vue || !window.Vue.createApp) {
    throw new Error('createXinfeiApp 需要 Vue 3 全局构建（plugins/vue3/vue.global*.js），且必须先于本文件加载')
  }

  /* Element Plus 的 global 图标包把 install 与 default 也挂在命名空间上，
     直接遍历会把它们注册成组件；只收真正的组件对象/函数组件。 */
  function registerIcons (app) {
    var ns = window.ElementPlusIconsVue
    if (!ns) return
    var raw = ns.icons || ns
    Object.keys(raw).forEach(function (name) {
      if (name === 'install' || name === 'default') return
      var comp = raw[name]
      if (comp && (comp.render || comp.setup || typeof comp === 'function')) {
        app.component(name, comp)
      }
    })
  }

  /* components-v3.js 把四个共享组件挂在这个对象上（Vue 3 没有 Vue.component 全局注册口） */
  function registerSharedComponents (app) {
    var comps = window.XinfeiComponentsV3
    if (!comps) return
    Object.keys(comps).forEach(function (name) { app.component(name, comps[name]) })
  }

  /* 全局格式化 mixin（formatMoney / formatDate / xinfeiStatusText …）。
     Vue 2 由 components.js 里的 Vue.mixin() 装到原型上，Vue 3 只能挂在 app 上，
     所以这里补一次；漏了的话所有页面模板里的 this.formatMoney 会变成 undefined 静默报错。 */
  function registerGlobalMixin (app) {
    if (window.XinfeiGlobalMixinV3) app.mixin(window.XinfeiGlobalMixinV3)
  }

  window.createXinfeiApp = function (options) {
    var opts = options || {}
    var selector = opts.el
    delete opts.el

    var app = Vue.createApp(opts)

    app.use(window.ElementPlus, {
      locale: window.ElementPlusLocaleZhCn
    })

    registerGlobalMixin(app)
    registerIcons(app)
    registerSharedComponents(app)

    if (selector) app.mount(selector)
    return app
  }
})()
