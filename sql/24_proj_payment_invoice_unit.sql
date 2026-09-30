-- ============================================================
-- 到账信息：新增「开票单位」列（invoice_unit），停用「发票号码」(invoice_no)
-- 背景：费用结算页「到账信息」编辑弹窗的「发票号码」改为「开票单位」（下拉选择委托单位）。
--       invoice_no 列物理保留（不动历史数据），代码不再写入/读取。
-- 日期：2026-09-30
-- ============================================================

ALTER TABLE proj_payment ADD COLUMN IF NOT EXISTS invoice_unit varchar(200) DEFAULT '';
COMMENT ON COLUMN proj_payment.invoice_unit IS '开票单位（下拉选择委托单位）';
