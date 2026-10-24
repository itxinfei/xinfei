import { http } from './request.js';
import { STORAGE_KEYS } from './config.js';
import { read, write, clearSession } from './storage.js';

/**
 * 三端登录态：路径与字段名逐条取自后端 Controller，未做任何"顺手改名"。
 *   顾客端 /user/**        —— UserController（手机号 + 短信验证码 / 微信一键登录，新用户自动注册）
 *   员工端 /employee/**    —— EmployeeController（用户名 + 密码，R<Map>）
 *   骑手端 /api/rider/**   —— RiderAuthController（手机号 + 密码，rememberMe 可选）
 *
 * 2026-10-22 后端前置改造状态：
 *   5.1 已落地：登录响应头下发 X-Auth-Token（request.js 统一捕获回带），小程序无 cookie 也能保持登录态；
 *   5.2 已落地：验证码存 Redis（sms:code:<phone>），发码与登录跨会话可校验；
 *   5.3 仅落地 openid 登录半链：/user/wxLogin（code2Session），未绑定返回 needBind + wxTicket，
 *       手机号验证码登录携带 wxTicket 完成绑定，之后凭 openid 直接登录；JSAPI 支付半链尚未落地。
 *   绑定语义：ticket 过期/已消费时后端降级为普通登录（仅跳过绑定，不阻塞）；
 *             绑定冲突（微信已绑其他账号）才返回业务错误，且不建立会话。
 */

export const customerAuth = {
  sendCode: phone => http.post('/user/sendMsg', { phone }),
  /** wxTicket 可选：来自 wxLogin 的 needBind 场景，登录成功即完成 openid 绑定 */
  login: (phone, code, wxTicket) => http.post('/user/login',
    Object.assign({ phone, code }, wxTicket ? { wxTicket } : {})).then(user => {
    write(STORAGE_KEYS.currentUser, user);
    return user;
  }),
  /**
   * 微信一键登录（仅微信小程序可用，需后端配置 xinfei.wechat-miniapp.appid/secret）。
   * @returns {Promise<{needBind?:boolean, wxTicket?:string}|User>}
   *   needBind=true 时调用方暂存 wxTicket，引导用户走手机号验证码登录完成绑定；
   *   否则为用户对象（X-Auth-Token 已由 request.js 捕获存储）。
   */
  wxLogin: () => new Promise((resolve, reject) => {
    uni.login({
      provider: 'weixin',
      success: res => {
        if (!res || !res.code) {
          reject(new Error('wx.login 未返回 code'));
          return;
        }
        http.post('/user/wxLogin', { code: res.code }).then(data => {
          if (data && data.needBind) return resolve(data);
          write(STORAGE_KEYS.currentUser, data);
          resolve(data);
        }).catch(reject);
      },
      fail: err => {
        // 用户主动取消不打扰；uni.login 本身失败（网络/配置等，后端业务错误走 http 层已被 request.js toast）
        const errMsg = (err && err.errMsg) || ''
        if (errMsg.indexOf('cancel') !== -1) {
          reject(Object.assign(new Error('用户取消微信登录'), { cancelled: true }))
          return
        }
        reject(Object.assign(new Error('微信登录调用失败'), { wxLoginFail: true }))
      }
    });
  }),
  logout: () => http.post('/user/loginout').then(clearSession),
  me: () => http.get('/user/info', null, { silent: true }),
  cached: () => read(STORAGE_KEYS.currentUser)
};

export const employeeAuth = {
  login: (username, password) => http.post('/employee/login', { username, password }).then(data => {
    write(STORAGE_KEYS.currentEmployee, data && data.employee ? data.employee : data);
    return data;
  }),
  logout: () => http.post('/employee/logout').then(clearSession),
  me: () => http.get('/employee/me', null, { silent: true }),
  cached: () => read(STORAGE_KEYS.currentEmployee)
};

export const riderAuth = {
  login: (phone, password, rememberMe) =>
    http.post('/api/rider/login', { phone, password, rememberMe: !!rememberMe }).then(rider => {
      write(STORAGE_KEYS.currentRider, rider);
      return rider;
    }),
  logout: () => http.post('/api/rider/logout').then(clearSession),
  me: () => http.get('/api/rider/me', null, { silent: true }),
  online: () => http.post('/api/rider/online'),
  offline: () => http.post('/api/rider/offline'),
  reportLocation: data => http.post('/api/rider/location', data),
  cached: () => read(STORAGE_KEYS.currentRider)
};

export function isLogged(kind) {
  const key = { customer: STORAGE_KEYS.currentUser, staff: STORAGE_KEYS.currentEmployee, rider: STORAGE_KEYS.currentRider }[kind];
  return !!read(key);
}
