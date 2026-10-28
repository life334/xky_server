-- ============================================================
-- 35、预警规则收敛：只保留「工期超期」
-- ============================================================

-- ---------------- 1. 规则级开关 ----------------
DELETE FROM sys_config WHERE config_key IN (
  'notify.alert.debt.enabled',
  'notify.alert.overdue.enabled',
  'notify.alert.contract_missing.enabled'
);

INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark) VALUES
('预警-欠款逾期扫描开关', 'notify.alert.debt.enabled', 'false', 'Y', 'admin', now(),
 'false=不扫描欠款逾期预警。该规则一次命中上千个项目，会把铃铛堆满，实际无人处理'),
('预警-工期超期扫描开关', 'notify.alert.overdue.enabled', 'true', 'Y', 'admin', now(),
 'true=每日扫描未办结且已过应完成日的项目（当前唯一启用的预警规则）'),
('预警-未关联合同扫描开关', 'notify.alert.contract_missing.enabled', 'false', 'Y', 'admin', now(),
 'false=不扫描未关联合同预警。合同台账由档案管理员在合同模块自行跟进');
