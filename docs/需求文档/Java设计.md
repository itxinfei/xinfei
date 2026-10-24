# Java 设计（后端架构与关键设计决策）

> 版本口径：2026-10-24。隶属 [./PRD.md](./PRD.md) 需求文档集。本文是**设计摘要 + 指向**：完整细节以 [../架构设计.md](../架构设计.md)、[../数据模型.md](../数据模型.md)、[../FLYWAY_MIGRATION.md](../FLYWAY_MIGRATION.md) 与仓库根 `AGENTS.md` 为唯一事实来源；技术红线速查见 PRD §2/§3。

---

## 1. 技术选型与既定决策（不可回退项）

| 决策 | 内容 | 理由 / 教训 |
|---|---|---|
| 运行时 | JDK 25 LTS + Spring Boot 4.1.1（Spring 7.0.9 / Jakarta EE 11） | 2026-10-20 从 2.4.5 一步升级；同期删除 animal-sniffer / enforcer 门禁 |
| ORM | MyBatis-Plus 3.5.17（`mybatis-plus-spring-boot4-starter` + `mybatis-plus-jsqlparser`） | boot3 starter 在 Boot 4 下 `@Mapper` 不注册，必须官方 boot4 线 |
| 连接池 | HikariCP（Boot 原生） | **Druid 已移除且禁止切回**：其 boot3 starter 引用 Boot 4 已迁移的类，启动即失败 |
| 迁移 | Flyway 12.4.0 + `spring-boot-flyway` + `flyway-mysql` | 只引 `flyway-core` 时迁移**静默不执行**（Boot 4 拆模块） |
| 序列化 | Jackson 3（`tools.jackson.*`） | 业务代码零 `com.fasterxml.jackson.databind`；`@Primary` bean 返回类型必须 `JsonMapper`，否则双 primary 全量启动失败；`JacksonException` 已 unchecked |
| 缓存 | Redis + Commons Pool2 | 限流 / 短信防盗刷 / Token 会话 / 验证码均依赖；生产必须开持久化 |
| 模块化 | 41 个业务/演示模块 `com.xinfei.module.*` | 新增业务代码先进对应模块，无模块扫描 |
| 线程模型 | dev/prod 虚拟线程（Tomcat + Boot taskScheduler）；自建 `ThreadPoolTaskExecutor` 与 `scheduled-task-N` 池**保持平台线程** | 自建池的 `queueCapacity`/`maxPoolSize` 正在承担背压限流，勿「顺手」改虚拟线程 |
| `ScopedValue` | 只在当前线程 `where().run()` 作用域可见，不向子线程传播 | 不能平替 `BaseContext` 里被 submit 到线程池读的场景（跨线程要 `StructuredTaskScope`，仍 preview） |

---

## 2. 分层与模块结构

```
Controller → Service(接口) → ServiceImpl → Mapper → XML(按需)
   DTO↓         DTO↓          Entity↓      Entity↓   SQL↓
```

| 层 | 命名 | 说明 |
|---|---|---|
| Controller | `*Controller` | REST 端点，返回 `R<T>`；接口路径/字段名是前端契约，**不改名** |
| DTO / VO | `*DTO`（请求）/ `*VO`（响应） | 参数校验用 `@Validated`，失败统一 400 |
| Service | `*Service` / `*ServiceImpl` | 包已迁 `...spring.service`（MP 3.5.9+ 拆包） |
| Mapper | `*Mapper extends BaseMapper<Entity>`，逐接口 `@Mapper`（**无 @MapperScan**） | 复杂查询 XML 放 `src/main/resources/com/xinfei/<包路径>/`（与 Java 包同路径，不在 resources/mapper/） |
| Entity | 与表名对应 | 全局字段高度统一（见数据模型.md §1） |

模块内目录：`controller / service(impl) / mapper / model / dto / platform(平台适配) / provider(外部服务) / channel(支付渠道) / core / config / task`。

**统一响应 `R<T>`**（`com.xinfei.common.R`）：`code / message / data / timestamp / requestId`；code 约定 200 成功、401 NOTLOGIN、403 无权限、422 业务异常、500 兜底。

**异常纪律（关键决策）**：业务错误一律 `throw new CustomException`（→ HTTP 422 + 实际消息）；**禁止 `throw new RuntimeException`**（会被兜底 handler 吞成 500「系统繁忙」，消息丢失，2026-08 已全量迁移完成）。catch 链注意：Jackson 3 的 `JacksonException` 是 unchecked，「先 RuntimeException 透传、后 Exception 兜底」的写法会让解析异常裸抛，需在其之前补 `catch (JacksonException e)` 包装。

### 2.1 一次请求的完整生命周期（写代码时对号入座）

```
SecurityHeaderFilter(@Order 0, 安全头)
  → RequestCachingFilter(请求体缓存, 可重复读)
  → CsrfFilter(prod 启用, 校验 X-CSRF-Token)
  → LoginCheckFilter(登录校验; 通过则写 BaseContext ThreadLocal + 注入 request 属性)
  → BruteForceProtectionFilter(防暴力)
  → TraceIdFilter(链路 ID)
  → RateLimitAspect(@RateLimit, Redis Lua 原子限流)
  → Controller(@Validated DTO 校验 → 业务编排)
  → ServiceImpl(@Transactional(rollbackFor=Exception.class))
    → MyBatis-Plus: TenantLineInnerInterceptor 自动注入 tenant_id + MyMetaObjectHandler 填充
  → 返回 R<T>(code/message/data/timestamp/requestId)
  → 异常出口: GlobalExceptionHandler(CustomException→422 / 校验→400 / 兜底→500)
```

要点：`BaseContext`（员工 ID/租户 ID）由 `LoginCheckFilter` 写入、请求结束清理——**定时任务与自建线程池里没有它**，跨线程不传播（`ScopedValue` 也不向子线程传播，见 §1），需手动 set/remove；Token 通道（`X-Auth-Token`）与 Session 双轨判定也在此过滤器层完成。

---

## 3. 多租户设计（核心横切机制）

1. **上下文**：`BaseContext`（ThreadLocal）存当前员工 ID / 租户 ID；`LoginCheckFilter` 登录校验通过后写入、请求结束清理；定时任务与异步线程需**手动设置/清理**。
2. **SQL 自动隔离**：MP `TenantLineInnerInterceptor` 对 SELECT/INSERT/UPDATE/DELETE 自动注入 `tenant_id`；INSERT 由 `MyMetaObjectHandler` 元数据填充（44 实体已带 `@TableField(fill=INSERT)`）。
3. **绕过即风险**：`@InterceptorIgnore(tenantLine="true")` 的 7 个 Mapper（C 端用户、审计日志、看板聚合、推荐、平台配送回传等）必须在 SQL 里**手动加** `tenantId = #{tenantId}`，否则越权——改动这些 Mapper 时逐条 SQL 复核。
4. **无 tenant_id 例外**：`tenant`（根表）、`region`（行政区划）、`permission`/`role_permission`（入 `IGNORE_TABLES`，走专用 Mapper）、`sms_send_log`（V8 全局台账）。

**测试库语义**：开发/测试共用 `xinfei` 单库，演示数据租户 1、自动化测试租户 999（`TenantTestExecutionListener` 全局注入）；清理只按租户/命名空间条件 DELETE，显式主键 insert 前用 `TestIdGuard.assertAbsent` 防撞演示数据。

---

## 4. 鉴权与会话

- **双通道**：Session+Cookie（HttpOnly、SameSite=strict，Web/H5）+ Token 通道（`X-Auth-Token`，Redis 存储、TTL 72h 可配，小程序）；非 JWT。上传/下载接口同时认两种登录态。
- **注解**：`@RequiresAdmin`（仅超管）/ `@RequiresPermission` / `@RequireEmployee`；混合公开/顾客端点的 Controller **只能方法级**标注。
- **过滤器链**：`SecurityHeaderFilter(@Order 0)` → `RequestCachingFilter` → `CsrfFilter`（dev 真实禁用、生产启用，前端回传 `X-CSRF-Token`）→ `LoginCheckFilter` → `BruteForceProtectionFilter` → `TraceIdFilter`。
- **短信防盗刷三层**（V8）：会话级 60s → Redis 冷却/单号日限/全站熔断 → DB 台账 `sms_send_log` 权威兜底；Redis 与 DB 计数基线不同（发送前 INCR `>` 判定 vs 发送后落库 `>=` 判定），分开判定、任一命中即拒绝，**不可合并**。

---

## 5. 事务、幂等与并发

- 事务统一 `@Transactional(rollbackFor = Exception.class)`（全库 174 处已补齐；不写 rollbackFor 会漏回滚 checked）。
- **TOCTOU 已修复**：财务对账/利润分析（`ConcurrentHashMap` + `synchronized` 串行化）、收银日结（幂等锁）、平台订单同步（`platformOrderId` 唯一键去重）。
- **乐观锁**：`Orders`/`PaymentOrder`/`RefundRecord`/`DeliveryOrder` 四核心实体 `@Version`；**遗留风险**（未加锁但涉金额，改动时留意）：`Member.balance`、`DailySettlement`、`CostRecord`、`PurchaseOrder`。
- **限流**：`@RateLimit` 注解 + `RateLimitAspect` + Redis Lua（`scripts/rate_limit.lua`，classpath 内）原子防竞态。

---

## 6. 数据模型与结构变更

- **全局字段约定**：实体高度统一（id/tenantId/createTime/updateTime/createUser/isDeleted 等；注意 `createTime` vs `createdTime` 双风格并存，元数据填充器已兼容，**勿单方面"统一"**）。
- **ID**：雪花 `IdType.ASSIGN_ID` 为主，测试硬编码高位主键段须对照 `db/xinfei.sql` 演示占用段。
- **结构变更流程（三同步）**：① Flyway 新版本 `V<n>__描述.sql`（dev 自动执行，已入库脚本**禁止改动**，错了追加新版本纠正；生产默认 `FLYWAY_ENABLED:false` 不自动 DDL）；② 同步 `src/test/resources/schema*.sql` 夹具（照抄 `SHOW CREATE TABLE`，行内/表级 PRIMARY KEY 不能共存）；③ 核对演示数据 ID 段。129 表 / 1878 列 / 355 索引已核验夹具与迁移终态一致。
- 已知技术债：列注释 GBK 乱码（≥308 列）维持现状，勿动（见 AGENTS.md「文件与 Git 注意」）。

---

## 7. 横向能力

| 能力 | 设计 |
|---|---|
| 导出 | `ExportUtil`：Excel 用 SXSSF 滑动窗口 100 行防 OOM；PDF 横向 A4 + 中文字体多级 fallback；所有导出接口 `LIMIT 10000` |
| 定时任务 | `module/schedule/task/`（券到期、对账、留存等）；**必须手动管理 BaseContext** ThreadLocal |
| 事件 | `common/event/listener/`（如订单取消监听器，扩展点见 PRD §8 O-15） |
| AI | 供应商/密钥 DB 管理（`ai_provider_config`，密钥加密存储），yml 仅兜底；多供应商切换 + 连通性测试 |
| 日志脱敏 | 统一 `LogMaskUtils.maskPhone/maskIdCard`，**禁止手写正则** |

---

## 8. 已知待改进点（架构文档 §11，改动相关区域时顺带评估）

前缀约定不统一（`/backend/`、`/front/`、`/api/`、`/admin/`、无前缀共存）；`@Version` 覆盖偏窄（见 §5）；命名双风格；`/marketing`、`/orders/platform` 存在重复类级前缀；部分财务/报表表无逻辑删除；`UserController` 混载 C 端登录与后台管理（隔离靠路径判断）；`@InterceptorIgnore` 越权风险面需持续人工复核。

> ⚠ 注意：`docs/CODE_STANDARDS.md` 引用悬空（仓库无此文件），Java 命名/结构规范以本文 §2 与 `docs/架构设计.md` §1 为准。

---

## 9. 测试与验证基线

- 1064 用例 / 131 可执行测试类，2026-10-20 JDK 25 + Boot 4.1.1 实测 100% 通过（JaCoCo 0.8.13 支持 JDK 25 字节码）；测试连真实 MySQL + Redis。
- 编译验证铁律：`export JAVA_HOME="D:/Program Files/Java/jdk-25.0.3"` 后 `mvn compile` / `mvn test -Dtest=类名`；PATH 上默认 java 是 JDK 21。
- E2E：`tests/`（Playwright + TS + Allure），需先启动服务。
