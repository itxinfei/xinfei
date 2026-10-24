# 变更记录（CHANGELOG）

格式：日期 | 模块 | 更新内容 | 开发者

## 2026-10-24 | 平台对接 / 测试基建 | 恢复拉单修复（O-14，P1 全清）+ 全量回归排障
- O-14：`PlatformRecoveryTask.recoverPlatform` 根因修复——原实现形式上已调 `pullOrders`，但把 `LocalDateTime.toString()`（ISO 带 T/毫秒）拼进平台 API 时间参数，与拉单链路 `yyyy-MM-dd HH:mm:ss` 口径不一致导致恢复实际拉不到单；现同格式化 + 窗口扩为 30 分钟（窗口重叠 + platformOrderId 唯一键去重幂等兜底）+ 起止同源防漂移 + 日志带拉取数。`PlatformRecoveryTaskTest` 3 例（格式回归守卫/全链透传/总开关）全绿。**至此 P1 清单 O-06~O-16 全部完成。**
- 全量回归（1112 用例）排障：2 个真实失败为 `DashboardTodoAndHourlyControllerTest` 夹具缺口（未清其他测试残留的租户 999 待退记录致 pendingRefund 计数被污染），已补 `cleanByCondition` 精准清理（保种子 ID）；226 个 context 连坐错误定位为**全量跑期间并行执行其他 mvn 命令导致 target/classes 并发写**（类加载撞上类文件替换 → PermissionServiceImpl 偶发缺失雪崩），单跑全绿验证代码无回归——教训：全量跑期间禁止并行任何 mvn。
- 开发者：AI

## 2026-10-24 | 会员 / 支付 / 营销 / 小程序 | 付费券包（对标 O-18 一期：打包售卖 + 支付自动发券）
- 数据结构（V11 迁移 + schema-member 夹具同步）：`coupon_package`（名称/售价/面值合计/状态/排序）+ `coupon_package_item`（包内券模板，一期每模板限一张）+ `coupon_package_order`（购买单，orderNo 唯一，状态 0 待支付/1 已支付已发券/2 已关闭）。
- 后端：`CouponPackageService`（adminPage/saveWithItems 含同模板重复拒绝/上下架/删除、listPublished、createOrder 幂等复用待支付单、deliverPaid CAS+拆包发券）；管理端 `/coupon-package` CRUD；顾客端 `/api/coupon-package`（list 匿名/buy/my）；支付走新 `CouponPackagePaymentController`（`/api/payment/coupon-package/pay`，与 RechargePaymentController 同构，JSAPI openid 从登录档案取）；`PaymentOrder.BIZ_COUPON_PACKAGE` 回调分支：deliverPaid 失败抛异常回滚支付单状态供回调重试，购买单缺失/已关闭走与充值同口径的 afterCommit 自动全额退款。
- 发券口径：复用 `claimCoupon` 内核（原子扣库存 + uk 幂等 + validDays 过期），购买者未开通会员自动开通；任一张失败整体回滚（防「付了钱只发一部分」）。
- 管理端：`page/marketing/coupon-package.html`（列表含明细标签/编辑弹窗券模板多选/上下架/删除，口径说明文案）挂营销组菜单；`api/coupon-package.js`。
- 小程序：优惠券页「可领取」顶部新增「超值券包」卡片区（主色渐变面卡，购买→微信 JSAPI→轮询支付单→成功后刷新）；`shared/pay.js` 新增 `payCouponPackageByWechat`（复用通用支付单轮询，mock 沙箱同契约）并加入 index 转出口。
- 测试：`CouponPackageIntegrationTest` 3 例（全链建包→购买→回调发券→幂等重放不双发；同模板重复拒绝+下架不可买；管理端分页明细回填+下架后顾客端不可见）；shared 9 例回归、`pnpm build:customer` 通过。期间确认 MockMvc 上下文 LoginCheckFilter 不生效为项目既有测试语义（顾客身份测试需 setUp 直设 BaseContext），已在测试注释注明。
- 未做（登记）：权益卡形态、H5 券包入口、储值渠道购买券包、包内同模板多张（需放开 coupon_user 唯一索引，涉及迁移与防重领逻辑复核）。
- 开发者：AI

## 2026-10-24 | 收银 / 员工 / 后台 | 员工提成管理（对标 O-17，收银提成口径）
- 数据结构（V10 迁移 + 夹具同步）：`employee` 增 `commission_rate decimal(5,2)`（0~100%，NULL/0=不参与，更新白名单校验边界，编辑表单 el-input-number 维护）；新表 `commission_record`（员工/收银记录ID/基数/比例快照/提成额/期间 yyyy-MM/状态，`uk_commission_cashier` 唯一键幂等）。
- 生成挂点：`CashierServiceImpl` 三条收银落库路径（普通收银 cashPayment / 桌台合并结账 cashPaymentByTable / 手工补录 saveCashierRecord）统一调 `recordCommissionQuietly`——基数=实收−找零（现金多收不计提），0 元单不落记录，比例取生成时刻快照（改比例不影响历史），宽异常兜底不阻断收银主事务。
- 查询接口：`CommissionController` `GET /commission/summary`（按员工聚合提成额/基数/单数，时间范围筛选，提成额降序）+ `GET /commission/page`（员工/期间筛选明细分页），员工鉴权。
- 后台页面：`page/employee/commission.html`（期间汇总卡片 + 员工汇总表 + 明细表 + 口径说明文案，照 v3 模板骨架），挂载侧边栏「员工管理」组；`api/commission.js` 封装。
- 测试基建：新增 `TestSchemaPatcher`（com.xinfei.test）——test profile 不跑 Flyway、夹具 IF NOT EXISTS 对历史已建表不生效，首次出现「已存在表加列」漂移，用 information_schema 判缺列才 ALTER，幂等；`CommissionCashierIntegrationTest` 5 例（含找零扣减/无比例跳过/比例快照），回归 CashierControllerTest 14 + EmployeeControllerTest 19 全绿。
- 开发者：AI

## 2026-10-24 | 进销存 / 套餐 / 成本 | 供应商欠款出网 + 套餐 BOM 成本试算（PRD O-11，P1 清单收官）
- 供应商欠款：`Supplier` 实体新增瞬态字段 `arrears`（`@TableField(exist=false)`，与 totalPurchaseAmount 同模式）；`SupplierService.fillArrears` 一次 in 查询 + 内存分组聚合（真实数据源 `supplier_settlement`：PENDING 结算单 Σ(total_amount-paid_amount)，PAID 已结清不计），挂在 `/api/inventory/supplier/page` 与既有采购汇总回填并列；供应商列表页新增「欠款」列（>0 红色金额 / 0 显示「已结清」）。
- 套餐 BOM 成本：`GET /setmeal/{id}/cost`（员工鉴权 + 租户校验）：按套餐明细聚合 Σ(菜品单位成本 × copies)，菜品成本取成本模块 `dish_cost.total_cost`（全量一次查询做映射，避免 N+1）；返回售价/BOM 成本/毛利/毛利率/逐菜品明细（`hasCost` 标记）/`missingDishCount`/`caliber` 口径文案——缺成本记录的菜品按 0 计并显式标注，不猜成本。套餐页（combo/list.html）操作列新增「成本」按钮 + 只读试算弹窗（摘要四格 + 缺成本告警条 + 明细表 + 口径说明），actions-width 180→300。
- 评分明确不做（诚实口径）：库内无供应商评分数据源，不造近似评分；待有真实评价数据（收货异常率等）再立项。
- 测试：新增 `SupplierArrearsSetmealCostTest` 2 例（欠款聚合含 PAID 排除与无结算单归零；BOM 成本含缺成本按 0 计、利润 66.7%、missingDishCount 标注），实测全绿。
- 开发者：AI

## 2026-10-24 | 小程序顾客端 | 预约配送时间（PRD O-07）
- 结算页配送模式新增「期望送达」卡（行内展开，复用券面板交互模式）：默认「尽快送达 · 预计 HH:MM」（进页按 now+40min 计算一次）；可选今天/明天/后天 × 四时段（早餐 07:30-09:00 / 午餐 11:30-12:30 / 下午茶 14:00-16:00 / 晚餐 17:30-19:00，与 H5 `scheduleTimeSlots` 同口径同顺序），「尽快送达」仅今天可选，切天时段越界自动回落。
- 提交透传 `expectDeliveryTime`（后端 `/order/submit` 既有透传，落 `orders.expect_delivery_time`，骑手任务 VO 已回显）：立即=`HH:MM 尽快送达`、预约=`yyyy-MM-dd HH:mm-HH:mm`，两种格式与 H5 完全一致；自提模式不显示该卡、传 null。样式全走设计令牌。
- 验证：`pnpm build:customer`（uni-app mp-weixin）构建通过（同时覆盖 O-06 的结算页改动）；后端零改动。
- 开发者：AI

## 2026-10-24 | 支付 / 小程序顾客端 | 会员储值支付闭环（PRD O-06，payMethod=5 / BALANCE 渠道）
- 后端：`PaymentController#pay` 新增 `BALANCE` 同步渠道分支（与既有 `SUBSIDY` 餐补渠道同构）：0 元单走 `completeZeroAmountPayment`；正额建 BALANCE 支付单 → `MemberRewardService#deductStoredBalance`（按 userId 原子扣余额，与收银台 payType=5 同一扣款内核，含租户防越权）→ `handlePaymentSuccess` 推进支付单 SUCCESS + 订单 1→2；扣款失败（未开通会员/余额不足）与餐补同口径保持订单待付款与支付单 PENDING，顾客可充值重试或换渠道。
- 小程序结算页：`bootstrap` 静默拉 `/api/member/member/my-info`，余额 >0 时展示「会员储值（余额 ¥xx）」支付项（payMethod=5）；提交后 `handleBalancePay` 调 BALANCE 渠道，成功本地刷新余额展示并跳下单完成页，失败 toast 原因并引导到订单详情重试。
- 测试：新增 `BalancePayIntegrationTest` 4 例（支付成功扣款+订单待接单+支付单 SUCCESS(BALANCE)/余额不足拒付且订单保持待付款/未开通会员拒付/支付后重复发起幂等不再扣款），实测全绿。
- 开发者：AI

## 2026-10-24 | 订单 / 通知 | 订单来源同屏标识（对标 O-16）+ 订单取消站内通知（O-15）
- 订单来源聚合（O-16，对标美团/客如云「同屏接单」）：核实平台拉取单经 `PlatformOrderPersistServiceImpl` 已落 `orders` 表（platform_type/platform_order_id），缺的只是展示。`GET /order/page` 新增可选 `platformType` 参数（SELF=自有单即 platform_type 为空；MEITUAN/ELEME/DOUYIN/JD=平台单，空=全部），`OrderService/Impl.orderPage` 同步加参；后台 `order/list.html` 新增「来源」列（自有 info / 美团 warning / 饿了么 primary / 抖音 danger / 京东 success tag）与「全部来源」筛选下拉（select 值经 lastSearch 自动并入请求）。
- 订单取消通知（O-15）：notification 模块新增 `OrderCancelledNotifyListener` 订阅 `OrderCancelledEvent`，向顾客消息中心（`marketing_message`，campaignId=0 系统消息）写「订单已取消」通知并对在线用户 SSE 推送；平台单（userId=0）/订单缺失跳过；异步线程手动设置并恢复 BaseContext 租户上下文（租户插件对无上下文 SQL fail-closed 注入 -1，与定时任务同口径）；common 骨架监听器的 TODO 注释更新为登记真实消费方。
- 测试：`OrderControllerTest` 新增 `testOrderPageFilterByPlatformType`（SELF/MEITUAN 双向过滤 + platformType 回传），`OrderCancelledNotifyListenerTest` 4 例（正常落消息+SSE、平台单跳过、订单缺失跳过、空原因默认文案+租户上下文恢复）；两类共 14 例全绿（`mvn test -Dtest=...` 实测）。
- 开发者：AI

## 2026-10-24 | 公共 / 顾客端 / 小程序 | 门店地址出网（PRD O-08）+ shared 包单测（O-13）+ 需求文档集
- 门店地址（O-08）：`/restaurant/info` 返回 `tenant.address`（并入既有 tenant 单次查询，无值不返回字段）；H5 `front/page/add-order.html` 自提提示新增「门店地址」行（`loadShopAddress` 静默加载，失败不阻塞结算）；小程序 `checkout.vue` 门店卡新增 `pickup__addr` 行与配套样式（无值不渲染）。验证：`mvn compile`（JDK 25）通过；8080 当时被 Vite dev server 占用，未做在线自测。
- shared 单测（O-13）：新增 `miniapp/packages/shared/test/shared.test.mjs`（node:test，9 用例全过：qs 拼参、R.code=1/0 解包与 msg 透出、HTTP 422 透传、CSRF 头捕获回带、X-Auth-Token 捕获回带、401 清会话+onUnauthorized 回登录、枚举字典对齐、customerAuth.login 写缓存）；`npm test` 脚本入 package.json，包声明 `"type": "module"`；src 相对导入补 `.js` 扩展（Vite 兼容、Node 原生 ESM 可跑）。
- 核实已修：骑手在线状态 / 门店暂停接单回显（PRD O-09）实际已由 `/rider/auth/me` 的 `status` 与 `/store/detail` 的 `pauseOrder` 回传支撑，`miniapp/README.md` §6 过时记录已更正（划线标注）。
- 需求文档集（docs/需求文档/）：`功能调研.md` 重写为完整市场调研（2026-10-24 实时检索 + 开源竞品 + SWOT/风险）并新增 §6「成熟产品能力对标」；PRD §8.3 标记 O-08/O-09/O-13 完成、新增对标登记 O-16～O-20（订单来源聚合/员工提成/付费券包/收银离线韧性/补货预测化，均未排期）。
- 开发者：AI

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

