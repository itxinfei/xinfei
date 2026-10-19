-- ============================================================================
-- V9：骑手定位数据加固 —— 轨迹表补二级索引 + 标记坐标系（百度地图改造 P1，2026-10-19 用户「可以」）
-- ----------------------------------------------------------------------------
-- 为什么要改（本轮地图子系统探查，功能测试报告 §15 之后的地图专项）：
--   1) rider_location_record 仅有 PRIMARY KEY(id)（见 V1 基线），但历史轨迹查询
--      DeliveryTrackingServiceImpl.getRiderLocationHistory 按 (rider_id, record_time) + 租户过滤，
--      且上报端每骑手每天约 7200 行（前端 12s 一次）、无清理 → 数据量涨起来即全表/范围扫。
--      补 (tenant_id, rider_id, record_time) 复合索引支撑「按租户+骑手取某时段轨迹」主查询路径。
--   2) 全系统坐标系口径此前只由前端单点保证（location.js 报 GCJ-02），库内不记录来源系；
--      一旦有人绕前端直报 WGS-84/BD-09，地图渲染会系统性偏移且无从判别。新增 coord_system 列显式登记。
-- 坐标系列默认值口径：
--   本次改造起，前端改为上报原始 WGS-84（location.js）。存量历史行系改造前由 GCJ-02 前端转换写入，
--   故列默认值取 'GCJ-02' 以如实标注历史数据；改造后新写入的行由服务端显式置为 'WGS-84'（或其它合法值），
--   不依赖默认值。渲染时按每行的 coord_system 转换到地图引擎坐标系（百度 BD-09），坐标系从此可追溯。
-- 幂等：Flyway 版本化，V9 仅执行一次；ADD COLUMN/ADD KEY 在 MySQL 8 非重复安全，故不做 IF NOT EXISTS
--   （与 V7「已入库脚本禁改、写错再加新版本」纪律一致，本文件一旦入库不得改动）。
-- 对测试的影响：同步 src/test/resources/schema-rider.sql（rider 与 rider_location_record 各加 coord_system
--   列、rider_location_record 加同名索引），保持「夹具与 V1→Vn 终态一致」核验口径。
-- ============================================================================

ALTER TABLE rider_location_record
  ADD COLUMN coord_system varchar(16) NOT NULL DEFAULT 'GCJ-02'
    COMMENT '坐标系标识：WGS-84(原始GPS)/GCJ-02(国测局)/BD-09(百度)，渲染时据此转换' AFTER latitude,
  ADD KEY idx_tenant_rider_time (tenant_id, rider_id, record_time);

ALTER TABLE rider
  ADD COLUMN coord_system varchar(16) DEFAULT 'GCJ-02'
    COMMENT '当前位置坐标系的标识：WGS-84/GCJ-02/BD-09' AFTER current_latitude;
