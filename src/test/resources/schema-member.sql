-- Member module test schema (H2 compatible)


CREATE TABLE IF NOT EXISTS member_level (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id bigint NULL DEFAULT NULL COMMENT '租户id',
  name varchar(50) NULL DEFAULT NULL COMMENT '等级名称',
  MIN_POINTS bigint NULL DEFAULT 0 COMMENT '最低积分要求',
  MAX_POINTS bigint NULL DEFAULT NULL COMMENT '最高积分上限',
  discount decimal(3,2) NULL DEFAULT 1.00 COMMENT '折扣',
  description varchar(200) NULL DEFAULT NULL COMMENT '等级说明',
  sort int NULL DEFAULT NULL COMMENT '排序',
  created_time datetime NULL DEFAULT NULL COMMENT '创建时间',
  update_time datetime NULL DEFAULT NULL COMMENT '更新时间',
  create_user bigint NULL DEFAULT NULL COMMENT '创建人ID',
  update_user bigint NULL DEFAULT NULL COMMENT '更新人ID',
  is_deleted int NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS member (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id bigint NULL DEFAULT NULL COMMENT '租户id',
  user_id bigint NULL DEFAULT NULL COMMENT '关联用户ID',
  level_id bigint NULL DEFAULT NULL COMMENT '会员等级ID',
  name varchar(50) NULL DEFAULT NULL COMMENT '会员姓名',
  phone varchar(11) NULL DEFAULT NULL COMMENT '手机',
  points bigint NULL DEFAULT 0 COMMENT '秈',
  balance decimal(10,2) NULL DEFAULT 0.00 COMMENT '余',
  total_consumption decimal(10,2) NULL DEFAULT 0.00 COMMENT '消费金',
  status int NULL DEFAULT 1 COMMENT '状',
  created_time datetime NULL DEFAULT NULL COMMENT '创建时间',
  update_time datetime NULL DEFAULT NULL COMMENT '更新时间',
  create_user bigint NULL DEFAULT NULL COMMENT '创建人ID',
  update_user bigint NULL DEFAULT NULL COMMENT '更新人ID',
  is_deleted int NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  version int NOT NULL DEFAULT 0 COMMENT '乐锁版朏',
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS coupon_template (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id bigint NULL DEFAULT NULL COMMENT '租户id',
  name varchar(100) NULL DEFAULT NULL COMMENT '模板名称',
  type varchar(20) NULL DEFAULT NULL COMMENT '类型',
  condition_amount decimal(10,2) NULL DEFAULT NULL COMMENT '满减条件金',
  discount_amount decimal(10,2) NULL DEFAULT NULL COMMENT '优惠金',
  discount_rate decimal(3,2) NULL DEFAULT NULL COMMENT '折扣',
  total_count int NULL DEFAULT 0 COMMENT '发放总数',
  remain_count int NULL DEFAULT 0 COMMENT '剩余数量',
  valid_days int NULL DEFAULT NULL COMMENT '有效天数',
  points_price int NULL DEFAULT NULL COMMENT '积分兑换所需积分',
  status int NULL DEFAULT 1 COMMENT '状',
  created_time datetime NULL DEFAULT NULL COMMENT '创建时间',
  update_time datetime NULL DEFAULT NULL COMMENT '更新时间',
  create_user bigint NULL DEFAULT NULL COMMENT '创建人ID',
  update_user bigint NULL DEFAULT NULL COMMENT '更新人ID',
  is_deleted int NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS coupon_user (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id bigint NULL DEFAULT NULL COMMENT '租户id',
  member_id bigint NULL DEFAULT NULL COMMENT '会员ID',
  template_id bigint NULL DEFAULT NULL COMMENT '优惠券模板ID',
  code varchar(64) NULL DEFAULT NULL COMMENT '优惠券码',
  status varchar(20) NULL DEFAULT 'unused' COMMENT '状态 UNUSED/USED/EXPIRED',
  used_time datetime NULL DEFAULT NULL COMMENT '使用时间',
  order_id bigint NULL DEFAULT NULL COMMENT '使用订单ID',
  expire_time datetime NULL DEFAULT NULL COMMENT '过期时间',
  created_time datetime NULL DEFAULT NULL COMMENT '创建时间',
  update_time datetime NULL DEFAULT NULL COMMENT '更新时间',
  create_user bigint NULL DEFAULT NULL COMMENT '创建人ID',
  update_user bigint NULL DEFAULT NULL COMMENT '更新人ID',
  is_deleted int NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_member_template (member_id,template_id)
);
-- 修改点：防重复领取唯一索引（与 xinfei.sql uk_member_template 对齐）

CREATE TABLE IF NOT EXISTS points_record (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id bigint NULL DEFAULT NULL COMMENT '租户id',
  member_id bigint NULL DEFAULT NULL COMMENT '会员ID',
  type varchar(20) NULL DEFAULT NULL COMMENT '类型',
  points int NULL DEFAULT NULL COMMENT '秈数量',
  biz_type varchar(50) NULL DEFAULT NULL COMMENT '关联业务类型',
  biz_id bigint NULL DEFAULT NULL COMMENT '关联业务ID',
  remark varchar(200) NULL DEFAULT NULL COMMENT '备注',
  expire_time datetime NULL DEFAULT NULL COMMENT '积分过期时间',
  created_time datetime NULL DEFAULT NULL COMMENT '创建时间',
  update_time datetime NULL DEFAULT NULL COMMENT '更新时间',
  create_user bigint NULL DEFAULT NULL COMMENT '创建人ID',
  update_user bigint NULL DEFAULT NULL COMMENT '修改人ID',
  is_deleted int NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uq_points_biz (tenant_id,biz_type,biz_id,type)
);

CREATE TABLE IF NOT EXISTS recharge_record (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id bigint NULL DEFAULT NULL COMMENT '租户id',
  member_id bigint NULL DEFAULT NULL COMMENT '会员ID',
  user_id bigint NULL DEFAULT NULL COMMENT '归属用户ID（C端自助充值冗余）',
  recharge_no varchar(64) NULL DEFAULT NULL COMMENT '充值单号（业务唯一）',
  status varchar(20) NULL DEFAULT 'SUCCESS' COMMENT '状态 PENDING/SUCCESS/CANCELLED',
  amount decimal(10,2) NULL DEFAULT NULL COMMENT '充金',
  gift_amount decimal(10,2) NULL DEFAULT 0.00 COMMENT '赠金',
  payment_method varchar(20) NULL DEFAULT NULL COMMENT '渠道 WECHAT/ALIPAY/CASH（预留在线支付）',
  trade_no varchar(64) NULL DEFAULT NULL COMMENT '渠道交易号（预留在线支付回填）',
  confirm_employee_id bigint NULL DEFAULT NULL COMMENT '确认到账员工ID',
  confirm_time datetime NULL DEFAULT NULL COMMENT '确认到账时间',
  created_time datetime NULL DEFAULT NULL COMMENT '创建时间',
  update_time datetime NULL DEFAULT NULL COMMENT '更新时间',
  create_user bigint NULL DEFAULT NULL COMMENT '创建人ID',
  update_user bigint NULL DEFAULT NULL COMMENT '修改人ID',
  is_deleted int NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_recharge_no (recharge_no),
  KEY idx_rr_member_status (member_id,status)
);
-- 充值单号唯一索引（NULL 不冲突，兼容历史无单号数据）

-- 修改点(2026-09-26)：每方法执行前清空模块表，消除跨方法残留导致的计数/主键冲突
-- 单库隔离（2026-10-06）：仅清理测试租户 999 的残留，禁止无条件全表 DELETE（会清掉租户 1 演示数据）
DELETE FROM member_level WHERE tenant_id = 999;
DELETE FROM member WHERE tenant_id = 999;
DELETE FROM coupon_template WHERE tenant_id = 999;
DELETE FROM coupon_user WHERE tenant_id = 999;
DELETE FROM points_record WHERE tenant_id = 999;
DELETE FROM recharge_record WHERE tenant_id = 999;

-- ==================== 付费券包（V11 终态，2026-10-24 O-18） ====================
CREATE TABLE IF NOT EXISTS coupon_package (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id bigint NOT NULL COMMENT '租户ID',
  name varchar(64) NOT NULL COMMENT '券包名称',
  price decimal(10, 2) NOT NULL COMMENT '售价（元）',
  original_value decimal(10, 2) NULL DEFAULT NULL COMMENT '包内券面值/权益合计（展示用）',
  description varchar(400) NULL DEFAULT NULL COMMENT '描述',
  image varchar(255) NULL DEFAULT NULL COMMENT '图片',
  status tinyint NOT NULL DEFAULT 1 COMMENT '状态 0:下架 1:上架',
  sort int NOT NULL DEFAULT 0 COMMENT '排序（小在前）',
  create_time datetime NOT NULL COMMENT '创建时间',
  update_time datetime NOT NULL COMMENT '更新时间',
  create_user bigint NULL DEFAULT NULL COMMENT '创建人',
  update_user bigint NULL DEFAULT NULL COMMENT '修改人',
  is_deleted int NOT NULL DEFAULT 0 COMMENT '逻辑删除 0:未删除 1:已删除',
  PRIMARY KEY (id),
  KEY idx_coupon_package_tenant_status (tenant_id, status)
) COMMENT = '付费券包';

CREATE TABLE IF NOT EXISTS coupon_package_item (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id bigint NOT NULL COMMENT '租户ID',
  package_id bigint NOT NULL COMMENT '券包ID',
  template_id bigint NOT NULL COMMENT '券模板ID（一期每模板限一张）',
  create_time datetime NOT NULL COMMENT '创建时间',
  update_time datetime NOT NULL COMMENT '更新时间',
  create_user bigint NULL DEFAULT NULL COMMENT '创建人',
  update_user bigint NULL DEFAULT NULL COMMENT '修改人',
  is_deleted int NOT NULL DEFAULT 0 COMMENT '逻辑删除 0:未删除 1:已删除',
  PRIMARY KEY (id),
  KEY idx_coupon_package_item_pkg (package_id),
  KEY idx_coupon_package_item_tpl (template_id)
) COMMENT = '付费券包明细';

CREATE TABLE IF NOT EXISTS coupon_package_order (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id bigint NOT NULL COMMENT '租户ID',
  order_no varchar(50) NOT NULL COMMENT '购买单号',
  user_id bigint NOT NULL COMMENT '购买用户ID',
  package_id bigint NOT NULL COMMENT '券包ID',
  package_name varchar(64) NULL DEFAULT NULL COMMENT '券包名称快照',
  price decimal(10, 2) NOT NULL COMMENT '支付金额（下单时快照）',
  status tinyint NOT NULL DEFAULT 0 COMMENT '状态 0:待支付 1:已支付(已发券) 2:已关闭',
  paid_time datetime NULL DEFAULT NULL COMMENT '支付时间',
  create_time datetime NOT NULL COMMENT '创建时间',
  update_time datetime NOT NULL COMMENT '更新时间',
  create_user bigint NULL DEFAULT NULL COMMENT '创建人',
  update_user bigint NULL DEFAULT NULL COMMENT '修改人',
  is_deleted int NOT NULL DEFAULT 0 COMMENT '逻辑删除 0:未删除 1:已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_coupon_pkg_order_no (order_no),
  KEY idx_coupon_pkg_order_user (tenant_id, user_id),
  KEY idx_coupon_pkg_order_pkg (package_id)
) COMMENT = '付费券包购买单';
