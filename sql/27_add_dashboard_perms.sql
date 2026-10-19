-- =============================================================
-- 首页驾驶舱：补数据权限码
-- 背景：/project/dashboard/* 六个接口加 @PreAuthorize('project:dashboard:view')，
--       但 sys_menu 中「首页」菜单的 perms 为空 ⇒ 若不补，所有人都拿不到该权限码，
--       连原本能看首页的角色也会被误伤。
-- 执行库：xky_project (PostgreSQL)
-- 幂等：可重复执行
-- =============================================================

-- 0) 执行前先确认首页菜单 id（脚本按 id=8，若你的库不同请替换下面的 8）
-- SELECT menu_id, menu_name, path, component, perms FROM sys_menu WHERE menu_name = '首页';

-- 1) 给「首页」菜单补权限码（RuoYi 的 perms 走 sys_role_menu 关联：
--    角色授权了该菜单 ⇒ 自动获得该权限码，无需额外授权脚本；admin 有 *:*:* 自动放行）
UPDATE sys_menu SET perms = 'project:dashboard:view' WHERE menu_id = 8;

-- 2) 校验：当前哪些角色被授权了「首页」菜单（= 哪些角色继续能看到驾驶舱数据）
SELECT r.role_id, r.role_name, r.role_key
FROM sys_role r
JOIN sys_role_menu rm ON rm.role_id = r.role_id
WHERE rm.menu_id = 8
ORDER BY r.role_id;

-- 3) 如需收回某角色（如档案管理员 role-doc）的首页权限，执行：
-- DELETE FROM sys_role_menu
-- WHERE menu_id = 8
--   AND role_id = (SELECT role_id FROM sys_role WHERE role_key = 'role-doc');

-- 注意：菜单与权限缓存在 Redis，执行完后需重新登录（或清 sys_config/菜单缓存）才生效。
