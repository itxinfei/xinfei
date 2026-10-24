-- V17：细粒度权限键入典（O-30，O-27 按钮级权限联动的收尾）
-- 背景：后端 36 处 @RequiresPermission 使用的细粒度键此前不在 permission 字典，
--       非超管角色永远无法通过校验（等效仅超管可用）。本脚本把键写入字典，
--       使其出现在角色管理「权限树」中可被授权；**本脚本不做任何授权**——
--       授权矩阵由管理员在 系统设置→角色管理→权限树 中按岗位勾选（勾选即生效，
--       菜单门（O-27 perm 字段）与端点门（PermissionAspect）共用同一 permSet）。
-- 层级：4 个 TYPE_MENU 顶级键（与 10 个既有菜单级键同层）+ 2 个 TYPE_BUTTON 子键
--       （挂在 evaluation:view 下）。未入典的键不新增（如 dish:edit 仅为注解示例无实际使用）。

-- 1) 平台对接管理（门：platform/config、platform/dish-mapping 页面菜单 + 全部端点）
INSERT INTO permission (permission_name, permission_key, permission_type, parent_id, sort, status)
SELECT '平台对接管理', 'platform:manage', 1, 0, 11, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM (SELECT 1 FROM permission WHERE permission_key = 'platform:manage') t);

-- 2) 支付渠道管理（门：payment/channel-config 页面菜单 + 全部端点）
INSERT INTO permission (permission_name, permission_key, permission_type, parent_id, sort, status)
SELECT '支付渠道管理', 'payment:manage', 1, 0, 12, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM (SELECT 1 FROM permission WHERE permission_key = 'payment:manage') t);

-- 3) 评价管理（门：report/evaluation-list 页面菜单 + evaluation:view 端点）
INSERT INTO permission (permission_name, permission_key, permission_type, parent_id, sort, status)
SELECT '评价管理', 'evaluation:view', 1, 0, 13, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM (SELECT 1 FROM permission WHERE permission_key = 'evaluation:view') t);

-- 4) 评价回复/审核（BUTTON 子键，挂在 evaluation:view 下）
INSERT INTO permission (permission_name, permission_key, permission_type, parent_id, sort, status)
SELECT '评价回复', 'evaluation:reply', 2, p.id, 1, 1
FROM permission p
WHERE p.permission_key = 'evaluation:view'
  AND NOT EXISTS (SELECT 1 FROM (SELECT 1 FROM permission WHERE permission_key = 'evaluation:reply') t);

INSERT INTO permission (permission_name, permission_key, permission_type, parent_id, sort, status)
SELECT '评价审核', 'evaluation:audit', 2, p.id, 2, 1
FROM permission p
WHERE p.permission_key = 'evaluation:view'
  AND NOT EXISTS (SELECT 1 FROM (SELECT 1 FROM permission WHERE permission_key = 'evaluation:audit') t);

-- 5) 通知发送（门：notification/list.html 的发送 Tab/按钮 + 全部发送类端点）
INSERT INTO permission (permission_name, permission_key, permission_type, parent_id, sort, status)
SELECT '通知发送', 'notification:send', 1, 0, 14, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM (SELECT 1 FROM permission WHERE permission_key = 'notification:send') t);
