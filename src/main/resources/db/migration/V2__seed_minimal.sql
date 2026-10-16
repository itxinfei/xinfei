-- ============================================================================
-- V2 最小可登录种子数据（新装环境专用）
-- ----------------------------------------------------------------------------
-- 执行语义：仅在空库（V1 刚建完表）时执行。既有库因 baseline-version=2 会跳过本脚本，
--           不会覆盖开发/生产库里已被人工改过的租户名、角色绑定等数据。
-- 内容：由 db/seed-minimal.sql 转写，额外补齐 employee_role.tenant_id —— 原脚本漏给
--       该列，而 employee_role 受 MyBatis-Plus 租户插件过滤（不在 IGNORE_TABLES 内），
--       tenant_id 为 NULL 时新装库的 admin 查不到角色，@RequiresAdmin 会直接 403。
-- 账号：admin / 123456（口令为 MD5('123456')，首次登录校验通过后系统自动升级为 BCrypt；
--       正式部署请首次登录立即改密 PUT /employee/password）。
-- 后台左侧菜单写死在 backend/index.html，非数据库驱动，绑定 SUPER_ADMIN 后即可见全部菜单。
-- ============================================================================

-- 租户（多租户隔离的根，所有业务数据都挂在某个 tenant_id 下）
INSERT INTO tenant (id, name, status, create_time, update_time)
VALUES (1, '演示租户', 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE name = VALUES(name), update_time = NOW();

-- 门店
INSERT INTO store (id, name, address, phone, status, create_time, update_time, is_deleted, tenant_id)
VALUES (1, '心飞外卖旗舰店', '北京市朝阳区示例路 1 号', '13900000000', 1, NOW(), NOW(), 0, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), address = VALUES(address), update_time = NOW();

-- 角色（role_key=SUPER_ADMIN 是 @RequiresAdmin / @RequiresPermission 的放行依据）
INSERT INTO role (id, tenant_id, role_name, role_key, sort, status, create_time, update_time, create_user, update_user, is_deleted)
VALUES (18, 1, '超级管理员', 'SUPER_ADMIN', 1, 1, NOW(), NOW(), 1, 1, 0)
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name), update_time = NOW();

-- 管理员员工（role=1 为旧字段兜底，employee_role 才是 RBAC 正解）
INSERT INTO employee (id, username, name, phone, status, create_time, update_time,
                      create_user, update_user, password, password_type, tenant_id, role)
VALUES (1, 'admin', '超级管理员', '13901010001', 1, NOW(), NOW(),
        1, 1, 'e10adc3949ba59abbe56e057f20f883e', 'MD5', 1, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), update_time = NOW();

-- 员工↔角色关联（tenant_id 必须显式给 1，否则租户插件过滤后查不到角色）
INSERT INTO employee_role (id, employee_id, role_id, tenant_id, create_time)
VALUES (1, 1, 18, 1, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id), tenant_id = VALUES(tenant_id);
