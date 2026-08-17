-- =============================================
-- 项目信息管理系统 — 内置报表模板录入（2 个）
-- 前置：已执行 9_create_report_tables.sql
-- =============================================

-- 模板 1：只定未验及补之前扣除项目（zdyw_report.xls）
-- 结构：标题行1 / 表头行2-3 / 数据自第4行 / 列12个
INSERT INTO proj_report_template (id, template_name, template_type, subject_table,
    source_template_id, template_file, file_name, title_row, header_row, data_start_row,
    has_summary_row, default_filter, remark, del_flag, create_by, create_time)
VALUES (1, '只定未验及补之前扣除项目', 'builtin', 'proj_project', NULL,
    'classpath:reportTemplates/zdyw_report.xls',
    '地下空间工程中心-{year}年{month}月只定未验及补之前扣除项目.xls',
    1, 2, 4, 'Y', NULL, '客户模板：管线定验线报表（序号/单位/系统编号/上报时间/归档/到账/备注）',
    '0', 'admin', now());

INSERT INTO proj_report_field (template_id, field_key, field_label, field_source, join_table, sort_order, width, column_index, del_flag) VALUES
(1, 'rowNo', '序号', 'agg', NULL, 1, 8, 1, '0'),
(1, 'clientUnit', '单位名称', 'subject', NULL, 2, 30, 2, '0'),
(1, 'projectCode', '系统编号', 'subject', NULL, 3, 14, 3, '0'),
(1, 'submitTimeYm', '上报时间', 'subject', NULL, 4, 12, 4, '0'),
(1, 'verifySystemNo', '系统编号', 'subject', NULL, 5, 14, 5, '0'),
(1, 'verifyReportTime', '上报时间', 'subject', NULL, 6, 12, 6, '0'),
(1, 'archiveDate', '归档情况', 'join', 'proj_contract', 7, 12, 7, '0'),
(1, 'receivedAmount', '到账金额', 'agg', NULL, 8, 12, 8, '0'),
(1, 'lastPayTime', '到账时间', 'agg', NULL, 9, 14, 9, '0'),
(1, 'reservedAmount', '需预留', 'agg', NULL, 10, 12, 10, '0'),
(1, 'needSupplement', '需补', 'agg', NULL, 11, 12, 11, '0'),
(1, 'remark', '备注', 'subject', NULL, 12, 20, 12, '0');


-- 2. 模板 1「只定未验及补之前扣除项目」：管线定线(列3-4) / 管线验线(列5-6) / 验线（2/3）(列10-11)
UPDATE proj_report_field SET header_group = '管线定线'
WHERE template_id = 1 AND column_index IN (3, 4) AND del_flag = '0';

UPDATE proj_report_field SET header_group = '管线验线'
WHERE template_id = 1 AND column_index IN (5, 6) AND del_flag = '0';

UPDATE proj_report_field SET header_group = '验线（2/3）'
WHERE template_id = 1 AND column_index IN (10, 11) AND del_flag = '0';

-- 模板 2：全院应收帐款统计表（yszk_report.xlsx）
-- 结构：标题行1 / 表头行3-4 / 数据自第5行 / 列19个
INSERT INTO proj_report_template (id, template_name, template_type, subject_table,
    source_template_id, template_file, file_name, title_row, header_row, data_start_row,
    has_summary_row, default_filter, remark, del_flag, create_by, create_time)
VALUES (2, '全院应收帐款统计表', 'builtin', 'proj_project', NULL,
    'classpath:reportTemplates/yszk_report.xlsx',
    '地下空间工程中心-{year}年{month}月全院应收帐款统计表.xlsx',
    1, 3, 5, 'Y', NULL, '客户模板：应收帐款及合同情况统计（合同/回款/发票/责任人/账期/措施）',
    '0', 'admin', now());

INSERT INTO proj_report_field (template_id, field_key, field_label, field_source, join_table, sort_order, width, column_index, del_flag) VALUES
(2, 'rowNo', '序号', 'agg', NULL, 1, 8, 1, '0'),
(2, 'clientUnit', '客户全称', 'subject', NULL, 3, 30, 3, '0'),
(2, 'projectName', '项目名称', 'subject', NULL, 4, 30, 4, '0'),
(2, 'contractAmount', '合同金额', 'join', 'proj_contract', 5, 14, 5, '0'),
(2, 'contractNo', '合同编号', 'join', 'proj_contract', 6, 16, 6, '0'),
(2, 'signDate', '签订时间', 'join', 'proj_contract', 7, 12, 7, '0'),
(2, 'finishDate', '完工时间', 'join', 'proj_contract', 8, 12, 8, '0'),
(2, 'receivedAmount', '已回款', 'agg', NULL, 9, 14, 9, '0'),
(2, 'pendingAmount', '合同未收金额', 'agg', NULL, 10, 14, 10, '0'),
(2, 'invoiceFlag', '是否开票', 'agg', NULL, 11, 10, 11, '0'),
(2, 'totalInvoiceAmount', '开票金额', 'agg', NULL, 12, 14, 12, '0'),
(2, 'leaderName', '项目责任人', 'join', NULL, 13, 12, 13, '0'),
(2, 'debtMonths', '应收账期(欠款时长/月)', 'agg', NULL, 14, 14, 14, '0'),
(2, 'remark', '未到账原因及应对措施', 'subject', NULL, 17, 30, 17, '0');

-- 模板 2「全院应收帐款统计表」：合同情况(列5-8) / 回款情况(列9-10) / 发票开具情况(列11-12)
UPDATE proj_report_field SET header_group = '合同情况'
WHERE template_id = 2 AND column_index IN (5, 6, 7, 8) AND del_flag = '0';

UPDATE proj_report_field SET header_group = '回款情况'
WHERE template_id = 2 AND column_index IN (9, 10) AND del_flag = '0';

UPDATE proj_report_field SET header_group = '发票开具情况'
WHERE template_id = 2 AND column_index IN (11, 12) AND del_flag = '0';
-- 模板 3：应收账款与客户对账情况统计表（yhdz_report.xlsx）
-- 来源文件：地下空间工程中心《应收账款与客户对账情况统计表》（2026.7.31）.xlsx
-- 结构：标题行1 / 表头行3-4（两级表头）/ 数据自第5行 / 合计行556
-- 列：A~N（14列），其中 4 列无对应字段池（所属部门/是否对账/是否发函/对账时间）
INSERT INTO proj_report_template (id, template_name, template_type, subject_table,
                                  source_template_id, template_file, file_name, title_row, header_row, data_start_row,
                                  has_summary_row, default_filter, remark, del_flag, create_by, create_time)
VALUES (3, '应收账款与客户对账情况统计表', 'builtin', 'proj_project', NULL,
        'classpath:reportTemplates/yhdz_report.xlsx',
        '地下空间工程中心《{year}年{month}月应收账款与客户对账情况统计表》.xlsx',
        1, 3, 5, 'Y', NULL, '客户模板：应收账款对账统计（合同/欠款/发票/账期/对账情况）',
        '0', 'admin', now());

INSERT INTO proj_report_field (template_id, field_key, field_label, field_source, join_table, sort_order, width, column_index, del_flag) VALUES
     (3, 'rowNo', '序号', 'agg', NULL, 1, 8, 1, '0'),
     (3, 'deptName', '所属部门',       'agg',     NULL,            2, 20, 2, '0'),
     (3, 'clientUnit', '客户全称', 'subject', NULL, 3, 30, 3, '0'),
     (3, 'projectName', '项目名称', 'subject', NULL, 4, 30, 4, '0'),
     (3, 'contractAmount', '合同金额', 'join', 'proj_contract', 5, 14, 5, '0'),
     (3, 'contractNo', '合同编号', 'join', 'proj_contract', 6, 16, 6, '0'),
     (3, 'pendingAmount', '欠款金额', 'agg', NULL, 7, 14, 7, '0'),
     (3, 'invoiceFlag', '是否开票', 'agg', NULL, 8, 10, 8, '0'),
     (3, 'totalInvoiceAmount', '开票金额', 'agg', NULL, 9, 14, 9, '0'),
     (3, 'debtMonths', '应收账期(欠款时长/月)', 'agg', NULL, 10, 14, 10, '0'),
     (3, 'pendingAmount', '欠款金额', 'agg', NULL, 11, 14, 13, '0');

-- 多级表头分组
UPDATE proj_report_field SET header_group = '合同及欠款情况'
WHERE template_id = 3 AND column_index IN (5, 6, 7) AND del_flag = '0';

UPDATE proj_report_field SET header_group = '发票开具情况'
WHERE template_id = 3 AND column_index IN (8, 9) AND del_flag = '0';


-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
-- 模板 4：市场性任务到账收入确认表（scsr_report.xls）
-- 来源文件：地下空间工程中心-2026年7月市场性任务到账收入确认表（0626-0729）.xls
-- 注意：该文件有 3 个 Sheet，系统使用第 1 个 Sheet「1定验线上报」
-- 结构：标题行1 / 表头行2-3（两级表头）/ 数据自第4行
-- 列：A~Q（17列），其中 5 列无对应字段池
-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

INSERT INTO proj_report_template (id, template_name, template_type, subject_table,
                                  source_template_id, template_file, file_name, title_row, header_row, data_start_row,
                                  has_summary_row, default_filter, remark, del_flag, create_by, create_time)
VALUES (4, '市场性任务到账收入确认表', 'builtin', 'proj_project', NULL,
        'classpath:reportTemplates/scsr_report.xls',
        '{year}年{month}月市场性任务到账收入确认表.xls',
        1, 2, 4, 'N', NULL,
        '客户模板：月度市场性任务到账收入确认（基础信息/任务详情/财务信息）',
        '0', 'admin', now());

-- 字段映射
INSERT INTO proj_report_field (template_id, field_key, field_label, field_source, join_table, sort_order, width, column_index, del_flag) VALUES
  (4, 'rowNo',           '序号',         'agg',     NULL,            1, 8,  1, '0'),
  (4, 'categoryName',    '项目类别',     'join',    NULL,            2, 12, 2, '0'),
  (4, 'clientUnit',      '委托单位',     'subject', NULL,            3, 30, 3, '0'),
  (4, 'projectCode',     '生产系统编号', 'subject', NULL,            4, 14, 4, '0'),
  (4, 'projectLocation', '项目地点',     'subject', NULL,            5, 20, 5, '0'),
  (4, 'archiveDate',     '归档情况',     'join',    'proj_contract', 6, 12, 6, '0'),
  (4, 'totalDuration',   '项目工期',     'subject', NULL,            7, 12, 9, '0'),
  (4, 'contractAmount',  '项目金额',     'join',    'proj_contract', 8, 14, 12, '0'),
  (4, 'receivedAmount',  '到账金额',     'agg',     NULL,            9, 14, 13, '0'),
  (4, 'lastPayTime',     '到账时间',     'agg',     NULL,            10, 14, 14, '0'),
  (4, 'invoiceFlag',     '发票情况',     'agg',     NULL,            11, 10, 15, '0'),
  (4, 'remark',          '备注',         'subject', NULL,            12, 20, 17, '0');
-- 未映射列：G(7)入库情况、H(8)项目工作量、J(10)质量情况、K(11)安全事故记录、P(16)到账确认

-- 多级表头分组
UPDATE proj_report_field SET header_group = '基础信息'
WHERE template_id = 4 AND column_index IN (2, 3, 4, 5, 6) AND del_flag = '0';

UPDATE proj_report_field SET header_group = '任务详情'
WHERE template_id = 4 AND column_index IN (9) AND del_flag = '0';

UPDATE proj_report_field SET header_group = '财务信息'
WHERE template_id = 4 AND column_index IN (12, 13, 14, 15) AND del_flag = '0';

-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
-- 模板 5：验线上报产值统计表（yscb_report.xlsx）
-- 来源文件：地下空间工程中心-2026年7月验线上报产值部分按.xlsx
-- 结构：标题行2 / 表头行3-5（三级表头，系统按两级处理）/ 数据自第6行
-- 列：A~R（18列），其中 8 列无对应字段池
-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

INSERT INTO proj_report_template (id, template_name, template_type, subject_table,
                                  source_template_id, template_file, file_name, title_row, header_row, data_start_row,
                                  has_summary_row, default_filter, remark, del_flag, create_by, create_time)
VALUES (5, '验线上报产值统计表', 'builtin', 'proj_project', NULL,
        'classpath:reportTemplates/yscb_report.xlsx',
        '{year}年{month}月验线上报产值统计表.xlsx',
        2, 3, 6, 'N', NULL,
        '客户模板：验线上报产值统计（外部收费/结算方式/内部工作量）',
        '0', 'admin', now());

-- 字段映射
INSERT INTO proj_report_field (template_id, field_key, field_label, field_source, join_table, sort_order, width, column_index, del_flag) VALUES
  (5, 'rowNo',           '序号',     'agg',     NULL,            1, 8,  1, '0'),
  (5, 'categoryName',    '项目类别', 'join',    NULL,            2, 12, 2, '0'),
  (5, 'clientUnit',      '委托单位', 'subject', NULL,            3, 30, 3, '0'),
  (5, 'projectCode',     '系统编号', 'subject', NULL,            4, 14, 4, '0'),
  (5, 'archiveDate',     '归档情况', 'join',    'proj_contract', 5, 12, 5, '0'),
  (5, 'projectLocation', '建设地点', 'subject', NULL,            6, 20, 6, '0'),
  (5, 'contractAmount',  '工程总价', 'join',    'proj_contract', 7, 14, 8, '0'),
  (5, 'receivedAmount',  '到账金额', 'agg',     NULL,            8, 14, 9, '0'),
  (5, 'lastPayTime',     '到账时间', 'agg',     NULL,            9, 14, 10, '0'),
  (5, 'remark',          '备注(定线号)', 'subject', NULL,        10, 20, 18, '0');
-- 未映射列：G(7)工作量、K(11)银行、L(12)现金、M(13)POS、N(14)内部工作量、O(15)工程、P(16)定验已收外部、Q(17)*2/3

-- 多级表头分组
UPDATE proj_report_field SET header_group = '外部收费'
WHERE template_id = 5 AND column_index IN (8, 9, 10) AND del_flag = '0';


-- 模板 6：补验线报表（byx_report.xls）
-- 结构：标题行1 / 表头行2-3 / 数据自第4行 / 列12个
INSERT INTO proj_report_template (id, template_name, template_type, subject_table,
                                  source_template_id, template_file, file_name, title_row, header_row, data_start_row,
                                  has_summary_row, default_filter, remark, del_flag, create_by, create_time)
VALUES (6, '补验线', 'builtin', 'proj_project', NULL,
        'classpath:reportTemplates/byx_report.xls',
        '地下空间工程中心-{year}年{month}月补验线.xls',
        1, 2, 4, 'Y', NULL, '客户模板：补验线报表',
        '0', 'admin', now());

INSERT INTO proj_report_field (template_id, field_key, field_label, field_source, join_table, sort_order, width, column_index, del_flag) VALUES
    (6, 'rowNo', '序号', 'agg', NULL, 1, 8, 1, '0'),
    (6, 'clientUnit', '单位名称', 'subject', NULL, 2, 30, 2, '0'),
    (6, 'relatedProjectCode', '系统编号', 'subject', NULL, 3, 14, 3, '0'),
    (6, 'relatedLastSubmitTime', '上报时间', 'subject', NULL, 4, 12, 4, '0'),
    (6, 'projectCode', '系统编号', 'subject', NULL, 5, 14, 5, '0'),
    (6, 'closeTime', '上报时间', 'subject', NULL, 6, 12, 6, '0'),
    (6, 'archiveDate', '归档情况', 'join', 'proj_contract', 7, 12, 7, '0'),
    (6, 'receivedAmount', '到账金额', 'agg', NULL, 8, 12, 8, '0'),
    (6, 'lastPayTime', '到账时间', 'agg', NULL, 9, 14, 9, '0'),
    (6, 'reservedAmount', '需预留', 'agg', NULL, 10, 12, 10, '0'),
    (6, 'needSupplement', '需补', 'agg', NULL, 11, 12, 11, '0'),
    (6, 'remark', '备注', 'subject', NULL, 12, 20, 12, '0');

UPDATE proj_report_field SET header_group = '管线定线'
WHERE template_id = 6 AND column_index IN (3, 4) AND del_flag = '0';

UPDATE proj_report_field SET header_group = '管线验线'
WHERE template_id = 6 AND column_index IN (5, 6) AND del_flag = '0';

UPDATE proj_report_field SET header_group = '验线（2/3）'
WHERE template_id = 6 AND column_index IN (10, 11) AND del_flag = '0';

