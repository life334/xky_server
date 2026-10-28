-- =============================================================
-- 管线长度统计 · 类别范围配置（驾驶舱管线长度卡片）
-- 需求：统计「类别名含管线」的项目管线总长度（定线除外）
-- 口径：长度 = proj_workload.workload，限 price_unit='千米' 且 billing_type='internal'
--       归属时间 = 所属项目 close_time（办结日期）
-- 说明：两项配置均可在「系统管理 → 参数设置」里改，改完点【刷新缓存】生效；
--       未配置时后端自动回落到下面的默认值，不影响功能。脚本幂等，可重复执行。
-- =============================================================

-- 纳入口径：项目类别名命中任一关键字（模糊匹配），逗号分隔
INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '管线长度统计-纳入类别关键字', 'proj.pipelineLength.categoryKeys', '管线', 'N', 'admin', now(),
       '驾驶舱管线长度卡片：项目类别名命中任一关键字即纳入统计，逗号分隔；改后需刷新参数缓存'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'proj.pipelineLength.categoryKeys');

-- 排除口径：项目类别名精确命中即不计入，逗号分隔
INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '管线长度统计-排除类别', 'proj.pipelineLength.categoryExcludes', '管线定线', 'N', 'admin', now(),
       '驾驶舱管线长度卡片：类别名全称精确命中则不计入统计，逗号分隔；改后需刷新参数缓存'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'proj.pipelineLength.categoryExcludes');
