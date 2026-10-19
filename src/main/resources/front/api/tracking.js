// 配送追踪相关接口
// 查询配送订单详情（用于追踪页面）
function queryDeliveryTracking(orderId) {
  return $axios({
    url: '/api/delivery/tracking/' + orderId,
    method: 'get'
  })
}

// 获取地图引擎配置（引擎类型 + 浏览器端 AK）；未配置 Key 时 enabled=false，页面降级为文字版
function getMapConfig() {
  return $axios({
    url: '/api/map/config',
    method: 'get'
  })
}

