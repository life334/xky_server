-- =============================================================================
-- 14_add_project_source_dict.sql
-- 目的：新增「项目来源」字典 proj_project_source，供项目列表「项目来源」列（proj_project.data_source）使用。
--
-- 背景：该列在后端列元数据（ProjProjectServiceImpl#getListColumns）里原注册为 text，
--       直接输出原始值 → 界面显示英文 import / manual。
--       现改为 dict，字典项如下；同时前端已做「字典缺失时的中文兜底」。
--
-- 取值来源（代码写入点）：
--   · ProjProjectServiceImpl  ：manual（界面新建/编辑）
--   · ProjImportServiceImpl   ：import（Excel 导入）
--
-- 幂等：可重复执行。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 步骤 1：预览（确认是否已存在）
-- -----------------------------------------------------------------------------
select * from sys_dict_type where dict_type = 'proj_project_source';
select * from sys_dict_data where dict_type = 'proj_project_source' order by dict_sort;

-- -----------------------------------------------------------------------------
-- 步骤 2：写入（字典类型 + 字典项，已存在则跳过）
-- -----------------------------------------------------------------------------
begin;

insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
select '项目来源', 'proj_project_source', '0', 'admin', now(), '项目列表-项目来源'
 where not exists (select 1 from sys_dict_type where dict_type = 'proj_project_source');

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 1, '手动录入', 'manual', 'proj_project_source', '', 'info', 'N', '0', 'admin', now(), '界面新建/编辑'
 where not exists (select 1 from sys_dict_data where dict_type = 'proj_project_source' and dict_value = 'manual');

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 2, 'Excel 导入', 'import', 'proj_project_source', '', 'primary', 'N', '0', 'admin', now(), 'Excel 批量导入'
 where not exists (select 1 from sys_dict_data where dict_type = 'proj_project_source' and dict_value = 'import');

commit;

-- -----------------------------------------------------------------------------
-- 步骤 3：核对（期望 1 条 dict_type + 2 条 dict_data）
-- -----------------------------------------------------------------------------
select dict_type, dict_name, status from sys_dict_type where dict_type = 'proj_project_source';
select dict_sort, dict_label, dict_value, list_class from sys_dict_data
 where dict_type = 'proj_project_source' order by dict_sort;
