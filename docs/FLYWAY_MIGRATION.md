# Flyway 数据库迁移约定

> 2026-10-16 接入。此前结构变更靠 `db/` 下"手工执行脚本 + DBA 手动补执行"，无自动迁移。
> 本文件是唯一口径来源；`agents.md` / `README.md` 的相关段落指回这里。

## 1. 机制

| 项 | 值 |
|---|---|
| 依赖 | `org.flywaydb:flyway-core`，版本由 Spring Boot 2.4.5 BOM 管理 = **7.1.1**（兼容 JDK 1.8 与 MySQL 8；7.x 的 MySQL 支持内置于 core，无需 `flyway-mysql`） |
| 脚本位置 | `src/main/resources/db/migration/`（入库；`db/` 目录整体仍被 `.gitignore` 忽略，只有 `seed-minimal.sql` 例外） |
| 记账表 | `flyway_schema_history` |
| 已有基线 | `V1__baseline_schema.sql`（129 张表全量表结构）、`V2__seed_minimal.sql`（租户/门店/超管最小数据） |
| **新迁移编号** | 从 **V3** 起递增：`V3__描述.sql`、`V4__描述.sql` … |

配置在 `application.yml` 的 `spring.flyway`：`baseline-on-migrate=true`、`baseline-version=2`、`validate-on-migrate=true`、`out-of-order=false`、`encoding=UTF-8`；总开关按 profile：

| profile | `enabled` | 理由 |
|---|---|---|
| dev | `${FLYWAY_ENABLED:true}` | 本机开发库自动跟上结构，正是这次要解决的痛点 |
| prod | `${FLYWAY_ENABLED:false}` | **红线：不在服务启动时自动对生产库执行 DDL**。需迁移时先备份，再临时注入 `FLYWAY_ENABLED=true` 单次启动，核对 `flyway_schema_history` 后改回 false |
| test | `false` | 建表仍由测试类的 `@Sql` + `src/test/resources/schema*.sql` 负责，避免启动期 DDL 干扰「租户 999 + test 命名空间」的 `TestDatabaseCleaner` 隔离 |
| Docker 自部署 | compose 传 `FLYWAY_ENABLED=${FLYWAY_ENABLED:-true}` | 单机空库首启需要应用建表；对公网部署请在 `.env` 设 false |

### 基线语义（为什么已有库不会被折腾）

- **空库**（新环境首启）：V1 建全表 → V2 写最小种子。
- **已有业务表但没有 history 表**（本地 `xinfei`、既有部署、从 `db/xinfei.sql` 恢复的库）：只插入一条 `version=2 / BASELINE` 记账行，**V1、V2 都不执行**，不覆盖任何已有数据；后续从 V3 开始增量。
- **已有 history 表**：正常增量 + `validate` 校验。

实测（2026-10-16，本机 MySQL 8.0.41）：
```
空库   → Migrating to "1 - baseline schema" → "2 - seed minimal" → Successfully applied 2 migrations (5.2s)，启动 39.8s
已有库 → Creating Schema History table with baseline → Successfully baselined schema with version: 2
        → Current version of schema `xinfei`: 2 → up to date. No migration necessary.，启动 28.5s
改前后业务数据零变化：orders=38 / employee=16 / dish=62 / user=35 / payment_order=1 / tenant1 名称未动
```

## 2. 新增一次结构变更的正确流程

1. 写 `src/main/resources/db/migration/V<n>__简述.sql`，**只前进不后退**；
2. 同步 `src/test/resources/schema*.sql`（基础表→`schema.sql`，模块表→`schema-<module>.sql`），否则测试报"表不存在"；
3. `mvn -B spring-boot:run` 在 dev 库上真跑一遍（或 `mvn -B test` 验证夹具）；
4. 提交迁移脚本；**V1/V2 及任何已入库的脚本一律禁止改动** —— 内容变了 checksum 就变，已迁移库 `validate` 会直接启动失败。写错了就再加一个新版本纠正。

## 3. V1 基线如实携带的既有缺陷（修复请开 V3+，不要在基线里偷改）

| # | 缺陷 | 规模 | 影响 |
|---|---|---|---|
| 1 | 列注释 GBK 乱码（如 `绉熸埛ID` = "租户ID"） | **175 处 / 76 张表** | 仅元数据，不影响运行；源自历史错误字符集导入。表注释无此问题 |
| 2 | ~~测试夹具缺表~~ | `invoice_record`、`invoice_title` | **已修复 2026-10-16**：新增 `src/test/resources/schema-invoice.sql`，列与索引按基线逐条对齐。当前没有任何测试触达这两张表，故该文件暂不挂 `@Sql`；新增发票用例时按本节约定用 `@Sql` 加载它 |
| 3 | ~~测试夹具缺二级索引~~ | 原 203 个 / 87 张表（含 20 个唯一约束） | **已修复 2026-10-16**：16 个夹具文件（`src/test/resources/schema*.sql` 共 20 个：16 个本轮重建 + 3 个原本一致 + 1 个本轮新建）的索引段按 `SHOW CREATE TABLE` 重建，夹具库与基线索引集合已核验一致（见第 7 节） |
| 4 | ~~夹具与基线的索引命名不一致~~ | 原 3 个无名 `UNIQUE KEY(...)` | **已修复 2026-10-16**：重建索引段时直接取基线的显式 `uk_*` 名字 |
| 5 | 遗留 3 个手工迁移辅助存储过程 | `add_col_if_not_exists` / `create_index_if_not_exists` / `safe_create_index` | 只服务旧手工流程，未纳入基线；确认无人调用后可删 |
| 6 | 建表时的 MySQL 8 deprecation 警告 | `utf8mb3` / `utf8mb3_bin`、整数显示宽度、`VALUES` 函数 | 迁移能跑但日志有 WARN；属长期字符集统一债务 |

> 夹具与基线的结构漂移已清零（第 7 节）。仍未决定的是要不要更进一步：**让 test profile 直接跑 Flyway 并删掉全部夹具**——更彻底，但会牵动 1019 个测试与租户 999 隔离机制，属独立决策。

> **V1 头部注释仍是修复前的口径**（第 26–28 行写着夹具缺两张表、缺约 200 个二级索引）。那是基线生成时刻的历史快照，按第 2 节规则不改已入库脚本；现行口径以本文件第 3、7 节为准。

## 4. 备份与回滚

- 本次改造前的全库备份：`backups/xinfei-pre-flyway-20261016.sql`（`backups/` 已 gitignore，勿提交）。
- 回滚 = 移除 `pom.xml` 的 flyway 依赖 + 各 profile `spring.flyway.enabled=false`。`src/test/resources/schema*.sql`、`db/seed-minimal.sql` 均原样保留，旧手工建表路径仍可用。
- `docker/db-init.sh` 已瘦身为只 `CREATE DATABASE`；如需回到旧行为，恢复其 schema/seed 导入段并在 compose 重新挂载 `./src/test/resources:/schemas:ro`、`./db:/seed:ro` 即可。

## 5. 与演示数据重置（`db/xinfei.sql`）的关系

`db/xinfei.sql` 是 Navicat 全库导出（含 `DROP TABLE IF EXISTS` + 演示数据），只用于把本地 `xinfei` 库重置为演示态，**不是迁移脚本**。两种顺序都安全：

- **先导入后启动**：库里有业务表、没有 history → Flyway 只记 `version=2` 基线，不重放 V1/V2，演示数据不动。
- **先启动后导入**：Flyway 已建表并记基线，再手工导 `xinfei.sql` 覆盖数据 → history 表不在导出内、不受影响，仍停在 `version=2`。

需要警惕的只有一种：**已经跑过 V3+ 的库**，再导入一份结构停留在 V2 时代的老 `xinfei.sql`，会把结构回退而 history 仍显示 V3，此后新迁移会因列已存在而失败。重置演示态时请连 `flyway_schema_history` 一起清掉（`DROP DATABASE` 重建即可），或只用与当前版本匹配的导出。

## 6. 启动期读库的组件（顺序约束）

Flyway 的迁移在 `flywayInitializer` 单例初始化时执行；`@PostConstruct` 可能排在它之前，**空库首启会读到"表不存在"**。因此凡是在启动期查库的组件，一律实现 `SmartInitializingSingleton`（`afterSingletonsInstantiated()` 保证晚于全部单例，含迁移），不要写 `@PostConstruct`：

- `AiProviderManager` —— 读 `ai_provider_config`（已改）
- `RecommendServiceImpl` —— 启动清理 `recommendation_cache` 过期行（已改）

未采用的替代方案：`@DependsOn("flywayInitializer")` 在 Flyway 关闭的 profile（test、prod 默认）下会因该 Bean 不存在而启动失败。新增同类组件时沿用 `SmartInitializingSingleton`。

## 7. 夹具与基线的一致性核验（每次改夹具后都跑）

夹具（`src/test/resources/schema*.sql`）不经 Flyway，所以 `validate` 管不到它，漂移只能实测。2026-10-16 全量对齐后的基准：

```
表数量：真实 129 / 夹具 129
夹具缺表 0 · 夹具独有表 0 · 列定义漂移 0 · 索引定义漂移 0 · 引擎与排序规则差异 0
20 个夹具文件单独建库零报错
```

核验做法（`tools/` 未入库，命令本身可手工复现）：

```bash
mysql -uroot -p -e "DROP DATABASE IF EXISTS xinfei_fixcheck; CREATE DATABASE xinfei_fixcheck CHARACTER SET utf8mb4;"
for f in src/test/resources/schema*.sql; do
  mysql -uroot -p --default-character-set=utf8mb4 --force xinfei_fixcheck < "$f"
done
# 然后按 information_schema 比对两库的 columns（column_type/is_nullable/column_default/extra）
# 与 statistics（index_name/non_unique/列顺序），并比对 tables.table_collation；验完删掉临时库
mysql -uroot -p -e "DROP DATABASE xinfei_fixcheck;"
```

对齐时踩到、后续必须避开的三个坑：

1. **行内 `PRIMARY KEY` 与表级 `PRIMARY KEY (id)` 不能同时存在** —— 建表报 `ERROR 1068 Multiple primary key defined`，该表在测试库里根本建不出来，而引用它的 `DELETE`/`INSERT` 会连带失败。`print_terminal`、`print_task` 原本用行内写法，已统一改成表级声明。
2. **`extra` 要一起比** —— 只比 `column_type` 会漏掉 `DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` 里的 `ON UPDATE`（`payment_channel_config`、`refund_record` 就是这么被发现的）；漏掉后测试库不会自动刷新 `update_time`，与生产行为不同。
3. **排序规则要一起比** —— 基线里 `payment_channel_config` 是 `utf8mb4_bin`（区分大小写）、`rider_remember_token` 是 `utf8mb4_general_ci`，夹具不写表尾选项就会继承库默认的 `utf8mb4_0900_ai_ci`；`WHERE channel='wechat'` 这类查询在测试库能命中、在生产库不命中。

另外：夹具的列名大小写与基线不完全一致（如 `ID` vs `id`），MySQL 列名不区分大小写、JDBC `getString("id")` 也不区分，因此不改；新增夹具请一律用小写。
