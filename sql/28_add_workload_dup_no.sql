-- ============================================================
-- 28、工作量表 proj_workload 新增 dup_no 列
-- ------------------------------------------------------------
-- 背景：外部工作量需支持「同一栏（sub_item_no）内同一计费类别多行」。
--       原唯一索引 uk_workload_billing 不含区分列 ⇒
--         直接 INSERT 两行同键会 UniqueViolation；走 upsert(on conflict) 会被覆盖合并。
--       故新增 dup_no（默认 1，标识同一栏内同计费类别的行内序号）并纳入唯一索引。
--       存量数据 dup_no 全部为 1 ⇒ 与改造前行为逐字节等价。
-- 幂等：可重复执行。
-- ============================================================

ALTER TABLE proj_workload ADD COLUMN IF NOT EXISTS dup_no INTEGER NOT NULL DEFAULT 1;

COMMENT ON COLUMN proj_workload.dup_no IS '同一子项内重复计费类别的行内序号（默认1；同栏同类别重复录入时依次 2,3…）';

DROP INDEX IF EXISTS uk_workload_billing;

CREATE UNIQUE INDEX IF NOT EXISTS uk_workload_billing
    ON proj_workload (project_id, user_id, category_id, billing_type, billing_category, sub_item_no, dup_no)
    WHERE del_flag = '0';
