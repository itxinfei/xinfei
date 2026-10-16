import { STORAGE_KEYS } from './config';

export function read(key) {
  try {
    return uni.getStorageSync(key) || null;
  } catch (e) {
    return null;
  }
}

export function write(key, value) {
  try {
    uni.setStorageSync(key, value);
  } catch (e) {
    /* 存储满或不可用时不阻断请求 */
  }
}

export function remove(key) {
  try {
    uni.removeStorageSync(key);
  } catch (e) {
    /* 同上 */
  }
}

export function clearSession() {
  [STORAGE_KEYS.authToken, STORAGE_KEYS.csrfToken,
    STORAGE_KEYS.currentUser, STORAGE_KEYS.currentEmployee, STORAGE_KEYS.currentRider]
    .forEach(remove);
}
