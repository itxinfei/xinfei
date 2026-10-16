<script>
import { initRequest } from '@xinfei/mini-shared'

export default {
  onLaunch() {
    // 401 时由请求层统一回登录页；各端在此声明自己的登录路由
    initRequest({ loginRoute: '/pages/login/login' })
  }
}
</script>

<style>
/* 设计令牌：由 scripts/sync-tokens.mjs 从 src/main/resources/shared/styles/tokens.css 同步 */
@import '@xinfei/mini-shared/styles/tokens.css';
</style>
