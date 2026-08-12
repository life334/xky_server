-- ============================================================
-- 项目信息管理系统 — 工作日历表 + 2026 内置数据 + 菜单
-- 用途：项目总时长（工作日）自动计算的基础数据
-- 日类型约定：
--   holiday  = 法定节假日休息（原本是工作日，放假）
--   workday  = 调休上班日（原本是周末，调休上班）
--   weekend  = 周末休息（默认基线，由"生成"按钮批量产生）
-- 判定规则（前端/后端一致）：
--   有记录：holiday/weekend → 休息；workday → 上班
--   无记录：周一~周五上班，周六~周日休息（降级兜底）
-- 说明：某年没有任何记录时，自动降级为"仅排除周末"的算法，
--       不会报错；维护后节假日立即生效。
-- ============================================================

-- ----------------------------
-- 1、工作日历表
-- ----------------------------
DROP TABLE IF EXISTS proj_workday_calendar;
CREATE TABLE proj_workday_calendar (
    day                 DATE            NOT NULL,
    day_type            VARCHAR(10)     DEFAULT 'weekend' NOT NULL,
    remark              VARCHAR(200)    DEFAULT '',
    create_by           VARCHAR(64)     DEFAULT '',
    create_time         TIMESTAMP,
    update_by           VARCHAR(64)     DEFAULT '',
    update_time         TIMESTAMP,
    PRIMARY KEY (day)
);

COMMENT ON TABLE proj_workday_calendar IS '工作日历表（每天一条，记录节假日/调休上班日及周末基线）';
COMMENT ON COLUMN proj_workday_calendar.day IS '日期（主键）';
COMMENT ON COLUMN proj_workday_calendar.day_type IS '日类型（holiday=法定节假日休息 workday=调休上班日 weekend=周末休息基线）';
COMMENT ON COLUMN proj_workday_calendar.remark IS '备注（如：春节假期、国庆调休等）';

CREATE INDEX idx_proj_wd_type ON proj_workday_calendar(day_type);

-- ----------------------------
-- 2、2026 年内置数据（国务院办公厅《关于2026年部分节假日安排的通知》）
--    共 33 天休息 + 6 天调休上班
-- ----------------------------

-- 2.1 法定节假日休息（33 天）
INSERT INTO proj_workday_calendar (day, day_type, remark, create_by, create_time) VALUES
('2026-01-01', 'holiday', '元旦', 'admin', now()),
('2026-01-02', 'holiday', '元旦', 'admin', now()),
('2026-01-03', 'holiday', '元旦', 'admin', now()),
('2026-02-15', 'holiday', '春节', 'admin', now()),
('2026-02-16', 'holiday', '春节', 'admin', now()),
('2026-02-17', 'holiday', '春节', 'admin', now()),
('2026-02-18', 'holiday', '春节', 'admin', now()),
('2026-02-19', 'holiday', '春节', 'admin', now()),
('2026-02-20', 'holiday', '春节', 'admin', now()),
('2026-02-21', 'holiday', '春节', 'admin', now()),
('2026-02-22', 'holiday', '春节', 'admin', now()),
('2026-02-23', 'holiday', '春节', 'admin', now()),
('2026-04-04', 'holiday', '清明节', 'admin', now()),
('2026-04-05', 'holiday', '清明节', 'admin', now()),
('2026-04-06', 'holiday', '清明节', 'admin', now()),
('2026-05-01', 'holiday', '劳动节', 'admin', now()),
('2026-05-02', 'holiday', '劳动节', 'admin', now()),
('2026-05-03', 'holiday', '劳动节', 'admin', now()),
('2026-05-04', 'holiday', '劳动节', 'admin', now()),
('2026-05-05', 'holiday', '劳动节', 'admin', now()),
('2026-06-19', 'holiday', '端午节', 'admin', now()),
('2026-06-20', 'holiday', '端午节', 'admin', now()),
('2026-06-21', 'holiday', '端午节', 'admin', now()),
('2026-09-25', 'holiday', '中秋节', 'admin', now()),
('2026-09-26', 'holiday', '中秋节', 'admin', now()),
('2026-09-27', 'holiday', '中秋节', 'admin', now()),
('2026-10-01', 'holiday', '国庆节', 'admin', now()),
('2026-10-02', 'holiday', '国庆节', 'admin', now()),
('2026-10-03', 'holiday', '国庆节', 'admin', now()),
('2026-10-04', 'holiday', '国庆节', 'admin', now()),
('2026-10-05', 'holiday', '国庆节', 'admin', now()),
('2026-10-06', 'holiday', '国庆节', 'admin', now()),
('2026-10-07', 'holiday', '国庆节', 'admin', now());

-- 2.2 调休上班日（6 天，均为周末）
INSERT INTO proj_workday_calendar (day, day_type, remark, create_by, create_time) VALUES
('2026-01-04', 'workday', '元旦调休上班', 'admin', now()),
('2026-02-14', 'workday', '春节调休上班', 'admin', now()),
('2026-02-28', 'workday', '春节调休上班', 'admin', now()),
('2026-05-09', 'workday', '劳动节调休上班', 'admin', now()),
('2026-09-20', 'workday', '国庆调休上班', 'admin', now()),
('2026-10-10', 'workday', '国庆调休上班', 'admin', now());

-- 2.3 2026 年周末基线（全年周六/周日，ON CONFLICT 幂等，可重复执行；不写备注）
INSERT INTO proj_workday_calendar (day, day_type, remark, create_by, create_time)
SELECT d::date, 'weekend', NULL, 'admin', now()
FROM generate_series('2026-01-01'::date, '2026-12-31'::date, '1 day') AS d
WHERE EXTRACT(ISODOW FROM d) IN (6, 7)
ON CONFLICT (day) DO NOTHING;

-- ----------------------------
-- 3、菜单：系统管理 → 工作日历
-- ----------------------------
INSERT INTO sys_menu VALUES (1200, '工作日历', 1, 10, 'workday', 'system/workday/index', '', 'Workday', 1, 0, 'C', '0', '0', 'system:workday:list', 'date', 'admin', now(), '', NULL, '工作日历菜单（节假日/调休维护）');
INSERT INTO sys_menu VALUES (1201, '工作日历查询', 1200, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'system:workday:query', '#', 'admin', now(), '', NULL, '');
INSERT INTO sys_menu VALUES (1202, '工作日历新增', 1200, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'system:workday:add', '#', 'admin', now(), '', NULL, '');
INSERT INTO sys_menu VALUES (1203, '工作日历修改', 1200, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'system:workday:edit', '#', 'admin', now(), '', NULL, '');
INSERT INTO sys_menu VALUES (1204, '工作日历删除', 1200, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'system:workday:remove', '#', 'admin', now(), '', NULL, '');
INSERT INTO sys_menu VALUES (1205, '工作日历生成', 1200, 5, '', '', '', '', 1, 0, 'F', '0', '0', 'system:workday:generate', '#', 'admin', now(), '', NULL, '');
INSERT INTO sys_menu VALUES (1206, '工作日历批量录入', 1200, 6, '', '', '', '', 1, 0, 'F', '0', '0', 'system:workday:batch', '#', 'admin', now(), '', NULL, '');

-- ============================================================
-- 汇总：proj_workday_calendar 表 + 2026 年 39 条节假日/调休记录
--       + 全年周末基线（约 104 条）+ 菜单 7 条
-- ============================================================
