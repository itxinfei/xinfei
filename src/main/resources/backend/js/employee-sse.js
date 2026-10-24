/**
 * 壳层员工 SSE 实时通道（O-25 导出任务推送 / O-26 库存预警推送）——仅 index.html 引入。
 *
 * 与 front/js/request.js 的 C 端 SSE 客户端同构：登录后建立一条同源 EventSource
 * （自动带 JSESSIONID，GET 无需 CSRF），监听后端推送的事件弹右上角通知：
 *   - export-task     导出任务终态（成功/失败，O-25）
 *   - inventory-alert 库存预警状态变迁（进入/恢复，O-26）
 * 浏览器原生自动重连；连续失败达到上限主动放弃，由页面刷新重新初始化
 * （防会话失效后无限重试刷日志）。
 */
;(function () {
  var win = window
  if (win.__xinfeiEmployeeSSE || typeof win.EventSource === 'undefined') { return }

  var MAX_CONSECUTIVE_ERRORS = 5
  var consecutiveErrors = 0
  var es
  try {
    es = new win.EventSource('/export/tasks/subscribe')
  } catch (e) { return }
  win.__xinfeiEmployeeSSE = es

  es.addEventListener('connected', function () { consecutiveErrors = 0 })

  // ── O-25：导出任务终态 ──
  es.addEventListener('export-task', function (ev) {
    var t = null
    try { t = JSON.parse(ev.data) } catch (e) { return }
    if (!t) return
    if (Number(t.status) === 2) {
      if (win.XinfeiUI && win.XinfeiUI.notify) {
        win.XinfeiUI.notify({
          title: '导出任务完成',
          message: (t.fileName || '文件已生成') + '，可在顶栏「导出中心」下载',
          type: 'success', duration: 6000, position: 'top-right'
        })
      }
    } else if (Number(t.status) === 3) {
      if (win.XinfeiUI && win.XinfeiUI.notify) {
        win.XinfeiUI.notify({
          title: '导出任务失败',
          message: t.errorMsg || '请到「导出中心」查看原因或重试',
          type: 'error', duration: 8000, position: 'top-right'
        })
      }
    }
  })

  // ── O-26：库存预警状态变迁（边沿触发，无变迁不推） ──
  es.addEventListener('inventory-alert', function (ev) {
    var a = null
    try { a = JSON.parse(ev.data) } catch (e) { return }
    if (!a || !win.XinfeiUI || !win.XinfeiUI.notify) return
    var items = a.items || []
    var detail = items.map(function (i) {
      return i.name + '（' + i.stockQty + '/' + i.minStock + '）'
    }).join('、')
    if (a.kind === 'entered') {
      win.XinfeiUI.notify({
        title: '库存预警',
        message: a.count + ' 项物料低于安全库存' + (detail ? '：' + detail : '')
          + (a.count > items.length ? ' 等' : ''),
        type: 'warning', duration: 8000, position: 'top-right'
      })
    } else if (a.kind === 'recovered') {
      win.XinfeiUI.notify({
        title: '库存已恢复',
        message: a.count + ' 项物料回到安全库存以上',
        type: 'success', duration: 6000, position: 'top-right'
      })
    }
  })

  es.onerror = function () {
    consecutiveErrors++
    if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
      // 会话失效/服务不可达：关闭通道交给整页刷新重连，避免无限重试
      try { es.close() } catch (e) {}
      win.__xinfeiEmployeeSSE = null
    }
    // 未达上限时浏览器原生重连（自带退避）
  }
})()
