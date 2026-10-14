-- 27_report_tpl5_related_code.sql
-- 模板5「验线上报产值统计表」的「备注(定线号)」列，取值来源改为「关联定线编号」。
--   原：field_key = 'remark'             → p.remark（项目自身备注；业务上不是"定线号"，且该项目备注常年为空）
--   新：field_key = 'relatedProjectCode' → coalesce(系统内关联定线项目编号, 手工输入的编号文本)
-- 仅改取值来源；列名「备注(定线号)」、列位置(column_index=18)、宽度均不变。
-- 与模板6「管线定线/系统编号」同源。

UPDATE proj_report_field
SET field_key = 'relatedProjectCode'
WHERE template_id = 5
  AND column_index = 18
  AND field_key = 'remark'
  AND del_flag = '0';
