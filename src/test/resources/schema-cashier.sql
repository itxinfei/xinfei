-- Cashier module test schema (H2 compatible)
-- Matches entity column names from MyBatis-Plus default camelCase conversion


-- CashierRecord entity (@TableName("cashier_record"))
-- Columns: id, orderId, orderNumber, payType, amount, actualAmount, changeAmount, cashierTime, cashierId, cashierName, remark, tenantId, createTime, createUser
CREATE TABLE IF NOT EXISTS cashier_record (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  order_id bigint NULL DEFAULT NULL COMMENT '订单ID',
  order_number varchar(50) NULL DEFAULT NULL COMMENT '订单',
  pay_type int NULL DEFAULT NULL COMMENT '收银类型 1-现金 2-徿 3-攻 4-银 5-会员储',
  amount decimal(10,2) NULL DEFAULT NULL COMMENT '收银金',
  actual_amount decimal(10,2) NULL DEFAULT NULL COMMENT '实收金',
  change_amount decimal(10,2) NULL DEFAULT NULL COMMENT '找零金',
  cashier_time datetime NULL DEFAULT NULL COMMENT '收银时间',
  cashier_id bigint NULL DEFAULT NULL COMMENT '收银员ID',
  cashier_name varchar(50) NULL DEFAULT NULL COMMENT '收银员',
  remark varchar(200) NULL DEFAULT NULL COMMENT '备注',
  voucher_url varchar(255) NULL DEFAULT NULL COMMENT '付款凭证图片相对路径',
  tenant_id bigint NULL DEFAULT NULL COMMENT '租户ID',
  create_time datetime NULL DEFAULT NULL COMMENT '创建时间',
  create_user bigint NULL DEFAULT NULL COMMENT '创建人ID',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_order (tenant_id,order_id)
);

-- DailySettlement entity (@TableName("daily_settlement"))
-- Columns: id, settlementDate, totalRevenue, cashIncome, wechatIncome, alipayIncome, bankcardIncome, otherIncome, orderCount, refundAmount, refundCount, netIncome, materialCost, laborCost, otherCost, totalCost, grossProfit, profitRate, status, settlementTime, settlementUserId, settlementUserName, remark, tenantId, createTime, updateTime, createUser, updateUser
CREATE TABLE IF NOT EXISTS daily_settlement (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  settlement_date date NULL DEFAULT NULL COMMENT '结算日期',
  total_revenue decimal(10,2) NULL DEFAULT NULL COMMENT '营业',
  cash_income decimal(10,2) NULL DEFAULT NULL COMMENT '现金收入',
  wechat_income decimal(10,2) NULL DEFAULT NULL COMMENT '徿收入',
  alipay_income decimal(10,2) NULL DEFAULT NULL COMMENT '攻宝收',
  bankcard_income decimal(10,2) NULL DEFAULT NULL COMMENT '银卡收',
  other_income decimal(10,2) NULL DEFAULT NULL COMMENT '其他收入',
  order_count int NULL DEFAULT NULL COMMENT '订单数量',
  refund_amount decimal(10,2) NULL DEFAULT NULL COMMENT '款金',
  refund_count int NULL DEFAULT NULL COMMENT '款数',
  net_income decimal(10,2) NULL DEFAULT NULL COMMENT '收入',
  material_cost decimal(10,2) NULL DEFAULT NULL COMMENT '食材成本',
  labor_cost decimal(10,2) NULL DEFAULT NULL COMMENT '人工成本',
  other_cost decimal(10,2) NULL DEFAULT NULL COMMENT '其他成本',
  total_cost decimal(10,2) NULL DEFAULT NULL COMMENT '总成',
  gross_profit decimal(10,2) NULL DEFAULT NULL COMMENT '毛利',
  profit_rate decimal(10,2) NULL DEFAULT NULL COMMENT '毛利(%)',
  status int NULL DEFAULT 0 COMMENT '结账状 0-朻 1-已结',
  settlement_time datetime NULL DEFAULT NULL COMMENT '结账时间',
  settlement_user_id bigint NULL DEFAULT NULL COMMENT '结账人ID',
  settlement_user_name varchar(50) NULL DEFAULT NULL COMMENT '结账人',
  remark varchar(200) NULL DEFAULT NULL COMMENT '备注',
  tenant_id bigint NULL DEFAULT NULL COMMENT '租户ID',
  create_time datetime NULL DEFAULT NULL COMMENT '创建时间',
  update_time datetime NULL DEFAULT NULL COMMENT '更新时间',
  create_user bigint NULL DEFAULT NULL COMMENT '创建人ID',
  update_user bigint NULL DEFAULT NULL COMMENT '更新人ID',
  version int NOT NULL DEFAULT 0 COMMENT '乐锁版朏',
  PRIMARY KEY (id),
  UNIQUE KEY uk_daily_settlement_tenant_date (tenant_id,settlement_date)
);
-- ==================== 员工提成记录（V10 终态，2026-10-24 O-17） ====================
CREATE TABLE IF NOT EXISTS commission_record (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id bigint NOT NULL COMMENT '租户ID',
  employee_id bigint NOT NULL COMMENT '收银员工ID',
  employee_name varchar(64) NULL DEFAULT NULL COMMENT '员工姓名（冗余快照）',
  cashier_record_id bigint NOT NULL COMMENT '收银记录ID（提成基数来源）',
  order_id bigint NULL DEFAULT NULL COMMENT '关联订单ID',
  base_amount decimal(12, 2) NOT NULL COMMENT '提成基数=收银实收-找零（元）',
  rate decimal(5, 2) NOT NULL COMMENT '提成比例(%)（生成时快照）',
  commission_amount decimal(12, 2) NOT NULL COMMENT '提成金额（元）',
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
) COMMENT = '员工提成记录（收银提成口径）';
