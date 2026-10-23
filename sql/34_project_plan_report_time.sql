-- ============================================================
-- 项目：新增「拟上报时间」列（plan_report_time）
-- 背景：费用结算页「到账信息」编辑弹窗里由用户自行选择该项目的拟上报时间；
--       费用结算高级筛选、报表导出筛选、回款管理「到账统计」口径（按拟上报时间）都要用它。
--       该时间挂在【项目】上（一个项目一个值），不是挂在单笔到账上。
-- 日期：2026-10-23
-- 执行库：xky_project (PostgreSQL)
-- 幂等：可重复执行
-- ============================================================

ALTER TABLE proj_project ADD COLUMN IF NOT EXISTS plan_report_time date DEFAULT NULL;
COMMENT ON COLUMN proj_project.plan_report_time IS '拟上报时间（用户在到账信息编辑时选择，项目级；统计/筛选/报表按此归集）';
