# miniapp — 微信小程序工程（uni-app）

把仓库里的三个 H5 端重写为微信小程序。**这是重写，不是转换**：现有端是无构建的 Vue 2 静态页，
小程序要求 Vue 3 + Vite 编译产物、无 DOM、无 cookie 容器，页面一律按接口重新实现。

技术栈：uni-app（`@dcloudio/*` 3.0.0-5020620260917001）+ Vue 3 + Vite 5.2.8 + pnpm workspace，
**Options API、纯 JS**（不引入 `<script setup>` / TypeScript），与本仓库 Web 端的写法习惯保持一致，降低维护认知成本。

> 与 `agents.md` 的「禁止 Vue 3 / Composition API / TypeScript / Vite」不冲突：那条约束的对象是
> `src/main/resources/{backend,front,rider}` 这些**无编译步骤、由 Spring Boot 直接伺服**的静态端。
> 小程序必须编译，无法遵守同一条，故本目录是显式例外。

## 1. 端清单（2026-10-17 实测）

| Web 端 | 位置 | 页面数 | 对应小程序 app | 说明 |
|---|---|---|---|---|
| 管理后台 | `src/main/resources/backend/` | **82 个 html / 35 个业务组** | `apps/staff`（门店端，仅移动高频场景） | 后台不做完整小程序：82 页的表单/图表密度不适合手机，只迁「看单—接单—完成—门店状态」 |
| 顾客端 | `src/main/resources/front/` | **26 个 html** | `apps/customer` | 点餐主链路 |
| 骑手端 | `src/main/resources/rider/` | **8 个 html** | `apps/rider` | 与 Web 端功能基本一一对应 |
| 官网落地页 | `src/main/resources/static/index.html` | 1 | 不迁 | 小程序不需要 |
| 公共层 | `src/main/resources/shared/` | — | `packages/shared` | 请求层/枚举/设计令牌的真源仍在 Web 侧，令牌由脚本同步 |

## 2. 目录

```
miniapp/
├── pnpm-workspace.yaml
├── package.json                 # 根脚本：gen / sync:tokens / dev:* / build:*
├── scripts/
│   ├── gen-app.mjs              # 生成三个 app 的骨架（pages.json 与页面请手写）
│   └── sync-tokens.mjs          # shared/styles/tokens.css → packages/shared/src/styles/tokens.css
├── packages/shared/             # @xinfei/mini-shared：request / auth / enums / storage / config / tokens
└── apps/{customer,rider,staff}/ # @xinfei/mini-{customer,rider,staff}
```

## 3. 命令

```bash
cd miniapp
pnpm install                       # 首装
pnpm sync:tokens                   # Web 侧令牌变更后执行
pnpm -F @xinfei/mini-customer dev:mp-weixin     # 产物在 apps/customer/dist/dev/mp-weixin，用微信开发者工具导入
pnpm -F @xinfei/mini-customer build:mp-weixin   # 生产构建
pnpm build                          # 三个端一起构建
```

调试用 H5 更快（有 cookie 容器，登录链路直接通）：`pnpm -F @xinfei/mini-customer dev:h5`。

真机/开发者工具里切后端地址：`wx.setStorageSync('xinfeiApiBase', 'https://你的域名')` 后重启小程序，
优先级高于构建期的 `VITE_API_BASE`。

## 4. 页面迁移台账

本轮每个端只落「能跑通主链路的最小骨架」，其余页面按台账逐个补。接口路径与字段名逐条取自 Controller，未做改名。

### apps/customer（4 页已落）

| 页面 | 接口 | 状态 |
|---|---|---|
| `pages/login/login` | `POST /user/sendMsg`、`POST /user/login`、`POST /user/loginout` | 已落 |
| `pages/index/index` | `GET /category/list?type=1`、`GET /dish/list?categoryId=`、`POST /shopping-cart/add`、`POST /shopping-cart/sub`、`GET /shopping-cart/list` | 已落（结算按钮占位） |
| `pages/order/list` | `GET /order/userPage`、`POST /order/again`、`PUT /order/userCancel?id=`、`PUT /order/userConfirmReceipt?id=` | 已落 |
| `pages/user/user` | `GET /user/info`、`GET /address-book/list`、`GET /order/userPage`（角标） | 已落 |

待迁（Web 侧 `front/page/` 其余 22 页）：`add-order`（结算）、`address` / `address-edit`、`order-detail`、
`pay-cashier` / `pay-success`、`tracking`、`qrcode-order`、`member-center`、`coupon-center`、`invoice` / `my-invoice`、
`my-evaluations`、`my-favorites`、`message`、`queue`、`reservation`、`browse-history`、`frequent-orders`、
`customer-service`、`ai-assistant`、`no-wifi`、`user`（编辑资料）。

### apps/rider（5 页已落，覆盖 Web 侧 8 页的主要功能）

| 页面 | 接口 | 状态 |
|---|---|---|
| `pages/login/login` | `POST /api/rider/login` | 已落 |
| `pages/hall/hall` | `GET /api/rider/tasks/hall`、`POST /api/rider/tasks/{id}/grab` | 已落 |
| `pages/task/task` | `GET /api/rider/tasks/mine?scope=todo\|delivering\|history`、`accept`/`pickup?pickupCode=`/`deliver` | 已落 |
| `pages/income/income` | `GET /api/rider/income/summary`、`GET /api/rider/income/records?range&page&size` | 已落 |
| `pages/me/me` | `GET /api/rider/me`、`online`/`offline`/`location`、`POST /api/rider/logout` | 已落 |

待迁：`evaluation`（`/api/rider-evaluation/**`）、`exception`（`/api/rider-exception/**`）、
`message`（`/api/rider-message/**`）、结算提现（`/api/rider/settlement/**`）。

### apps/staff（5 页已落）

| 页面 | 接口 | 状态 |
|---|---|---|
| `pages/login/login` | `POST /employee/login` | 已落 |
| `pages/workbench/workbench` | `GET /api/dashboard/overview`、`GET /order/statistics` | 已落 |
| `pages/order/order` | `GET /order/page`、`PUT /order/confirm?id=`、`/order/reject?id=`、`/order/complete?id=`、`/order/cancel?id=&reason=` | 已落 |
| `pages/store/store` | `GET /store/detail/{tenantId}`、`PUT /store/{tenantId}/status?status=`、`PUT /store/{tenantId}/pause?pause=` | 已落 |
| `pages/me/me` | `GET /employee/me`、`POST /employee/logout` | 已落 |

后台其余 77 页不在小程序范围（菜品/套餐/分类维护、报表、库存、营销、财务、系统设置等），继续走 Web 后台。

## 5. 后端前置改造（**不做完这三件，小程序上不了线**）

### 5.1 会话通道：Cookie → Token（阻塞项，最优先）

现状：登录态是 Tomcat `HttpSession` + `JSESSIONID`（`HttpOnly`、`SameSite=strict`），见 `filter/LoginCheckFilter`。
小程序没有 cookie 容器，`uni.request` 不回传 `JSESSIONID`，**除公开接口外一律 401 `NOTLOGIN`**。
H5 调试能通（浏览器有 cookie），微信开发者工具与真机必挂——别在小程序里等它自己好。

可选方案（择一，前端 `packages/shared/src/request.js` 已按 `X-Auth-Token` 预留读取与回带）：

1. **推荐**：登录成功时把会话标识写进响应体（或响应头）返回，客户端存 storage 并以自定义头回带；
   `LoginCheckFilter` 增加「先读自定义头 → 再回落 cookie」的分支。改动面最小，H5 与小程序共存。
2. 引入无状态 token（JWT）：改动大，还要处理吊销与租户上下文的注入时机。
3. 小程序 `web-view` 内嵌现有 H5：不用改后端，但要求 H5 全站 HTTPS + 配置业务域名，且失去小程序原生体验。

### 5.2 顾客端验证码存在 HttpSession（随 5.1 一起改）

`UserController#sendMsg` 把验证码写进 `session.setAttribute("smsCode_" + phone)`，`#login` 再从同一 session 取。
即使补了 token 头，只要服务端 session 不共享，**顾客端登录链整条不通**。
建议改为 Redis 键（`sms:code:<phone>`，项目已有 `smsRateLimitRedisTemplate` 与 60s 冷却、每日上限，可直接并表）。

### 5.3 微信支付：JSAPI + openid

Web 侧走的是 H5/Native 支付。小程序必须 `WECHAT_PAY_JSAPI`，且要拿 `openid`：
`wx.login` → 临时 `code` → 后端 `code2Session`（`appid`+`secret`）换 `openid` 并持久化到用户。
落地前需先定：支付与退款分账链路沿用现有 `payment` 模块的哪个渠道实现、`openid` 存哪张表（`user` 表当前无该列，要开 V5+ 迁移）。

### 5.4 其余环境约束

- **域名**：小程序只允许 HTTPS 且需在微信公众平台配「业务域名 / request 合法域名」；本机 `http://127.0.0.1:8080` 仅在开发者工具关掉「不校验合法域名」后可用。
- **appid**：`apps/*/src/manifest.json` 的 `mp-weixin.appid` 留空，填各自小程序 appid。
- **图片**：后端返回的是相对路径，`enums.js#fullImage` 统一拼成绝对地址；`<image>` 不支持 `var()`。
- **上传**：`<input type=file>` 不存在，走 `uni.chooseImage` + `uni.uploadFile`（后端上传接口不变）。
- **跨端样式**：`sync-tokens.mjs` 会把 `:root {` 改写成 `page, :root {`，WXSS 里自定义属性挂在 `page` 上才生效；改令牌请改 Web 侧真源后重跑脚本，别改同步产物。

## 6. 已知缺口（诚实记录）

- 顾客端「结算」是占位弹窗：`/order/submit` 需要 `addressBookId` 与支付方式，地址簿页与支付尚未迁。
- 骑手在线状态、门店「暂停接单」的当前值不在详情接口里回传，页面只在写成功后本地回填，重进页面会回到默认值。
- `packages/shared` 未做单元测试；请求层的行为（CSRF 头读写、`R.code` 解包、401 回登录）目前只靠代码走查。
