/**
 * 付费券包 API（O-18）
 */

const couponPackagePage = (params) => $axios({ url: '/coupon-package/page', method: 'get', params })

const saveCouponPackage = (data) => $axios({ url: '/coupon-package', method: 'post', data })

const couponPackageStatus = (id, status) =>
  $axios({ url: '/coupon-package/' + id + '/status/' + status, method: 'put' })

const deleteCouponPackage = (id) => $axios({ url: '/coupon-package/' + id, method: 'delete' })
