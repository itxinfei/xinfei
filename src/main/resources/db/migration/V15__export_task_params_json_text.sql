-- V15：export_task.params_json 扩容为 text（O-25 后续收尾）
-- 背景：订单/菜品/员工/用户列表页的勾选导出改走任务通道后，ids 串（上限 1000 个 ID，
-- 约束与同步导出 MAX_EXPORT_IDS 一致）序列化进 params_json 可达 ~8KB，varchar(1000) 容不下；
-- 同步导出历史上把 ids 放 GET URL，实际容量同为 KB 级，此处对齐。
ALTER TABLE export_task MODIFY COLUMN params_json text NULL COMMENT '查询参数JSON';
