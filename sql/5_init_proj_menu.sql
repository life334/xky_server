-- =============================================
-- 项目信息管理系统 — 菜单初始化
-- 执行前请确保已执行 2_create_table.sql 和 3_init_sys_data.sql
-- =============================================

-- -----------------------------------------------
-- 2. 新建项目信息管理目录（顶级菜单）
-- -----------------------------------------------
INSERT INTO sys_menu VALUES (4, '项目信息管理', 0, 1, 'project', NULL, '', '', 1, 0, 'M', '0', '0', '', 'build', 'admin', now(), '', NULL, '项目信息管理目录');

-- -----------------------------------------------
-- 3. 项目类别管理菜单 + 按钮权限
-- -----------------------------------------------
-- 菜单
INSERT INTO sys_menu VALUES (502, '项目类别', 4, 1, 'category', 'project/category/index', '', 'Category', 1, 0, 'C', '0', '0', 'project:category:list', 'tree-table', 'admin', now(), '', NULL, '项目类别菜单');
-- 按钮
INSERT INTO sys_menu VALUES (1061, '类别查询', 4, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'project:category:query', '#', 'admin', now(), '', NULL, '');
INSERT INTO sys_menu VALUES (1062, '类别新增', 4, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'project:category:add', '#', 'admin', now(), '', NULL, '');
INSERT INTO sys_menu VALUES (1063, '类别修改', 4, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'project:category:edit', '#', 'admin', now(), '', NULL, '');
INSERT INTO sys_menu VALUES (1064, '类别删除', 4, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'project:category:remove', '#', 'admin', now(), '', NULL, '');

-- -----------------------------------------------
-- 4. 给超级管理员角色（role_id=1）分配新菜单
-- -----------------------------------------------
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(1, 4),
(1, 502),
(1, 1061),
(1, 1062),
(1, 1063),
(1, 1064);
