import { http, isLogged } from '@xinfei/mini-shared'

/**
 * 骑手位置自动上报器（模块单例）。
 *
 * 背景：后端 POST /api/rider/location 注释明确「骑手端每 12s 定时上报，
 * 用于顾客配送追踪页地图与距离展示」，但小程序端此前只有「我的」页手动按钮，
 * 顾客 tracking 地图永远停在「获取骑手位置」。本模块补上定时上报。
 *
 * 设计约束：
 * 1. 仅有在途单（delivering 列表非空）期间运行，无单立即停表，省电省流量；
 * 2. 小程序切后台系统会挂起 JS 定时器，App.onHide 显式 stop，回前台 onShow resync；
 * 3. tab 页间切换不停（骑手可能在收入/我的页，配送仍在进行）；
 * 4. getLocation 被拒/系统定位关闭时连续失败达上限自动停表，不空转；
 *    任务页 onShow / 回前台 resync / 手动上报会再次唤醒；
 * 5. 自动请求一律 silent，12s 一轮的网络抖动不弹 toast 打扰骑手。
 */

// 与后端注释约定一致：12s 一轮；接口限流 5 次/秒，单骑手远触不到
const INTERVAL_MS = 12000
// getLocation 连续失败上限：拒绝授权后微信不再弹框，每次调用立即 fail，没有重试价值
const MAX_LOCATE_FAILS = 3

let timer = null
let locating = false
let locateFails = 0

function reportOnce() {
  if (locating) return
  locating = true
  uni.getLocation({
    type: 'gcj02',
    success: res => {
      locateFails = 0
      const payload = {
        longitude: res.longitude,
        latitude: res.latitude,
        // uni.getLocation type=gcj02 返回的就是国测局 GCJ-02 坐标，必须显式声明；
        // 不传时后端按 WGS-84 归一，会把 GCJ-02 错标成 WGS-84
        coordSystem: 'GCJ-02'
      }
      // speed/heading 为可选字段，部分机型/平台不返回（heading 可能是 NaN），有有效值才带
      if (typeof res.speed === 'number' && isFinite(res.speed) && res.speed >= 0) payload.speed = res.speed
      if (typeof res.heading === 'number' && isFinite(res.heading) && res.heading >= 0) payload.heading = res.heading
      http.post('/api/rider/location', payload, { silent: true })
        .catch(() => {})
        .then(() => { locating = false })
    },
    fail: () => {
      locating = false
      locateFails += 1
      if (locateFails >= MAX_LOCATE_FAILS) stop()
    }
  })
}

function start() {
  if (timer) return
  // 未登录（登录页/登出后）不开表，resync 也不会发鉴权请求
  if (!isLogged('rider')) return
  locateFails = 0
  reportOnce()
  timer = setInterval(reportOnce, INTERVAL_MS)
}

function stop() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
  locating = false
}

/**
 * 按「是否有在途单」校准定时器：任务页 load 完成后调用。
 * 只在 delivering 视图下校准——切到待接单/历史页不代表在途单消失，不能停。
 */
function sync(hasActiveOrders) {
  if (hasActiveOrders) start()
  else stop()
}

/**
 * 回前台 / 启动时自查：拉一次 delivering 列表决定启停。
 * 未登录直接停表且不发请求。
 */
function resync() {
  if (!isLogged('rider')) {
    stop()
    return
  }
  http.get('/api/rider/tasks/mine', { scope: 'delivering' }, { silent: true })
    .then(list => sync(Array.isArray(list) && list.length > 0))
    .catch(() => {})
}

export const locationReporter = {
  start,
  stop,
  sync,
  resync
}
