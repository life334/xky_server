-- =============================================
-- 21. 合同状态改为 3 态 + 已取消；下线 审核日期/用户返回日期/完成日期
-- 前置：已执行 4_create_proj_tables.sql、10_init_builtin_report_templates.sql
-- 幂等：可重复执行
-- ---------------------------------------------
-- 背景：
--   ① 合同状态原为 6 值（草稿/已签署/执行中/已完成/已归档/已取消），业务上只需要
--      进行中 / 待返回 / 已完成 三态；「待返回」= 本单位已盖章并寄出、等待客户盖章返回。
--      系统无法感知「已寄出」，三态不做自动派生，由人工经「状态变更」入口维护。
--   ② 审核日期 / 用户返回日期 / 完成日期 三个字段停止录入（表单已移除）。
--      物理列保留（旧数据仍可追溯、便于回滚），仅停止读写；报表「完工时间」列同步下线。
-- =============================================

-- ───────────── ① 合同状态字典重刷为 4 值 ─────────────
DELETE FROM sys_dict_data WHERE dict_type = 'proj_contract_status';

INSERT INTO sys_dict_data
    (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
VALUES
    (1, '进行中', 'ongoing',        'proj_contract_status', '', 'primary', 'Y', '0', 'admin', now(), '合同已签署、执行中'),
    (2, '待返回', 'pending_return', 'proj_contract_status', '', 'warning', 'N', '0', 'admin', now(), '本单位已盖章并寄出，等待客户盖章后返回'),
    (3, '已完成', 'completed',      'proj_contract_status', '', 'success', 'N', '0', 'admin', now(), '双方均已盖章，签署完成'),
    (4, '已取消', 'cancelled',      'proj_contract_status', '', 'info',    'N', '0', 'admin', now(), '合同取消 / 作废');

-- ───────────── ② 历史状态码值映射 ─────────────
-- 空值 / 草稿 / 已签署 / 执行中 → 进行中
UPDATE proj_contract
   SET status = 'ongoing'
 WHERE status IS NULL
    OR status = ''
    OR status IN ('draft', 'signed', 'ongoing', '草稿', '已签署', '执行中');

-- 已完成 / 已归档 → 已完成
UPDATE proj_contract
   SET status = 'completed'
 WHERE status IN ('completed', 'archived', '已完成', '已归档');

-- 已取消 → 已取消
UPDATE proj_contract
   SET status = 'cancelled'
 WHERE status IN ('cancelled', '已取消');

-- 其它未知脏值 → 进行中（兜底，保证状态统计与胶囊口径一致）
UPDATE proj_contract
   SET status = 'ongoing'
 WHERE status NOT IN ('ongoing', 'pending_return', 'completed', 'cancelled');

-- 列默认值同步（新增合同未显式给状态时落「进行中」）
ALTER TABLE proj_contract ALTER COLUMN status SET DEFAULT 'ongoing';
COMMENT ON COLUMN proj_contract.status IS '合同状态：ongoing 进行中 / pending_return 待返回 / completed 已完成 / cancelled 已取消';

-- ───────────── ③ 报表：「完工时间」等合同日期列下线 ─────────────
-- 字段池（ReportFieldPool）已移除 auditDate / finishDate；此处同步下线所有模板对该三字段的引用。
-- 注：导出按 column_index 定位单元格，删除映射后该列留空（与模板中本就未映射的列一致），
--     其余列的 column_index 无需重排。
UPDATE proj_report_field
   SET del_flag = '2'
 WHERE del_flag = '0'
   AND field_key IN ('finishDate', 'auditDate', 'returnDate');

-- ───────────── ④ 字段权限：清理合同表三日期字段授权 ─────────────
DELETE FROM sys_role_field_auth
 WHERE table_name = 'proj_contract'
   AND field_key IN ('audit_date', 'return_date', 'finish_date');

-- ───────────── ⑤ 物理列标注为已停用（保留数据，不 DROP） ─────────────
COMMENT ON COLUMN proj_contract.audit_date  IS '[已停用] 审核日期（不再录入）';
COMMENT ON COLUMN proj_contract.return_date IS '[已停用] 用户返回日期（不再录入）';
COMMENT ON COLUMN proj_contract.finish_date IS '[已停用] 完成日期（不再录入）';
