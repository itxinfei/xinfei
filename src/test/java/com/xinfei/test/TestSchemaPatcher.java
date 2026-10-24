package com.xinfei.test;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 测试库结构补丁器：给「测试库中已存在的历史表」幂等补列。
 * <p>背景：test profile 关闭 Flyway，夹具 schema.sql 的 CREATE TABLE IF NOT EXISTS
 * 对历史已建表不会生效——首次出现「给已存在表加列」的迁移（V12 employee.commission_rate）
 * 时，旧测试库会缺列。本工具按 information_schema 判断缺列才 ALTER，新库/旧库都幂等。</p>
 *
 * @author xinfei
 * @since 2026-10-24
 */
@Component
public class TestSchemaPatcher {

    private final JdbcTemplate jdbcTemplate;

    public TestSchemaPatcher(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 确认列存在，缺列才 ALTER（幂等，可重复调用）。
     *
     * @param table      表名（白名单内部使用，不拼接用户输入以外的内容）
     * @param column     列名
     * @param columnDdl  列定义（如 "commission_rate decimal(5,2) NULL DEFAULT NULL COMMENT 'x'"）
     */
    public void ensureColumn(String table, String column, String columnDdl) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Integer.class, table, column);
        if (count != null && count == 0) {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + columnDdl);
        }
    }
}
