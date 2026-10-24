// 收银管理 API

// 收银记录管理
const getCashierRecordList = (params) => $axios({ url: '/cashier/record/list', method: 'get', params })
const getCashierRecordByOrderId = (orderId) => $axios({ url: `/cashier/record/order/${orderId}`, method: 'get' })
const saveCashierRecord = (data) => $axios({ url: '/cashier/record', method: 'post', data })
const cashPayment = (params) => $axios({ url: '/cashier/cash-payment', method: 'post', params })
const deleteCashierRecord = (id) => $axios({ url: `/cashier/record/${id}`, method: 'delete' })
// 修改点(2026-09-18)：结算预览（服务端权威下发应付金额，消除前端自算与后端 BigDecimal 的口径/浮点差异）
const previewCheckout = (params) => $axios({ url: '/cashier/preview', method: 'get', params })
// 修改点(2026-09-18)：按桌台合并结账（一桌多单时一次性结清全部待付款订单并释放桌台）
const tablePayment = (params) => $axios({ url: '/cashier/table-payment', method: 'post', params })

// 日结管理
const getDailySettlementList = (params) => $axios({ url: '/cashier/settlement/list', method: 'get', params })
const getDailySettlementByDate = (date) => $axios({ url: `/cashier/settlement/date/${date}`, method: 'get' })
const executeDailySettlement = (params) => $axios({ url: '/cashier/settlement/execute', method: 'post', params })
const cancelDailySettlement = (params) => $axios({ url: '/cashier/settlement/cancel', method: 'post', params })
const deleteDailySettlement = (id) => $axios({ url: `/cashier/settlement/${id}`, method: 'delete' })

// 统计分析
const getCashierStatistics = (params) => $axios({ url: '/cashier/statistics', method: 'get', params })
const getPaymentTypeStatistics = (params) => $axios({ url: '/cashier/statistics/payment-type', method: 'get', params })
const getCashierTrend = (params) => $axios({ url: '/cashier/trend', method: 'get', params })
const getDailySettlementSummary = (params) => $axios({ url: '/cashier/settlement/summary', method: 'get', params })
// 交班对账汇总（O-22）：当日当前收银员的收款聚合
const getShiftSummary = () => $axios({ url: '/cashier/shift-summary', method: 'get' })

// O-29 交班台账：确认交班（落快照）+ 历史分页
const closeCashierShift = (remark) => $axios({ url: '/cashier/shift/close', method: 'post', data: { remark: remark || '' } })
const pageCashierShifts = (params) => $axios({ url: '/cashier/shift/page', method: 'get', params })
