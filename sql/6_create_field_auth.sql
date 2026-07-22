-- ============================================================
-- 字段权限菜单 + 按钮权限
-- 在系统管理 → 角色管理页面中增加"字段权限"配置入口
-- ============================================================

-- 直接在角色管理的菜单下增加按钮权限标识即可（无需新增菜单，因为字段权限是角色管理的一个Tab/弹窗）
-- 如果有菜单表，在角色管理的菜单记录中增加 perms 字段即可
-- 此处提供 SQL 示例，实际菜单 ID 需根据数据库中的值调整

-- 为角色管理菜单增加"字段权限"按钮权限（假设角色管理菜单的 perms 字段需要扩展）
-- 实际执行方式：在系统管理界面手动给角色管理菜单添加权限字符，或在菜单初始化脚本中追加

-- 如果需要独立的字段权限管理菜单（不在角色管理内嵌），可以执行以下 INSERT
-- INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon, create_by, create_time)
-- VALUES ('字段权限', 1, 6, 'filedauth', 'system/fieldauth/index', 'C', '0', '0', 'system:fieldauth:list', 'lock', 'admin', now());
