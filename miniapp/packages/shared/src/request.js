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
 * 鉴权通道（2026-10-22 后端 5.1/5.2 已落地）：登录成功时后端在响应头下发 X-Auth-Token，
 * 本层捕获后存进 STORAGE_KEYS.authToken，后续请求以同名头回带；后端 LoginCheckFilter
 * 在无会话时凭该头恢复登录态。顾客端短信验证码已迁 Redis（sms:code:<phone>），
 * 发码与登录跨会话可校验，三端登录链均已全链路可用。
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

        // 5.1 Token 会话通道：登录响应头下发会话标识，捕获后由后续请求统一回带
        const issuedToken = headerValue(res.header, AUTH_HEADER);
        if (issuedToken) write(STORAGE_KEYS.authToken, issuedToken);

        if (res.statusCode === 401) {
          toLogin();
          return reject(Object.assign(new Error('NOTLOGIN'), { notLogin: true }));
        }
        if (res.statusCode < 200 || res.statusCode >= 300) {
          // 后端 400(参数校验)/422(CustomException 业务失败)/429(限流) 仍返回标准 R 体，
          // 必须透出 body.msg（如"积分不足"），不能一律盖成"服务异常（422）"；
          // 读不到 R 体（网关错误/非 JSON）时再回退状态码文案
          const errBody = res.data;
          const bizMsg = errBody && typeof errBody === 'object' ? errBody.msg : '';
          const msg = bizMsg || ('服务异常（' + res.statusCode + '）');
          if (!opts.silent) toast(msg);
          return reject(Object.assign(new Error(msg), {
            statusCode: res.statusCode,
            data: errBody && typeof errBody === 'object' ? errBody.data : undefined
          }));
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
        // 业务失败也可能携带结构化 data（如配送追踪 R.error 带 data.reason=EAT_IN/NO_DELIVERY），
        // 挂到 Error 上供调用方在 silent 模式下判别处理
        const bizError = Object.assign(new Error(body.msg || '业务失败'), { data: body.data });
        if (opts.silent) return reject(bizError);
        toast(body.msg);
        return reject(bizError);
      },
      fail: err => {
        if (!opts.silent) toast('网络不可用，请检查后端地址与网络');
        reject(err);
      }
    });
  });
}

/**
 * uni.uploadFile 版的统一上传：走 /common/upload，登录态与 CSRF 头同 request 规则
 * （uni.uploadFile 不经过 request 封装，必须自己补 X-Auth-Token / X-CSRF-Token）。
 * 后端 /common/upload 已兼容 token 通道（CommonController hasEmployeePrincipal/hasUserPrincipal）。
 *
 * @param {Object} opts
 * @param {string} opts.filePath  本地临时文件路径（uni.chooseImage 的 tempFilePaths[i]）
 * @param {string} [opts.bizType] 业务目录（ImageStoragePathResolver：avatar/evaluation/chat 等）
 * @param {string} [opts.name='file'] 文件表单字段名，后端固定 file
 * @returns {Promise<string>} resolve 上传后的服务器相对路径（R.data）
 */
export function uploadFile(opts) {
  const headers = {};
  const authToken = read(STORAGE_KEYS.authToken);
  if (authToken) headers[AUTH_HEADER] = authToken;
  const csrf = read(STORAGE_KEYS.csrfToken);
  if (csrf) headers[CSRF_HEADER] = csrf;

  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: getBaseUrl() + '/common/upload',
      filePath: opts.filePath,
      name: opts.name || 'file',
      formData: opts.bizType ? { bizType: opts.bizType } : {},
      header: headers,
      success: res => {
        let body = null;
        try { body = JSON.parse(res.data); } catch (e) { /* 非 JSON 响应按失败处理 */ }
        if (res.statusCode === 401 || (body && body.msg === 'NOTLOGIN')) {
          toLogin();
          return reject(Object.assign(new Error('NOTLOGIN'), { notLogin: true }));
        }
        if (res.statusCode !== 200 || !body || body.code !== 1 || !body.data) {
          return reject(new Error((body && body.msg) || '上传失败，请重试'));
        }
        resolve(body.data);
      },
      fail: () => reject(new Error('网络不可用，上传失败'))
    });
  });
}

export const http = {
  get: (url, data, extra) => request(Object.assign({ url, method: 'GET', data }, extra)),
  post: (url, data, extra) => request(Object.assign({ url, method: 'POST', data }, extra)),
  put: (url, data, extra) => request(Object.assign({ url, method: 'PUT', data }, extra)),
  del: (url, data, extra) => request(Object.assign({ url, method: 'DELETE', data }, extra))
};
