/**
 * 运行期配置：接口地址、鉴权头名、存储键。
 *
 * baseURL 解析优先级（小程序没有同源，必须绝对地址 + HTTPS + 业务域名白名单）：
 *   1. _storage_ 覆盖（真机调试时切后端地址，不用重新编译）
 *   2. 构建期 VITE_API_BASE（.env / .env.production）
 *   3. DEFAULT_BASE_URL 兜底（本机联调）
 * 凭据一律不进这里：appid/secret 走 manifest.json 与后端配置。
 */

const DEFAULT_BASE_URL = 'http://127.0.0.1:8080';

const STORAGE_KEYS = {
  /** 登录响应 X-Auth-Token 头的值；由 request.js 统一捕获写入，登出随 clearSession 清除 */
  authToken: 'xinfeiAuthToken',
  csrfToken: 'xinfeiCsrfToken',
  /** 登录态载体：顾客 user / 员工 employee / 骑手 rider，字段名与后端会话属性一致 */
  currentUser: 'xinfeiCurrentUser',
  currentEmployee: 'xinfeiCurrentEmployee',
  currentRider: 'xinfeiCurrentRider',
  /** 地址簿选择模式一次性交接：选中的地址 ID 回传来源页（消费即删） */
  customerSelectedAddressId: 'customerSelectedAddressId',
  baseUrl: 'xinfeiApiBase'
};

/** 写操作需带的 CSRF 头，取值与后端 CsrfFilter.CSRF_HEADER_NAME 对齐 */
const CSRF_HEADER = 'X-CSRF-Token';

/** 会话标识头（2026-10-22 后端 5.1 已落地：登录响应同名声头下发，请求时回带） */
const AUTH_HEADER = 'X-Auth-Token';

const WRITE_METHODS = ['post', 'put', 'delete', 'patch'];

/**
 * P0-1 订阅消息「订单状态通知」模板 ID（微信公众平台申请后填入）。
 * 空串 = 未配置：checkout 的 requestOrderPushAuth 直接跳过，后端发送同样静默——
 * 模板未申请时整条链路安全降级，不报错不打扰。
 */
const SUBSCRIBE_STATUS_TMPL = '';

function getBaseUrl() {
  try {
    const override = uni.getStorageSync(STORAGE_KEYS.baseUrl);
    if (override) return override;
  } catch (e) {
    /* 存储不可用时回落构建期配置 */
  }
  const fromEnv = import.meta.env && import.meta.env.VITE_API_BASE;
  return fromEnv || DEFAULT_BASE_URL;
}

export { DEFAULT_BASE_URL, STORAGE_KEYS, CSRF_HEADER, AUTH_HEADER, WRITE_METHODS, getBaseUrl, SUBSCRIBE_STATUS_TMPL };
