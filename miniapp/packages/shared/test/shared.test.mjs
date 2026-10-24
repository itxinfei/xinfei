import { test } from 'node:test';
import assert from 'node:assert/strict';

/**
 * @xinfei/mini-shared 最小用例集（O-13，2026-10-24）。
 * 覆盖请求层三个契约行为 + qs + 枚举字典 + 登录态缓存写入：
 *   1. R.code 解包：code=1 resolve data，code=0 业务失败 reject 且透出 msg；
 *   2. CSRF 头：响应头 X-CSRF-Token 捕获存储，写操作回带；
 *   3. 401 未登录：清会话并触发 onUnauthorized 回登录；
 *   4. qs()：@RequestParam 语义拼 URL（跳过空值、?/& 正确）。
 *
 * 运行：miniapp/packages/shared 下 `npm test`（node --test，Node ≥22.7 的
 * ESM 语法探测；本仓库锁定 Node 24）。uni.* 全部以内存 stub 替代。
 */

const store = new Map();
const toasts = [];
const pending = [];
let unauthorizedCalls = 0;

globalThis.uni = {
  getStorageSync: k => (store.has(k) ? store.get(k) : ''),
  setStorageSync: (k, v) => store.set(k, v),
  removeStorageSync: k => store.delete(k),
  showToast: o => toasts.push(o),
  reLaunch: () => {},
  request: opts => pending.push(opts)
};

const { request, qs, initRequest } = await import('../src/request.js');
const { STORAGE_KEYS, CSRF_HEADER, AUTH_HEADER } = await import('../src/config.js');
const { ORDER, REFUND, SOURCE } = await import('../src/enums.js');
const { customerAuth } = await import('../src/auth.js');

/** 取最近一次 uni.request 调用并以给定响应触发其 success 回调 */
function respond(res) {
  const opts = pending.pop();
  assert.ok(opts, 'uni.request 未被调用');
  setImmediate(() => opts.success(res));
  return opts;
}

initRequest({ onUnauthorized: () => { unauthorizedCalls++; } });

test('qs：拼 @RequestParam 语义 query，跳过空值，已有 ? 用 &', () => {
  assert.equal(qs('/order/userCancel', { id: 3 }), '/order/userCancel?id=3');
  assert.equal(qs('/store/1/pause', { pause: true, from: null, to: undefined, extra: '' }),
    '/store/1/pause?pause=true');
  assert.equal(qs('/a?x=1', { y: 2 }), '/a?x=1&y=2');
  assert.equal(qs('/a', {}), '/a');
});

test('请求成功：R.code=1 解包 resolve data', async () => {
  const p = request({ url: '/restaurant/info' });
  const opts = respond({ statusCode: 200, data: { code: 1, msg: '', data: { name: '心飞外卖' } } });
  assert.equal(opts.url, 'http://127.0.0.1:8080/restaurant/info');
  assert.equal(opts.method, 'GET');
  assert.deepEqual(await p, { name: '心飞外卖' });
});

test('业务失败：R.code=0 reject 且透出 msg，toast 一次；silent 不 toast', async () => {
  toasts.length = 0;
  const p = request({ url: '/order/submit', method: 'POST', data: {} });
  respond({ statusCode: 200, data: { code: 0, msg: '积分不足', data: null } });
  await assert.rejects(p, e => e.message === '积分不足');
  assert.equal(toasts.length, 1);

  toasts.length = 0;
  const p2 = request({ url: '/order/submit', method: 'POST', silent: true });
  respond({ statusCode: 200, data: { code: 0, msg: '库存不足', data: { reason: 'NO_STOCK' } } });
  await assert.rejects(p2, e => e.message === '库存不足' && e.data && e.data.reason === 'NO_STOCK');
  assert.equal(toasts.length, 0);
});

test('HTTP 422：透出后端 R 体 msg，不盖成"服务异常"', async () => {
  toasts.length = 0;
  const p = request({ url: '/order/submit', method: 'POST' });
  respond({ statusCode: 422, data: { code: 422, msg: '起送价不满足' } });
  await assert.rejects(p, e => e.message === '起送价不满足');
});

test('CSRF：响应头捕获存储，写操作回带 X-CSRF-Token', async () => {
  store.delete(STORAGE_KEYS.csrfToken);
  const p1 = request({ url: '/cart/add', method: 'POST' });
  const sent = respond({ statusCode: 200, header: { 'X-CSRF-Token': 'csrf-abc' }, data: { code: 1, data: 1 } });
  await p1;
  assert.equal(store.get(STORAGE_KEYS.csrfToken), 'csrf-abc');
  assert.equal(sent.header[CSRF_HEADER], undefined, '捕获前不应携带');

  const p2 = request({ url: '/cart/add', method: 'POST' });
  const sent2 = respond({ statusCode: 200, data: { code: 1, data: 1 } });
  await p2;
  assert.equal(sent2.header[CSRF_HEADER], 'csrf-abc', '写操作须回带 CSRF');
});

test('Token：登录响应头 X-Auth-Token 捕获后，后续请求统一回带', async () => {
  store.delete(STORAGE_KEYS.authToken);
  const p1 = request({ url: '/user/login', method: 'POST' });
  respond({ statusCode: 200, header: { 'X-Auth-Token': 'tok-1' }, data: { code: 1, data: { id: 1 } } });
  await p1;

  const p2 = request({ url: '/user/info' });
  const sent = respond({ statusCode: 200, data: { code: 1, data: {} } });
  await p2;
  assert.equal(sent.header[AUTH_HEADER], 'tok-1');
});

test('401 未登录：清会话 + onUnauthorized 回调 + notLogin 标记', async () => {
  store.set(STORAGE_KEYS.authToken, 'tok-stale');
  store.set(STORAGE_KEYS.currentUser, { id: 9 });
  const before = unauthorizedCalls;
  const p = request({ url: '/user/info' });
  respond({ statusCode: 401, data: { msg: 'NOTLOGIN' } });
  await assert.rejects(p, e => e.notLogin === true);
  assert.equal(unauthorizedCalls, before + 1, '应触发 onUnauthorized');
  assert.equal(store.has(STORAGE_KEYS.authToken), false, '会话 token 应被清除');
  assert.equal(store.has(STORAGE_KEYS.currentUser), false, '登录态应被清除');
});

test('枚举字典：订单/退款/来源与后端枚举对齐', () => {
  assert.equal(ORDER[1].text, '待付款');
  assert.equal(ORDER[6].text, '已退款');
  // 后端 code 大小写不统一，字典按大写归一化查表
  assert.equal(REFUND.SUCCESS.code, 'SUCCESS');
  assert.equal(SOURCE.SELF_PICKUP, '到店自提');
});

test('customerAuth.login：成功后写入 currentUser 缓存', async () => {
  store.delete(STORAGE_KEYS.currentUser);
  const p = customerAuth.login('13800000000', '123456');
  respond({ statusCode: 200, header: { 'X-Auth-Token': 'tok-2' }, data: { code: 1, data: { id: 990001, phone: '13800000000' } } });
  const user = await p;
  assert.equal(user.id, 990001);
  const cached = store.get(STORAGE_KEYS.currentUser);
  assert.equal(cached && cached.id, 990001, '登录态应写入 storage');
});
