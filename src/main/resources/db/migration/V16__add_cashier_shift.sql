-- V16：收银交班台账（O-29，O-22 交班对账的持久化收尾）
-- 形态：员工在收银台点「确认交班」时，把当时点的当日+本人收款聚合快照落一条台账
-- （口径与 GET /cashier/shift-summary 完全一致，复用同一聚合函数），历史可查、跨日可对。
CREATE TABLE IF NOT EXISTS cashier_shift (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id bigint NOT NULL COMMENT '租户ID',
  employee_id bigint NOT NULL COMMENT '收银员工ID',
  employee_name varchar(64) NULL DEFAULT NULL COMMENT '员工姓名（快照）',
  shift_date date NOT NULL COMMENT '班次所属日期（自然日口径，与 shift-summary 一致）',
  close_time datetime NOT NULL COMMENT '交班时间（落库时刻）',
  order_count int NOT NULL DEFAULT 0 COMMENT '本班收款笔数',
  total_amount decimal(12, 2) NOT NULL DEFAULT 0.00 COMMENT '本班收款总额（元，实收口径）',
  change_amount decimal(12, 2) NOT NULL DEFAULT 0.00 COMMENT '本班找零合计（元）',
  pay_method_summary varchar(500) NULL DEFAULT NULL COMMENT '按支付方式分布JSON（payType/count/amount）',
  remark varchar(200) NULL DEFAULT NULL COMMENT '交班备注（差额说明等）',
  create_time datetime NOT NULL COMMENT '创建时间',
  update_time datetime NOT NULL COMMENT '更新时间',
  create_user bigint NULL DEFAULT NULL COMMENT '创建人',
  update_user bigint NULL DEFAULT NULL COMMENT '修改人',
  is_deleted int NOT NULL DEFAULT 0 COMMENT '逻辑删除 0:未删除 1:已删除',
  PRIMARY KEY (id),
  KEY idx_cashier_shift_tenant_emp (tenant_id, employee_id, shift_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '收银交班台账';
