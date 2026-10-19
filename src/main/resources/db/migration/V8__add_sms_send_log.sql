-- ============================================================================
-- V8：新建 sms_send_log 短信发送台账表（验证码防盗刷三层防护的第②层：DB 权威兜底）
-- ----------------------------------------------------------------------------
-- 为什么要建（功能测试报告 §13 根因 + 2026-10-19 用户「①②③全做」拍板）：
--   现有 /user/sendMsg 频控（UserController.java:130-157）完全依赖 Redis 计数键
--   sms:cooldown:{phone} / sms:daily:{phone}:{yyyyMMdd}。§13 已定性：开发机 Redis
--   （v3.0.504，未开 AOF）重启后计数键全部丢失，当日已发次数被重置归零，
--   SMS_DAILY_LIMIT=10 形同虚设——这是真实盗刷风险（对任意手机号无限重试轰炸），
--   不只是环境问题。Redis 快路径保留（性能），但必须有一层不依赖 Redis 持久化的
--   权威兜底计数，落 DB 即为此目的；表本身同时充当审计台账（谁在何时给哪个号发了验证码）。
-- 为什么不加 tenant_id 列：
--   频控维度是「手机号」本身（跨租户、跨会话的全局防盗刷），而 /user/sendMsg 是
--   公开匿名端点（登录前调用，BaseContext.getCurrentTenantId() 此时为 null，
--   若走租户插件会被 MybatisPlusConfig.getTenantId() 兜底成 -1，把台账污染成
--   一堆无意义的 -1 行，且不同环境注入值不一致，COUNT 聚合失去意义）。
--   因此本表登记进 tenant-tables.yml + TenantTableRegistry.FALLBACK_IGNORE_TABLES
--   白名单，不参与租户隔离（与 permission/region 等同为全局表，理由见该文件注释）。
-- 清理口径：src/test/java/com/xinfei/test/TestDatabaseCleaner.java 的
--   cleanGlobalTable() 需登记 case "sms_send_log"（手机号无天然 test_ 命名空间，
--   但本表内容由 V8 建表起才有、从未被 db/xinfei.sql 演示导入灌过数据，
--   属纯运维流水，按当日 send_date 条件删除即为测试语义，非无 WHERE 全表删）。
-- 幂等：CREATE TABLE IF NOT EXISTS，重复执行无副作用。
-- 对测试的影响：同步在 src/test/resources/schema.sql 追加同名夹具，
--   保持「夹具与 V1→V8 终态一致」的核验口径（AGENTS.md 新增表/列两步都要做）。
-- ============================================================================

CREATE TABLE IF NOT EXISTS `sms_send_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `phone` varchar(20) NOT NULL COMMENT '接收验证码的手机号',
  `scene` varchar(32) NOT NULL DEFAULT 'login' COMMENT '发送场景，当前仅登录验证码，预留扩展位',
  `send_date` date NOT NULL COMMENT '发送日期（按日频控计数窗口）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录落库时间',
  PRIMARY KEY (`id`),
  KEY `idx_sms_send_log_phone_date` (`phone`, `send_date`),
  KEY `idx_sms_send_log_send_date` (`send_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='短信发送台账（验证码防盗刷，全局表不参与租户隔离）';
