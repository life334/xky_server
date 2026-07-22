-- =============================================
-- 项目信息管理系统 — 菜单初始化
-- 执行前请确保已执行 2_create_table.sql 和 3_init_sys_data.sql
-- =============================================

-- -----------------------------------------------
-- 2. 新建项目信息管理目录（顶级菜单）
-- -----------------------------------------------
INSERT INTO sys_menu VALUES (4, '项目管理', 0, 1, 'project', NULL, '', '', 1, 0, 'M', '0', '0', '', 'build', 'admin', now(), '', NULL, '项目管理目录');
INSERT INTO sys_menu VALUES (5, '合同管理', 0, 1, 'contract', NULL, '', '', 1, 0, 'M', '0', '0', '', 'documentation', 'admin', now(), '', NULL, '合同管理目录');
INSERT INTO sys_menu VALUES (6, '资料管理', 0, 1, 'material', 'project/material/index', '', 'MaterialList', 1, 0, 'C', '0', '0', 'project:material:list', 'list', 'admin', now(), '', NULL, '资料管理菜单');

-- -----------------------------------------------
-- 3. 项目类别管理菜单 + 按钮权限
-- -----------------------------------------------
-- 菜单
INSERT INTO sys_menu VALUES (502, '项目类别', 4, 1, 'category', 'project/category/index', '', 'Category', 1, 0, 'C', '0', '0', 'project:category:list', 'tree-table', 'admin', now(), '', NULL, '项目类别菜单');
INSERT INTO sys_menu VALUES (503, '项目列表', 4, 2, 'list', 'project/project/index', '', 'ProjectList', 1, 0, 'C', '0', '0', 'project:project:list', 'tree-table', 'admin', now(), '', NULL, '项目列表菜单');
INSERT INTO sys_menu VALUES (506, '任务列表', 4, 3, 'task', 'project/task/index', '', 'TaskList', 1, 0, 'C', '0', '0', 'project:task:list', 'list', 'admin', now(), '', NULL, '任务列表菜单');
INSERT INTO sys_menu VALUES (507, '工作量列表', 4, 4, 'workload', 'project/workload/index', '', 'WorkloadList', 1, 0, 'C', '0', '0', 'project:workload:list', 'list', 'admin', now(), '', NULL, '工作量列表菜单');
INSERT INTO sys_menu VALUES (508, '付款管理', 4, 5, 'payment', 'project/payment/index', '', 'PaymentList', 1, 0, 'C', '0', '0', 'project:payment:list', 'money', 'admin', now(), '', NULL, '付款管理菜单');

INSERT INTO sys_menu VALUES (504, '合同列表', 5, 1, 'list', 'project/contract/index', '', 'ContractList', 1, 0, 'C', '0', '0', 'project:contract:list', 'list', 'admin', now(), '', NULL, '合同列表页面');
INSERT INTO sys_menu VALUES (505, '合同单价', 5, 2, 'price', 'project/contract/price/index', '', 'ContractPrice', 1, 0, 'C', '0', '0', 'project:contract:price:list', 'money', 'admin', now(), '', NULL, '合同单价配置');

-- 项目类别按钮
INSERT INTO sys_menu VALUES (1061, '类别查询', 502, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'project:category:query', '#', 'admin', now(), '', NULL, '');
INSERT INTO sys_menu VALUES (1062, '类别新增', 502, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'project:category:add', '#', 'admin', now(), '', NULL, '');
INSERT INTO sys_menu VALUES (1063, '类别修改', 502, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'project:category:edit', '#', 'admin', now(), '', NULL, '');
INSERT INTO sys_menu VALUES (1064, '类别删除', 502, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'project:category:remove', '#', 'admin', now(), '', NULL, '');

-- 项目列表按钮
INSERT INTO sys_menu VALUES (1065, '项目查询', 503, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'project:project:query', '#', 'admin', now(), '', NULL, '');
INSERT INTO sys_menu VALUES (1066, '项目新增', 503, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'project:project:add', '#', 'admin', now(), '', NULL, '');
INSERT INTO sys_menu VALUES (1067, '项目修改', 503, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'project:project:edit', '#', 'admin', now(), '', NULL, '');
INSERT INTO sys_menu VALUES (1068, '项目删除', 503, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'project:project:remove', '#', 'admin', now(), '', NULL, '');
INSERT INTO sys_menu VALUES (1069, '项目导出', 503, 5, '', '', '', '', 1, 0, 'F', '0', '0', 'project:project:export', '#', 'admin', now(), '', NULL, '');

-- 合同列表按钮
INSERT INTO sys_menu VALUES (1070, '合同查询', 504, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'project:contract:query', '#', 'admin', now(), '', NULL, '');
INSERT INTO sys_menu VALUES (1071, '合同新增', 504, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'project:contract:add', '#', 'admin', now(), '', NULL, '');
INSERT INTO sys_menu VALUES (1072, '合同修改', 504, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'project:contract:edit', '#', 'admin', now(), '', NULL, '');
INSERT INTO sys_menu VALUES (1073, '合同删除', 504, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'project:contract:remove', '#', 'admin', now(), '', NULL, '');
INSERT INTO sys_menu VALUES (1074, '合同导出', 504, 5, '', '', '', '', 1, 0, 'F', '0', '0', 'project:contract:export', '#', 'admin', now(), '', NULL, '');

-- 合同单价按钮
INSERT INTO sys_menu VALUES (1075, '合同单价查询', 505, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'project:contract:price:list', '#', 'admin', now(), '', NULL, '');
INSERT INTO sys_menu VALUES (1076, '合同单价保存', 505, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'project:contract:price:save', '#', 'admin', now(), '', NULL, '');

-- 任务列表按钮
INSERT INTO sys_menu VALUES (1077, '任务查询', 506, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'project:task:query', '#', 'admin', now(), '', NULL, '');
INSERT INTO sys_menu VALUES (1078, '任务新增', 506, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'project:task:add', '#', 'admin', now(), '', NULL, '');
INSERT INTO sys_menu VALUES (1079, '任务修改', 506, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'project:task:edit', '#', 'admin', now(), '', NULL, '');
INSERT INTO sys_menu VALUES (1080, '任务删除', 506, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'project:task:remove', '#', 'admin', now(), '', NULL, '');
INSERT INTO sys_menu VALUES (1081, '任务导出', 506, 5, '', '', '', '', 1, 0, 'F', '0', '0', 'project:task:export', '#', 'admin', now(), '', NULL, '');

-- 工作量列表按钮
INSERT INTO sys_menu VALUES (1082, '工作量查询', 507, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'project:workload:query', '#', 'admin', now(), '', NULL, '');
INSERT INTO sys_menu VALUES (1083, '工作量新增', 507, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'project:workload:add', '#', 'admin', now(), '', NULL, '');
INSERT INTO sys_menu VALUES (1084, '工作量修改', 507, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'project:workload:edit', '#', 'admin', now(), '', NULL, '');
INSERT INTO sys_menu VALUES (1085, '工作量删除', 507, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'project:workload:remove', '#', 'admin', now(), '', NULL, '');
INSERT INTO sys_menu VALUES (1086, '工作量导出', 507, 5, '', '', '', '', 1, 0, 'F', '0', '0', 'project:workload:export', '#', 'admin', now(), '', NULL, '');

-- 付款管理按钮
INSERT INTO sys_menu VALUES (1087, '付款查询', 508, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'project:payment:query', '#', 'admin', now(), '', NULL, '');
INSERT INTO sys_menu VALUES (1088, '付款新增', 508, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'project:payment:add', '#', 'admin', now(), '', NULL, '');
INSERT INTO sys_menu VALUES (1089, '付款修改', 508, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'project:payment:edit', '#', 'admin', now(), '', NULL, '');
INSERT INTO sys_menu VALUES (1090, '付款删除', 508, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'project:payment:remove', '#', 'admin', now(), '', NULL, '');
INSERT INTO sys_menu VALUES (1091, '付款导出', 508, 5, '', '', '', '', 1, 0, 'F', '0', '0', 'project:payment:export', '#', 'admin', now(), '', NULL, '');

-- 资料管理按钮
INSERT INTO sys_menu VALUES (1092, '资料查询', 6, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'project:material:query', '#', 'admin', now(), '', NULL, '');
INSERT INTO sys_menu VALUES (1093, '资料新增', 6, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'project:material:add', '#', 'admin', now(), '', NULL, '');
INSERT INTO sys_menu VALUES (1094, '资料修改', 6, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'project:material:edit', '#', 'admin', now(), '', NULL, '');
INSERT INTO sys_menu VALUES (1095, '资料删除', 6, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'project:material:remove', '#', 'admin', now(), '', NULL, '');
INSERT INTO sys_menu VALUES (1096, '资料导出', 6, 5, '', '', '', '', 1, 0, 'F', '0', '0', 'project:material:export', '#', 'admin', now(), '', NULL, '');
