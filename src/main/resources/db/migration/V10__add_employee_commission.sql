-- V10：员工提成（O-17，收银提成口径）
-- 1) employee 增加提成比例列：NULL/0 = 不参与提成；按收银实收（现金扣除找零）计提
ALTER TABLE employee
    ADD COLUMN commission_rate decimal(5, 2) NULL COMMENT '提成比例(%)，NULL/0=不参与提成' AFTER department_id;

-- 2) 提成记录表：收银成功即按当次比例快照落一条，uk_commission_cashier 保证一单一提幂等
CREATE TABLE IF NOT EXISTS commission_record (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id bigint NOT NULL COMMENT '租户ID',
  employee_id bigint NOT NULL COMMENT '收银员工ID',
  employee_name varchar(64) NULL DEFAULT NULL COMMENT '员工姓名（冗余快照）',
  cashier_record_id bigint NOT NULL COMMENT '收银记录ID（提成基数来源）',
  order_id bigint NULL DEFAULT NULL COMMENT '关联订单ID',
  base_amount decimal(12, 2) NOT NULL COMMENT '提成基数=收银实收-找零（元）',
  rate decimal(5, 2) NOT NULL COMMENT '提成比例(%)（生成时快照，改比例不影响历史）',
  commission_amount decimal(12, 2) NOT NULL COMMENT '提成金额（元，四舍五入到分）',
  period varchar(7) NOT NULL COMMENT '所属期间 yyyy-MM（按收银时间）',
  status tinyint NOT NULL DEFAULT 0 COMMENT '状态 0:未结算 1:已结算',
  create_time datetime NOT NULL COMMENT '创建时间',
  update_time datetime NOT NULL COMMENT '更新时间',
  create_user bigint NULL DEFAULT NULL COMMENT '创建人',
  update_user bigint NULL DEFAULT NULL COMMENT '修改人',
  is_deleted int NOT NULL DEFAULT 0 COMMENT '逻辑删除 0:未删除 1:已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_commission_cashier (cashier_record_id),
  KEY idx_commission_tenant_emp_period (tenant_id, employee_id, period)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '员工提成记录（收银提成口径）';
