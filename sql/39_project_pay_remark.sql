-- =============================================================
-- 39. 项目级「到账备注」字段
--     背景：费用结算「到账信息」弹窗里只有一个「备注」输入框，保存时后端把这段文字写到每一笔付款行
--           （proj_payment.remark）。项目没有任何付款行时（用户只填备注、金额/时间都不填），
--           保存逻辑的「空行防御」会整行跳过 ⇒ 备注无处保存、直接丢失。
--     做法：在项目表加独立列 pay_remark，保存时同时写项目级；
--           读取时「项目级优先，为空则回退到付款行聚合」⇒ 历史数据无需迁移。
--     幂等：可重复执行。
-- =============================================================

alter table proj_project
    add column if not exists pay_remark varchar(1000);

comment on column proj_project.pay_remark is
    '到账备注（项目级，费用结算-到账信息弹窗的备注框）；为空时列表/报表回退到 proj_payment.remark 聚合';

-- 可选：把历史上「各付款行备注一致」的项目回填到项目级（多笔备注不一致的不回填，保持读时聚合结果）
-- 仅回填「非退款付款行备注去重后恰好 1 条」的项目，避免改变已有展示
update proj_project p
   set pay_remark = t.only_remark
  from (
        select pm.project_id,
               min(btrim(pm.remark)) as only_remark
          from proj_payment pm
         where pm.del_flag = '0'
           and pm.payment_type <> 'refund'
           and btrim(coalesce(pm.remark, '')) <> ''
         group by pm.project_id
        having count(distinct btrim(pm.remark)) = 1
       ) t
 where p.id = t.project_id
   and (p.pay_remark is null or btrim(p.pay_remark) = '');
