-- ============================================================================
-- V4：清理历史手工迁移遗留（全库唯一一处字符集债 + 3 个辅助存储过程）
-- 口径：2026-10-16 以现网 xinfei 库 information_schema 实测为准。
--
-- 1) 列级非 utf8mb4 字符集全库只有 1 处：notification_template.content
--    （text / utf8mb3 / utf8mb3_bin）；表级默认字符集 130 张表全部已是 utf8mb4。
--    utf8mb3 在 MySQL 8.0.28+ 属废弃别名，建表时触发 deprecation 告警。
--    原 COLLATE 是二进制比较语义，故等值迁到 utf8mb4_bin，不迁 *_0900_ai_ci
--    （后者会放宽成大小写与重音不敏感，属行为变更）。该列无索引、无外键，
--    现网 10 行、最长 51 字节，转换不改数据。
--
-- 2) 3 个手工迁移辅助存储过程 add_col_if_not_exists / create_index_if_not_exists /
--    safe_create_index 来自旧手工流程，不在 V1 基线内，Flyway 接管后无调用点
--    （全仓 grep 仅命中 db/xinfei.sql 与 backups/ 下的定义本身）；定义仍保留在
--    db/xinfei.sql，需要时可原样重建。对新装库这三条是空操作。
--
-- 明确不做：12 处 tinyint(1) 显示宽度不抹平——Connector/J 默认 tinyInt1isBit=true，
--   (1) 是布尔语义标记，抹掉会改变 JDBC 返回类型与元数据；现网第 13 处
--   tinyint(1) 属 flyway_schema_history.success，是 Flyway 自己的表，不动。
--   其余整数列已无显示宽度（int / bigint / tinyint 裸类型，实测 898 列 / 整数列共 910 列）。
-- ============================================================================

ALTER TABLE `notification_template` MODIFY COLUMN `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_bin COMMENT '打印内容';

DROP PROCEDURE IF EXISTS `add_col_if_not_exists`;
DROP PROCEDURE IF EXISTS `create_index_if_not_exists`;
DROP PROCEDURE IF EXISTS `safe_create_index`;
