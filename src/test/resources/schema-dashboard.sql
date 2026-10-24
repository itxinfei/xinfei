-- 工作台待办计数 / 时段分布测试夹具（租户 999 命名空间）
-- refund_record 不在基础 schema.sql 中，此处 IF NOT EXISTS 兜底建表（结构同 schema-payment.sql）
CREATE TABLE IF NOT EXISTS refund_record (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  payment_order_id bigint NOT NULL COMMENT '支付订单id',
  order_id bigint DEFAULT NULL COMMENT '业务订单ID',
  tenant_id bigint DEFAULT NULL COMMENT '租户ID',
  refund_no varchar(64) NOT NULL COMMENT '退款单号',
  amount decimal(10,2) NOT NULL COMMENT '退款金额',
  reason varchar(255) DEFAULT NULL COMMENT '退款原因',
  status varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态 PENDING/SUCCESS/FAIL',
  refund_type int DEFAULT NULL COMMENT '售后类型：1=整单退款 2=部分退款',
  apply_user_id bigint DEFAULT NULL COMMENT '申请人ID',
  audit_user_id bigint DEFAULT NULL COMMENT '审核人ID',
  audit_time datetime DEFAULT NULL COMMENT '审核时间',
  reject_reason varchar(500) DEFAULT NULL COMMENT '拒绝原因',
  refund_time datetime DEFAULT NULL COMMENT '退款完成时间',
  created_time datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  is_deleted int NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删除 1=已删除',
  version int NOT NULL DEFAULT 1 COMMENT '版本号',
  create_user bigint DEFAULT NULL COMMENT '创建人ID',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  update_user bigint DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (id) USING BTREE,
  UNIQUE KEY uk_refund_no (refund_no) USING BTREE,
  KEY idx_payment (payment_order_id) USING BTREE,
  KEY idx_tenant_id (tenant_id)
);

-- 单库隔离（2026-10-06）：仅清理租户 999 测试数据，禁止无条件全表 DELETE
DELETE FROM refund_record WHERE tenant_id = 999 AND id IN (99101, 99102);
DELETE FROM dish_evaluation WHERE tenant_id = 999 AND order_id IN (99001, 99002, 99003);
DELETE FROM order_detail WHERE tenant_id = 999 AND order_id IN (99001, 99002, 99003);
DELETE FROM orders WHERE tenant_id = 999 AND id IN (99001, 99002, 99003);

-- 订单A：已完成、今日10:30、含未评价菜品 → 计入待评价 + 10点时段
INSERT INTO orders (id, number, status, user_id, address_book_id, order_time, checkout_time, pay_method, amount, phone, address, user_name, consignee, dining_type, create_time, update_time, create_user, update_user, is_deleted, tenant_id)
VALUES (99001, 'DASH99001', 4, 1, 1, CONCAT(CURDATE(), ' 10:30:00'), CONCAT(CURDATE(), ' 11:00:00'), 1, 100.00, '13900139001', '测试地址', '测试用户', '测试用户', 'OUTSIDE', NOW(), NOW(), 1, 1, 0, 999);
INSERT INTO order_detail (name, order_id, dish_id, setmeal_id, number, amount, tenant_id, create_time, update_time, is_deleted)
VALUES ('待评价菜品', 99001, 501, NULL, 1, 100.00, 999, NOW(), NOW(), 0);

-- 订单B：已完成、今日11:00、菜品已有评价 → 不计待评价 + 11点时段
INSERT INTO orders (id, number, status, user_id, address_book_id, order_time, checkout_time, pay_method, amount, phone, address, user_name, consignee, dining_type, create_time, update_time, create_user, update_user, is_deleted, tenant_id)
VALUES (99002, 'DASH99002', 4, 1, 1, CONCAT(CURDATE(), ' 11:00:00'), CONCAT(CURDATE(), ' 11:30:00'), 1, 200.00, '13900139001', '测试地址', '测试用户', '测试用户', 'OUTSIDE', NOW(), NOW(), 1, 1, 0, 999);
INSERT INTO order_detail (name, order_id, dish_id, setmeal_id, number, amount, tenant_id, create_time, update_time, is_deleted)
VALUES ('已评价菜品', 99002, 502, NULL, 1, 200.00, 999, NOW(), NOW(), 0);
INSERT INTO dish_evaluation (id, tenant_id, order_id, user_id, user_name, dish_id, dish_name, star_rating, content, anonymous, status, create_time, update_time, is_deleted)
VALUES (99002, 999, 99002, 1, '测试用户', 502, '已评价菜品', 5, '好吃', 0, 1, NOW(), NOW(), 0);

-- 订单C：已取消、今日10:45、菜品未评价 → 不计待评价（非已完成）+ 10点时段
INSERT INTO orders (id, number, status, user_id, address_book_id, order_time, checkout_time, pay_method, amount, phone, address, user_name, consignee, dining_type, create_time, update_time, create_user, update_user, is_deleted, tenant_id)
VALUES (99003, 'DASH99003', 5, 1, 1, CONCAT(CURDATE(), ' 10:45:00'), NULL, 1, 50.00, '13900139001', '测试地址', '测试用户', '测试用户', 'OUTSIDE', NOW(), NOW(), 1, 1, 0, 999);
INSERT INTO order_detail (name, order_id, dish_id, setmeal_id, number, amount, tenant_id, create_time, update_time, is_deleted)
VALUES ('取消单菜品', 99003, 503, NULL, 1, 50.00, 999, NOW(), NOW(), 0);

-- 退款申请：99101 待审核(pending) → 计入待退款；99102 已成功 → 不计
INSERT INTO refund_record (id, payment_order_id, order_id, tenant_id, refund_no, amount, reason, status, refund_type, apply_user_id, created_time, is_deleted, version)
VALUES (99101, 99001, 99001, 999, 'RFD-DASH-99001', 100.00, '测试退款', 'pending', 1, 1, NOW(), 0, 1);
INSERT INTO refund_record (id, payment_order_id, order_id, tenant_id, refund_no, amount, reason, status, refund_type, apply_user_id, created_time, is_deleted, version)
VALUES (99102, 99002, 99002, 999, 'RFD-DASH-99002', 200.00, '已完成退款', 'success', 1, 1, NOW(), 0, 1);
