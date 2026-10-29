-- =============================================================================
-- 38  付款笔级「拟上报月份」（需求：拟上报时间下沉到每一笔付款）
--
-- 背景：原「拟上报时间」是项目级字段（proj_project.plan_report_time），一个项目只有一个值。
--      现业务要求：每一笔付款（预付款/尾款/手动新增的付款）各自有自己的拟上报时间，
--      报表导出里涉及「到账金额」的，只统计「拟上报月份落在所选月份区间」的那些笔。
--
-- 口径决定（2026-10-29 与用户确认）：
--   ① 拟上报时间只到「年月」，不存日 ⇒ 用 varchar(7) 存 'YYYY-MM'，语义即格式。
--   ② 默认值为该笔「到账时间（pay_time）」的年月 ⇒ 一次性回填存量数据，
--      并保证运行时不会长期存在空值（保存时兜底 + SQL  coalesce 兜底）。
--   ③ 筛选没填拟上报时间 ⇒ 按系统当前月统计（报表 ReportDataMapper 实现）。
--   ④ 同一项目允许有多笔付款 ⇒ 放宽唯一索引：只对 advance/final 保持「一类一行」约束，
--      手动新增的付款类型 'other' 允许同一项目多笔。
-- =============================================================================

ALTER TABLE proj_payment ADD COLUMN IF NOT EXISTS plan_report_month varchar(7);

COMMENT ON COLUMN proj_payment.plan_report_month IS
    '拟上报月份（YYYY-MM）。每笔付款各自一个值；为空时业务口径 = 该笔到账时间(pay_time)的年月。报表/到账统计按它过滤到账金额。';

-- 回填存量：有到账时间的笔，拟上报月份默认取到账时间的年月（幂等：只填空值）
UPDATE proj_payment
   SET plan_report_month = to_char(pay_time, 'YYYY-MM')
 WHERE del_flag = '0'
   AND (plan_report_month IS NULL OR btrim(plan_report_month) = '')
   AND pay_time IS NOT NULL;

-- 查询辅助索引（付表按月过滤/分组）
CREATE INDEX IF NOT EXISTS idx_proj_payment_plan_month ON proj_payment (plan_report_month);

-- ---------------------------------------------------------------------------
-- 放宽唯一索引：原 (project_id, payment_type) where payment_type <> 'refund'
--   ⇒ 导致同一项目同一类型只能有一行，无法「手动添加付款」。
--   改为只对 advance(预付款)/final(尾款) 保持唯一（upsertPayment 的 on conflict 依赖它），
--   other(手动新增的付款) 与 refund(退款) 不受约束，可多笔。
--   ⚠️ 必须与 ProjPaymentMapper.xml#upsertPayment 的 on conflict 谓词完全一致，否则 PostgreSQL 报
--      「no unique or exclusion constraint matching the ON CONFLICT specification」。
-- ---------------------------------------------------------------------------
DROP INDEX IF EXISTS idx_payment_unique;
CREATE UNIQUE INDEX idx_payment_unique
    ON proj_payment (project_id, payment_type)
 WHERE del_flag = '0' AND payment_type IN ('advance', 'final');
