-- ============================================================================
-- V5：堂食桌台演示种子（租户 1）——补齐 db/xinfei.sql 里空缺的 dining_area / dining_table
-- ----------------------------------------------------------------------------
-- 为什么需要：这两张表在全库导出里是【空表】（实测 0 行），导致
--   1) 后台「桌台管理」开屏空白，扫码点餐链路无法演示；
--   2) tests/c-table-noorder.mjs、tests/c-p0-eatin-fix.mjs 无桌台可用，只能 process.exit(2)
--      报「前置条件缺失」，两条堂食专项回归无法免手工造数直接复跑。
-- 执行语义：仅插入缺失行（INSERT IGNORE），【不覆盖】已有行——status / current_order_id
--   是运行态，重复执行或人工改过的桌台状态不能被回滚。
-- 刻意不种 OCCUPIED：占用必须与真实订单绑定，否则进「占用却无订单无法结账」异常态
--   （正是 c-table-noorder.mjs 要验证的脏数据形态）。演示需要占用时走后台「开台」。
-- 主键：dining_table 由 MyBatis-Plus 雪花算法生成（历史 AUTO_INCREMENT 达 2.1e18），
--   这里用固定小 ID（区域 1~2 / 桌台 1~6）便于测试脚本以默认值定位；
--   现网两表为空、测试数据一律挂租户 999，不存在撞主键。
-- qr_code_url：与 QRCodeUtil.buildTableQRContent 的拼接格式一致，站点地址取本地演示端口；
--   真实部署后由后台「生成二维码」按现场站点地址覆盖，也可整体留空。
-- 对测试无影响：mvn test 的夹具只按租户 999 清理与断言，本种子全部挂租户 1。
-- ============================================================================

INSERT IGNORE INTO dining_area (id, tenant_id, name, sort, created_time, update_time,
                                create_user, update_user, is_deleted)
VALUES (1, 1, '大厅散座', 1, NOW(), NOW(), 1, 1, 0),
       (2, 1, '临窗卡座', 2, NOW(), NOW(), 1, 1, 0);

INSERT IGNORE INTO dining_table (id, tenant_id, area_id, name, seat_count, status, min_amount,
                                 qr_code_url, current_order_id, sort,
                                 create_time, update_time, create_user, update_user, is_deleted)
VALUES (1, 1, 1, 'A01', 4, 'FREE', 0.00,
        'http://localhost:8080/front/page/qrcode-order.html?tableId=1', NULL, 1,
        NOW(), NOW(), 1, 1, 0),
       (2, 1, 1, 'A02', 2, 'FREE', 0.00,
        'http://localhost:8080/front/page/qrcode-order.html?tableId=2', NULL, 2,
        NOW(), NOW(), 1, 1, 0),
       (3, 1, 1, 'A03', 4, 'FREE', 30.00,
        'http://localhost:8080/front/page/qrcode-order.html?tableId=3', NULL, 3,
        NOW(), NOW(), 1, 1, 0),
       (4, 1, 1, 'A04', 6, 'CLEANING', 0.00,
        'http://localhost:8080/front/page/qrcode-order.html?tableId=4', NULL, 4,
        NOW(), NOW(), 1, 1, 0),
       (5, 1, 2, 'B01', 8, 'FREE', 88.00,
        'http://localhost:8080/front/page/qrcode-order.html?tableId=5', NULL, 1,
        NOW(), NOW(), 1, 1, 0),
       (6, 1, 2, 'B02', 4, 'FREE', 0.00,
        'http://localhost:8080/front/page/qrcode-order.html?tableId=6', NULL, 2,
        NOW(), NOW(), 1, 1, 0);
