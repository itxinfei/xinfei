/**
 * 小程序工程脚手架：生成三个端的 uni-app 工程骨架（除页面外的公共文件）。
 *
 * 为什么用脚本而不是手工复制模板目录：
 *   1. 三端除了名字、启动标题、登录页路径以外完全一致，手抄必然漂移；
 *   2. 以后加第 4 个端（比如供应商端）在 APPS 里加一行即可；
 *   3. 生成物全部入库可见，改过的地方 diff 得出来，不是黑盒。
 *
 * 用法：node miniapp/scripts/gen-app.mjs            # 三端全覆盖生成（会重置页面外的骨架）
 *      node miniapp/scripts/gen-app.mjs customer    # 只生成指定端
 * 注意：脚本不生成 pages.json 与 pages/**，那两处是各端真正的差异，手写维护。
 *      已存在的 pages.json / pages/** 不会被触碰。
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

/** 依赖版本对齐 uni-app 官方 vite 模板（3.x = Vue 3 运行时线） */
const UNI = '3.0.0-5020620260917001';

const APPS = {
  customer: { cnName: '心飞外卖', englishName: 'xinfeiTakeout', loginRoute: '/pages/login/login' },
  rider: { cnName: '心飞骑手', englishName: 'xinfeiRider', loginRoute: '/pages/login/login' },
  staff: { cnName: '心飞门店', englishName: 'xinfeiStaff', loginRoute: '/pages/login/login' }
};

const pkg = name => JSON.stringify({
  name: '@xinfei/mini-' + name,
  version: '0.1.0',
  private: true,
  description: APPS[name].cnName + ' 微信小程序（uni-app + Vue 3，Options API）',
  scripts: {
    'dev:mp-weixin': 'uni -p mp-weixin',
    'build:mp-weixin': 'uni build -p mp-weixin',
    'dev:h5': 'uni',
    'build:h5': 'uni build'
  },
  dependencies: {
    '@dcloudio/uni-app': UNI,
    '@dcloudio/uni-components': UNI,
    '@dcloudio/uni-h5': UNI,
    '@dcloudio/uni-mp-weixin': UNI,
    '@xinfei/mini-shared': 'workspace:*',
    vue: '^3.4.21'
  },
  devDependencies: {
    '@dcloudio/vite-plugin-uni': UNI,
    vite: '5.2.8'
  }
}, null, 2) + '\n';

const viteConfig = `import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

export default defineConfig({
  plugins: [uni()]
})
`;

/**
 * H5 壳的 index.html（照抄 uni-app 官方 vite 模板）。
 * 只在 build:h5 时生效，小程序构建不读这个文件；里面的 document.write 是官方
 * 原版片段，只写入一段固定字符串的 viewport meta，无动态输入，请勿"顺手清理"。
 */
const indexHtml = `<!DOCTYPE html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <script>
      var coverSupport = 'CSS' in window && typeof CSS.supports === 'function' && (CSS.supports('top: env(a)') ||
        CSS.supports('top: constant(a)'))
      document.write(
        '<meta name="viewport" content="width=device-width, user-scalable=no, initial-scale=1.0, maximum-scale=1.0, minimum-scale=1.0' +
        (coverSupport ? ', viewport-fit=cover' : '') + '" />')
    </script>
    <title>__TITLE__</title>
    <!--preload-links-->
    <!--app-context-->
  </head>
  <body>
    <div id="app"><!--app-html--></div>
    <script type="module" src="/src/main.js"></script>
  </body>
</html>
`;

const mainJs = `import { createSSRApp } from 'vue'
import App from './App.vue'

export function createApp() {
  const app = createSSRApp(App)
  return { app }
}
`;

const appVue = name => {
  const cfg = APPS[name];
  return `<script>
import { initRequest } from '@xinfei/mini-shared'

export default {
  onLaunch() {
    // 401 时由请求层统一回登录页；各端在此声明自己的登录路由
    initRequest({ loginRoute: '${cfg.loginRoute}' })
  }
}
</script>

<style>
/* 设计令牌：由 scripts/sync-tokens.mjs 从 src/main/resources/shared/styles/tokens.css 同步 */
@import '@xinfei/mini-shared/styles/tokens.css';
</style>
`;
};

const manifest = name => JSON.stringify({
  name: APPS[name].cnName,
  appid: '',
  description: APPS[name].cnName + ' 小程序端，与 Web 端共用同一套后端接口',
  versionName: '0.1.0',
  versionCode: '100',
  transformPx: false,
  'mp-weixin': {
    appid: '',
    setting: { urlCheck: false, es6: true, minified: true },
    usingComponents: true,
    __usePrivacyCheck__: true,
    permission: {
      'scope.userLocation': { desc: '用于配送定位与附近门店展示' }
    },
    requiredPrivateInfos: ['getLocation']
  },
  vueVersion: '3',
  uniStatistics: { enable: false }
}, null, 2) + '\n';

const uniScss = `/* uni-app 内置组件样式变量入口；品牌色一律走 tokens.css 的自定义属性，禁止在此写 hex */
`;

const envExample = `# 后端地址（小程序必须是绝对地址；真机要求 HTTPS + 已在公众平台配业务域名）
# 本机微信开发者工具联调可关掉「不校验合法域名」直接用 http://127.0.0.1:8080
VITE_API_BASE=http://127.0.0.1:8080
`;

const gitkeep = '';

function writeFile(dir, rel, content) {
  const target = path.join(dir, rel);
  fs.mkdirSync(path.dirname(target), { recursive: true });
  fs.writeFileSync(target, content, 'utf8');
}

function genOne(name) {
  const dir = path.join(ROOT, 'apps', name);
  writeFile(dir, 'package.json', pkg(name));
  writeFile(dir, 'vite.config.js', viteConfig);
  writeFile(dir, 'index.html', indexHtml.replace('__TITLE__', APPS[name].cnName));
  writeFile(dir, 'src/main.js', mainJs);
  writeFile(dir, 'src/App.vue', appVue(name));
  writeFile(dir, 'src/manifest.json', manifest(name));
  writeFile(dir, 'src/uni.scss', uniScss);
  writeFile(dir, 'src/static/.gitkeep', gitkeep);
  writeFile(dir, '.env.example', envExample);
  const pagesJson = path.join(dir, 'src', 'pages.json');
  console.log('已生成骨架 apps/' + name
    + (fs.existsSync(pagesJson) ? '（pages.json 与 pages/** 未改动）' : '（缺 pages.json，页面请手写）'));
}

const requested = process.argv.slice(2).filter(a => Object.prototype.hasOwnProperty.call(APPS, a));
if (process.argv.slice(2).length && !requested.length) {
  console.error('未知端：' + process.argv.slice(2).join(' ') + '；可选 ' + Object.keys(APPS).join(' / '));
  process.exit(1);
}
(requested.length ? requested : Object.keys(APPS)).forEach(genOne);
