-- ============================================================
-- 报表模板4/7「项目金额」列口径调整（2026-09-30）
-- ------------------------------------------------------------
-- 背景：原「项目金额」列 field_key = contractAmount，取 proj_contract.contract_amount，
--       需「项目已关联合同」且「合同金额非空」才有值。存量项目普遍未关联合同
--       （合同模块 2026-09-29 才上线），导致该列整列空白。
-- 调整：改取「项目结算金额」= 外部产值合计（proj_workload.external_output，
--       指令性任务的外部产值已在 SQL 子查询内剔除），不依赖合同关联。
--       列头文案保持客户模板原样「项目金额」不变（只改取值口径）。
-- 生效模板：模板4（市场性任务到账收入确认表1 已完成 管线图）
--           模板7（市场性任务到账收入确认表2 已完成 定验线控）
-- ============================================================

UPDATE proj_report_field
   SET field_key    = 'projectSettleAmount',
       field_source = 'agg',
       join_table   = NULL
 WHERE del_flag = '0'
   AND template_id IN (4, 7)
   AND column_index = 12
   AND field_key = 'contractAmount';
