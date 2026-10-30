-- =============================================================
-- 修复：角色已勾选全部权限，但登录后 /project/dashboard/* 仍提示「没有权限，请联系管理员授权」
-- 影响接口：summary / structure / trend / risk / pipelineLength（还有 getDashboard、alerts）
-- 执行库：生产库 xky_project (PostgreSQL)
-- 幂等：可重复执行；脚本先「诊断」，再「修复」，最后「验证」，三段的输出都要看
-- =============================================================
-- 根因说明（务必先看）
--   ProjDashboardController 六个接口全部加了 @PreAuthorize("@ss.hasPermi('project:dashboard:view')")。
--   在 RuoYi 体系里，权限码不是独立存在的，它挂在 sys_menu.perms 上：
--     角色 -> sys_role_menu -> sys_menu.perms
--   而 sql/5_init_proj_menu.sql 建的「首页」菜单（menu_id=8）perms 是空串 ''，
--   所以 2026-10-19 补了 sql/27_add_dashboard_perms.sql 专门给该菜单填 'project:dashboard:view'。
--   ⇒ 生产若没执行 sql/27，这个权限码在系统里根本不存在。
--     表现就是：除了 admin（走 *:*:* 直通）之外，任何角色勾多少权限都拿不到它，后台接口一律 403。
--   另有一个隐蔽点：selectMenuPermsByRoleId 的 WHERE 里有 m.status = '0'，
--   菜单若是「停用」状态，perms 填了也不会生效，所以下面一并处理 status。
-- =============================================================


-- =============================================================
-- 一、诊断（只读，先看结果再往下执行）
-- =============================================================

-- 1.1 首页菜单当前长什么样：重点看 menu_id / perms / status
--     perms 为空串        ⇒ 没跑过 sql/27，就是本次故障根因
--     perms 有值但 status='1' ⇒ 菜单被停用，perms 不生效
--     查不到结果          ⇒ 生产的菜单不是叫「首页」/ path 不是 dashboard，需要人工确认
SELECT menu_id, menu_name, parent_id, path, component, perms, menu_type, visible, status
FROM   sys_menu
WHERE  menu_name = '首页' OR path = 'dashboard'
ORDER  BY menu_id;

-- 1.2 「生产管理」这类角色的定义（确认 role_id / role_key / status）
SELECT role_id, role_name, role_key, status, del_flag
FROM   sys_role
WHERE  role_name LIKE '%生产管理%'
ORDER  BY role_id;

-- 1.3 这些角色有没有拿到 project:dashboard:view（把 1.2 查到的 role_id 填进去即可，也可以不填跑全角色）
--     注意这里复刻的是后端 selectMenuPermsByRoleId 的真实逻辑（含 status='0' 过滤）
SELECT r.role_id, r.role_name, r.role_key,
       bool_or(coalesce(m.perms, '') = 'project:dashboard:view') AS has_dashboard_perm
FROM   sys_role r
LEFT   JOIN sys_role_menu rm ON rm.role_id = r.role_id
LEFT   JOIN sys_menu      m  ON m.menu_id = rm.menu_id AND m.status = '0'
WHERE  r.del_flag = '0'
GROUP  BY r.role_id, r.role_name, r.role_key
ORDER  BY has_dashboard_perm, r.role_id;

-- 1.4 「首页」菜单被授权给了哪些角色（这些角色才看得到驾驶舱）
SELECT r.role_id, r.role_name, r.role_key
FROM   sys_role r
JOIN   sys_role_menu rm ON rm.role_id = r.role_id
WHERE  rm.menu_id = (SELECT menu_id FROM sys_menu WHERE menu_name='首页' OR path='dashboard' LIMIT 1)
ORDER  BY r.role_id;


-- =============================================================
-- 二、修复（动手改库前请确认 1.1 / 1.2 的输出符合预期）
-- =============================================================

-- 2.1 给「首页」菜单补权限码 + 确保启用。
--     这里用 menu_name/path 定位而不是写死 menu_id=8：
--     生产库的菜单 id 可能与初始化脚本不一致，写死 id 会改错行（且不会报错）。
UPDATE sys_menu
SET    perms  = 'project:dashboard:view',
       status = '0'
WHERE  (menu_name = '首页' OR path = 'dashboard')
  AND  coalesce(perms, '') <> 'project:dashboard:view'
RETURNING menu_id, menu_name, perms, status AS new_status;
-- ↑ 输出为空 ⇒ 已经是目标状态，没改到任何行，可直接跳到第三步验证

-- 2.2 只在「角色缺该菜单授权」时才补关系（正常情况不需要跑）
--     什么情况才要跑：1.4 里查不到「生产管理」，即该角色压根没勾到首页菜单。
--     默认注释掉，避免无谓写库；确认要用时把下面两行解开，并把 '%生产管理%' 换成实际角色名。
-- INSERT INTO sys_role_menu (role_id, menu_id)
-- SELECT r.role_id, m.menu_id
-- FROM   sys_role r
-- CROSS  JOIN (SELECT menu_id FROM sys_menu WHERE menu_name='首页' OR path='dashboard' LIMIT 1) m
-- WHERE  r.del_flag = '0' AND r.role_name LIKE '%生产管理%'
--   AND  NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.role_id AND x.menu_id = m.menu_id);


-- 2.3 兜底：若 1.1 一行都查不到（生产库里根本没有「首页」菜单），先补菜单行再回到 2.1 / 2.2。
--     默认注释掉。menu_id 是 IDENTITY 自增，不要手写。
-- INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, route_name,
--                       is_frame, is_cache, menu_type, visible, status, perms, icon,
--                       create_by, create_time, remark)
-- SELECT '首页', 0, 0, 'dashboard', 'index', 'Dashboard',
--        '1', '0', 'C', '0', '0', 'project:dashboard:view', 'dashboard',
--        'admin', now(), '首页'
-- WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_name = '首页' OR path = 'dashboard');


-- =============================================================
-- 三、验证 + 生效说明
-- =============================================================

-- 3.1 再跑一次，能看到 生产管理 的 has_dashboard_perm = t 即修复成功
SELECT r.role_id, r.role_name,
       bool_or(coalesce(m.perms, '') = 'project:dashboard:view') AS has_dashboard_perm
FROM   sys_role r
LEFT   JOIN sys_role_menu rm ON rm.role_id = r.role_id
LEFT   JOIN sys_menu      m  ON m.menu_id = rm.menu_id AND m.status = '0'
WHERE  r.del_flag = '0' AND r.role_name LIKE '%生产管理%'
GROUP  BY r.role_id, r.role_name;

-- 3.2 【生效方式】不需要重启后端，也不需要退出登录：
--     权限码没有 @Cacheable，selectMenuPermsByRoleId 每次直接读库；
--     而 /getInfo（SysLoginController:87）每次打开/刷新页面都会用库里的最新结果重算权限集合，
--     一旦发现与 token 里缓存的不一致，会 setPermissions + refreshToken 把缓存覆盖掉。
--     ⇒ 改完库后，让用户刷新一次浏览器页面（F5）即可生效；保险起见也可让他重新登录。
--     如果刷新后仍报无权限，那说明不是「首页菜单 perms 为空」这一个原因，
--     请回看第一步 1.1/1.3 的输出：多半是该角色没有勾到「首页」菜单（跑 2.2），
--     或菜单被停用（2.1 已顺带把 status 置回 '0'）。
