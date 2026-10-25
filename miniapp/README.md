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

### apps/customer（24 页已落，Web 侧 26 页全部覆盖）

| 页面 | 接口 | 状态 |
|---|---|---|
| `pages/login/login` | `POST /user/sendMsg`、`POST /user/login`、`POST /user/wxLogin`、`POST /user/loginout` | 已落（含微信一键登录） |
| `pages/index/index` | `GET /category/list?type=1`、`GET /dish/list?categoryId=`、`POST /shopping-cart/add`、`POST /shopping-cart/sub`、`GET /shopping-cart/list`、`GET /dish/list`（搜索全量） | 已落（「去结算」进结算页；O-42 口味弹层——带口味菜品「选规格」每组单选拼串；O-46 菜品搜索——首输懒拉全店本地跨分类过滤） |
| `pages/order/list` | `GET /order/userPage`、`POST /order/again`、`PUT /order/userCancel?id=`、`PUT /order/userConfirmReceipt?id=` | 已落（整卡进详情，快捷操作 `.stop` 防冒泡） |
| `pages/order/checkout` | `GET /restaurant/info`、`GET /address-book/default`、`GET /address-book/{id}`、`GET /shopping-cart/list`、`POST /api/order/preview`、`GET /front/coupon/usable`、`POST /order/submit`、`POST /api/payment/pay`、`GET /api/payment/user/query/{tradeNo}` | 已落（**P0-1 订阅授权**：下单成功 requestSubscribeMessage，模板 ID 留空安全降级；**配送上门/到店自提双模式**：自提 `source=SELF_PICKUP`、地址豁免、配送费 0、不起送价/不校验范围，下单即生成取餐码并跳详情；自提门店卡仅展示名称/营业时间/公告，`/restaurant/info` 不返回地址；**微信 JSAPI 在线支付已接通**——payMethod=2 下单后调 shared/pay.js 闭环，含 0 元单自动完成、支付结果 2s 轮询回查、mock 沙箱模拟回调分支；**结算选券抵扣已接**——`/front/coupon/usable` 按商品额拉可用券，preview/submit 带 `usedCouponId`，券失效自动清空重核；货到/到店付款 payMethod=6 保留；支付宝小程序内无 JSAPI 能力保持灰显；不含预约配送/会员余额） |
| `pages/order/detail` | `GET /order/{id}`、`GET /order/userRefundRecords?id=`、`POST /order/again`、`POST /order/userApplyRefund?id=&reason=`、`PUT /order/userCancel?id=`、`PUT /order/userConfirmReceipt?id=`、`POST /api/urgency/customer/trigger/{id}`、`GET /api/dish-evaluation/order/{id}`、`GET /api/rider-evaluation/order/{id}?riderId=`、`POST /api/payment/pay`、`GET /api/payment/user/query/{tradeNo}` | 已落（状态横幅/履约信息/价格明细/售后记录；状态 4 按查重结果显示「去评价/评价骑手」入口；**状态 1 显示「去支付」**，复用 shared/pay.js 调起 JSAPI 并轮询回查，取消/超时/失败均不离开详情；发票、客服入口待对应页面落地） |
| `pages/order/evaluate` | `GET /order/{id}`、`GET /api/dish-evaluation/order/{id}`、`POST /api/dish-evaluation`（逐菜）、`GET/POST /api/rider-evaluation/order/{id}` | 已落（同菜多风味合并为一条评价；星级必填+快捷标签+200 字文本+整单匿名；部分失败可重试剩余项；骑手评价独立提交；**评价图片已接**——shared/uploadFile 走 X-Auth-Token 通道（后端 CommonController 双通道鉴权早已支持，AuthTokenChannelTest#userTokenGrantsUploadWithoutSession 实测覆盖；README 旧记载「待后端改造」系文档滞后，2026-10-24 核实修正）） |
| `pages/delivery/tracking` | `GET /api/delivery/tracking/{orderId}` | 已落（原生 `<map>` GCJ-02 渲染骑手位置+轨迹，WGS-84 历史点内置转换；15s 轮询，终态/堂食/自提停轮，onHide 停 onShow 续） |
| `pages/user/user` | `GET /user/info`、`GET /address-book/list`、`GET /order/userPage`（角标） | 已落 |
| `pages/address/list` | `GET /address-book/list`、`PUT /address-book/default`、`DELETE /address-book?ids=` | 已落（支持 `?select=1` 选择模式，经 `customerSelectedAddressId` 一次性交接） |
| `pages/address/edit` | `GET /address-book/{id}`、`POST/PUT /address-book`、`GET /region/children?parentId=`、`DELETE /address-book?ids=` | 已落（新增/编辑同页；detail 与经纬度由后端拼接/地理编码） |
| `pages/member/member` | `GET /api/member/member/my-info`、`GET /api/member/member/my-levels`、`GET /api/member/member/my-points`、`GET /api/member/member/my-recharges`、`POST /api/member/portal/open`、`POST /api/member/portal/recharge/create`、`GET /api/member/portal/recharge/status/{no}`、`POST /api/payment/recharge/pay`、mock 沙箱 `POST /api/payment/notify/WECHAT`（均经 shared/pay.js） | 已落（会员卡：等级/折扣徽章/积分/储值余额/券数/升级进度；非会员一键免费开通；积分明细与充值记录分页；**在线充值已接微信 JSAPI**——快捷金额 50/100/200/500 + 自定义 1~5000 元，建充值单→支付→轮询充值单 SUCCESS，回调自动入余额；非会员不显示充值入口） |
| `pages/coupon/coupon` | `GET /front/coupon/available`、`GET /front/coupon/my`、`POST /front/coupon/claim/{templateId}`、`POST /front/coupon/exchange/{templateId}`、`GET /api/member/member/my-info` | 已落（**O-46 临期感知**：unused 态 3 天内到期置顶+即将过期角标；可领取：免费领/积分兑换，按 `templateId` 去重已领置灰，兑换前本地积分预检+二次确认；我的券：unused/used/expired 前端三段，unused「去使用」回点餐首页；非会员显开通引导；支持 `?tab=mine` 跳入） |
| `pages/favorite/list` | `GET /api/user-favorite/my`、`POST /api/user-favorite/toggle` | 已落（我的收藏；失效收藏可清理——取消路径后端不再校验菜品存在性） |
| `pages/user/profile` | `GET /user/info`、`PUT /user/info`、`POST /common/upload`（bizType=avatar） | 已落（头像更换/昵称/性别；手机号只读脱敏展示；保存后同步本地缓存；生日无实体字段，Web 版亦无） |
| `pages/message/message` | `GET /recommend/messages`、`GET /recommend/messages/unread-count`、`PUT /recommend/messages/{id}/read`、`PUT /recommend/messages/read-all` | 已落（分页+未读角标+全部已读+详情弹层；SSE 实时推送不适用小程序，靠下拉刷新兜底） |
| `pages/history/browse` | `GET /recommend/browse-history?limit=30` | 已落（近 30 条足迹，行为标签；后端无清空端点，仅「去逛逛」） |
| `pages/order/frequent` | `GET /order/userPage`（≤3 页聚合）、`POST /shopping-cart/add` | 已落（近 300 单按菜品/套餐聚合计次取前 20；单品一键再加购；带口味明细按无口味加购） |
| `pages/order/evaluations` | `GET /api/dish-evaluation/user/my` | 已落（我的评价只读分页列表：星级/图片九宫/商家回复/审核状态；提交通道在 `pages/order/evaluate`，本页不重复提供） |

| `pages/invoice/invoice` | `GET /order/userPage?status=4`、`GET /order/{id}`、`GET /invoice/title/list`、`POST /invoice/title/save`、`DELETE /invoice/title/{id}`、`GET /invoice/order/{orderId}`、`POST /invoice/apply/{orderId}` | 已落（选单+抬头管理（企业税号校验）+已有发票记录，详情页可带参直入；发票 PDF 查看不迁——`/common/download` 鉴权只认 Session） |
| `pages/invoice/my` | `GET /invoice/my/page` | 已落（分页+状态四色标签，整卡跳开发票页） |
| `pages/service/service` | `/cs/portal/**`（会话/消息/投诉/评价）+ `GET /order/userPage` | 已落（双 tab：在线客服（4s 轮询、快捷问题、自动已读）+ 投诉工单（五类型/关联订单/满意度评价）；图片消息/投诉凭证已接（chooseImage→shared/uploadFile bizType=chat；private 目录图 <image> 无法带头，经 imgCache 带 X-Auth-Token 下载到本地后展示——与 Web 版同口径）） |
| `pages/ai/assistant` | `GET /api/ai/scene-config`、`POST /api/ai/chat`、`GET /api/ai/conversations/{id}` | 已落（对话+推荐菜品卡+conversationId 持久化恢复；SSE 流式不迁（uni.request 无法消费 POST+SSE），走非流式同链路，历史互通；多会话侧板/反馈不迁） |
| `pages/queue/queue` | `GET /api/dining/queue/customer/my`、`POST /api/dining/queue/customer/take`、`PUT /api/dining/queue/customer/{id}/cancel` | 已落（取号/15s 叫号轮询/取消，与 Web 一一对应） |
| `pages/reservation/reservation` | `POST /api/dining/reservation/customer`、`GET /api/dining/reservation/customer/my`、`PUT /api/dining/reservation/customer/{id}/cancel` | 已落（预订表单+我的预订+取消；桌台由商家安排不传 tableId） |
| `pages/nowifi/no-wifi` | 无 | 已落（纯 CSS 插画 + 网络检测 + 重试/回首页） |

「我的」页已接入全部功能入口（资料/常购/评价/足迹/消息/发票/排队/订座/客服/AI）。

不迁：`qrcode-order`（扫码点餐，依赖桌台码入口，暂缓）、`pay-cashier` / `pay-success`（已由 `shared/pay.js` 支付闭环取代）。

### apps/rider（9 页已落，覆盖 Web 侧 8 页的全部功能）

| 页面 | 接口 | 状态 |
|---|---|---|
| `pages/login/login` | `POST /api/rider/login` | 已落 |
| `pages/hall/hall` | `GET /api/rider/tasks/hall`、`POST /api/rider/tasks/{id}/grab` | 已落（O-46 新单声震：前台 10s 轮询 diff→震动+提示音+Toast；后台推送依赖订阅消息骑手模板，登记） |
| `pages/task/task` | `GET /api/rider/tasks/mine?scope=todo\|delivering\|history`、`accept`/`pickup?pickupCode=`/`deliver` | 已落 |
| `pages/income/income` | `GET /api/rider/income/summary`、`GET /api/rider/income/records?range&page&size` | 已落 |
| `pages/me/me` | `GET /api/rider/me`、`online`/`offline`/`location`、`POST /api/rider/logout` | 已落（含评价/异常/消息/结算 4 个入口，消息带未读角标） |
| `pages/evaluation/evaluation` | `GET /api/rider-evaluation/received`、`GET /api/rider-evaluation/rider/{id}/stats` | 已落（评分 hero+真分页列表，匿名脱敏/标签/商家回复；Web 版一次拉 100 条改为真分页） |
| `pages/exception/exception` | `POST /api/rider-exception`、`GET /api/rider-exception/mine`、`GET /api/rider/tasks/mine` | 已落（选单上报+工单进度列表；Web 的转单弹窗属任务域未迁，异常类型 5「申请转单」可达同目的） |
| `pages/message/message` | `GET /api/rider-message/mine`、`GET /api/rider-message/unread-count`、`PUT /api/rider-message/{id}/read`、`PUT /api/rider-message/read-all` | 已落（分页+未读数走专用接口+单条/全部已读） |
| `pages/settlement/settlement` | `GET /api/rider/settlement/balance`、`POST /api/rider/settlement/withdraw?amount=`、`GET /api/rider/settlement/withdrawals` | 已落（余额 hero+提现弹层（`qs()` 拼 URL）+记录列表；后端无最低金额配置、记录接口无分页参数，均按实际契约实现） |

> 位置自动上报：有在途单期间每 12s 上报一次 GCJ-02（`utils/location-reporter.js` 模块单例，接单即开、送完即停），App 切后台显式停表、回前台按 `/tasks/mine?scope=delivering` resync，定位连续失败 3 次自动停；「我的」页保留手动上报。顾客端 tracking 地图即消费该数据。

骑手端 Web 侧 8 页功能已全覆盖（结算在 Web 是 income.html 内一块，小程序独立成页）。

### apps/staff（7 页已落）

| 页面 | 接口 | 状态 |
|---|---|---|
| `pages/login/login` | `POST /employee/login` | 已落 |
| `pages/workbench/workbench` | `GET /api/dashboard/overview`、`GET /order/statistics` | 已落 |
| `pages/order/order` | `GET /order/page`、`PUT /order/confirm?id=`、`/order/reject?id=`、`/order/complete?id=`、`/order/cancel?id=&reason=` | 已落 |
| `pages/store/store` | `GET /store/detail/{tenantId}`、`PUT /store/{tenantId}/status?status=`、`PUT /store/{tenantId}/pause?pause=` | 已落 |
| `pages/me/me` | `GET /employee/me`、`POST /employee/logout` | 已落 |
| `pages/boss/boss`（O-43 老板驾驶舱） | `GET /api/dashboard/overview`、`GET /api/inventory/material/warning`、`GET /cashier/shift/page` | 已落（今日营业额大字+订单/客单/完成率+待退款+库存预警 Top5 比值排序+今日交班摘要；工作台入口） |

后台其余 77 页不在小程序范围（菜品/套餐/分类维护、报表、库存、营销、财务、系统设置等），继续走 Web 后台。

## 5. 后端前置改造（**不做完这三件，小程序上不了线**）

### 5.1 会话通道：Cookie → Token（✅ 2026-10-22 已落地）

~~原状：登录态是 Tomcat `HttpSession` + `JSESSIONID`（`HttpOnly`、`SameSite=strict`），小程序没有
cookie 容器，除公开接口外一律 401~~。

已按方案 1 落地（`com.xinfei.module.auth.service.AuthTokenService` + `LoginCheckFilter` token 分支）：

- 登录成功（`/employee/login`、`/api/rider/login`、`/user/login`）响应头下发 `X-Auth-Token`
  （SecureRandom 256bit），载荷（主体类型/ID/租户ID/角色）存 Redis，键 `auth:token:<token>`，
  TTL 默认 72h（`xinfei.auth-token.ttl-hours` 可配）；
- 客户端存 storage 后以 `X-Auth-Token` 头回带（`packages/shared/src/request.js` 已统一捕获与回带）；
  `LoginCheckFilter` 在无会话时凭该头恢复上下文，Web/H5 的 Session 通道零改动共存；
- 登出（`/employee/logout`、`/api/rider/logout`、`/user/loginout`）吊销令牌；Redis 降级时颁发失败
  仅不下发头、校验失败回落 401（口径同 R-01）；
- ~~顾客端登录链仍不通~~ → 5.2 已落地，三端均全链路可用。
- 测试：`AuthTokenChannelTest`（RANDOM_PORT 真实容器三端链路，经
  `xinfei.test.keep-auth-filters=true` 保留过滤器链）+ `LoginCheckFilterTest` token 分支单元用例。

### 5.2 顾客端验证码存在 HttpSession（✅ 2026-10-22 已落地）

~~原状：`UserController#sendMsg` 把验证码写进 `session.setAttribute("smsCode_" + phone)`，`#login` 再从同一 session 取，小程序发码与登录两个请求不落在同一 session，登录链整条不通~~。

已落地：新增 `com.xinfei.module.user.service.SmsCodeService`，验证码迁 Redis 键 `sms:code:<phone>`（TTL 5 分钟，由 TTL 承担过期判断）：

- `save`：Redis 优先；Redis 不可用回落 HttpSession（键不变，R-01 降级口径，Web 端零感知）；
- `verifyAndConsume`：Redis 命中常量时间比较、通过即删（一次性）；未命中回落 session 原逻辑；
  三种提示文案（请先获取验证码 / 验证码已过期 / 验证码错误）保持不变；
- 消费方：`/user/login`、`/employee/forgot-password`、`/api/rider/forgot-password`（骑手重置密码与顾客共用 `/user/sendMsg` 下发的同一码，跨端生效）；
- 频控三层结构保留：Redis 冷却 60s（跨会话）→ Redis 快计数 → DB 台账权威兜底。
- 测试：`AuthTokenChannelTest#customerSmsLoginFullChainViaToken`（发码 → Redis 取码 → 无 session 登录 → token → /user/info 全链路）；既有 session 造码用例经「未命中回落 session」路径零改动兼容。

### 5.3 微信支付：JSAPI + openid（✅ 2026-10-22 全链已接通，真机待配）

**openid 登录半链 ✅ 2026-10-22：**

- `user` 表新增 `openid` 列（`db/migration/V10__add_user_openid.sql`，`uk_user_openid` 唯一键）；
- `/user/wxLogin`：`wx.login` 临时 code 经 `WxCode2SessionClient.code2Session`（appid/secret 走
  `xinfei.wechat-miniapp.*` 配置）换 openid；已绑定直接登录（建会话 + 下发 X-Auth-Token），
  未绑定返回 `needBind` + 一次性 `wxTicket`（Redis 键 `wxbind:ticket:*`，5 分钟 TTL），
  手机验证码登录携带 ticket 完成绑定，之后凭 openid 直接登录；
- 绑定语义：绑定段在建立会话**之前**执行，冲突（微信已绑其他账号 / 本账号已绑微信）才拒绝且不发 token；
  ticket 过期/已消费降级为普通登录（手机+验证码已通过，不阻塞），仅跳过本次绑定；
- openid 不回传前端（两个登录方法返回前均脱敏）；链路测试见 `WxLoginChannelTest`。

**JSAPI 支付半链 ✅ 2026-10-22（前后端均已接通，货到付款与微信在线支付均可完整下单）：**

- 后端 `WechatV3PayChannel` 产出 JSAPI 五参（`appId/timeStamp/nonceStr/package=prepay_id/signType=RSA/paySign` 自签）；
  `POST /api/payment/pay` body `{orderId, channel:"WECHAT", payType:"JSAPI"}`，`payer.openid` 从登录态 `user.openid` 取、
  不接受前端传值，未绑微信返回业务拒绝；0 元单（券抵扣后实付 0）由 `completeZeroAmountPayment` 同步完成、无支付单号。
- 小程序闭环在 `packages/shared/src/pay.js`（checkout 与 detail 复用）：创建支付单 → `uni.requestPayment` 调起 →
  `GET /api/payment/user/query/{tradeNo}` 每 2s 轮询、最多 90 次（3 分钟），**只以后端支付单 SUCCESS 为支付成功依据**，
  不信前端调起成功回调；用户取消不报错（订单已建，落详情页可续付），FAIL/CLOSED 提示失败，轮询窗口超时提示稍后自查。
- mock 沙箱（`payment.mock-mode=true`）：mock 渠道不产 JSAPI 参数，pay.js 按 H5 收银台同契约打
  `POST /api/payment/notify/WECHAT`（`{out_trade_no, total_fee:分}`）模拟回调后再轮询回查，开发期可无证书走完整链路。
- 接线：结算页 payMethod=2（微信支付）下单后立即调起；订单详情 status=1 显示「去支付」可对同一订单重试。
- 会员充值同链已通（2026-10-22）：`POST /api/payment/recharge/pay`（bizType=RECHARGE）补齐 JSAPI openid 接线
  （从登录用户档案取，与订单支付同口径；`RechargePaymentController` 无 openid 即拒绝）；pay.js 的
  `payRechargeByWechat` 轮询 `GET /api/member/portal/recharge/status/{no}` 直到充值单 SUCCESS（回调 RECHARGE
  分支自动加余额，入账失败自动退款）；会员中心弹层支持快捷金额与自定义 1~5000 元。
- 真机待办（用户后期自测）：`manifest.json` 填 appid、后端配小程序 appid/secret 与商户证书、后台给租户配置微信支付渠道；
  支付宝小程序内无 JSAPI 能力（`RealAlipayChannel` 仅 NATIVE/PC/WAP），结算页保持灰显。

### 5.4 其余环境约束

- **域名**：小程序只允许 HTTPS 且需在微信公众平台配「业务域名 / request 合法域名」；本机 `http://127.0.0.1:8080` 仅在开发者工具关掉「不校验合法域名」后可用。
- **appid**：`apps/*/src/manifest.json` 的 `mp-weixin.appid` 留空，填各自小程序 appid。
- **图片**：后端返回的是相对路径，`enums.js#fullImage` 统一拼成绝对地址；`<image>` 不支持 `var()`。
- **上传**：`<input type=file>` 不存在，走 `uni.chooseImage` + `uni.uploadFile`（后端上传接口不变）。
- **跨端样式**：`sync-tokens.mjs` 会把 `:root {` 改写成 `page, :root {`，WXSS 里自定义属性挂在 `page` 上才生效；改令牌请改 Web 侧真源后重跑脚本，别改同步产物。

## 6. 已知缺口（诚实记录）

- 顾客端结算页（`pages/order/checkout`）已接通：配送上门/到店自提双模式、地址交接、`POST /api/order/preview` 同源核价、货到/到店付款、微信 JSAPI 与会员储值（BALANCE 同步渠道，2026-10-24 接通）三种支付、结算选券抵扣（`GET /front/coupon/usable` + `usedCouponId`）、期望送达时间（2026-10-24 接通：尽快送达或预约今天/明天/后天四时段，`expectDeliveryTime` 与 H5 同口径透传）。会员中心在线充值（微信 JSAPI，回调自动入余额）已接通。支付宝小程序内无 JSAPI 能力保持灰显。~~自提门店地址 `/restaurant/info` 未出网~~（2026-10-24 已出网：接口返回 `tenant.address`，小程序门店卡与 H5 自提提示均展示，无值不渲染）。
- ~~骑手在线状态、门店「暂停接单」的当前值不在详情接口里回传~~（2026-10-24 核实已修：staff 门店页读 `/store/detail` 的 `pauseOrder`、骑手「我的」页读 `/rider/auth/me` 的 `status`，进页即正确回显）。
- ~~`packages/shared` 未做单元测试~~（2026-10-24 已补：`test/shared.test.mjs` 9 用例，`npm test` 覆盖 R.code 解包 / CSRF 头读写 / 401 回登录 / qs / 枚举 / 登录缓存；源文件相对导入已补 `.js` 扩展以兼容 Node 原生 ESM）。
- 付费券包（O-18，2026-10-24）：优惠券页「可领取」顶部新增券包购买区（`/api/coupon-package/list|buy` + `payCouponPackageByWechat` 支付闭环，支付成功自动发放到「我的券」）；一期包内每模板限一张，权益卡形态未做（H5 券包入口已于 2026-10-24 接通）。
