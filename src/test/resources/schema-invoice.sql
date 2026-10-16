-- Invoice module test schema (MySQL compatible)
-- 结构与 V1 基线（src/main/resources/db/migration/V1__baseline_schema.sql）逐列/逐索引对齐
-- 列注释按真实语义修正：基线里 order_no 的注释是 GBK 乱码（璁㈠崟鍙 = 订单号），此处不复刻乱码


-- InvoiceRecord entity (@TableName("invoice_record"))
CREATE TABLE IF NOT EXISTS invoice_record (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  order_id bigint NOT NULL COMMENT '订单ID',
  user_id bigint NULL DEFAULT NULL COMMENT '申请用户ID（用户归属列，防止跨用户越权查询）',
  order_no varchar(32) NULL DEFAULT NULL COMMENT '订单号',
  title_id bigint NULL DEFAULT NULL COMMENT '发票抬头ID',
  title varchar(200) NULL DEFAULT NULL COMMENT '发票抬头（冗余）',
  tax_number varchar(100) NULL DEFAULT NULL COMMENT '税号（冗余）',
  type int NOT NULL DEFAULT 1 COMMENT '发票类型（1-个人 2-企业）',
  amount decimal(10,2) NULL DEFAULT NULL COMMENT '发票金额',
  status int NOT NULL DEFAULT 0 COMMENT '状态（0-待申请 1-已申请 2-已开具 3-已作废）',
  invoice_no varchar(64) NULL DEFAULT NULL COMMENT '发票号码',
  invoice_code varchar(64) NULL DEFAULT NULL COMMENT '发票代码',
  invoice_url varchar(500) NULL DEFAULT NULL COMMENT '发票PDF地址',
  apply_time datetime NULL DEFAULT NULL COMMENT '申请时间',
  issue_time datetime NULL DEFAULT NULL COMMENT '开具时间',
  tenant_id bigint NOT NULL COMMENT '租户ID',
  create_time datetime NULL DEFAULT NULL COMMENT '创建时间',
  update_time datetime NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_record_order (order_id),
  KEY idx_record_tenant (tenant_id),
  KEY idx_record_user (tenant_id, user_id)
);

-- InvoiceTitle entity (@TableName("invoice_title"))
CREATE TABLE IF NOT EXISTS invoice_title (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  title varchar(200) NOT NULL COMMENT '发票抬头',
  tax_number varchar(100) NULL DEFAULT NULL COMMENT '税号',
  company_name varchar(200) NULL DEFAULT NULL COMMENT '公司名称（企业发票用）',
  type int NOT NULL DEFAULT 1 COMMENT '发票类型（1-个人 2-企业）',
  tenant_id bigint NOT NULL COMMENT '租户ID',
  user_id bigint NULL DEFAULT NULL COMMENT '归属用户ID（用户隔离，防止跨用户越权）',
  create_time datetime NULL DEFAULT NULL COMMENT '创建时间',
  update_time datetime NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_title_tenant (tenant_id),
  KEY idx_title_user (tenant_id, user_id)
);

-- 单库隔离（2026-10-16）：仅清理测试租户 999 的残留，禁止无条件全表 DELETE（会清掉租户 1 演示数据）
DELETE FROM invoice_record WHERE tenant_id = 999;
DELETE FROM invoice_title WHERE tenant_id = 999;
