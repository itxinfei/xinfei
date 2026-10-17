-- ============================================================================
-- V6：把 V5 的桌台演示种子从主键 1~6 迁到 8800011~8800016（避让测试硬编码主键）
-- ----------------------------------------------------------------------------
-- 为什么要改：V5 用固定小主键（区域 1~2 / 桌台 1~6）的理由是「测试数据挂租户 999，不会撞」，
--   这个判断是错的——主键是全局唯一，租户隔离管不到它。实测 V5 落地后
--   `mvn test -Dtest=TableAreaControllerTest` 7 个用例全在 setUp 报 DuplicateKey：
--   该测试往 dining_area 显式插 id=1（租户 999），与 V5 的 area id=1 正面相撞（49 跑 7 错）。
--   现网测试侧在 dining 两表上用到的固定主键：area 1 / 996101，table 10 / 20 / 996001 / 996201 / 996202。
-- 处置：演示种子整体搬到 8800001 段（远离上述所有 band，且 MyBatis-Plus 雪花 ID 在 2.1e18 量级，永不相撞）。
--   V5 已入库、禁止改动（checksum 一变，已迁移库 validate 直接启动失败），故按仓库既定纪律「写错了再加一个新版本纠正」。
-- 删除条件为什么这么写：
--   1) tenant_id=1 + 原 id 段 + 原 name 三重限定，只可能命中 V5 自己插的行；
--   2) 再加 current_order_id IS NULL：若现场有人把演示桌真用起来了（挂着订单），不删它，
--      宁可同时留两套演示桌，也绝不出「订单指向已消失的桌台」的孤儿引用。
--   表没有外键，dining_area 无订单引用列，区域行可直接删。
-- 幂等：INSERT IGNORE + 固定主键，重复执行只补不覆盖；DELETE 命中不到就是空操作。
-- qr_code_url 同步改到新房号，格式仍与 QRCodeUtil.buildTableQRContent 一致；真实部署由后台「生成二维码」覆盖。
-- 对测试的影响：搬到 8800001 段后，与测试夹具（schema-dining.sql 只按租户 999 清理）零交集，A 层恢复全绿。
-- ============================================================================

DELETE FROM dining_area
WHERE tenant_id = 1 AND id IN (1, 2)
  AND name IN ('大厅散座', '临窗卡座');

DELETE FROM dining_table
WHERE tenant_id = 1 AND id BETWEEN 1 AND 6
  AND name IN ('A01', 'A02', 'A03', 'A04', 'B01', 'B02')
  AND current_order_id IS NULL;

INSERT IGNORE INTO dining_area (id, tenant_id, name, sort, created_time, update_time,
                                create_user, update_user, is_deleted)
VALUES (8800001, 1, '大厅散座', 1, NOW(), NOW(), 1, 1, 0),
       (8800002, 1, '临窗卡座', 2, NOW(), NOW(), 1, 1, 0);

INSERT IGNORE INTO dining_table (id, tenant_id, area_id, name, seat_count, status, min_amount,
                                 qr_code_url, current_order_id, sort,
                                 create_time, update_time, create_user, update_user, is_deleted)
VALUES (8800011, 1, 8800001, 'A01', 4, 'FREE', 0.00,
        'http://localhost:8080/front/page/qrcode-order.html?tableId=8800011', NULL, 1,
        NOW(), NOW(), 1, 1, 0),
       (8800012, 1, 8800001, 'A02', 2, 'FREE', 0.00,
        'http://localhost:8080/front/page/qrcode-order.html?tableId=8800012', NULL, 2,
        NOW(), NOW(), 1, 1, 0),
       (8800013, 1, 8800001, 'A03', 4, 'FREE', 30.00,
        'http://localhost:8080/front/page/qrcode-order.html?tableId=8800013', NULL, 3,
        NOW(), NOW(), 1, 1, 0),
       (8800014, 1, 8800001, 'A04', 6, 'CLEANING', 0.00,
        'http://localhost:8080/front/page/qrcode-order.html?tableId=8800014', NULL, 4,
        NOW(), NOW(), 1, 1, 0),
       (8800015, 1, 8800002, 'B01', 8, 'FREE', 88.00,
        'http://localhost:8080/front/page/qrcode-order.html?tableId=8800015', NULL, 1,
        NOW(), NOW(), 1, 1, 0),
       (8800016, 1, 8800002, 'B02', 4, 'FREE', 0.00,
        'http://localhost:8080/front/page/qrcode-order.html?tableId=8800016', NULL, 2,
        NOW(), NOW(), 1, 1, 0);
