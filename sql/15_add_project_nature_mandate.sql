-- -----------------------------------------------------------------------------
-- 步骤 1：预览（执行前确认）
-- -----------------------------------------------------------------------------
select column_name, data_type, column_default
  from information_schema.columns
 where table_name = 'proj_project' and column_name = 'project_nature';

select to_regclass('public.proj_mandate_rule') as mandate_rule_table;
select * from sys_dict_type where dict_type = 'proj_project_nature';

-- -----------------------------------------------------------------------------
-- 步骤 2：proj_project.project_nature
-- -----------------------------------------------------------------------------
begin;

alter table proj_project
    add column if not exists project_nature varchar(16) not null default 'normal';

comment on column proj_project.project_nature is
    '项目性质：normal=常规项目（外部产值计入应收）；mandate=指令性任务（外部产值不计入应收，仅内部产值计入）';

-- 部分索引：指令性项目数量极少（当前 5 条），只为「筛出指令性」的查询走索引
create index if not exists idx_proj_project_nature_mandate
    on proj_project (project_nature)
    where del_flag = '0' and project_nature = 'mandate';

commit;

-- -----------------------------------------------------------------------------
-- 步骤 3：指令性任务规则表（按「委托单位关键词」包含匹配自动打标）
-- -----------------------------------------------------------------------------
begin;

create table if not exists proj_mandate_rule (
    id          bigserial    primary key,
    keyword     varchar(120) not null,
    enabled     char(1)      not null default '0',
    remark      varchar(255)          default '',
    del_flag    char(1)      not null default '0',
    create_by   varchar(64)           default '',
    create_time timestamp             default now(),
    update_by   varchar(64)           default '',
    update_time timestamp
);

comment on table  proj_mandate_rule          is '指令性任务规则（委托单位关键词，包含匹配）';
comment on column proj_mandate_rule.keyword  is '委托单位关键词（包含匹配 proj_project.client_unit）';
comment on column proj_mandate_rule.enabled  is '是否启用：0=启用 1=停用（与若依 sys_dict 约定一致）';

create unique index if not exists uk_proj_mandate_rule_keyword
    on proj_mandate_rule (keyword) where del_flag = '0';

-- 内置初始规则：西安市勘察测绘院（一词覆盖全部 3 种名称变体）
insert into proj_mandate_rule (keyword, enabled, remark, del_flag, create_by, create_time)
select '西安市勘察测绘院', '0', '客户指定：指令性任务委托单位', '0', 'admin', now()
 where not exists (select 1 from proj_mandate_rule where keyword = '西安市勘察测绘院' and del_flag = '0');

commit;

-- -----------------------------------------------------------------------------
-- 步骤 4：「项目性质」字典（中文只在显示层映射，dict_value 就是码值）
-- -----------------------------------------------------------------------------
begin;

insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
select '项目性质', 'proj_project_nature', '0', 'admin', now(), '项目列表-项目性质（指令性任务）'
 where not exists (select 1 from sys_dict_type where dict_type = 'proj_project_nature');

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 1, '常规', 'normal', 'proj_project_nature', '', 'info', 'Y', '0', 'admin', now(), '外部产值计入应收'
 where not exists (select 1 from sys_dict_data where dict_type = 'proj_project_nature' and dict_value = 'normal');

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 2, '指令性任务', 'mandate', 'proj_project_nature', '', 'warning', 'N', '0', 'admin', now(), '外部产值不计入应收，内部产值照常'
 where not exists (select 1 from sys_dict_data where dict_type = 'proj_project_nature' and dict_value = 'mandate');

commit;

-- -----------------------------------------------------------------------------
-- 步骤 5：自动匹配开关（sys_config，RuoYi 原生；新建/导入项目时是否按规则自动打标）
-- -----------------------------------------------------------------------------
begin;

insert into sys_config (config_id, config_name, config_key, config_value, config_type, create_by, create_time, remark)
select (select coalesce(max(config_id), 0) + 1 from sys_config),
       '指令性任务自动打标', 'proj.mandate.autoMatch', 'true', 'Y', 'admin', now(),
       '新建 / 导入项目时按 proj_mandate_rule 关键词自动判定项目性质'
 where not exists (select 1 from sys_config where config_key = 'proj.mandate.autoMatch');

select setval('sys_config_config_id_seq', (select max(config_id) from sys_config), true);

commit;
