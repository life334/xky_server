-- =============================================================
-- 通知中心（待办/消息）改进：认领机制 + 超期口径修正 + 历史 due_time 回填
-- 执行库：xky_project (PostgreSQL)
-- 幂等：可重复执行（ADD COLUMN IF NOT EXISTS / IF NOT EXISTS / 先删后插）
-- =============================================================

-- ---------------- 1. 认领机制（角色群发：谁先认领归谁，其余人自动关闭） ----------------
ALTER TABLE proj_notify ADD COLUMN IF NOT EXISTS claimed_by  bigint;
ALTER TABLE proj_notify ADD COLUMN IF NOT EXISTS claim_time timestamp;

COMMENT ON COLUMN proj_notify.claimed_by  IS '认领人用户 id（角色群发待办时，谁先认领归谁）';
COMMENT ON COLUMN proj_notify.claim_time  IS '认领时间';

CREATE INDEX IF NOT EXISTS idx_proj_notify_claim
    ON proj_notify (biz_type, biz_id, claimed_by) WHERE del_flag = '0';

-- ---------------- 2. 历史 due_time 回填 ----------------
-- 旧行从未写入 due_time（此前超期判定用 create_time + N 天在 SQL 里现算）。
-- 现在改为「以 due_time 为准」，必须回填，否则历史待办会被判定为 1970 年到期而全部升级 high。
-- 回填口径 = 创建时间 + 当前配置的超期天数（默认 3 天），与旧判定等价。
UPDATE proj_notify
   SET due_time = create_time + (COALESCE(
           (SELECT CAST(config_value AS int) FROM sys_config
             WHERE config_key = 'notify.todo.due.days' AND config_value ~ '^[0-9]+$'),
           3) * interval '1 day')
 WHERE del_flag = '0' AND notify_type = 'todo' AND due_time IS NULL;

-- ---------------- 3. sys_config 新增（可重复执行：先删后插） ----------------
-- ⚠️ 【已废弃】待办认领能力已整体下线（前端无入口、后端 claim 链路已删），
--    本节不再插入 `notify.todo.claim.enabled`，避免本脚本重跑时把已删除的开关复活。
--    清理动作见 sql/30_remove_todo_claim.sql。
--
-- 历史说明：待办完结一律由业务事实驱动（登记资料 / 录入结算 / 签署合同 ⇒ sweep 自动 done），
--          点击进入看一眼不产生任何状态变更，同角色的其他接收人不受影响。
--
-- DELETE FROM sys_config WHERE config_key IN ('notify.todo.claim.enabled');
-- INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
-- VALUES ('待办认领开关', 'notify.todo.claim.enabled', 'false', 'Y', 'admin', now(), '【已废弃】认领能力已下线');

-- ---------------- 4. 验收查询（执行后人工核对） ----------------
-- ① 确认认领列已加且历史 due_time 无遗漏
-- SELECT count(*) AS todo_without_due FROM proj_notify
--  WHERE del_flag='0' AND notify_type='todo' AND due_time IS NULL;   -- 期望 0
--
-- ② 当前各状态的待办分布
-- SELECT status, priority, count(*) FROM proj_notify
--  WHERE del_flag='0' AND notify_type='todo' GROUP BY status, priority ORDER BY 1,2;
