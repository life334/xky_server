-- ① 担保人姓名（手动输入文本）
alter table proj_material      add column if not exists guarantor_name varchar(50);
alter table proj_material_flow add column if not exists guarantor_name varchar(50);

comment on column proj_material.guarantor_name is
  '担保人姓名（手动输入文本；guarantor_id 保留但已弃用）';
comment on column proj_material_flow.guarantor_name is
  '担保人姓名快照（手动输入文本；guarantor_id 保留但已弃用）';

-- ② 资料状态字典：returned → archived（已归档）
--    幂等：若已存在 archived 行则删掉 returned 行，否则把 returned 改名。
do $$
begin
  if exists (select 1 from sys_dict_data where dict_type = 'proj_material_status' and dict_value = 'archived') then
    delete from sys_dict_data where dict_type = 'proj_material_status' and dict_value = 'returned';
  else
    update sys_dict_data
       set dict_value = 'archived'
     where dict_type = 'proj_material_status' and dict_value = 'returned';
  end if;
end $$;

-- ③ 资料状态列语义说明（值保留；读侧以「archive_flag='Y' → archived，
--    否则存在未删除的「领取」流转 → received，否则 pending」实时派生为准）
comment on column proj_material.status is
  '资料流转状态（冗余列，读侧以「归档标志 + 领取流转」实时派生为准：archived > received > pending）';
comment on column proj_material.submit_status is
  '提交状态（已退役，不再作为筛选/展示口径；列保留）';
