-- =============================================================
-- 报表字段池：新增「到账备注」列 + 内置模板4 默认备注列改取到账备注
-- 背景：原字段池只有一个「备注」= proj_project.remark（项目备注），dev 库 3743 个项目该列全为空；
--       客户实际需要的是到账信息里的备注（proj_payment.remark，1079 条有值，覆盖 1039 个项目）。
-- 口径：一个项目多笔到账时，备注内容多为复制粘贴的同一句 ⇒ 聚合时先去重再用「；」拼接，
--       避免出现「微信支付；微信支付」这类重复；无备注则留空。该列不可作为筛选条件。
-- 说明：脚本幂等，可重复执行。
-- =============================================================

-- 内置模板4「市场性任务到账收入确认表1已完成（管线图）」：
--   原默认「备注」列取值改为到账备注（field_key remark→paymentRemark、field_source subject→agg）。
--   ⚠️ 列名保持「备注」：设计器里两个字段分别显示「项目备注」「到账备注」以便区分来源，
--      但预览表头与导出 Excel 的列名一律只显示「备注」（客户要求）——
--      由 ProjReportServiceImpl#saveTemplate 的输出列名归一化与 ReportFieldPool#outputLabelOverride 保证。
UPDATE proj_report_field
   SET field_key    = 'paymentRemark',
       field_source = 'agg'
 WHERE template_id = 4
   AND field_key   = 'remark'
   AND del_flag    = '0';

-- 兜底修正：若已执行过本脚本（field_key 已变更），列名仍统一回「备注」
UPDATE proj_report_field
   SET field_label = '备注'
 WHERE template_id = 4
   AND field_key   = 'paymentRemark'
   AND del_flag    = '0';
