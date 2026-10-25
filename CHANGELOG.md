# 变更记录（CHANGELOG）

格式：日期 | 模块 | 更新内容 | 开发者

## 2026-10-24 | miniapp | uniapp 工程设计全面审查（O-39，1 违例修复）
- **审查范围**：架构层（workspace/依赖/manifest/构建）→ shared 包（817 行封装质量）→ 运行时健壮性（40 页定时器/v-for key/规范纯度）→ 硬编码色分类。
- **结论：架构与代码质量优秀**。三端依赖版本完全一致；request 双通道鉴权 + 401 统一收口；pay.js 三支付闭环只认后端 SUCCESS；40 页定时器全配对清理（queue 页疑点核实为误报——_timer 单例 + stopTimer 封装正确）；v-for :key 零缺失；data 全函数式；console.log 零残留；wx.* 专属 API 零泄漏（uni.* + provider 声明规范）；Options API 纯度 100%。
- **唯一违例已修**：硬编码色 17 处——login.vue 品牌色系 7 处改令牌（4 令牌映射 + 渐变改 --color-primary-gradient）、index.vue #fff 改令牌白；其余 9 处属原生组件参数合规例外（uni.showModal confirmColor / 地图 polyline / switch color 属性不认 CSS 变量，与 Web 端 canvas 同口径）逐处补注释归类。复扫未标注例外为 0。
- 验证：三端 pnpm build 全通过。建议级登记：easycom 组件抽取时再启用；manifest appid 留空属真机待办（O-36 检查单）。
- 开发者：AI

## 2026-10-24 | 表格 | O-33「8 页迁移 crud-table」登记项复核：误判划除（O-38，零改动）
- 逐页逐处核对 14 处手写 el-table 实际位置：combo 选菜/成本、employee 导入结果、delivery 阶梯弹窗、group-booking 明细、supplier 采购弹窗、platform 订单明细、subsidy 流水/对账、dining 区域、material 分类、order 支付记录、printer 桌台、kds 看板——**全部为弹窗内明细/辅助小表**；所有页面的主列表早已走 crud-table 组件（含选择列/批量条/统一空态）。
- O-33 登记时以「页含手写 el-table」倒推「主列表未组件化」属口径错误，登记项划除；弹窗小表保持手写是合理设计（无分页/选择需求，组件能力用不上）。
- 连续两个登记项（O-37 图片、O-38 表格）复核后均为「已完成但登记未回滚」——文档债双例，教训已在 O-37 固化。
- 开发者：AI

## 2026-10-24 | miniapp | 图片功能核实：token 通道与三处图片功能均已实现（O-37，文档滞后修正，零代码改动）
- 核实结论：README 声称「评价图片未做——token 通道待后端改造」「客服图片消息与凭证不迁」均为**陈旧记载**。后端 CommonController 上传/下载双通道鉴权（Session + X-Auth-Token）早已落地，`AuthTokenChannelTest#userTokenGrantsUploadWithoutSession` 实测覆盖（本次复跑 7 例全绿）；小程序侧评价图片（chooseImage→顺序上传→5MB/张→上传中禁提交）、客服图片消息（bizType=chat）、投诉凭证图、头像更换、private 图 imgCache 带 token 下载全部已实现（shared/uploadFile 封装并自动补双头）。
- 修正 README 两处台账；三端 pnpm build 全通过 + shared 9 测试绿。
- 教训登记：功能升级.md/README 的「未做」条目完成后未回滚清除是文档债固定来源，本次起完成项随手划除并注日期。
- **小程序代码层进度就此补齐至：顾客端/骑手端全部功能落地，剩余仅真机配置**（appid/证书/域名，见 O-36 检查单）。
- 开发者：AI

## 2026-10-24 | 工程决策 | 「下线旧 Web 顾客端/骑手端」检查单登记（O-36，用户咨询可否删除）
- 用户以 uniapp 重写前台/骑手端后咨询旧代码可否删除。盘点结论（分三类）：**可删**仅 target/ 本轮排查临时脚本（不入库）；**条件可删**为 front/（2.1M）与 rider/（128K）——四条活依赖：WebMvcConfig 资源映射、AuthConstants 白名单、tests/ 9 个 c-* E2E 打 Web 版、小程序未真机验收需旧端做兜底对照；**不可删**为 backend/ 82 页（后台完整功能唯一实现，小程序门店端仅 6 页）、miniapp/（新代码本体）、shared/（后台+小程序共用 + tokens 真源）。
- 检查单已登记功能升级.md O-36：真机验收 → 冒烟替代 → 联动删映射/白名单/E2E → 文档口径同步；全部满足后一次性执行下线。
- 开发者：AI

## 2026-10-24 | UI | 空态文案全站统一（O-35，手写表 empty-text 补齐 21 处）
- 13 页 21 处手写 el-table 此前走 EP 默认「暂无数据」，与统一组件页的引导式文案（「暂无相关记录，试试调整筛选条件」）割裂；按表格场景逐处语义化补齐（交班台账/区域/待收明细/导入结果/支付记录/团餐明细/规格选项/桌台筛选/距离档位/发放流水/对账记录/AI 历史等）。
- **审计脚本两则踩坑**（防复发）：① 统计 `<el-table` 必须排除 `<el-table-column`（`\b` 命中连字符词）；② 标签体属性值可含 `>`（`v-else-if="x > 0"`），`[^>]*` 在字符串内截断产生假缺口——须归一换行 + 人工核对属性值。
- 另核实 label-width 70~120px 共 9 种值系按内容适配（4 字标签 90~110px、短标签 70px 合理），**不硬统一**防长标签换行。
- 验证：18 页语法扫描 + 三门禁全绿（依赖审计 0/0、UI 规范 0/0）。
- 开发者：AI

## 2026-10-24 | UI | 全站按钮密度统一 24px（O-34，工具栏 32px 与弹窗 24px 混排修复）
- **根因三层**：① 13 页 38 处 el-button 漏写 size（EP 默认 32px）；② page.css 两份 Element UI 2 时代强制规则（97 行 / 879 行双副本 height:32px，覆盖组件 small 声明——密度漂移主源，879 行注释还挂「dialog footer 统一 medium」废弃规范）；③ crud-dialog footer 组件模板写死 size=default。
- **修复**：app-v3 的 `app.use(ElementPlus)` 增 `size: 'small'` 全局默认（一处配置盖 38 处漏写，页面显式声明不受影响）；page.css 删除双份 32px 副本并在最高优先级一次声明 small 皮肤官方尺寸（24px / 5px 11px）；crud-dialog footer default→small。
- **缓存链踩坑记录**：@import 内层 css（page.css?v=）的缓存键独立于入口文件（main-v3.css?v=），入口升版对内层无效——须逐层递增（page.css→20261024d、main-v3→3001008、app-v3→3001003、components-v3→3001002）。
- **验证**：combo/order/material/employee 四页按钮高度实测 100% 归一 24px（61/38/18/51 个）；截图确认视觉紧凑无变形；三门禁全绿。
- 开发者：AI

## 2026-10-24 | 表格 | 全站表格样式统一收口（O-33，用户反馈设计不统一 + 订单详情闪动）
- **订单详情闪动定位**：MutationObserver + body/表格宽度前后对比实测——当前构建打开订单详情 0 突变、0 宽度变化，闪动属旧缓存旧版体验（旧版明细为表格、现行版已重构为卡片布局），Ctrl+F5 强刷即消。
- **样式统一根因**：全站表格统一视觉由 `.tableBox` 类承载（page.css：边框/圆角/密度 13px/斑马纹/操作按钮间距与圆角），crud-table 组件自动挂该类；14 页含手写 el-table 中 11 页未挂 → 视觉漂移。
- **修复**：14 处手写 el-table 统一补 `class="tableBox"`（已有 class 追加）：cashier 交班弹窗×2、employee 导入结果×2、delivery 费率、dining 区域/团餐、supplier 采购、subsidy×2、order 支付记录、material 分类、combo 成本、qrcode-center 桌台；刻意不动 platform/order-list（po-detail__table）与 purchase-list（detail-dialog__table）的专属布局类表（原设计意图）。
- **验证**：补类后手写表与 crud 主表实测完全一致（圆角 12px / 边框 / 字号 14px 同源同值，4 页抽查）；14 页语法扫描 + 三门禁全绿。
- **登记未做**：8 个主列表页从手写 el-table 迁移到 crud-table 组件（交互层统一：选择列/批量条/空态文案；工作量大独立排期）。
- 开发者：AI

## 2026-10-24 | 前端清理 | 死文件清零（O-32，用户要求清理旧/多余代码）
- 新增资产引用审计：CSS 以 main-v3.css @import 链 + 页面直链双向可达性判定；js/api 以文件名引用计数（排除自身、含脚本间引用）。
- **git rm 5 个死文件**：`js/emoji-to-icon.js`、`api/export.js`（同步导出前端已全量迁任务通道，无页面引用；后端同步端点保留为对外 API）、`api/kds.js`、`api/platform.js`（include 移除后整体失联）、`plugins/vant/index.css`（后台目录里的 Vant CSS，小程序端有独立副本）。
- 门禁加固：audit-page-deps.js 新增「死文件检测」（api/js 无页面且无脚本引用 → WARNING），今后死文件在门禁现身。
- 验证：三门禁全绿（依赖审计 0/0、UI 规范 0/0、文档对账 0 ERROR）；受影响三页（kds 看板/平台配置/订单列表）浏览器抽查正常挂载。**刻意不做**：活 CSS 内 Element UI 2 死规则手术式清理（误删风险大于收益，登记不动）、后端同步导出端点删除、仓库根旧 AI 工具目录（非页面代码）。
- 另：app-v3.js 全站缓存版本 3001001→3001002（68 页，506e56f）；67 页运行时体检三症状（框中框/表格缺列/表单折行）0 命中，用户如仍见旧渲染请 Ctrl+F5 强刷。
- 开发者：AI

## 2026-10-24 | 登录页 | 输入框「框中框」修复（O-31，用户反馈）
- **根因**：登录页自定义 `.inp-wrap` 外框内的 Element Plus 输入框，边框画在 `.el-input__wrapper`（inset box-shadow）上，而 login.css「去边框/背景」规则写的是 Element UI 2 时代的 `.el-input__inner`——EP 下不命中，wrapper 自带描边 + 浅底在外框内又画一层框（聚焦时内外两层尤其明显）。
- **修复**：`.inp-wrap` 内 wrapper 完全透明化（box-shadow/background/padding 归零，含 hover 与 is-focus），视觉只保留外层一处边框；顺带修 `.inp-wrap.ok` 同优先级覆盖 `.on` 导致**有内容的聚焦框永远灰色无反馈**——ok 收敛为 `:not(.on)`，聚焦色优先于校验通过色。
- 验证（8081 截图）：浅色/深色模式均单层边框；聚焦态品牌黄边框 + 3px 黄色光晕 + 图标变黄；对勾/眼睛/占位符不受影响。CSS-only，login.css 缓存版本 20260926→20261024b。
- 开发者：AI

## 2026-10-24 | 权限 | 细粒度权限键入典（O-30，仅入典零行为变更）
- V17 幂等写入 6 个细粒度键到 permission 字典：TYPE_MENU 顶级 ×4（platform:manage / payment:manage / evaluation:view / notification:send，此前后端 36 处 @RequiresPermission 用的键全不在字典，非超管永远无法通过校验）+ TYPE_BUTTON 子键 ×2（evaluation:reply / evaluation:audit 挂 evaluation:view）。
- **不做任何授权（零行为变更）**：授权矩阵由管理员在 系统设置→角色管理→权限树 勾选——勾选即同时生效于 O-27 前端菜单门与后端 PermissionAspect（共用同一 permSet；Redis 权限缓存由既有角色保存链路失效）。**待决策**：默认授权矩阵（建议店长+副店全给 4 菜单键、大堂经理给 evaluation:view，或全部手动配）。
- V17 幂等写法踩坑：MySQL 无 FROM 的 SELECT+WHERE 需 `FROM DUAL`；同表 NOT EXISTS 用派生表包裹。
- 验证：RoleControllerTest/RoleMapperTest 回归绿（test: 前缀测试键与字典键互不干扰）；8081 实测 /sys/role/permissions/tree 返回 17 节点、6 新键层级正确。
- 开发者：AI

## 2026-10-24 | 收银 / 工程纪律 | 交班台账落库（O-29）+ 依赖审计误报修正与首次清零
- **O-29 交班台账**：V16 新表 `cashier_shift`（员工/日期/交班时间/笔数/总额/找零/支付方式分布JSON/备注）；`POST /cashier/shift/close` **复用 shiftSummary 同一聚合函数**落时点快照（台账与对账弹窗数字天然一致；时点快照语义——落库后退款/改账不影响本班历史，重复交班落多条不设唯一键，前端确认文案提示）；`GET /cashier/shift/page` 台账分页（收银员/日期过滤）。收银台「交班对账」弹窗升级：备注输入（≤200 字）+「确认交班并记录」二次确认 + 弹窗内最近 5 条台账。测试 `CashierShiftIntegrationTest` 5 例全绿（空班全零/实收口径/分页倒序/按人过滤/非法日期 422）+ 收银回归绿；8081 E2E 全链（备注→确认→弹条→台账表实时出现）。
- **依赖审计首次 0 ERROR / 0 WARNING**：三类误报修正——① 命名空间对象用法（`sysApi.menuAccess()` 被裸调用判定漏掉）；② 回调引用（`const refApi = cond ? fnA : fnB`，category/list 实测）；③ 对象键位前导逗号误判（`categoryOptions: []` 被当使用，产生 4 假 ERROR）。按全页全文核对后移除 7 个零引用 include（ai/provider-config、cashier/index、kds/board、payment/channel-config、platform×3），支付渠道页清理后浏览器抽查正常。
- 接口文档同步：cashier 模块 2 Controller / 21 端点（补记 O-17 提成 2 端点与 shift 4 端点），总口径 41/117/901（ctrl-scan 实测）。
- 开发者：AI

## 2026-10-24 | 后台 UX / 权限 | 按钮级权限前端联动（O-27）+ 待办台跳转闭环核查（O-28）
- **O-27 按钮级权限**：壳层把 /sys/menu/access 的权限快照写入 sessionStorage（同源 iframe 共享，页面零额外请求）；新增 `js/perm.js`（XinfeiPerm 助手 + v-perm 指令，SUPER_ADMIN 恒放行、session 无数据 fail-open——后端仍是真拦截点）；app-v3 增加共享指令注册口 XinfeiDirectivesV3 并把 XinfeiPerm 挂 globalProperties（**Vue 3 模板沙箱不允许访问 window 全局，漏挂会整页渲染空白——实测踩坑**）。口径盘点：36 处 @RequiresPermission 集中在 5 控制器，platform:manage/payment:manage 连查询端点都拦（整页级），其余为操作级；**细粒度键均不在权限字典（仅 10 个菜单级键），等效仅超管可用——前端照实镜像**。接入：整页级=菜单项补 perm 字段（壳层 allow() 早已支持只是没人用）×4 项；按钮级=通知页发送 Tab×2+重发、评价页审核/回复、地区页新增/修改/批量删除/删除。**双角色 E2E**：超管全可见；店长 4 菜单项隐藏、Ctrl+K 搜不到、通知页仅剩 2 Tab、地区页三按钮全隐；快照随登录切换正确。未做（登记）：细粒度键入典+角色授权 UI。
- **O-28 待办台闭环**：逐卡核查 5 张待办卡——待退款卡**断链修复**（原指向已被 O-21④ 摘除的 payment/order-list.html，改跳订单列表）；库存预警卡升级为 `material-list.html?warning=1` 直达（页面新增参数：加载后自动展开预警弹窗，实测 ✓）；待接单/配送中卡确认 order/list 原生支持 ?status= 预筛；待评价卡保持原样（登记待定目标：C 端口径与后台评价管理不同域）。
- 验证：8 文件语法扫描全过；页面依赖/文档对账两审计全绿（纯前端改动零后端变更）。
- 开发者：AI

## 2026-10-24 | 库存 / 通知 | 库存预警实时推送接入员工 SSE 通道（O-26）
- **InventoryAlertTask**（inventory/task）：定时差分扫描（默认 5 分钟，`xinfei.alert.sweep-interval` 可配，启动 10s 首扫）——检测口径与库存预警页一致（`stock_qty < min_stock` 且正常状态）。库存变更入口分散（订单扣减/采购入库/盘点调整/直改），故选**单点定时扫描**而非逐变更点埋事件：一处接入覆盖全部路径，边沿触发（状态变迁才推）天然节流。O-23 同族模式：Redis 分布式锁单节点 + 逐租户 BaseContext + Redis 上次预警集差分；**首扫无基线只记录不推送**（防部署/重启对存量预警误报一轮，与 O-23 保守口径一致）。
- **推送**：进入预警按「库存/安全线」比值升序取最危急前 10 项（载荷含名称与 库存/安全线），恢复推数量；接收者为租户全部在职员工（离线静默跳过）。壳层 SSE 客户端更名 `js/employee-sse.js`（通道不再只服务导出），新增 `inventory-alert` 事件弹条。
- **手动触发口**：`POST /api/inventory/alert/sweep`（限流 2/s）——运维排查「为什么没推」与联调时立即执行当前租户差分扫描。
- 演示数据：补 3 行租户 1 演示物料（material 表此前为空，库存预警页顺带从空表变为有数据）。
- 验证：`InventoryAlertTaskTest` 7 例纯函数单测全绿（基线未建/无变迁/进入/恢复/双向/空集边界/序列化往返）；浏览器 E2E 全链——基线扫描 0 弹条 → 生菜降库手动扫描即时弹「库存预警：生菜（1/2）」→ 恢复后即时弹「库存已恢复」；两个静态审计（页面依赖/文档对账）全绿，模块与接口.md 同步 117 Controller / 899 端点。
- **未做（登记）**：成本利润预警接入（页面级计算无事件源，需先定义触发口径）、按角色定向推送、离线员工补看（需员工站内信中心）、库存告罄（0 值）细分等级。
- 开发者：AI

## 2026-10-24 | 导出 / 通知 | O-25 终章：员工 SSE 通道 + 导出任务完成实时推送
- **EmployeeSseManager**（notification/sse）：员工端 SSE 连接管理器，与 C 端 `SseEmitterManager` 刻意分开建类——员工 ID 与 C 端用户 ID 分属两表、数值可撞（员工 1 与顾客 1），共用一张 Long 键连接表会跨主体串台。语义对齐 C 端实现（多连接/终态摘除/建链 connected/心跳保活），总开关与超时/心跳配置复用 `xinfei.sse.*`。
- **端点**：`GET /export/tasks/subscribe`（text/event-stream）——员工会话鉴权，挂在 ExportTaskController 下（/export/** 天然要求员工登录，无需改 LoginCheckFilter）。
- **推送**：`ExportTaskServiceImpl.execute` 终态（成功/失败）向发起员工推 "export-task" 事件（taskId/taskType/fileFormat/status/fileName/errorMsg），推送失败只记日志绝不反噬任务状态。
- **壳层客户端** `js/export-task-sse.js`：同源 EventSource（自动带 JSESSIONID），完成弹「导出任务完成：文件名」、失败弹「导出任务失败：原因」（右上角通知）；浏览器原生重连，连续 5 次失败主动放弃（防会话失效后无限重试），整页刷新自动重建。
- 验证：`ExportTaskIntegrationTest` 8 例全绿（新增 SSE 端点 asyncStarted 冒烟）；8081 实后端浏览器实测——通道 readyState=1，提交员工数据导出后**无轮询**即时弹「导出任务完成」、非法日期构造失败即时弹「导出任务失败」。
- 至此 O-25 全部子项完成；登记项转出：预警类信息（库存/成本）接入员工 SSE 通道（通道已就绪，缺事件源接线）。
- 开发者：AI

## 2026-10-24 | 导出 / 工程纪律 | O-25 收尾：全站导出任务化 + 两道静态审计脚本
- **同步导出全量任务化**：订单/菜品/员工/用户 4 个列表页的「导出Excel/PDF」由同步 blob 下载改走 `/export/tasks` 任务通道（勾选行 ids 经 params_json 透传；后端补 `TYPE_USERS` 类型分发）。V15 迁移：`export_task.params_json` 扩容 text（1000 个勾选 ID 序列化 ~8KB，varchar(1000) 容不下，与同步导出 GET URL 历史容量对齐），服务端限长校验同步放宽至 60KB 防滥用。
- **壳层全局「导出中心」**：顶栏新增入口（弹窗组件挂壳层 Vue 应用）——列表页提交任务后无需回经营报表页即可查看/下载；导出中心补「用户数据」类型。
- **管理页修正**：经营报表「导出 Excel/PDF」可用性改区间口径（原只看开始日单日数据，区间有单也禁用属误限）。
- **两道静态审计脚本入库**（scripts/，与 lint-ui-conventions 同惯例）：① `audit-page-deps.js`——页面→api 隐式依赖检查（漏引 include = ERROR，本会话报表合并时曾漏引 api/category.js 实测踩坑）+ 未使用 include 提醒 + 菜单可达性，当前 0 ERROR/26 WARN（人工确认类）；② `docs-audit.js`——后台页面清单 vs page/ 目录互查 + 模块与接口.md 头部口径 vs ctrl-scan 实测，首跑即抓到 4 处漂移（dining/area-list、inventory/category-list、payment/order-list 合并后未回改，employee/commission 漏登记）已全部修正。
- 修正 AGENTS/约束引用的 `.codebuddy/memory/MEMORY.md` 失效问题（本机建档为速查索引层，.gitignore 维持本机不入库）。
- 验证：`ExportTaskIntegrationTest` 7 例全绿；8081 实后端浏览器实测（订单勾选导出 ids 透传生成成功、users/PDF 任务成功、壳层导出中心可达）；audit-page-deps 0 ERROR、docs-audit 0 ERROR。
- 开发者：AI

## 2026-10-24 | 报表 / 导出 | 报表合并收尾（O-21 二期⑤）+ 异步导出任务中心（O-25）+ UI 紧凑化补登记（O-24）
- **O-21 二期⑤ 报表 5→2**：新建 `page/report/management.html`「经营报表」五 Tab 全内置——营业日报/销售报表/菜品排行/时段分析/支付分析 13 个 ECharts 实例逐页忠实移植（el-tabs lazy 懒挂载 + 首次激活加载 + 重激活 resize，日报骨架屏/排行 Top3 趋势/时段热力图/支付退款面板全部保留原口径）；评价管理保持独立。git rm 旧页 6 个（5 报表页 + export/index.html），菜单 数据报表 7→2，导出中心并入页内工具栏「导出中心」弹窗。8081 实后端浏览器实测：五 Tab 全挂载 13/13 canvas、带真实数据渲染、全 Tab 图表与查询正常。
- **O-25 异步导出任务中心**：V14 新表 `export_task`（状态机 0排队→1生成→2完成/3失败）；`ExportDataService` 把 ExportController 数据构建逻辑原样抽取、同步/异步共用单一口径；`POST /export/tasks`（限流 5/s）→ `exportTaskExecutor`（core1/max2/queue100 + TaskDecorator 传租户上下文）异步生成落盘（`xinfei.export.dir` 默认 {user.dir}/data/exports，非静态伺服，仅鉴权下载端点读出）→ 任务中心轮询/下载/删除；`ExportTaskCleanupTask` 就绪+每日 03:30 分布式锁清扫（卡死 1h 标失败、终态 7 天保留期清文件）。前端新组件 `js/export-task-center.js`（原导出中心 4 卡片收编为快捷导出表单 + 任务列表弹窗，3s 轮询）；日报导出 Excel/PDF 改走任务通道。
- 测试：`ExportTaskIntegrationTest` 6 例全绿（排队态快照/异步终态/下载/非法类型 422/跨租户不可见/删除清文件）+ schema-export.sql 夹具；浏览器全链实测（提交→生成→完成→可下载）。
- O-24 补登记：tableBar 紧凑单行化 + 18 处表头宽度修复（c7ccff7，详见功能升级.md）。
- 开发者：AI

## 2026-10-24 | 后台 IA / 收银 / 店务 | O-21 二期页面归并（②③④）+ O-22 交班对账 + O-23 自动接单 + 三项修复
- 页面归并（O-21 二期，用户确认全内置+浏览器实测）：① 营销 5 页→activities.html 多 Tab（5 类 CRUD 全链实测，顺带修时段 Date 序列化既有回归）；② 食材分类并入食材管理弹窗；③ 区域管理并入桌台管理弹窗；④ 支付记录并入订单明细弹窗。git rm 旧页 7 个，菜单 数据报表 7→2 待办、营销 7→3、库存 7→6、堂食 5→4、订单中心 -1。
- O-22 交班对账：/cashier/shift-summary 当日+本人收款聚合（笔数/总额/找零/按支付方式），收银台「交班对账」按钮+弹窗。
- O-23 自动接单：StoreAutoPauseTask 按 businessHours 边沿触发翻转挂牌（跨午夜支持、不碰手动暂停、保守恢复策略）。
- 修复三项：收银台整页挂载失败（O-19 缺右花括号，语法级静默故障）、printer 时间查询参数回归（Jackson 同族）、V12 迁移共享 dev 库撞已存在列（条件化改写+Flyway 失败记录清理，实测应用成功）。
- 验证：全菜单 70 页浏览器体检 0 异常；76 页静态语法扫描 0 错误；新增单测 StoreAutoPauseTaskTest 4 例；后端 8081 常驻供自查。
- 开发者：AI

## 2026-10-24 | 平台对接 / 测试基建 | 恢复拉单修复（O-14，P1 全清）+ 全量回归排障
- O-14：`PlatformRecoveryTask.recoverPlatform` 根因修复——原实现形式上已调 `pullOrders`，但把 `LocalDateTime.toString()`（ISO 带 T/毫秒）拼进平台 API 时间参数，与拉单链路 `yyyy-MM-dd HH:mm:ss` 口径不一致导致恢复实际拉不到单；现同格式化 + 窗口扩为 30 分钟（窗口重叠 + platformOrderId 唯一键去重幂等兜底）+ 起止同源防漂移 + 日志带拉取数。`PlatformRecoveryTaskTest` 3 例（格式回归守卫/全链透传/总开关）全绿。**至此 P1 清单 O-06~O-16 全部完成。**
- 全量回归（1112 用例）排障：2 个真实失败为 `DashboardTodoAndHourlyControllerTest` 夹具缺口（未清其他测试残留的租户 999 待退记录致 pendingRefund 计数被污染），已补 `cleanByCondition` 精准清理（保种子 ID）；226 个 context 连坐错误定位为**全量跑期间并行执行其他 mvn 命令导致 target/classes 并发写**（类加载撞上类文件替换 → PermissionServiceImpl 偶发缺失雪崩），单跑全绿验证代码无回归——教训：全量跑期间禁止并行任何 mvn。
- 开发者：AI

## 2026-10-24 | 会员 / 支付 / 营销 / 小程序 | 付费券包（对标 O-18 一期：打包售卖 + 支付自动发券）
- 数据结构（V13 迁移 + schema-member 夹具同步；原编 V11 撞号改 V13）：`coupon_package`（名称/售价/面值合计/状态/排序）+ `coupon_package_item`（包内券模板，一期每模板限一张）+ `coupon_package_order`（购买单，orderNo 唯一，状态 0 待支付/1 已支付已发券/2 已关闭）。
- 后端：`CouponPackageService`（adminPage/saveWithItems 含同模板重复拒绝/上下架/删除、listPublished、createOrder 幂等复用待支付单、deliverPaid CAS+拆包发券）；管理端 `/coupon-package` CRUD；顾客端 `/api/coupon-package`（list 匿名/buy/my）；支付走新 `CouponPackagePaymentController`（`/api/payment/coupon-package/pay`，与 RechargePaymentController 同构，JSAPI openid 从登录档案取）；`PaymentOrder.BIZ_COUPON_PACKAGE` 回调分支：deliverPaid 失败抛异常回滚支付单状态供回调重试，购买单缺失/已关闭走与充值同口径的 afterCommit 自动全额退款。
- 发券口径：复用 `claimCoupon` 内核（原子扣库存 + uk 幂等 + validDays 过期），购买者未开通会员自动开通；任一张失败整体回滚（防「付了钱只发一部分」）。
- 管理端：`page/marketing/coupon-package.html`（列表含明细标签/编辑弹窗券模板多选/上下架/删除，口径说明文案）挂营销组菜单；`api/coupon-package.js`。
- 小程序：优惠券页「可领取」顶部新增「超值券包」卡片区（主色渐变面卡，购买→微信 JSAPI→轮询支付单→成功后刷新）；`shared/pay.js` 新增 `payCouponPackageByWechat`（复用通用支付单轮询，mock 沙箱同契约）并加入 index 转出口。
- 测试：`CouponPackageIntegrationTest` 3 例（全链建包→购买→回调发券→幂等重放不双发；同模板重复拒绝+下架不可买；管理端分页明细回填+下架后顾客端不可见）；shared 9 例回归、`pnpm build:customer` 通过。期间确认 MockMvc 上下文 LoginCheckFilter 不生效为项目既有测试语义（顾客身份测试需 setUp 直设 BaseContext），已在测试注释注明。
- 未做（登记）：权益卡形态、H5 券包入口、储值渠道购买券包、包内同模板多张（需放开 coupon_user 唯一索引，涉及迁移与防重领逻辑复核）。
- 开发者：AI

## 2026-10-24 | 收银 / 员工 / 后台 | 员工提成管理（对标 O-17，收银提成口径）
- 数据结构（V12 迁移 + 夹具同步；原编 V10 与 V10__add_user_openid 撞号改 V12）：`employee` 增 `commission_rate decimal(5,2)`（0~100%，NULL/0=不参与，更新白名单校验边界，编辑表单 el-input-number 维护）；新表 `commission_record`（员工/收银记录ID/基数/比例快照/提成额/期间 yyyy-MM/状态，`uk_commission_cashier` 唯一键幂等）。
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


## 2026-10-24 | 安全 / 配置 | 地图服务配置勘误与泄露事件闭环
- 事件：Gitee 公开仓库 git 历史含初始提交（ad5e826）遗留的高德地图 key（application-dev.yml，3cbb41f「配置去密」已移出跟踪，234 star/159 fork 公开可还原）。定性：该 key 属瑞吉遗留死配置——`xinfei.geo.amap` 全代码零消费（地理编码实际由 `GeoUtils` 走百度地图 `xinfei.map.baidu.server-ak`），且用户确认该 key 已过期，历史泄露无实害，**事件闭环，无需历史重写**（159 fork 均持旧历史，重写无意义）。
- 清理：本地 application-dev.yml 移除死配置块（误导源），替换为百度 AK 占位说明。注意：当前百度 AK 为空 → 地址转经纬度（geocode）处于优雅降级态（返回 null），申请到百度地图 server AK 后填入 dev.yml 的 `xinfei.map.baidu.server-ak` 即恢复配送范围距离判定能力。
- 关键结论供后续会话参考：本项目地图服务=百度（非高德）；全历史扫描确认唯一泄露过的真实密钥即此过期高德 key，微信 app-secret 历史值均为空占位符。
- 开发者：AI
