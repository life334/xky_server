-- -----------------------------------------------------------------------------
-- 委托单位性质（client_unit_nature）
-- 用途：项目编辑弹窗录入 / 项目列表展示与筛选 / 统计口径（民营、国有等）
-- 说明：与 proj_project.project_nature（项目性质：常规 / 指令性任务）无关，两者独立。
--       dict_value 为英文码值，中文只在显示层映射（与 proj_project_nature 同一约定）。
-- 幂等：全部语句均带 if not exists / where not exists，可重复执行。
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- 步骤 1：预览（执行前确认，只读）
-- -----------------------------------------------------------------------------
select column_name, data_type, column_default
  from information_schema.columns
 where table_name = 'proj_project' and column_name = 'client_unit_nature';

select * from sys_dict_type where dict_type = 'proj_client_unit_nature';

-- -----------------------------------------------------------------------------
-- 步骤 2：proj_project.client_unit_nature
-- -----------------------------------------------------------------------------
begin;

alter table proj_project
    add column if not exists client_unit_nature varchar(32);

comment on column proj_project.client_unit_nature is
    '委托单位性质：government=政府机关 / institution=事业单位 / state_owned=国有企业 / private=民营企业 / collective=集体企业 / foreign=外资（含合资） / other=其他；为空表示未维护';

-- 部分索引：支撑「按委托单位性质」筛选
create index if not exists idx_proj_project_client_unit_nature
    on proj_project (client_unit_nature)
    where del_flag = '0';

commit;

-- -----------------------------------------------------------------------------
-- 步骤 3：「委托单位性质」字典
-- -----------------------------------------------------------------------------
begin;

insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
select '委托单位性质', 'proj_client_unit_nature', '0', 'admin', now(), '项目列表-委托单位性质'
 where not exists (select 1 from sys_dict_type where dict_type = 'proj_client_unit_nature');

-- 注意：list_class 对应前端 <dict-tag> 的 elTagType（'' 走默认主题色）
insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 1, '政府机关', 'government', 'proj_client_unit_nature', '', 'primary', 'N', '0', 'admin', now(), ''
 where not exists (select 1 from sys_dict_data where dict_type = 'proj_client_unit_nature' and dict_value = 'government');

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 2, '事业单位', 'institution', 'proj_client_unit_nature', '', 'success', 'N', '0', 'admin', now(), ''
 where not exists (select 1 from sys_dict_data where dict_type = 'proj_client_unit_nature' and dict_value = 'institution');

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 3, '国有企业', 'state_owned', 'proj_client_unit_nature', '', 'warning', 'N', '0', 'admin', now(), ''
 where not exists (select 1 from sys_dict_data where dict_type = 'proj_client_unit_nature' and dict_value = 'state_owned');

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 4, '民营企业', 'private', 'proj_client_unit_nature', '', 'info', 'N', '0', 'admin', now(), ''
 where not exists (select 1 from sys_dict_data where dict_type = 'proj_client_unit_nature' and dict_value = 'private');

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 5, '集体企业', 'collective', 'proj_client_unit_nature', '', '', 'N', '0', 'admin', now(), ''
 where not exists (select 1 from sys_dict_data where dict_type = 'proj_client_unit_nature' and dict_value = 'collective');

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 6, '外资（含合资）', 'foreign', 'proj_client_unit_nature', '', '', 'N', '0', 'admin', now(), ''
 where not exists (select 1 from sys_dict_data where dict_type = 'proj_client_unit_nature' and dict_value = 'foreign');

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 7, '其他', 'other', 'proj_client_unit_nature', '', 'info', 'N', '0', 'admin', now(), ''
 where not exists (select 1 from sys_dict_data where dict_type = 'proj_client_unit_nature' and dict_value = 'other');

commit;

-- -----------------------------------------------------------------------------
-- 步骤 4：存量数据回填（可选，默认不执行）
-- 存量项目该字段为空。如确认按「委托单位名关键词」批量回填，按需打开下面语句，
-- 逐条确认关键词后执行；执行完建议用同口径 select 复核命中条数。
-- -----------------------------------------------------------------------------
-- begin;
-- update proj_project set client_unit_nature = 'government'
--  where del_flag = '0' and client_unit_nature is null
--    and (client_unit like '%自然资源局%' or client_unit like '%规划局%' or client_unit like '%住建局%');
--
-- update proj_project set client_unit_nature = 'institution'
--  where del_flag = '0' and client_unit_nature is null
--    and client_unit like '%勘察测绘院%';
--
-- update proj_project set client_unit_nature = 'state_owned'
--  where del_flag = '0' and client_unit_nature is null
--    and (client_unit like '%有限公司%' or client_unit like '%集团有限公司%');
--
-- -- 复核：按性质分组统计
-- select coalesce(client_unit_nature, '(未填)') as nature, count(*)
--   from proj_project where del_flag = '0' group by 1 order by 2 desc;
-- commit;
