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
