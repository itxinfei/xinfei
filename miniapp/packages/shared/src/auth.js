import { http } from './request';
import { STORAGE_KEYS } from './config';
import { read, write, clearSession } from './storage';

/**
 * 三端登录态：路径与字段名逐条取自后端 Controller，未做任何"顺手改名"。
 *   顾客端 /user/**        —— UserController（手机号 + 短信验证码，新用户自动注册）
 *   员工端 /employee/**    —— EmployeeController（用户名 + 密码，R<Map>）
 *   骑手端 /api/rider/**   —— RiderAuthController（手机号 + 密码，rememberMe 可选）
 *
 * 注意：/user/sendMsg 把验证码写进 HttpSession（属性名 smsCode_<phone>），
 * 小程序拿不到 JSESSIONID 时这条链路整条不通，属"后端前置改造"必须先解决的一项。
 */

export const customerAuth = {
  sendCode: phone => http.post('/user/sendMsg', { phone }),
  login: (phone, code) => http.post('/user/login', { phone, code }).then(user => {
    write(STORAGE_KEYS.currentUser, user);
    return user;
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
