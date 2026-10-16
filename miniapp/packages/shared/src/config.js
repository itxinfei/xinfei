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
  /** 后端 token 鉴权通道落地后写入；当前 session cookie 通道下为空 */
  authToken: 'xinfeiAuthToken',
  csrfToken: 'xinfeiCsrfToken',
  /** 登录态载体：顾客 user / 员工 employee / 骑手 rider，字段名与后端会话属性一致 */
  currentUser: 'xinfeiCurrentUser',
  currentEmployee: 'xinfeiCurrentEmployee',
  currentRider: 'xinfeiCurrentRider',
  baseUrl: 'xinfeiApiBase'
};

/** 写操作需带的 CSRF 头，取值与后端 CsrfFilter.CSRF_HEADER_NAME 对齐 */
const CSRF_HEADER = 'X-CSRF-Token';

/** 预留：后端补 token 通道后由它承载会话标识 */
const AUTH_HEADER = 'X-Auth-Token';

const WRITE_METHODS = ['post', 'put', 'delete', 'patch'];

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

export { DEFAULT_BASE_URL, STORAGE_KEYS, CSRF_HEADER, AUTH_HEADER, WRITE_METHODS, getBaseUrl };
