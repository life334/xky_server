-- =============================================================
-- 合同新增通知：sys_config 参数（复用 proj_notify 表，无需新增表）
-- 执行库：xky_project (PostgreSQL)
-- 幂等：可重复执行（先删后插）
-- =============================================================

DELETE FROM sys_config WHERE config_key IN (
  'notify.receivers.contract_created'
);

INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark) VALUES
('合同新增接收角色', 'notify.receivers.contract_created', 'role-doc', 'Y', 'admin', now(), '逗号分隔 role_key；默认档案管理员 role-doc。非档案管理员新增合同时推送「合同待签署」待办');
