-- ============================================================================
-- 41_backfill_project_category.sql
-- 目的：修复「项目类别文本有值、外键 project_category_id 为空」的历史数据，
--       并修正「文本旧名 / 外键新名」的错配。
-- 背景：粘贴导入把类别名当纯文本写入 proj_project.engineering_project；
--       后端早期逻辑按名反查失败时只写文本、外键留空且不报错。
--       受影响：结算/产值统计的「按类别筛选」查不到这批项目、导出类别为空。
-- 幂等：只更新 project_category_id IS NULL 的行，可重复执行。
-- 执行前：务必先备份（步骤 0）。
-- ============================================================================

-- ============ 步骤 0：备份（在服务器 shell 执行，不在本文件里） ============
-- pg_dump -Fc -h <host> -p <port> -U postgres -d xky_project \
--         -f /backup/xky_project_before_cat_backfill_$(date +%Y%m%d).dump


-- ============ 步骤 1：规模盘点（只读） ============
-- 1.1 外键为空的合计
select count(*) as "外键为空项目数"
from proj_project
where del_flag = '0' and project_category_id is null;

-- 1.2 按状态分布（看是集中在已办结的历史数据，还是散落在进行中）
select status, count(*) as cnt
from proj_project
where del_flag = '0' and project_category_id is null
group by status
order by cnt desc;

-- 1.3 脏引用：id 指向不存在的类别
select count(*) as "脏引用项目数"
from proj_project p
left join proj_category c on c.id = p.project_category_id
where p.del_flag = '0' and p.project_category_id is not null and c.id is null;


-- ============ 步骤 2：可自动回填 vs 需人工（只读） ============
-- 注意：类别名的唯一性只按 parent 判定（同父下唯一），不同父类别下可能存在同名子类别。
--       同名多个时无法确定该挂哪个 ⇒ 归入「需人工」，不自动回填。

with leaf as (
    select btrim(name) as nm, count(*) as cnt
    from proj_category
    where del_flag = '0' and level = 2
    group by btrim(name)
),
p as (
    select id, btrim(engineering_project) as nm
    from proj_project
    where del_flag = '0'
      and project_category_id is null
      and engineering_project is not null
      and btrim(engineering_project) <> ''
)
select
    (select count(*) from p where nm in (select nm from leaf where cnt = 1))          as "可自动回填",
    (select count(*) from p where nm in (select nm from leaf where cnt > 1))          as "同名多类别_需人工",
    (select count(*) from p where nm not in (select nm from leaf))                    as "无此类别_需人工",
    (select count(*) from p)                                                          as "空外键且有文本合计";

-- 2.2 需人工清单（无匹配 + 同名多义）—— 交给业务：是新建类别、改名，还是录错了
with leaf as (
    select btrim(name) as nm, count(*) as cnt
    from proj_category
    where del_flag = '0' and level = 2
    group by btrim(name)
)
select p.project_code, p.engineering_project as "类别文本", p.status, p.create_time,
       case when l.nm is null then '类别不存在' else '同名多类别' end as "原因"
from proj_project p
left join leaf l on l.nm = btrim(p.engineering_project)
where p.del_flag = '0'
  and p.project_category_id is null
  and coalesce(btrim(p.engineering_project), '') <> ''
  and (l.nm is null or l.cnt > 1)
order by p.create_time desc;

-- 2.3 完全没有类别文本、也没有外键的项目（无法自动补，只能人工归类或挂「其它」）
select project_code, project_name, status, create_time
from proj_project
where del_flag = '0'
  and project_category_id is null
  and coalesce(btrim(engineering_project), '') = ''
order by create_time desc
limit 200;


-- ============ 步骤 3：回填（唯一的写操作，务必在步骤 0 备份之后执行） ============
begin;

with leaf as (
    select btrim(name) as nm, count(*) as cnt, min(id) as category_id
    from proj_category
    where del_flag = '0' and level = 2
    group by btrim(name)
)
update proj_project p
set project_category_id = l.category_id,
    update_time         = now()
from leaf l
where l.cnt = 1
  and p.del_flag = '0'
  and p.project_category_id is null
  and btrim(p.engineering_project) = l.nm;
-- 期望：UPDATE N，N 应等于步骤 2 的「可自动回填」

commit;

-- 3.1 校验：剩余空外键数（应等于 需人工数 + 无文本数）
select count(*) as "剩余空外键"
from proj_project
where del_flag = '0' and project_category_id is null;


-- ============ 步骤 4（可选）：修正「文本旧名 / 外键新名」的错配 ============
-- 这条在代码上线后会自动收敛（保存项目时写规范名、类别改名时同步文本），
-- 但历史遗留的不一致不会自己消失，可选择性执行。

-- 4.1 先看有多少（只读）
select count(*) as "文本与外键名不一致"
from proj_project p
join proj_category c on c.id = p.project_category_id
where p.del_flag = '0'
  and c.del_flag = '0'
  and btrim(coalesce(p.engineering_project, '')) is distinct from btrim(c.name);

-- 4.2 以类别表为准，覆盖文本列（执行前建议先备份；如需保留原文本，先导出 4.1 明细）
-- begin;
-- update proj_project p
-- set engineering_project = c.name,
--     update_time         = now()
-- from proj_category c
-- where c.id = p.project_category_id
--   and p.del_flag = '0'
--   and c.del_flag = '0'
--   and btrim(coalesce(p.engineering_project, '')) is distinct from btrim(c.name);
-- commit;
