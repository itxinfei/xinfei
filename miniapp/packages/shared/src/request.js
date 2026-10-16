import { getBaseUrl, CSRF_HEADER, AUTH_HEADER, WRITE_METHODS, STORAGE_KEYS } from './config';
import { read, write, clearSession } from './storage';

/**
 * 统一请求层：对应 Web 端的 shared/js/request-core.js + 各端 js/request.js。
 *
 * 与后端约定的三件事（勿改，改了要同步 CsrfFilter / LoginCheckFilter）：
 *   1. 响应体固定为 com.xinfei.common.R：{ code, msg, data, timestamp, requestId }，code=1 成功、0 失败；
 *   2. CSRF token 由响应头 X-CSRF-Token 下发，写操作（POST/PUT/DELETE/PATCH）需原样带回；
 *   3. 未登录 = HTTP 401 且 body.msg='NOTLOGIN'。
 *
 * 鉴权通道现状：后端登录态是 Tomcat HttpSession + JSESSIONID（HttpOnly、SameSite=strict），
 * 小程序没有浏览器 cookie 容器，wx.request 不回传 JSESSIONID，因此
 * **除公开接口外，所有需要登录态的接口在小程序里都会拿到 401**，直到后端补出
 * 非 cookie 的会话通道（方案见 miniapp/README.md「后端前置改造」）。
 * 本层已按目标形态预留：服务端下发的会话标识存进 STORAGE_KEYS.authToken 并以
 * X-Auth-Token 头回带；后端未落地时该头不出现，公开接口不受影响。
 */

const options = { loginRoute: '', onUnauthorized: null };

/**
 * 后端有一批 @RequestParam 语义的写接口（/order/confirm?id=、/order/userCancel?id=、
 * /order/selfPickup/verify 的 DTO 除外），Web 端靠 axios 的 params 拼 query，
 * uni.request 没有 params 概念，只能自己拼进 URL。
 */
export function qs(path, params) {
  const parts = [];
  Object.keys(params || {}).forEach(k => {
    const v = params[k];
    if (v === undefined || v === null || v === '') return;
    parts.push(encodeURIComponent(k) + '=' + encodeURIComponent(v));
  });
  if (!parts.length) return path;
  return path + (path.indexOf('?') === -1 ? '?' : '&') + parts.join('&');
}

/** 各端在 main.js/App.vue 里初始化自己的登录页路径或回调 */
export function initRequest(cfg) {
  Object.assign(options, cfg || {});
}

function headerValue(header, name) {
  if (!header) return null;
  const lower = name.toLowerCase();
  const hit = Object.keys(header).find(k => k.toLowerCase() === lower);
  return hit ? header[hit] : null;
}

function toLogin() {
  clearSession();
  if (typeof options.onUnauthorized === 'function') {
    options.onUnauthorized();
  } else if (options.loginRoute) {
    uni.reLaunch({ url: options.loginRoute });
  }
}

function toast(msg) {
  uni.showToast({ title: msg || '请求失败', icon: 'none', duration: 2000 });
}

/**
 * @param {Object} opts
 * @param {string} opts.url        后端路径，以 / 开头
 * @param {string} [opts.method]   默认 GET
 * @param {Object} [opts.data]     GET 转 query，其余转 body
 * @param {boolean} [opts.silent]  失败不弹 toast，交由调用方处理
 * @returns {Promise<any>} resolve R.data
 */
export function request(opts) {
  const method = (opts.method || 'GET').toLowerCase();
  const headers = Object.assign({ 'Content-Type': 'application/json;charset=utf-8' }, opts.headers || {});

  const authToken = read(STORAGE_KEYS.authToken);
  if (authToken) headers[AUTH_HEADER] = authToken;
  if (WRITE_METHODS.indexOf(method) !== -1) {
    const csrf = read(STORAGE_KEYS.csrfToken);
    if (csrf) headers[CSRF_HEADER] = csrf;
  }

  return new Promise((resolve, reject) => {
    uni.request({
      url: getBaseUrl() + opts.url,
      method: method.toUpperCase(),
      data: opts.data,
      header: headers,
      timeout: opts.timeout || 30000,
      success: res => {
        const csrf = headerValue(res.header, CSRF_HEADER);
        if (csrf) write(STORAGE_KEYS.csrfToken, csrf);

        if (res.statusCode === 401) {
          toLogin();
          return reject(Object.assign(new Error('NOTLOGIN'), { notLogin: true }));
        }
        if (res.statusCode < 200 || res.statusCode >= 300) {
          const msg = '服务异常（' + res.statusCode + '）';
          if (!opts.silent) toast(msg);
          return reject(new Error(msg));
        }

        const body = res.data;
        if (!body || typeof body !== 'object') {
          return reject(new Error('响应体不是 JSON'));
        }
        if (body.code === 1) return resolve(body.data);
        if (body.msg === 'NOTLOGIN') {
          toLogin();
          return reject(Object.assign(new Error('NOTLOGIN'), { notLogin: true }));
        }
        if (opts.silent) return reject(new Error(body.msg || '业务失败'));
        toast(body.msg);
        return reject(new Error(body.msg || '业务失败'));
      },
      fail: err => {
        if (!opts.silent) toast('网络不可用，请检查后端地址与网络');
        reject(err);
      }
    });
  });
}

export const http = {
  get: (url, data, extra) => request(Object.assign({ url, method: 'GET', data }, extra)),
  post: (url, data, extra) => request(Object.assign({ url, method: 'POST', data }, extra)),
  put: (url, data, extra) => request(Object.assign({ url, method: 'PUT', data }, extra)),
  del: (url, data, extra) => request(Object.assign({ url, method: 'DELETE', data }, extra))
};
