-- ============================================================
-- 导出模块表（O-25 异步导出任务中心，2026-10-24）
-- 按模块约定拆分至 schema-export.sql（AGENTS.md：模块表进 schema-<module>.sql）。
-- 结构与 db/migration/V14__add_export_task.sql 终态一致（夹具刻意不写索引注释）。
-- ============================================================

-- ==================== 异步导出任务表 ====================
CREATE TABLE IF NOT EXISTS export_task (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id bigint NOT NULL COMMENT '租户ID',
  employee_id bigint NOT NULL COMMENT '发起员工ID',
  employee_name varchar(64) NULL DEFAULT NULL COMMENT '发起员工姓名（冗余快照）',
  task_type varchar(32) NOT NULL COMMENT '任务类型 orders/dishes/employees/report',
  file_format varchar(16) NOT NULL COMMENT '文件格式 excel/pdf',
  params_json text NULL COMMENT '查询参数JSON',
  status tinyint NOT NULL DEFAULT 0 COMMENT '状态 0:排队中 1:生成中 2:已完成 3:失败',
  file_name varchar(255) NULL DEFAULT NULL COMMENT '下载文件名（完成时写入）',
  file_size bigint NULL DEFAULT NULL COMMENT '文件大小（字节）',
  error_msg varchar(500) NULL DEFAULT NULL COMMENT '失败原因',
  finish_time datetime NULL DEFAULT NULL COMMENT '完成/失败时间',
  create_time datetime NOT NULL COMMENT '创建时间',
  update_time datetime NOT NULL COMMENT '更新时间',
  create_user bigint NULL DEFAULT NULL COMMENT '创建人',
  update_user bigint NULL DEFAULT NULL COMMENT '修改人',
  is_deleted int NOT NULL DEFAULT 0 COMMENT '逻辑删除 0:未删除 1:已删除',
  PRIMARY KEY (id),
  KEY idx_export_task_tenant_emp (tenant_id, employee_id, create_time),
  KEY idx_export_task_tenant_status (tenant_id, status)
);
