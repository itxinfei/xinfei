/**
 * 员工提成 API（O-17 收银提成口径）
 */

// 按员工聚合提成（时间范围筛选）
const getCommissionSummary = (params) => {
  return $axios({ url: '/commission/summary', method: 'get', params })
}

// 提成明细分页（员工/期间筛选）
const getCommissionPage = (params) => {
  return $axios({ url: '/commission/page', method: 'get', params })
}
