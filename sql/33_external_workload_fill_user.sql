-- =============================================================
-- 外部工作量负责人回填（user_id = 0 → 该项目唯一负责人）
-- 背景：2026-10 起「工作量明细」弹窗新增外部工作量时，前端把 user_id 写死为 0
--       （quickAddWorkload 里 `userId: isExternal ? 0 : rec.userId`），
--       而历史导入数据一直是 user_id = 该行负责人（ProjImportServiceImpl.buildSubItemWorkloads）。
--       结果：新录入的外部工作量在页面上"没有负责人"，与历史数据形态不一致。
--       现改造为「外部工作量也按负责人录入」，新增行会正确带上 user_id；
--       本脚本只修复改造前产生的存量行。
--
-- 处理范围：只回填「项目只有一个有效负责人」的外部行 —— 归属可唯一确定，不会猜错。
--         项目有 2 个及以上负责人的，保持 user_id = 0 不动，改为在页面
--         「未指定负责人」卡片里手工指定（弹窗已提供逐行负责人下拉）。
--
-- 执行库：xky_project (PostgreSQL)
-- 幂等：可重复执行（条件只匹配 user_id 为空 / 0 的行，回填后不再命中）
--
-- ⚠️ 唯一索引 uk_workload_billing 含 (project_id, user_id, category_id,
--    billing_type, billing_category, sub_item_no, dup_no)：回填后 user_id 变成真人，
--    可能与该项目该负责人已有的同类别行撞索引。本脚本用 NOT EXISTS 跳过这些行，
--    不会整条语句失败（开发环境实测冲突 0 条）。
-- =============================================================

begin;

with candidate as (
    select w.id,
           w.project_id,
           w.category_id,
           w.billing_category,
           w.sub_item_no,
           w.dup_no,
           (select min(pl.user_id)
              from proj_leader pl
             where pl.project_id = w.project_id
               and coalesce(pl.del_flag, '0') = '0') as only_leader
      from proj_workload w
     where coalesce(w.del_flag, '0') = '0'
       and w.billing_type = 'external'
       and coalesce(w.user_id, 0) = 0
       and (select count(*)
              from proj_leader pl
             where pl.project_id = w.project_id
               and coalesce(pl.del_flag, '0') = '0') = 1
)
update proj_workload w
   set user_id     = c.only_leader,
       update_by   = 'external-user-fix',
       update_time = now()
  from candidate c
 where w.id = c.id
   and c.only_leader is not null
   and not exists (
        select 1
          from proj_workload w2
         where coalesce(w2.del_flag, '0') = '0'
           and w2.project_id = c.project_id
           and w2.user_id = c.only_leader
           and w2.billing_type = 'external'
           and w2.category_id         is not distinct from c.category_id
           and w2.billing_category    is not distinct from c.billing_category
           and w2.sub_item_no         is not distinct from c.sub_item_no
           and w2.dup_no              is not distinct from c.dup_no
   );

commit;

-- 验收 1：剩余未回填的外部行（开发环境预期 2 条：2025管定018、2026管定077 两个多负责人项目）
-- select w.project_id, p.project_code, count(*)
--   from proj_workload w join proj_project p on p.id = w.project_id
--  where coalesce(w.del_flag,'0') = '0'
--    and w.billing_type = 'external'
--    and coalesce(w.user_id, 0) = 0
--  group by 1, 2;
--
-- 验收 2：外部行负责人是否都是该项目负责人（预期 not_leader = 0）
-- select case when exists (select 1 from proj_leader pl
--                           where pl.project_id = w.project_id and pl.user_id = w.user_id
--                             and coalesce(pl.del_flag,'0') = '0')
--             then 'is_leader' else 'not_leader' end as kind, count(*)
--   from proj_workload w
--  where coalesce(w.del_flag,'0') = '0' and w.billing_type = 'external'
--    and coalesce(w.user_id, 0) <> 0
--  group by 1;
