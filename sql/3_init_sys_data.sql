-- ----------------------------
-- 初始化-部门表数据
-- ----------------------------
insert into sys_dept values(100,  0,   '0', '管理部门',   0, 'admin', '15888888888', 'xx@qq.com', '0', '0', 'admin', now(), '', null, null);
SELECT setval('sys_dept_dept_id_seq', 200, false);

-- ----------------------------
-- 初始化-用户信息表数据
-- ----------------------------
insert into sys_user values(1,  100, 'admin',   '最高权限', '00', 'xx@163.com', '15888888888', '1', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', now(), now(), 'admin', now(), '', null, '管理员');
SELECT setval('sys_user_user_id_seq', 100, false);

-- ----------------------------
-- 初始化-角色信息表数据
-- ----------------------------
insert into sys_role values('1', '超级管理员',   'admin',  1, 1, true, true, '0', '0', 'admin', now(), '', null, '超级管理员');
SELECT setval('sys_role_role_id_seq', 100, false);

-- ----------------------------
-- 初始化-菜单信息表数据
-- ----------------------------
-- 一级菜单
insert into sys_menu values('1', '系统管理', '0', '2', 'system',           null, '', '', '1', '0', 'M', '0', '0', '', 'system',   'admin', now(), '', null, '系统管理目录');
insert into sys_menu values('2', '系统监控', '0', '3', 'monitor',          null, '', '', '1', '0', 'M', '0', '0', '', 'monitor',  'admin', now(), '', null, '系统监控目录');
insert into sys_menu values('3', '系统工具', '0', '4', 'tool',             null, '', '', '1', '0', 'M', '0', '0', '', 'tool',     'admin', now(), '', null, '系统工具目录');

-- 二级菜单
insert into sys_menu values('100',  '用户管理', '1',   '1', 'user',       'system/user/index',        '', '', '1', '0', 'C', '0', '0', 'system:user:list',        'user',          'admin', now(), '', null, '用户管理菜单');
insert into sys_menu values('101',  '角色管理', '1',   '2', 'role',       'system/role/index',        '', '', '1', '0', 'C', '0', '0', 'system:role:list',        'peoples',       'admin', now(), '', null, '角色管理菜单');
insert into sys_menu values('102',  '菜单管理', '1',   '3', 'menu',       'system/menu/index',        '', '', '1', '0', 'C', '0', '0', 'system:menu:list',        'tree-table',    'admin', now(), '', null, '菜单管理菜单');
insert into sys_menu values('103',  '部门管理', '1',   '4', 'dept',       'system/dept/index',        '', '', '1', '0', 'C', '0', '0', 'system:dept:list',        'tree',          'admin', now(), '', null, '部门管理菜单');
insert into sys_menu values('104',  '岗位管理', '1',   '5', 'post',       'system/post/index',        '', '', '1', '0', 'C', '0', '0', 'system:post:list',        'post',          'admin', now(), '', null, '岗位管理菜单');
insert into sys_menu values('105',  '字典管理', '1',   '6', 'dict',       'system/dict/index',        '', '', '1', '0', 'C', '0', '0', 'system:dict:list',        'dict',          'admin', now(), '', null, '字典管理菜单');
insert into sys_menu values('106',  '参数设置', '1',   '7', 'config',     'system/config/index',      '', '', '1', '0', 'C', '0', '0', 'system:config:list',      'edit',          'admin', now(), '', null, '参数设置菜单');
insert into sys_menu values('107',  '通知公告', '1',   '8', 'notice',     'system/notice/index',      '', '', '1', '0', 'C', '0', '0', 'system:notice:list',      'message',       'admin', now(), '', null, '通知公告菜单');
insert into sys_menu values('108',  '日志管理', '1',   '9', 'log',        '',                         '', '', '1', '0', 'M', '0', '0', '',                        'log',           'admin', now(), '', null, '日志管理菜单');

insert into sys_menu values('109',  '在线用户', '2',   '1', 'online',     'monitor/online/index',     '', '', '1', '0', 'C', '0', '0', 'monitor:online:list',     'online',        'admin', now(), '', null, '在线用户菜单');
insert into sys_menu values('110',  '定时任务', '2',   '2', 'job',        'monitor/job/index',        '', '', '1', '0', 'C', '0', '0', 'monitor:job:list',        'job',           'admin', now(), '', null, '定时任务菜单');
insert into sys_menu values('111',  '数据监控', '2',   '3', 'druid',      'monitor/druid/index',      '', '', '1', '0', 'C', '0', '0', 'monitor:druid:list',      'druid',         'admin', now(), '', null, '数据监控菜单');
insert into sys_menu values('112',  '服务监控', '2',   '4', 'server',     'monitor/server/index',     '', '', '1', '0', 'C', '0', '0', 'monitor:server:list',     'server',        'admin', now(), '', null, '服务监控菜单');
insert into sys_menu values('113',  '缓存监控', '2',   '5', 'cache',      'monitor/cache/index',      '', '', '1', '0', 'C', '0', '0', 'monitor:cache:list',      'redis',         'admin', now(), '', null, '缓存监控菜单');
insert into sys_menu values('114',  '缓存列表', '2',   '6', 'cacheList',  'monitor/cache/list',       '', '', '1', '0', 'C', '0', '0', 'monitor:cache:list',      'redis-list',    'admin', now(), '', null, '缓存列表菜单');

insert into sys_menu values('115',  '表单构建', '3',   '1', 'build',      'tool/build/index',         '', '', '1', '0', 'C', '0', '0', 'tool:build:list',         'build',         'admin', now(), '', null, '表单构建菜单');
insert into sys_menu values('116',  '代码生成', '3',   '2', 'gen',        'tool/gen/index',           '', '', '1', '0', 'C', '0', '0', 'tool:gen:list',           'code',          'admin', now(), '', null, '代码生成菜单');
insert into sys_menu values('117',  '系统接口', '3',   '3', 'swagger',    'tool/swagger/index',       '', '', '1', '0', 'C', '0', '0', 'tool:swagger:list',       'swagger',       'admin', now(), '', null, '系统接口菜单');

-- 三级菜单
insert into sys_menu values('500',  '操作日志', '108', '1', 'operlog',    'monitor/operlog/index',    '', '', '1', '0', 'C', '0', '0', 'monitor:operlog:list',    'form',          'admin', now(), '', null, '操作日志菜单');
insert into sys_menu values('501',  '登录日志', '108', '2', 'logininfor', 'monitor/logininfor/index', '', '', '1', '0', 'C', '0', '0', 'monitor:logininfor:list', 'logininfor',    'admin', now(), '', null, '登录日志菜单');
-- 用户管理按钮
insert into sys_menu values('1000', '用户查询', '100', '1',  '', '', '', '', '1', '0', 'F', '0', '0', 'system:user:query',          '#', 'admin', now(), '', null, '');
insert into sys_menu values('1001', '用户新增', '100', '2',  '', '', '', '', '1', '0', 'F', '0', '0', 'system:user:add',            '#', 'admin', now(), '', null, '');
insert into sys_menu values('1002', '用户修改', '100', '3',  '', '', '', '', '1', '0', 'F', '0', '0', 'system:user:edit',           '#', 'admin', now(), '', null, '');
insert into sys_menu values('1003', '用户删除', '100', '4',  '', '', '', '', '1', '0', 'F', '0', '0', 'system:user:remove',         '#', 'admin', now(), '', null, '');
insert into sys_menu values('1004', '用户导出', '100', '5',  '', '', '', '', '1', '0', 'F', '0', '0', 'system:user:export',         '#', 'admin', now(), '', null, '');
insert into sys_menu values('1005', '用户导入', '100', '6',  '', '', '', '', '1', '0', 'F', '0', '0', 'system:user:import',         '#', 'admin', now(), '', null, '');
insert into sys_menu values('1006', '重置密码', '100', '7',  '', '', '', '', '1', '0', 'F', '0', '0', 'system:user:resetPwd',       '#', 'admin', now(), '', null, '');
-- 角色管理按钮
insert into sys_menu values('1007', '角色查询', '101', '1',  '', '', '', '', '1', '0', 'F', '0', '0', 'system:role:query',          '#', 'admin', now(), '', null, '');
insert into sys_menu values('1008', '角色新增', '101', '2',  '', '', '', '', '1', '0', 'F', '0', '0', 'system:role:add',            '#', 'admin', now(), '', null, '');
insert into sys_menu values('1009', '角色修改', '101', '3',  '', '', '', '', '1', '0', 'F', '0', '0', 'system:role:edit',           '#', 'admin', now(), '', null, '');
insert into sys_menu values('1010', '角色删除', '101', '4',  '', '', '', '', '1', '0', 'F', '0', '0', 'system:role:remove',         '#', 'admin', now(), '', null, '');
insert into sys_menu values('1011', '角色导出', '101', '5',  '', '', '', '', '1', '0', 'F', '0', '0', 'system:role:export',         '#', 'admin', now(), '', null, '');
-- 菜单管理按钮
insert into sys_menu values('1012', '菜单查询', '102', '1',  '', '', '', '', '1', '0', 'F', '0', '0', 'system:menu:query',          '#', 'admin', now(), '', null, '');
insert into sys_menu values('1013', '菜单新增', '102', '2',  '', '', '', '', '1', '0', 'F', '0', '0', 'system:menu:add',            '#', 'admin', now(), '', null, '');
insert into sys_menu values('1014', '菜单修改', '102', '3',  '', '', '', '', '1', '0', 'F', '0', '0', 'system:menu:edit',           '#', 'admin', now(), '', null, '');
insert into sys_menu values('1015', '菜单删除', '102', '4',  '', '', '', '', '1', '0', 'F', '0', '0', 'system:menu:remove',         '#', 'admin', now(), '', null, '');
-- 部门管理按钮
insert into sys_menu values('1016', '部门查询', '103', '1',  '', '', '', '', '1', '0', 'F', '0', '0', 'system:dept:query',          '#', 'admin', now(), '', null, '');
insert into sys_menu values('1017', '部门新增', '103', '2',  '', '', '', '', '1', '0', 'F', '0', '0', 'system:dept:add',            '#', 'admin', now(), '', null, '');
insert into sys_menu values('1018', '部门修改', '103', '3',  '', '', '', '', '1', '0', 'F', '0', '0', 'system:dept:edit',           '#', 'admin', now(), '', null, '');
insert into sys_menu values('1019', '部门删除', '103', '4',  '', '', '', '', '1', '0', 'F', '0', '0', 'system:dept:remove',         '#', 'admin', now(), '', null, '');
-- 岗位管理按钮
insert into sys_menu values('1020', '岗位查询', '104', '1',  '', '', '', '', '1', '0', 'F', '0', '0', 'system:post:query',          '#', 'admin', now(), '', null, '');
insert into sys_menu values('1021', '岗位新增', '104', '2',  '', '', '', '', '1', '0', 'F', '0', '0', 'system:post:add',            '#', 'admin', now(), '', null, '');
insert into sys_menu values('1022', '岗位修改', '104', '3',  '', '', '', '', '1', '0', 'F', '0', '0', 'system:post:edit',           '#', 'admin', now(), '', null, '');
insert into sys_menu values('1023', '岗位删除', '104', '4',  '', '', '', '', '1', '0', 'F', '0', '0', 'system:post:remove',         '#', 'admin', now(), '', null, '');
insert into sys_menu values('1024', '岗位导出', '104', '5',  '', '', '', '', '1', '0', 'F', '0', '0', 'system:post:export',         '#', 'admin', now(), '', null, '');
-- 字典管理按钮
insert into sys_menu values('1025', '字典查询', '105', '1', '#', '', '', '', '1', '0', 'F', '0', '0', 'system:dict:query',          '#', 'admin', now(), '', null, '');
insert into sys_menu values('1026', '字典新增', '105', '2', '#', '', '', '', '1', '0', 'F', '0', '0', 'system:dict:add',            '#', 'admin', now(), '', null, '');
insert into sys_menu values('1027', '字典修改', '105', '3', '#', '', '', '', '1', '0', 'F', '0', '0', 'system:dict:edit',           '#', 'admin', now(), '', null, '');
insert into sys_menu values('1028', '字典删除', '105', '4', '#', '', '', '', '1', '0', 'F', '0', '0', 'system:dict:remove',         '#', 'admin', now(), '', null, '');
insert into sys_menu values('1029', '字典导出', '105', '5', '#', '', '', '', '1', '0', 'F', '0', '0', 'system:dict:export',         '#', 'admin', now(), '', null, '');
-- 参数设置按钮
insert into sys_menu values('1030', '参数查询', '106', '1', '#', '', '', '', '1', '0', 'F', '0', '0', 'system:config:query',        '#', 'admin', now(), '', null, '');
insert into sys_menu values('1031', '参数新增', '106', '2', '#', '', '', '', '1', '0', 'F', '0', '0', 'system:config:add',          '#', 'admin', now(), '', null, '');
insert into sys_menu values('1032', '参数修改', '106', '3', '#', '', '', '', '1', '0', 'F', '0', '0', 'system:config:edit',         '#', 'admin', now(), '', null, '');
insert into sys_menu values('1033', '参数删除', '106', '4', '#', '', '', '', '1', '0', 'F', '0', '0', 'system:config:remove',       '#', 'admin', now(), '', null, '');
insert into sys_menu values('1034', '参数导出', '106', '5', '#', '', '', '', '1', '0', 'F', '0', '0', 'system:config:export',       '#', 'admin', now(), '', null, '');
-- 通知公告按钮
insert into sys_menu values('1035', '公告查询', '107', '1', '#', '', '', '', '1', '0', 'F', '0', '0', 'system:notice:query',        '#', 'admin', now(), '', null, '');
insert into sys_menu values('1036', '公告新增', '107', '2', '#', '', '', '', '1', '0', 'F', '0', '0', 'system:notice:add',          '#', 'admin', now(), '', null, '');
insert into sys_menu values('1037', '公告修改', '107', '3', '#', '', '', '', '1', '0', 'F', '0', '0', 'system:notice:edit',         '#', 'admin', now(), '', null, '');
insert into sys_menu values('1038', '公告删除', '107', '4', '#', '', '', '', '1', '0', 'F', '0', '0', 'system:notice:remove',       '#', 'admin', now(), '', null, '');
-- 操作日志按钮
insert into sys_menu values('1039', '操作查询', '500', '1', '#', '', '', '', '1', '0', 'F', '0', '0', 'monitor:operlog:query',      '#', 'admin', now(), '', null, '');
insert into sys_menu values('1040', '操作删除', '500', '2', '#', '', '', '', '1', '0', 'F', '0', '0', 'monitor:operlog:remove',     '#', 'admin', now(), '', null, '');
insert into sys_menu values('1041', '日志导出', '500', '3', '#', '', '', '', '1', '0', 'F', '0', '0', 'monitor:operlog:export',     '#', 'admin', now(), '', null, '');
-- 登录日志按钮
insert into sys_menu values('1042', '登录查询', '501', '1', '#', '', '', '', '1', '0', 'F', '0', '0', 'monitor:logininfor:query',   '#', 'admin', now(), '', null, '');
insert into sys_menu values('1043', '登录删除', '501', '2', '#', '', '', '', '1', '0', 'F', '0', '0', 'monitor:logininfor:remove',  '#', 'admin', now(), '', null, '');
insert into sys_menu values('1044', '日志导出', '501', '3', '#', '', '', '', '1', '0', 'F', '0', '0', 'monitor:logininfor:export',  '#', 'admin', now(), '', null, '');
insert into sys_menu values('1045', '账户解锁', '501', '4', '#', '', '', '', '1', '0', 'F', '0', '0', 'monitor:logininfor:unlock',  '#', 'admin', now(), '', null, '');
-- 在线用户按钮
insert into sys_menu values('1046', '在线查询', '109', '1', '#', '', '', '', '1', '0', 'F', '0', '0', 'monitor:online:query',       '#', 'admin', now(), '', null, '');
insert into sys_menu values('1047', '批量强退', '109', '2', '#', '', '', '', '1', '0', 'F', '0', '0', 'monitor:online:batchLogout', '#', 'admin', now(), '', null, '');
insert into sys_menu values('1048', '单条强退', '109', '3', '#', '', '', '', '1', '0', 'F', '0', '0', 'monitor:online:forceLogout', '#', 'admin', now(), '', null, '');
-- 定时任务按钮
insert into sys_menu values('1049', '任务查询', '110', '1', '#', '', '', '', '1', '0', 'F', '0', '0', 'monitor:job:query',          '#', 'admin', now(), '', null, '');
insert into sys_menu values('1050', '任务新增', '110', '2', '#', '', '', '', '1', '0', 'F', '0', '0', 'monitor:job:add',            '#', 'admin', now(), '', null, '');
insert into sys_menu values('1051', '任务修改', '110', '3', '#', '', '', '', '1', '0', 'F', '0', '0', 'monitor:job:edit',           '#', 'admin', now(), '', null, '');
insert into sys_menu values('1052', '任务删除', '110', '4', '#', '', '', '', '1', '0', 'F', '0', '0', 'monitor:job:remove',         '#', 'admin', now(), '', null, '');
insert into sys_menu values('1053', '状态修改', '110', '5', '#', '', '', '', '1', '0', 'F', '0', '0', 'monitor:job:changeStatus',   '#', 'admin', now(), '', null, '');
insert into sys_menu values('1054', '任务导出', '110', '6', '#', '', '', '', '1', '0', 'F', '0', '0', 'monitor:job:export',         '#', 'admin', now(), '', null, '');
-- 代码生成按钮
insert into sys_menu values('1055', '生成查询', '116', '1', '#', '', '', '', '1', '0', 'F', '0', '0', 'tool:gen:query',             '#', 'admin', now(), '', null, '');
insert into sys_menu values('1056', '生成修改', '116', '2', '#', '', '', '', '1', '0', 'F', '0', '0', 'tool:gen:edit',              '#', 'admin', now(), '', null, '');
insert into sys_menu values('1057', '生成删除', '116', '3', '#', '', '', '', '1', '0', 'F', '0', '0', 'tool:gen:remove',            '#', 'admin', now(), '', null, '');
insert into sys_menu values('1058', '导入代码', '116', '4', '#', '', '', '', '1', '0', 'F', '0', '0', 'tool:gen:import',            '#', 'admin', now(), '', null, '');
insert into sys_menu values('1059', '预览代码', '116', '5', '#', '', '', '', '1', '0', 'F', '0', '0', 'tool:gen:preview',           '#', 'admin', now(), '', null, '');
insert into sys_menu values('1060', '生成代码', '116', '6', '#', '', '', '', '1', '0', 'F', '0', '0', 'tool:gen:code',              '#', 'admin', now(), '', null, '');


SELECT setval('sys_menu_menu_id_seq', 2000, false);

-- ----------------------------
-- 初始化-用户和角色关联表数据
-- ----------------------------
insert into sys_user_role values ('1', '1');

-- ----------------------------
-- 初始化字典类型
-- ----------------------------
insert into sys_dict_type values(1,  '用户性别', 'sys_user_sex',        '0', 'admin', now(), '', null, '用户性别列表');
insert into sys_dict_type values(2,  '菜单状态', 'sys_show_hide',       '0', 'admin', now(), '', null, '菜单状态列表');
insert into sys_dict_type values(3,  '系统开关', 'sys_normal_disable',  '0', 'admin', now(), '', null, '系统开关列表');
insert into sys_dict_type values(4,  '项目状态', 'proj_project_status',  '0', 'admin', now(), '', null, '项目状态列表');
insert into sys_dict_type values(5,  '合同状态', 'proj_contract_status',  '0', 'admin', now(), '', null, '合同状态列表');
insert into sys_dict_type values(6,  '成果类型', 'proj_material_result_type',  '0', 'admin', now(), '', null, '资料成果类型');
insert into sys_dict_type values(7,  '资料状态', 'proj_material_status',  '0', 'admin', now(), '', null, '资料领取状态列表');
insert into sys_dict_type values(8,  '任务状态', 'proj_task_status',      '0', 'admin', now(), '', null, '任务执行状态列表');
insert into sys_dict_type values(9,  '付款类型',         'proj_payment_type',             '0', 'admin', now(), '', null, '付款类型列表');
insert into sys_dict_type values(10, '付款到账状态',     'proj_payment_received_status',  '0', 'admin', now(), '', null, '付款到账状态列表');
insert into sys_dict_type values(11, '付款概览状态',     'proj_payment_overview_status',  '0', 'admin', now(), '', null, '付款概览状态列表');
insert into sys_dict_type values(12, '资料提交状态',     'proj_material_submit_status',   '0', 'admin', now(), '', null, '资料提交状态列表');
insert into sys_dict_type values(13, '合同类型',         'proj_contract_type',            '0', 'admin', now(), '', null, '合同类型列表');
insert into sys_dict_type values(14, '合同附件分类',         'proj_attachment_category',            '0', 'admin', now(), '', null, '合同附件的文件归类');


-- ----------------------------
-- 初始化字典数据
-- ----------------------------
-- 用户性别列表
insert into sys_dict_data values(1,  1,  '男',       '0',       'sys_user_sex',        '',   '',        'Y', '0', 'admin', now(), '', null, '性别男');
insert into sys_dict_data values(2,  2,  '女',       '1',       'sys_user_sex',        '',   '',        'N', '0', 'admin', now(), '', null, '性别女');
insert into sys_dict_data values(3,  3,  '未知',     '2',       'sys_user_sex',        '',   '',        'N', '0', 'admin', now(), '', null, '性别未知');
-- 菜单状态列表
insert into sys_dict_data values(4,  1,  '显示',     '0',       'sys_show_hide',       '',   'primary', 'Y', '0', 'admin', now(), '', null, '显示菜单');
insert into sys_dict_data values(5,  2,  '隐藏',     '1',       'sys_show_hide',       '',   'danger',  'N', '0', 'admin', now(), '', null, '隐藏菜单');
-- 系统开关列表
insert into sys_dict_data values(6,  1,  '正常',     '0',       'sys_normal_disable',  '',   'primary', 'Y', '0', 'admin', now(), '', null, '正常状态');
insert into sys_dict_data values(7,  2,  '停用',     '1',       'sys_normal_disable',  '',   'danger',  'N', '0', 'admin', now(), '', null, '停用状态');

-- 项目状态列表
insert into sys_dict_data values(8,  1,  '进行中',   'ongoing',   'proj_project_status', '', 'primary', 'Y', '0', 'admin', now(), '', null, '进行中状态');
insert into sys_dict_data values(9,  2,  '已办结',   'closed',    'proj_project_status', '', 'success', 'N', '0', 'admin', now(), '', null, '已办结状态');
insert into sys_dict_data values(10, 3,  '已归档',   'archived',  'proj_project_status', '', '',        'N', '0', 'admin', now(), '', null, '已归档状态');

-- 合同状态列表
insert into sys_dict_data values(14,  1,  '草稿',     'draft',       'proj_contract_status',     '',   'info', 'Y', '0', 'admin', now(), '', null, '草稿状态');
insert into sys_dict_data values(15,  2,  '已签署',     'signed',       'proj_contract_status',     '',   'primary', 'N', '0', 'admin', now(), '', null, '已签署状态');
insert into sys_dict_data values(16,  3,  '执行中',     'ongoing',       'proj_contract_status',     '',   'warning', 'N', '0', 'admin', now(), '', null, '执行中状态');
insert into sys_dict_data values(17,  4,  '已完成',     'completed',       'proj_contract_status',     '',   'success', 'N', '0', 'admin', now(), '', null, '已完成状态');
insert into sys_dict_data values(18,  5,  '已归档',     'archived',       'proj_contract_status',     '',   '', 'N', '0', 'admin', now(), '', null, '已归档状态');
insert into sys_dict_data values(29, 6,  '已取消',     'cancelled',       'proj_contract_status',     '',   'danger', 'N', '0', 'admin', now(), '', null, '已取消状态');

-- 资料状态列表（待领取/已领取/已归还）
insert into sys_dict_data values(22, 1,  '待领取',   'pending',   'proj_material_status', '', 'info',    'Y', '0', 'admin', now(), '', null, '待领取状态');
insert into sys_dict_data values(23, 2,  '已领取',   'received',  'proj_material_status', '', 'primary', 'N', '0', 'admin', now(), '', null, '已领取状态');
insert into sys_dict_data values(24, 3,  '已归还',   'returned',  'proj_material_status', '', 'success', 'N', '0', 'admin', now(), '', null, '已归还状态');

-- 任务状态列表（待开始/进行中/已完成/已暂停）
insert into sys_dict_data values(25, 1,  '待开始',   'pending',    'proj_task_status', '', 'info',     'N', '0', 'admin', now(), '', null, '待开始状态');
insert into sys_dict_data values(26, 2,  '进行中',   'ongoing',    'proj_task_status', '', 'primary',  'Y', '0', 'admin', now(), '', null, '进行中状态');
insert into sys_dict_data values(27, 3,  '已完成',   'completed',  'proj_task_status', '', 'success',  'N', '0', 'admin', now(), '', null, '已完成状态');
insert into sys_dict_data values(28, 4,  '已暂停',   'paused',     'proj_task_status', '', 'warning',  'N', '0', 'admin', now(), '', null, '已暂停状态');

-- 资料成果类型列表
insert into sys_dict_data values(19, 1, '纸质',     'paper',         'proj_material_result_type', '', '', 'N', '0', 'admin', now(), '', null, '纸质成果');
insert into sys_dict_data values(20, 2, '电子',     'digital',       'proj_material_result_type', '', '', 'N', '0', 'admin', now(), '', null, '电子成果');
insert into sys_dict_data values(21, 3, '纸质+电子', 'paper_digital',  'proj_material_result_type', '', '', 'N', '0', 'admin', now(), '', null, '纸质+电子成果');

-- 付款类型列表
insert into sys_dict_data values(30, 1, '预付款',   'advance',   'proj_payment_type', '', 'info',   'Y', '0', 'admin', now(), '', null, '预付款');
insert into sys_dict_data values(31, 2, '进度款',   'progress',  'proj_payment_type', '', 'primary','N', '0', 'admin', now(), '', null, '进度款');
insert into sys_dict_data values(32, 3, '尾款',    'final',     'proj_payment_type', '', 'success','N', '0', 'admin', now(), '', null, '尾款');

-- 付款到账状态列表
insert into sys_dict_data values(33, 1, '待收款',   'pending',   'proj_payment_received_status', '', 'warning',  'Y', '0', 'admin', now(), '', null, '待收款');
insert into sys_dict_data values(34, 2, '已到账',   'received',  'proj_payment_received_status', '', 'success',  'N', '0', 'admin', now(), '', null, '已到账');

-- 付款概览状态列表（SQL 计算字段，前端筛选用）
insert into sys_dict_data values(35, 1, '未付款',   'unpaid',    'proj_payment_overview_status', '', 'danger',   'Y', '0', 'admin', now(), '', null, '未付款');
insert into sys_dict_data values(36, 2, '部分付款', 'partial',   'proj_payment_overview_status', '', 'warning',  'N', '0', 'admin', now(), '', null, '部分付款');
insert into sys_dict_data values(37, 3, '已结清',   'settled',   'proj_payment_overview_status', '', 'success',  'N', '0', 'admin', now(), '', null, '已结清');

-- 资料提交状态列表
insert into sys_dict_data values(38, 1, '待提交',   'pending',   'proj_material_submit_status',  '', 'warning',  'Y', '0', 'admin', now(), '', null, '待提交');
insert into sys_dict_data values(39, 2, '已提交',   'submitted', 'proj_material_submit_status',  '', 'success',  'N', '0', 'admin', now(), '', null, '已提交');

-- 合同类型列表
insert into sys_dict_data values(40, 1, '勘察合同',   'survey',        'proj_contract_type', '', '',       'Y', '0', 'admin', now(), '', null, '勘察合同');
insert into sys_dict_data values(41, 2, '测绘合同',   'mapping',       'proj_contract_type', '', '',       'N', '0', 'admin', now(), '', null, '测绘合同');
insert into sys_dict_data values(42, 3, '设计合同',   'design',        'proj_contract_type', '', '',       'N', '0', 'admin', now(), '', null, '设计合同');
insert into sys_dict_data values(43, 4, '施工合同',   'construction',  'proj_contract_type', '', '',       'N', '0', 'admin', now(), '', null, '施工合同');
insert into sys_dict_data values(44, 5, '其他合同',   'other',         'proj_contract_type', '', '',       'N', '0', 'admin', now(), '', null, '其他合同');

-- 合同附件分类列表
insert into sys_dict_data values(45, 1, '合同正本',   'contract',        'proj_attachment_category', '', '',       'N', '0', 'admin', now(), '', null, '合同正本');
insert into sys_dict_data values(46, 2, '补充协议',   'supplement',       'proj_attachment_category', '', '',       'N', '0', 'admin', now(), '', null, '补充协议');
insert into sys_dict_data values(47, 3, '其它',   'other',       'proj_attachment_category', '', '',       'N', '0', 'admin', now(), '', null, '其它');


-- ----------------------------
-- 参数配置表
-- ----------------------------
insert into sys_config values(1, '主框架页-默认皮肤样式名称',     'sys.index.skinName',               'skin-blue',     'Y', 'admin', now(), '', null, '蓝色 skin-blue、绿色 skin-green、紫色 skin-purple、红色 skin-red、黄色 skin-yellow' );
insert into sys_config values(2, '用户管理-账号初始密码',         'sys.user.initPassword',            '123456',        'Y', 'admin', now(), '', null, '初始化密码 123456' );
insert into sys_config values(3, '主框架页-侧边栏主题',           'sys.index.sideTheme',              'theme-dark',    'Y', 'admin', now(), '', null, '深色主题theme-dark，浅色主题theme-light' );
insert into sys_config values(4, '账号自助-验证码开关',           'sys.account.captchaEnabled',       'false',          'Y', 'admin', now(), '', null, '是否开启验证码功能（true开启，false关闭）');
insert into sys_config values(5, '账号自助-是否开启用户注册功能', 'sys.account.registerUser',         'false',         'Y', 'admin', now(), '', null, '是否开启注册用户功能（true开启，false关闭）');
insert into sys_config values(6, '用户登录-黑名单列表',           'sys.login.blackIPList',            '',              'Y', 'admin', now(), '', null, '设置登录IP黑名单限制，多个匹配项以;分隔，支持匹配（*通配、网段）');
insert into sys_config values(7, '用户管理-初始密码修改策略',     'sys.account.initPasswordModify',   '1',             'Y', 'admin', now(), '', null, '0：初始密码修改策略关闭，没有任何提示，1：提醒用户，如果未修改初始密码，则在登录时就会提醒修改密码对话框');
insert into sys_config values(8, '用户管理-账号密码更新周期',     'sys.account.passwordValidateDays', '0',             'Y', 'admin', now(), '', null, '密码更新周期（填写数字，数据初始化值为0不限制，若修改必须为大于0小于365的正整数），如果超过这个周期登录系统时，则在登录时就会提醒修改密码对话框');

SELECT setval('sys_config_config_id_seq', 100, false);
