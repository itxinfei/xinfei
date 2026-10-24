-- V11：付费券包（O-18，对标有赞/天财商龙「付费券包」玩法）
-- 形态：多张优惠券打包售卖（一期口径：包内每模板限一张，受 coupon_user.uk_member_template 约束）
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
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '付费券包';

CREATE TABLE IF NOT EXISTS coupon_package_item (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id bigint NOT NULL COMMENT '租户ID',
  package_id bigint NOT NULL COMMENT '券包ID',
  template_id bigint NOT NULL COMMENT '券模板ID（一期口径：包内每模板限一张）',
  create_time datetime NOT NULL COMMENT '创建时间',
  update_time datetime NOT NULL COMMENT '更新时间',
  create_user bigint NULL DEFAULT NULL COMMENT '创建人',
  update_user bigint NULL DEFAULT NULL COMMENT '修改人',
  is_deleted int NOT NULL DEFAULT 0 COMMENT '逻辑删除 0:未删除 1:已删除',
  PRIMARY KEY (id),
  KEY idx_coupon_package_item_pkg (package_id),
  KEY idx_coupon_package_item_tpl (template_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '付费券包明细（包内券模板）';

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
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '付费券包购买单';
