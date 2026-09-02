-- ============================================================
-- 7、项目模块动态字段定义初始化
-- 说明：合同数据导入会将「项目类型」「测绘地址」写入 proj_contract.extra_data JSONB，
--       此处注册对应动态字段，供合同列表「显隐列」面板与详情展示。
-- ============================================================

-- 合同表：项目类型
INSERT INTO proj_field_def (table_name, field_key, field_label, field_type, field_options, is_required, sort_order, status, del_flag, create_by, create_time, remark)
SELECT 'proj_contract', 'projectType', '项目类型', 'text', NULL, '0', 1, '0', '0', 'admin', now(), '合同数据导入'
WHERE NOT EXISTS (
    SELECT 1 FROM proj_field_def
    WHERE table_name = 'proj_contract' AND field_key = 'projectType' AND del_flag = '0'
);

-- 合同表：测绘地址
INSERT INTO proj_field_def (table_name, field_key, field_label, field_type, field_options, is_required, sort_order, status, del_flag, create_by, create_time, remark)
SELECT 'proj_contract', 'surveyAddress', '测绘地址', 'text', NULL, '0', 2, '0', '0', 'admin', now(), '合同数据导入'
WHERE NOT EXISTS (
    SELECT 1 FROM proj_field_def
    WHERE table_name = 'proj_contract' AND field_key = 'surveyAddress' AND del_flag = '0'
);
