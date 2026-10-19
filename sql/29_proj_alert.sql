-- ============================================================
-- 29、项目预警通知（走铃铛，每日扫描一次）
--
-- 设计要点：
--   1. 复用 proj_notify，notify_type='alert'；biz_type 区分三类规则；biz_id = 项目 id
--   2. 幂等：复用 uk_proj_notify_biz (biz_type, biz_id, receiver_id) where del_flag='0'
--      ⇒ 同一项目同一规则对同一接收人只存在一行，每日重复扫描不会重复推送
--   3. 自动消解：今日不再命中的项目，其未处理预警置 done（风险已解除）
--   4. 已忽略(ignored)的行不会被复活 —— 用户明确忽略过就尊重其意愿
--   5. 接收人：默认按角色群发；配置了具体人时，具体人覆盖角色
-- ============================================================

-- ---------------- 1. 索引：支撑"按规则+项目"反查现存预警（自动消解用） ----------------
CREATE INDEX IF NOT EXISTS idx_proj_notify_alert
    ON proj_notify (biz_type, biz_id) WHERE del_flag = '0' AND notify_type = 'alert';

-- ---------------- 2. sys_config 参数（可重复执行：先删后插） ----------------
DELETE FROM sys_config WHERE config_key IN (
  'notify.alert.enabled',
  'notify.alert.debt.roles',      'notify.alert.debt.users',      'notify.alert.debt.min_amount', 'notify.alert.debt.min_days',
  'notify.alert.overdue.roles',   'notify.alert.overdue.users',
  'notify.alert.contract_missing.roles', 'notify.alert.contract_missing.users', 'notify.alert.contract_missing.workdays'
);

INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark) VALUES
-- 总开关
('预警总开关', 'notify.alert.enabled', 'true', 'Y', 'admin', now(),
 '关闭后不再扫描与推送任何预警（已产生的历史预警保留，可正常查看与已读）'),

-- 规则一：欠款逾期
('欠款预警-接收角色', 'notify.alert.debt.roles', 'role-admin,role-product', 'Y', 'admin', now(),
 'role_key 列表，逗号分隔；配置了 notify.alert.debt.users 时该项被覆盖'),
('欠款预警-接收人', 'notify.alert.debt.users', '', 'Y', 'admin', now(),
 'sys_user.user_id 列表，逗号分隔。留空=按接收角色群发；填写后仅发给这些人'),
('欠款预警-金额下限(元)', 'notify.alert.debt.min_amount', '10000', 'Y', 'admin', now(),
 '外产值 − 已到账 ≥ 该金额才预警'),
('欠款预警-办结天数下限', 'notify.alert.debt.min_days', '90', 'Y', 'admin', now(),
 '办结后经过的自然日 ≥ 该天数才预警'),

-- 规则二：工期超期
('工期超期预警-接收角色', 'notify.alert.overdue.roles', 'role-admin,role-product', 'Y', 'admin', now(),
 'role_key 列表，逗号分隔'),
('工期超期预警-接收人', 'notify.alert.overdue.users', '', 'Y', 'admin', now(),
 'sys_user.user_id 列表，逗号分隔。留空=按角色群发'),

-- 规则三：未关联合同
('未关联合同预警-接收角色', 'notify.alert.contract_missing.roles', 'role-admin,role-doc', 'Y', 'admin', now(),
 'role_key 列表，逗号分隔'),
('未关联合同预警-接收人', 'notify.alert.contract_missing.users', '', 'Y', 'admin', now(),
 'sys_user.user_id 列表，逗号分隔。留空=按角色群发'),
('未关联合同预警-工作日阈值', 'notify.alert.contract_missing.workdays', '3', 'Y', 'admin', now(),
 '项目录入后经过 N 个工作日仍未关联合同则预警（按工作日历扣除节假日）');

-- ---------------- 3. 验收查询（执行后人工核对） ----------------
-- ① 确认配置已写入
-- SELECT config_key, config_value FROM sys_config WHERE config_key LIKE 'notify.alert.%' ORDER BY 1;
--
-- ② 确认预警已产生（首次需等到调度触发，或重启后手动调一次）
-- SELECT biz_type, status, count(*) FROM proj_notify
--  WHERE del_flag='0' AND notify_type='alert' GROUP BY biz_type, status ORDER BY 1,2;
--
-- ③ 清理某类预警的全部历史（如需重来）
-- UPDATE proj_notify SET del_flag='2' WHERE notify_type='alert' AND biz_type='alert_debt';
