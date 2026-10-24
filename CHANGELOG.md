# 变更记录（CHANGELOG）

格式：日期 | 模块 | 更新内容 | 开发者

## 2026-10-22 | 小程序 / 鉴权 | 5.2 验证码迁移 Redis：HttpSession → sms:code:<phone>
- 新增 `com.xinfei.module.user.service.SmsCodeService`：验证码存储统一入口。`save` 写 Redis 键 `sms:code:<phone>`（TTL 5 分钟，过期由 TTL 承担，替代原 session `_time` 时间戳比较）；Redis 不可用回落 HttpSession（键不变，R-01 降级口径）。`verifyAndConsume` Redis 命中常量时间比较（口径同 CsrfFilter P1-3）、通过即删（一次性使用）；未命中回落 session 原逻辑，兼容「发码时降级、校验时恢复」窗口。
- 消费方改造：`UserController#login`、`EmployeeController#verifyForgotPasswordSmsCode`、`RiderAuthController#forgotPassword` 均改走 service；骑手/员工忘记密码与顾客登录共用 `/user/sendMsg` 下发的同一码，跨会话/跨端（小程序）可校验。提示文案三种保持不变。
- `UserController#sendMsg` 写码与发送失败清理改走 service；频控三层结构保留（Redis 冷却 60s → Redis 快计数 → DB 台账权威兜底），会话级 60s 冷却在 Redis 路径由跨会话冷却键覆盖。
- 测试：`AuthTokenChannelTest` 新增 `customerSmsLoginFullChainViaToken`（发码 → Redis 取码 → 无 session 登录 → X-Auth-Token → /user/info，顾客端小程序登录链全链路打通）；`UserControllerTest` 取码改 Redis 并清理 `sms:code:*`；`RiderAuthControllerTest` setUp 清 `sms:code:*`（既有 session 造码用例经回落路径零改动兼容）。
- 回归实测：UserControllerTest 5 + SmsAntiAbuseGuardTest 4 + AuthTokenChannelTest 10 + RiderAuthControllerTest 20 + EmployeeControllerTest 19 + EmployeeLoginSecurityTest 4 + LoginCheckFilterTest 10，共 72 例全绿。
- 至此小程序三端（员工/骑手/顾客）登录链全部走通（5.1 Token 会话 + 5.2 验证码迁 Redis）。
- 开发者：AI

## 2026-10-22 | 小程序 / 鉴权 | 5.1 Token 会话通道：Cookie → X-Auth-Token
- 新增 `com.xinfei.module.auth.service.AuthTokenService`：登录颁发 / 校验 / 吊销非 cookie 会话令牌（SecureRandom 256bit），载荷（主体类型/ID/租户ID/角色）存 Redis 键 `auth:token:<token>`，TTL 默认 72h（`xinfei.auth-token.ttl-hours` 可配）；Redis 降级安全（颁发失败登录不受影响、校验失败回落 401，口径同 R-01）。
- `LoginCheckFilter` 新增 token 分支：会话三分支未命中后凭 `X-Auth-Token` 头恢复登录态（不创建 session，CSRF 既有语义不变）；员工/顾客/骑手分别写入 employeeId/userId/riderId request 属性，与 AOP 鉴权切面口径一致。
- 三端登录下发、登出吊销：`/employee/login`+`/employee/logout`、`/user/login`+`/user/loginout`、`/api/rider/login`+`/api/rider/logout`；`/user/info`（GET/PUT）补 BaseContext 回落（token 通道无 session）。
- 小程序侧：`packages/shared/src/request.js` 统一捕获响应头 `X-Auth-Token` 存 storage 并回带；`config.js` 注释同步。
- 测试基建：`MockMvcFilterExclusionAutoConfiguration` 支持属性 `xinfei.test.keep-auth-filters=true` 保留过滤器链（默认行为不变）。
- 测试：新增 `AuthTokenChannelTest`（RANDOM_PORT 真实容器，三端 9 例：登录下发/无会话访问/无效令牌 401/登出吊销）；`LoginCheckFilterTest` 补 token 分支 4 例；回归 EmployeeLoginSecurityTest + RiderAuthControllerTest + UserControllerTest 29 例全绿。
- 边界：顾客端登录链仍依赖 5.2（验证码尚存 HttpSession）；员工端/骑手端小程序已可全链路跑通。
- 开发者：AI

## 2026-09-28 | 骑手端 / 订单 | 修复计划 P0-6：取餐码核销（R9）
- 新增字段 `orders.pickup_code`（`schema.sql` + 迁移参考 `db/migration/V20260928__add_order_pickup_code.sql`）：6 位数字，在**派单（`dispatchOrder`）与抢单（`grabOrder`）**时生成，保证骑手到店前门店已持有可核销的取餐码。
- 核销：`OrderStatusFlowService.pickupRiderTask(orderId, riderId, pickupCode)` 增加取餐码入参，订单已有取餐码时**必须校验一致**方可取餐，否则抛"取餐码不正确，请向店员索取正确的取餐码"；`POST /api/rider/tasks/{id}/pickup` 新增可选参数 `pickupCode`。
- 兼容性：历史无取餐码订单（存量数据）放行不校验，避免被卡住；已有测试 `RiderOrderFlowTest.fullDispatchFlow` 同步改为携带取餐码取餐。
- 前端骑手端：`detail.html` 取餐改为弹窗输入 6 位取餐码核销（前端正则校验），取餐卡增加"需向店员索取取餐码"提示；`js/api.js` 的 `pickup` 传递取餐码；`css/rider.css` 增加提示样式。
- 管理端：`order/list.html` 详情弹窗展示取餐码（加粗、字距放大），便于店员报码给骑手核销。
- 测试：`RiderPickupCodeTest`（5 例）覆盖派单/抢单生成取餐码（6 位数字）、错误码与空码被拒且不记取餐时间、正确码取餐成功并记录取餐时间、历史无码订单放行；受影响回归 49 例（订单 7 + 配送 42）全绿。
- 开发者：AI

## 2026-09-28 | 跨端 / 订单 | 修复计划 P0-5：三端语义落地与取消/退款回执
- 缺陷修复：`cancelOrder` 此前把取消原因写进 `orders.remark`，**覆盖顾客下单备注**；现新增独立字段 `cancel_reason`（`schema.sql` + 迁移参考 `db/migration/V20260928__add_order_cancel_reason.sql`），取消/拒单均写入该字段（拒单固定为"商家拒单"），已支付取消分支也补写原因用于回执。
- 回执落地：顾客端 `order-detail.html` 在已取消(5)/已退款(6) 展示「取消原因」区块；后台 `order/list.html` 详情弹窗同步展示同一字段，两端同源可核对。
- 三端语义：新增 `shared/js/refund-status.js` 作为退款状态单一真源（对齐后端 `com.xinfei.enums.RefundStatus`；因后端 code 大小写不统一——`SUCCESS` 大写、其余小写——字典按大写归一化查表，大小写作不敏感）。顾客端移除内联映射改引用该字典；配套 `refund-status.test.js`（node 直接运行）锁定文案与大小写兼容。
- 测试：`OrderCancelReasonTest`（3 例）覆盖取消写原因且备注不丢、拒单写"商家拒单"、无原因时留空；受影响回归 44 例（订单 7 + 配送 37）全绿；`order-status.test.js` 未回归。
- 已知环境项：取消/更新类写接口偶发 MySQL 死锁——后台定时任务（`OrderTimeoutTask` 等）在测试期间并发更新 `orders`，与本类行级更新争锁（`DeadlockLoserDataAccessException`）。该测试已对写接口做 3 次有界重试；项目尚无测试期关闭定时任务的开关。
- 开发者：AI

## 2026-09-28 | 骑手端 / 配送 | 修复计划 P0-4：骑手消息中心（R5）
- 新增表 `rider_message`（骑手个人收件箱：类型 1派单提醒/2催单提醒/3异常处理/4系统公告/5其他；含 `is_read` 与 `read_time`），测试同步 `schema-delivery.sql`。
- 后端：`module/delivery` 新增 `RiderMessage`/Mapper/`RiderMessageService(+Impl)`/`RiderMessageController`；骑手端 `GET /api/rider-message/mine`、`/unread-count`、`PUT /{id}/read`、`PUT /read-all`；管理端 `POST /api/rider-message/broadcast`（向租户下全部骑手广播公告）。
- 消息挂钩（均宽异常兜底，消息写入失败不阻塞业务主流程）：店长派单 → 派单提醒；改派/转单 → 分别通知目标骑手与原骑手；顾客催单（`UrgencyServiceImpl.customerTrigger`）→ 提醒在途骑手；异常工单处理 → 结果回执给上报骑手。
- 前端骑手端：新增 `rider/message.html` 消息中心（列表/未读标记/单条已读/全部已读）；工作台加「消息中心」入口与未读红点角标（随 15s 轮询刷新）；`js/api.js` 增加消息接口；`css/rider.css` 增加角标样式。
- 测试：`RiderMessageTest`（4 例）覆盖派单挂钩写入、收件箱与未读数、标记已读（含幂等与跨骑手越权拒绝）、异常处理回执、公告广播与全部已读；受影响模块回归 47 例（含 `RiderOrderFlowTest`/`DeliveryServiceTest`/`RiderExceptionTest`/`RiderSettlementTest`/催单两类）全绿。
- 测试卫生：`RiderMessageTest`/`RiderExceptionTest` 增加 `@AfterEach` 清理订单/消息/工单——surefire 复用 JVM 且连真实 MySQL，遗留的待接单订单会跨类污染其他骑手测试的列表断言。
- 开发者：AI

## 2026-09-28 | 骑手端 / 配送 | 修复计划 P0-3：异常上报与转单改派（R3/R4）
- 新增表 `rider_exception_order`（异常工单：类型 1联系不上顾客/2商品破损/3地址有误/4顾客拒收/5申请转单/6其他；状态 0待处理/1已处理/2已转单/3已关闭），测试同步 `schema-delivery.sql`。
- 后端：`module/delivery` 新增 `RiderExceptionOrder`/Mapper/`RiderExceptionService(+Impl)`/`RiderExceptionController`；骑手端 `POST /api/rider-exception`（上报）、`GET /api/rider-exception/mine`、`GET /api/rider-exception/available-riders`（可转单骑手）；管理端 `GET /api/rider-exception/admin/page`、`/admin/count`、`PUT /{id}/handle`（RESOLVE/CLOSE/REASSIGN）、`POST /admin/reassign`（直接改派）。
- 改派能力：`OrderStatusFlowService.reassignRiderOrder(orderId, oldRiderId, newRiderId)` 行级 CAS（`rider_id=原骑手 AND status in (2,3)`）转移订单归属；新增 `DeliveryTrackingService.releaseRiderLoad(riderId)` 释放在途**不计入累计完成单量**（区别于 `adjustRiderLoad(-1)`），目标骑手 `adjustRiderLoad(+1)` 置忙；骑手转单 `POST /api/rider/tasks/{id}/transfer`。
- 前端骑手端：`detail.html` 增加「配送工具（上报异常/转单）」卡片与两个弹窗；新增 `exception.html`（我的异常工单列表）+ 工作台「我的异常」入口；`js/api.js` 增加异常/转单接口；`css/rider.css` 增加弹窗样式（复用设计令牌）。
- 管理端：新增 `page/delivery/rider-exception.html`（按状态/类型/订单号筛选、处理与改派），财务管理菜单加「骑手异常」。
- 测试：`RiderExceptionTest`（7 例）覆盖上报与校验、骑手/管理端查询、处理（解决/重复被拒）、管理端改派（归属转移+双方在途单量）、骑手转单与校验、可转单骑手列表；新增 7 例通过。
- 已知环境项：全量 `mvn test`（`reuseForks=true` 共享 JVM）在接近末尾时于 `module.withdraw` 出现 `OutOfMemoryError: GC overhead limit exceeded`（上下文重复重建累积），**非本次改动**；该 8 例单独运行通过。
- 开发者：AI

## 2026-09-28 | 骑手端 / 订单 | 修复计划 P0-1 增量1：骑手"我的收入"
- 新增骑手端「我的收入」页（今日/本周/本月汇总：配送费收入、完成单量、里程、准时率、平均时长、单均收入；含单笔配送费明细列表）。
- 新增后端只读聚合接口：`GET /api/rider/income/summary`、`GET /api/rider/income/records`。
- 收入数据完全基于现有 `orders.delivery_fee` 与 `delivery_time_record` 聚合，未新增表、未改动记账逻辑（可提现/结算单/提现留待 P0-1 增量2）。
- 工作台新增「我的收入」入口。
- 开发者：AI

## 2026-09-28 | 跨端状态机 S0
- 新增 `shared/js/order-status.js` 作为三端订单状态文案单一真源（主单 1–7 + 配送 6 态），并配套 `order-status.test.js` 单元测试。
- 骑手端 `util.js`、配送追踪页 `tracking.html`、顾客端 `order.html`/`common.js` 改为引用统一字典，消除三端文案不一致；骑手详情页按取餐态区分"取餐中/配送中"。
- 开发者：AI

## 2026-09-28 | 骑手端 / 结算 | 修复计划 P0-1 增量2：骑手可提现账户 + 提现（与会员余额解耦）
- 新增 3 张表：`rider_account`（骑手独立账户：可提现/冻结/累计收入/累计已提现）、`rider_income_ledger`（按 `order_id` 唯一，保证配送完成幂等入账）、`rider_withdrawal`（骑手提现申请）。测试同步 `src/test/resources/schema-delivery.sql`。
- 记账钩子：在既有 `OrderStatusFlowServiceImpl.deliverRiderOrder(...)`（同事务、状态守卫保证不重复）内调用 `RiderSettlementService.settle(...)`，按 `Orders.deliveryFee` 入账；复用 CAS 防双扣思路（参考 `WithdrawalServiceImpl`）。
- 现有会员 `WithdrawalService` 强耦合 `memberService.deductBalance`，**不复用**；骑手走独立结算服务，余额与会员完全隔离。
- 接口：`GET /api/rider/settlement/balance`、`POST /api/rider/settlement/withdraw`、`GET /api/rider/settlement/withdrawals`（骑手端）；`GET /api/delivery/rider-withdrawal/page`、`POST /api/delivery/rider-withdrawal/{id}/review`（管理端，@RequireEmployee）。
- 前端：骑手 `income.html` 增加可提现余额、申请提现弹窗、提现记录；后台新增 `page/delivery/rider-withdrawal.html` 审核页，并在"财务管理"菜单组加"骑手提现"入口。
- 测试：`RiderSettlementTest`（8 例）覆盖幂等入账、提现冻结、余额不足、审核通过/驳回、防双扣，及骑手端/管理端 HTTP 端点；全量 856 测试通过。
- 开发者：AI

## P0-2 骑手评价闭环（R2，2026-09-28）
- 后端：`module/delivery` 新增 `RiderEvaluation` 实体/Mapper/Service/Controller（顾客提交/查询、公开列表、骑手收到的评价、管理端分页筛选+回复）；提交即自动通过并即时刷新骑手 `rider.rating`。
- 鉴权：新增 `@RequireUser` 注解 + `UserGuardAspect`（与 `RequireEmployee`/`RequireRider` 对称，MockMvc 下兜底写入 BaseContext）；顾客端点加 `@RequireUser`、骑手收到的评价加 `@RequireRider`。
- 前端顾客端：`front/page/order-detail.html` 已完成订单增加"评价骑手"入口与评分/标签/匿名弹窗（复用 `front/api/evaluation.js`）。
- 前端骑手端：`rider/js/api.js` + 新 `rider/evaluation.html`（平均分/评价数 + 收到的评价列表）+ 工作台"我的评价"入口。
- 管理端：`backend/page/delivery/rider-evaluation.html`（筛选/回复）+ 财务管理菜单"骑手评价"。
- 测试：`RiderEvaluationTest`（6 例）覆盖提交/幂等/订单校验/评分统计/回复及顾客/骑手/管理端 HTTP 端点；全量 862 测试通过。
- 注意：`rider` 表复用既有 `rating` 列，评价数改为查询派生（避免真实 MySQL 表迁移）；`rider_evaluation` 表由 `schema-rider.sql` 自动建表。
- 开发者：AI

