-- 29_add_db_backup_menu.sql
-- 数据库备份菜单：挂在「系统管理」下，仅绑定超管角色(role_id=1)
-- 说明：备份接口本身在代码里做 isAdmin 校验（不挂权限码拦截），
--       因此这里只需建菜单 + 绑定超管，业务角色看不到该菜单也不会被 403。
-- 幂等：已存在 system/dbBackup/index 组件时跳过。

do $$
declare
    v_menu_id bigint;
begin
    if exists (select 1 from sys_menu where component = 'system/dbBackup/index') then
        raise notice '[skip] 数据备份菜单已存在';
        return;
    end if;

    insert into sys_menu(menu_name, parent_id, order_num, path, component, route_name,
                         is_frame, is_cache, menu_type, visible, status, perms, icon,
                         create_by, create_time, remark)
    values ('数据备份', 1, 10, 'dbBackup', 'system/dbBackup/index', 'DbBackup',
            1, 0, 'C', '0', '0', 'system:dbBackup:list', 'database',
            'admin', now(), '数据库定时备份与下载')
    returning menu_id into v_menu_id;

    insert into sys_role_menu(role_id, menu_id) values (1, v_menu_id);

    raise notice '[ok] 数据备份菜单已创建，menu_id=%', v_menu_id;
end $$;
