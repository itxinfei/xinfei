-- ============================================================================
-- V7：加宽 order_detail / shopping_cart 的名称与口味列，消除跨表复制列宽错配
-- ----------------------------------------------------------------------------
-- 为什么要改（功能测试报告 §4.2 P3 / §11-7 拍板②，2026-10-18 用户选定「加宽目标列」）：
--   来源列 dish.name varchar(64)、setmeal.name varchar(64)、dish_flavor.name varchar(64)
--   + dish_flavor.value varchar(255) 的合法值，复制到目标列
--   order_detail.name / shopping_cart.name varchar(50)、
--   order_detail.dish_flavor / shopping_cart.dish_flavor varchar(50) 时无任何截断或校验
--   （OrderServiceImpl.java:1748/1753、:411/414、:421/424、:2232，
--     PlatformOrderPersistServiceImpl.java:152）。
--   STRICT_TRANS_TABLES 下，管理员把菜名起 51~64 字（服务端 @Size(max=64) 允许）后，
--   该菜一加购即 ERROR 1406 → 下单失败。属潜伏缺陷，本轮演示库最长菜名未超 50 未复现。
-- 为什么选加宽不选收紧：收紧到 50 要同时改 Dish/Setmeal/DishFlavor 注解与后台 maxlength，
--   且库里已有的 51~64 字名称（若有）会成存量不一致；加宽只动 4 列、不改任何产品能力。
-- 目标宽度：name → varchar(64)（与来源同宽）；dish_flavor → varchar(255)
--   （组合串由 flavor 名值拼接，报告建议与 dish_flavor.value 同宽）。
-- 幂等：MODIFY 重复执行结果一致；宽度已达标时执行亦为无副作用重写。
-- 注释口径：顺手把这两列的乱码注释（V3 未覆盖批次）改写为明文；只涉及本迁移的 4 列，
--   其余残留乱码维持 §4.2 P2 已决的「维持现状」。
-- 对测试的影响：同步加宽 src/test/resources 夹具（schema.sql 两表 +
--   schema-payment / schema-payment-controller / schema-platform / schema-printer / schema-report 的 order_detail），
--   保持「夹具与 V1→V7 终态一致」的核验口径。
-- ============================================================================

ALTER TABLE order_detail
  MODIFY COLUMN name varchar(64) NOT NULL COMMENT '名称',
  MODIFY COLUMN dish_flavor varchar(255) NULL DEFAULT NULL COMMENT '口味';

ALTER TABLE shopping_cart
  MODIFY COLUMN name varchar(64) NOT NULL COMMENT '名称',
  MODIFY COLUMN dish_flavor varchar(255) NULL DEFAULT NULL COMMENT '口味';
