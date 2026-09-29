
-- ① 领取类型列（历史/导入数据为空时，状态派生按纸质版兜底）
alter table proj_material_flow add column if not exists pickup_type varchar(20);
comment on column proj_material_flow.pickup_type is '领取类型：electronic=电子版 / paper=纸质版 / both=电子+纸质版';

-- ② 字典类型：资料领取类型
do $$
begin
  if not exists (select 1 from sys_dict_type where dict_type = 'proj_material_pickup_type') then
    insert into sys_dict_type(dict_name, dict_type, status, create_by, create_time, remark)
    values ('资料领取类型', 'proj_material_pickup_type', '0', 'admin', now(), '资料领取介质：电子版/纸质版/电子+纸质版');
  end if;
end $$;

do $$
begin
  if not exists (select 1 from sys_dict_data where dict_type = 'proj_material_pickup_type' and dict_value = 'electronic') then
    insert into sys_dict_data(dict_sort, dict_label, dict_value, dict_type, list_class, is_default, status, create_by, create_time, remark)
    values (1, '电子版', 'electronic', 'proj_material_pickup_type', 'primary', 'N', '0', 'admin', now(), '电子版');
  end if;
  if not exists (select 1 from sys_dict_data where dict_type = 'proj_material_pickup_type' and dict_value = 'paper') then
    insert into sys_dict_data(dict_sort, dict_label, dict_value, dict_type, list_class, is_default, status, create_by, create_time, remark)
    values (2, '纸质版', 'paper', 'proj_material_pickup_type', 'success', 'N', '0', 'admin', now(), '纸质版');
  end if;
  if not exists (select 1 from sys_dict_data where dict_type = 'proj_material_pickup_type' and dict_value = 'both') then
    insert into sys_dict_data(dict_sort, dict_label, dict_value, dict_type, list_class, is_default, status, create_by, create_time, remark)
    values (3, '电子+纸质版', 'both', 'proj_material_pickup_type', 'warning', 'N', '0', 'admin', now(), '电子+纸质版');
  end if;
end $$;

-- ③ 资料状态字典：整体替换为 4 态（旧码值 pending/received/returned/archived 一并清掉）
delete from sys_dict_data where dict_type = 'proj_material_status';
insert into sys_dict_data(dict_sort, dict_label, dict_value, dict_type, list_class, is_default, status, create_by, create_time, remark)
values (1, '未领取',           'pending',             'proj_material_status', 'info',    'Y', '0', 'admin', now(), '未领取'),
       (2, '已领取(电子版)',    'received_electronic', 'proj_material_status', 'primary', 'N', '0', 'admin', now(), '已领取-电子版'),
       (3, '已领取(纸质版)',    'received_paper',      'proj_material_status', 'primary', 'N', '0', 'admin', now(), '已领取-纸质版'),
       (4, '已领取(电子+纸质)', 'received_both',       'proj_material_status', 'success', 'N', '0', 'admin', now(), '已领取-电子+纸质版');
