/**
 * 壳层导出任务实时通道（O-25 收尾）——仅 index.html 引入。
 *
 * 与 front/js/request.js 的 C 端 SSE 客户端同构：登录后建立一条同源 EventSource
 * （自动带 JSESSIONID，GET 无需 CSRF），监听 "export-task" 终态事件弹右上角通知，
 * 无需打开导出中心轮询。浏览器原生自动重连；连续失败达到上限主动放弃，
 * 由页面刷新重新初始化（防会话失效后无限重试刷日志）。
 */
;(function () {
  var win = window
  if (win.__xinfeiExportSSE || typeof win.EventSource === 'undefined') { return }

  var MAX_CONSECUTIVE_ERRORS = 5
  var consecutiveErrors = 0
  var es
  try {
    es = new win.EventSource('/export/tasks/subscribe')
  } catch (e) { return }
  win.__xinfeiExportSSE = es

  es.addEventListener('connected', function () { consecutiveErrors = 0 })

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

  es.onerror = function () {
    consecutiveErrors++
    if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
      // 会话失效/服务不可达：关闭通道交给整页刷新重连，避免无限重试
      try { es.close() } catch (e) {}
      win.__xinfeiExportSSE = null
    }
    // 未达上限时浏览器原生重连（自带退避）
  }
})()
