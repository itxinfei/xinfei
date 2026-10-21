/* ============================================================
 * Vue 3 + Vant 4 装配层（C 端与骑手端共用）
 * ============================================================
 * 后台那套的对应文件是 js/app-v3.js（装 Element Plus），本文件只把 UI 库换成 Vant，
 * 其余职责一致：页面侧把
 *   Vue.use(vant); new Vue({ el: '#app', ... }).$mount('#app')
 * 改成
 *   createVantApp({ el: '#app', ... })
 * el 从 options 里取出、在装配完成后自行 mount —— Vue 3 的 createApp 返回的是 app
 * 而不是 vm，旧代码里的 .$mount 链必须换成 .mount，所以每页差异面缩到这一个标识符。
 *
 * 装配内容（顺序即依赖）：Vant 安装 → 全局 mixin（common.js 导出的 imgPath/cssVar 等）
 * → C 端共享组件（components-cend-v3.js 导出的注册表；骑手端不引该文件，自然跳过）。
 *
 * Vant 4 的 global 构建默认语言就是 zh-CN，不像 Element Plus 必须显式传 locale。
 * ============================================================ */
(function () {
  'use strict'

  if (!window.Vue || !window.Vue.createApp) {
    throw new Error('createVantApp 需要 Vue 3 全局构建（backend/plugins/vue3/vue.global*.js），且必须先于本文件加载')
  }

  function registerVant (app) {
    if (window.vant) app.use(window.vant)
  }

  function registerGlobalMixin (app) {
    if (window.XinfeiGlobalMixinCendV3) app.mixin(window.XinfeiGlobalMixinCendV3)
  }

  function registerSharedComponents (app) {
    var comps = window.XinfeiComponentsCendV3
    if (!comps) return
    Object.keys(comps).forEach(function (name) { app.component(name, comps[name]) })
  }

  window.createVantApp = function (options) {
    var opts = options || {}
    var selector = opts.el
    delete opts.el

    var app = Vue.createApp(opts)

    registerVant(app)
    registerGlobalMixin(app)
    registerSharedComponents(app)

    if (selector) app.mount(selector)
    return app
  }
})()
