-- ============================================================
-- 项目来源标识 + 工作量单价来源重构
-- 背景：
--   费用结算中，外部工作量单价取值优先级应为：
--     ① 用户手改价(price_source='manual')
--     ② 合同价（项目关联合同且合同已编辑该计费方式 billingId 的单价）
--     ③ 兜底价：
--          - 导入项目(data_source='import')  → 导入推导价(price_source='imported')
--          - 手动项目(data_source='manual')  → 字典价(price_source='dict')
--   重算触发时机：仅在「合同关联发生变化」(0→A / A→B / A→0) 或「合同单价被改动」时触发。
-- ============================================================

-- 1) 项目来源标识：import=历史数据导入 / manual=手动或粘贴新增
alter table proj_project add column if not exists data_source varchar(20) default 'manual';
comment on column proj_project.data_source is '项目来源（import=导入 manual=手动新增/粘贴）';

-- 存量项目来源按“历史导入项目”特征回填（如需）：
--   导入项目在 proj_workload 中存在 price_source 相关特征，
--   本次约定：存量数据不做处理（用户将手动删除后重新导入）。
--   故此处仅保留字段默认值 'manual'，不执行回填。

-- 2) 存量工作量 price_source 取值说明（不修改数据）
--    contract : 合同价
--    dict     : 字典默认价
--    manual   : 用户手动覆盖价
--    imported : 导入时由合计金额推导出的单价（本次新增，仅导入项目使用）

-- 3) 索引：按项目来源筛选 / 按项目重算时快速定位
create index if not exists idx_proj_project_data_source on proj_project(data_source);

-- 4) 重算时需要按 (project_id, billing_type, billing_category) 定位外部工作量行，
--    如数据量大可考虑加索引（可选）
create index if not exists idx_proj_workload_proj_billing
    on proj_workload(project_id, billing_type, billing_category)
    where del_flag = '0';
