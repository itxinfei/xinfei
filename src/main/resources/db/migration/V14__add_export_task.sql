-- V14：异步导出任务（O-25）
-- 形态：导出中心各类型（订单/菜品/员工/经营报表）导出改为异步任务——提交即建任务落库，
--       后台 exportTaskExecutor 异步生成文件落盘，任务中心轮询状态、按任务下载。
-- 文件存储在 xinfei.export.dir（默认 {user.dir}/data/exports，非静态伺服目录，仅下载端点带会话鉴权读出）。
CREATE TABLE IF NOT EXISTS export_task (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id bigint NOT NULL COMMENT '租户ID',
  employee_id bigint NOT NULL COMMENT '发起员工ID',
  employee_name varchar(64) NULL DEFAULT NULL COMMENT '发起员工姓名（冗余快照）',
  task_type varchar(32) NOT NULL COMMENT '任务类型 orders:订单 dishes:菜品 employees:员工 report:经营报表',
  file_format varchar(16) NOT NULL COMMENT '文件格式 excel/pdf',
  params_json varchar(1000) NULL DEFAULT NULL COMMENT '查询参数JSON（k=v 键值对）',
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
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '异步导出任务（导出中心）';
