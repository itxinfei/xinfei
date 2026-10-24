<script>
import { initRequest } from '@xinfei/mini-shared'
import { locationReporter } from './utils/location-reporter'

export default {
  onLaunch() {
    // 401 时由请求层统一回登录页；各端在此声明自己的登录路由
    initRequest({ loginRoute: '/pages/login/login' })
  },
  onShow() {
    // 回前台（含冷启动）：按是否有在途单决定是否恢复位置上报
    locationReporter.resync()
  },
  onHide() {
    // 进后台：系统会挂起定时器，显式停掉；回前台由 onShow resync 恢复
    locationReporter.stop()
  }
}
</script>

<style>
/* 设计令牌：由 scripts/sync-tokens.mjs 从 src/main/resources/shared/styles/tokens.css 同步 */
@import '@xinfei/mini-shared/styles/tokens.css';

/* 全局重置：去除原生 button 默认边框（::after 伪元素） */
button::after {
  border: none;
}

/* tabBar 页高度基准：H5 端 page 已扣除 tabBar 高度，100% 不会被遮挡 */
page {
  height: 100%;
}

/* 全局触摸反馈：可点击元素统一加 hover-class="hover-press" */
.hover-press {
  opacity: 0.65;
}
</style>
