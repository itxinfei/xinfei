/**
 * 小程序三端共用的展示/格式化工具（Options API 友好，纯函数 + 数据工厂，无额外运行时依赖）。
 *
 * 1) 列表三态（loading / error / empty 互斥） —— listState()
 * 2) 写操作防连点（in-flight 锁）            —— busyLock()
 * 3) 金额展示统一                            —— fmtAmount()
 * 4) 百分比展示统一                          —— fmtPercent()
 *
 * 修改点(2026-10-17)：此前各端列表页 .catch(() => {}) 吞错，请求失败与"暂无数据"
 * 长得一样（toast 2s 即消失）；写操作按钮无 in-flight 锁，连点会重复提交；
 * 金额/比率各页口径不一（裸 {{ amount }} vs .toFixed(2) vs rate<=1?100:1 启发式）。
 * 本模块把三者收口为单一实现，三端页面统一接入。
 */

/**
 * 列表三态数据字段。用法：
 *   data() { return Object.assign(listState(), { records: [], loading: false }) }
 *   load(reset) {
 *     this.listError = false
 *     ...
 *     return http.get(...).then(...).catch(() => { this.listError = true }).then(() => { this.loading = false })
 *   }
 * 模板（三态互斥）：
 *   <view v-if="listError" class="list__error"><text>加载失败</text><text class="list__retry" @tap="load()">重试</text></view>
 *   <view v-if="!records.length && !loading && !listError" class="list__empty">暂无数据</view>
 * 请求层失败已有 toast，但 2s 即消失；listError 是持久错误态，
 * 让用户知道列表是"加载失败"而不是"本来就没数据"。
 */
export function listState() {
  return { listError: false }
}

/**
 * 写操作 in-flight 锁（防连点重复提交）。用法：
 *   data() { return Object.assign(busyLock(), { ... }) }
 *   grab(task) {
 *     const key = 'grab:' + task.id
 *     if (this.busy(key)) return
 *     this.setBusy(key, true)
 *     http.post(...).then(...).catch(() => {}).then(() => { this.setBusy(key, false) })
 *   }
 * 按钮锁定态视觉：:class="{ 'btn--off': busy('grab:' + t.id) }"
 */
export function busyLock() {
  return {
    _busy: {},
    busy(key) { return !!this._busy[key] },
    setBusy(key, on) { this._busy[key] = on }
  }
}

/** 金额统一两位小数（后端 BigDecimal 可能回 "25.5"，展示口径统一为 25.50） */
export function fmtAmount(v) {
  if (v === null || v === undefined || v === '') return '0.00'
  const n = Number(v)
  if (isNaN(n)) return '0.00'
  return n.toFixed(2)
}

/**
 * 百分比统一展示（保留一位小数）。
 * 注意：后端比率字段已是 0-100 的百分比值
 * （RiderIncomeSummaryVO.onTimeRate javadoc "百分比，0-100"，服务端实现已 ×100），
 * 展示时不再做量纲猜测——旧的 formatRate 启发式（rate<=1 ? 100 : 1）
 * 会把 0.5% 这类低准时率错误显示成 50%。
 */
export function fmtPercent(v) {
  if (v === null || v === undefined || v === '') return '-'
  const n = Number(v)
  if (isNaN(n)) return '-'
  return (Math.round(n * 10) / 10) + '%'
}
