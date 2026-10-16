import { getBaseUrl } from './config';

/**
 * 状态与来源字典（三端共用，单一真源）。
 * 对齐关系：
 *   ORDER    ← com.xinfei.enums.OrderStatus + shared/js/order-status.js
 *   DELIVERY ← com.xinfei.enums.DeliveryOrderStatus + shared/js/order-status.js
 *   SOURCE   ← com.xinfei.enums.OrderSource
 *   REFUND   ← com.xinfei.enums.RefundStatus + shared/js/refund-status.js
 * cls 沿用 Web 三端的类名，样式里的颜色一律引 tokens.css，禁止再写 hex。
 */

export const ORDER = {
  1: { text: '待付款', cls: 'status-pending' },
  2: { text: '待接单', cls: 'status-accepted' },
  3: { text: '配送中', cls: 'status-shipped' },
  4: { text: '已完成', cls: 'status-completed' },
  5: { text: '已取消', cls: 'status-cancelled' },
  6: { text: '已退款', cls: 'status-refunding' },
  7: { text: '已分账', cls: 'status-completed' }
};

export const DELIVERY = {
  PENDING: { text: '待接单', cls: 'status-pending', desc: '商家正在确认订单' },
  ACCEPTED: { text: '已接单', cls: 'status-accepted', desc: '商家已接单，正在准备' },
  PICKING: { text: '取餐中', cls: 'status-accepted', desc: '骑手正在取餐' },
  DELIVERING: { text: '配送中', cls: 'status-shipped', desc: '骑手正在配送' },
  DELIVERED: { text: '已送达', cls: 'status-completed', desc: '订单已送达' },
  CANCELLED: { text: '已取消', cls: 'status-cancelled', desc: '订单已取消' }
};

export const SOURCE = {
  TAKEOUT: '外卖配送',
  EAT_IN: '堂食扫码',
  QUEUE: '排队',
  RESERVATION: '预订',
  SELF_PICKUP: '到店自提'
};

/** 后端 code 大小写不统一（SUCCESS 大写、其余小写），按大写归一化查表 */
export const REFUND = {
  PENDING: { code: 'pending', text: '退款申请中', cls: 'st-pending' },
  PROCESSING: { code: 'processing', text: '退款处理中', cls: 'st-processing' },
  SUCCESS: { code: 'SUCCESS', text: '退款成功', cls: 'st-success' },
  FAIL: { code: 'fail', text: '退款失败', cls: 'st-fail' },
  REJECTED: { code: 'rejected', text: '审核拒绝', cls: 'st-rejected' }
};

export function orderText(code) {
  return ORDER[code] ? ORDER[code].text : '未知状态';
}

export function orderClass(code) {
  return ORDER[code] ? ORDER[code].cls : '';
}

export function deliveryText(status) {
  const hit = status && DELIVERY[String(status).toUpperCase()];
  return hit ? hit.text : '未知状态';
}

export function refundText(status) {
  if (!status) return '';
  const hit = REFUND[String(status).toUpperCase()];
  return hit ? hit.text : String(status);
}

export function sourceText(source) {
  return SOURCE[source] || source || '';
}

/**
 * 图片地址归一：后端存的是相对路径（如 /images/xxx.jpg），小程序 <image> 必须绝对地址。
 * 与 shared/js/img-path.js 同一语义。
 */
export function fullImage(path) {
  if (!path) return '';
  if (/^(https?:)?\/\//.test(path) || path.indexOf('data:') === 0) return path;
  return getBaseUrl() + (path.charAt(0) === '/' ? path : '/' + path);
}
