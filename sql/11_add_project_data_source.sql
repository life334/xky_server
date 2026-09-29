-- ============================================================
-- 项目来源标识 + 工作量单价来源重构
-- ============================================================

-- 1) 项目来源标识：import=历史数据导入 / manual=手动或粘贴新增
alter table proj_project add column if not exists data_source varchar(20) default 'manual';
comment on column proj_project.data_source is '项目来源（import=导入 manual=手动新增/粘贴）';
-- 3) 索引：按项目来源筛选 / 按项目重算时快速定位
create index if not exists idx_proj_project_data_source on proj_project(data_source);

-- 4) 重算时需要按 (project_id, billing_type, billing_category) 定位外部工作量行，
--    如数据量大可考虑加索引（可选）
create index if not exists idx_proj_workload_proj_billing
    on proj_workload(project_id, billing_type, billing_category)
    where del_flag = '0';
