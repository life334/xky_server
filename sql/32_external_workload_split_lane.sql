-- =============================================================
-- 外部工作量：重复计费类别由「同一栏 dup_no 递增」改为「新开一栏」
-- 背景：外部工作量原本允许同一栏（sub_item_no）内重复录入同一计费类别，
--       靠 dup_no = 2、3… 区分（见 sql/28_add_workload_dup_no.sql）。
--       历史导入数据是「一栏一个类别实例」（多栏 sub_item_no = 1、2、3…），
--       两种形态并存导致页面显示口径不一致，现统一为后者：
--       录入时检测到本栏已有该类别会自动新开一栏，dup_no 恒为 1。
-- 处理：把存量 dup_no > 1 的外部行，按 dup_no 顺序依次挪到该项目新的栏号上
--       （sub_item_no = 该项目外部最大栏号 + 1，逐条重算，不会撞唯一索引）。
-- 执行库：xky_project (PostgreSQL)
-- 幂等：可重复执行（处理完 dup_no 全为 1，再次执行没有符合条件的行）
--
-- ⚠️ 唯一索引 uk_workload_billing 包含 (project_id, user_id, category_id,
--    billing_type, billing_category, sub_item_no, dup_no)，这里只改 sub_item_no / dup_no，
--    挪到新栏号后不会与其它行冲突。
--    开发环境实测只有 1 条：proj_workload.id = 23873（项目 2026管图477）。
-- =============================================================

do $$
declare
    r          record;
    v_max_no   integer;
begin
    for r in
        select id, project_id
        from proj_workload
        where coalesce(del_flag, '0') = '0'
          and coalesce(dup_no, 1) > 1
        order by project_id, dup_no, id
    loop
        select coalesce(max(sub_item_no), 0)
        into v_max_no
        from proj_workload
        where project_id = r.project_id
          and billing_type = 'external'
          and coalesce(del_flag, '0') = '0';

        update proj_workload
        set sub_item_no = v_max_no + 1,
            dup_no = 1
        where id = r.id;
    end loop;
end $$;

-- 验收（开发环境预期：0 条）
-- select count(*) as 剩余重复行 from proj_workload
--  where coalesce(del_flag,'0') = '0' and coalesce(dup_no,1) > 1;
