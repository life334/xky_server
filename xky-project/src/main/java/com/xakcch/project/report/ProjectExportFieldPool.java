package com.xakcch.project.report;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 项目导出字段池（唯一出处）——项目列表导出 / 费用结算导出 **共用同一份清单**。
 *
 * <p>需求（2026-11-11）：两个页面的导出可选字段必须一致，无论在哪个页面导出，
 * 用户想要的字段都能选到、且都有值。此前两页各自维护一份字段清单
 * （项目列表 = {@code ProjProjectServiceImpl#getListColumns()} 的页面列；
 * 结算 = {@code ProjSettlementController#buildColumnMeta()} 的页面列），
 * 字段互有缺漏。现把「导出字段」与「页面表格列」解耦：
 * <ul>
 *   <li>页面表格列：各自维持原状（显隐面板、表格渲染不受影响）</li>
 *   <li>导出字段：一律以此处为准（弹窗选项 / 表头 / 取值口径）</li>
 * </ul>
 *
 * <p>⚠️ 只纳入业务字段。系统字段（ID/创建人/创建时间/更新人/更新时间）与
 *    项目动态字段暂不纳入（2026-11-11 用户确认）。
 *
 * <p>⚠️ 行粒度不由本类决定：项目列表 = 一项目一行（人级字段合并成多值/合计），
 *    费用结算 = 一负责人一行 —— 两页保持各自现有语义。
 */
public final class ProjectExportFieldPool
{
    /** 页面标识：项目列表 */
    public static final String PAGE_PROJECT = "project";
    /** 页面标识：费用结算 */
    public static final String PAGE_SETTLEMENT = "settlement";

    /** 分组：项目信息 */
    public static final String GROUP_PROJECT = "project";
    /** 分组：合同 */
    public static final String GROUP_CONTRACT = "contract";
    /** 分组：工作量与产值 */
    public static final String GROUP_WORKLOAD = "workload";
    /** 分组：结算付款 */
    public static final String GROUP_SETTLEMENT = "settlement";

    /** 单个字段定义（不可变） */
    public static final class Field
    {
        private final String key;
        private final String label;
        /** 列类型：text / dict / date / number / money / duration / total */
        private final String type;
        private final String group;
        private final int width;
        /** 字典类型（type=dict 时必填） */
        private final String dictType;
        private final boolean defaultProject;
        private final boolean defaultSettlement;

        private Field(String key, String label, String type, String group, int width,
                      String dictType, boolean defaultProject, boolean defaultSettlement)
        {
            this.key = key;
            this.label = label;
            this.type = type;
            this.group = group;
            this.width = width;
            this.dictType = dictType;
            this.defaultProject = defaultProject;
            this.defaultSettlement = defaultSettlement;
        }

        public String getKey() { return key; }

        public String getLabel() { return label; }

        public String getType() { return type; }

        public String getGroup() { return group; }

        public int getWidth() { return width; }

        public String getDictType() { return dictType; }

        /** 该页面默认是否勾选 */
        public boolean isDefaultVisible(String page)
        {
            return PAGE_SETTLEMENT.equals(page) ? defaultSettlement : defaultProject;
        }

        public boolean isNumeric()
        {
            return "number".equals(type) || "money".equals(type);
        }
    }

    /** 已选字段 key → 定义 */
    private static final Map<String, Field> BY_KEY;

    /** 全量字段（顺序即弹窗默认展示顺序） */
    private static final List<Field> FIELDS;

    private static final Set<String> KEYS;

    static
    {
        List<Field> list = new ArrayList<>();
        // ==================== 项目信息 ====================
        add(list, "projectCode", "工程编号", "text", GROUP_PROJECT, 18, null, true, true);
        add(list, "projectName", "项目名称", "text", GROUP_PROJECT, 28, null, false, false);
        add(list, "clientUnit", "委托单位", "text", GROUP_PROJECT, 26, null, true, true);
        add(list, "clientUnitNature", "委托单位性质", "dict", GROUP_PROJECT, 16, "proj_client_unit_nature", true, false);
        add(list, "contactName", "联系人", "text", GROUP_PROJECT, 14, null, true, false);
        add(list, "contactPhone", "联系电话", "text", GROUP_PROJECT, 18, null, true, false);
        add(list, "projectLocation", "工程地点", "text", GROUP_PROJECT, 24, null, true, true);
        // 项目类别：值 = 类别表规范名 categoryName，为空回退文本列 engineering_project
        //（与 2026-11-11 项目列表显示/导出口径统一，见 ProjProjectServiceImpl#formatValue）
        add(list, "categoryName", "项目类别", "text", GROUP_PROJECT, 18, null, true, true);
        add(list, "status", "状态", "dict", GROUP_PROJECT, 12, "proj_project_status", true, false);
        add(list, "projectNature", "项目性质", "dict", GROUP_PROJECT, 14, "proj_project_nature", true, false);
        add(list, "closeTime", "办结日期", "date", GROUP_PROJECT, 14, null, true, false);
        add(list, "assignDate", "安排日期", "date", GROUP_PROJECT, 14, null, true, false);
        add(list, "durationRequire", "工期要求", "duration", GROUP_PROJECT, 12, null, true, false);
        add(list, "totalDuration", "总时长", "total", GROUP_PROJECT, 12, null, true, false);
        add(list, "relatedProjectCode", "关联定线编号", "text", GROUP_PROJECT, 18, null, true, false);
        add(list, "dataSource", "项目来源", "dict", GROUP_PROJECT, 12, "proj_project_source", false, false);
        add(list, "remark", "项目备注", "text", GROUP_PROJECT, 24, null, false, false);
        // ==================== 合同 ====================
        add(list, "contractName", "合同", "text", GROUP_CONTRACT, 26, null, false, false);
        add(list, "contractNo", "合同编号", "text", GROUP_CONTRACT, 20, null, false, false);
        add(list, "contractAmount", "合同金额", "number", GROUP_CONTRACT, 16, null, false, false);
        // ==================== 工作量与产值 ====================
        // 项目负责人：proj_leader 汇总（多值逗号分隔）；与结算的「登记人」不是同一概念
        add(list, "leaderNames", "项目负责人", "text", GROUP_WORKLOAD, 18, null, true, false);
        // 登记人：工作量登记人（结算页按它拆行）。项目列表一项目一行时合并为多值
        add(list, "userName", "登记人", "text", GROUP_WORKLOAD, 14, null, false, true);
        // 内部/外部工作量：单元格内多行明细文本（与结算导出一致），不是数字
        add(list, "internalWorkload", "内部工作量", "text", GROUP_WORKLOAD, 30, null, false, true);
        // 内部产值：统一「含 6000 保底」口径（2026-11-11 用户确认），与结算页合计一致
        add(list, "internalOutput", "内部产值", "money", GROUP_WORKLOAD, 16, null, false, true);
        add(list, "externalWorkload", "外部工作量", "text", GROUP_WORKLOAD, 30, null, false, true);
        add(list, "externalOutput", "外部产值", "money", GROUP_WORKLOAD, 16, null, false, true);
        // ==================== 结算付款 ====================
        add(list, "planReportTime", "拟上报时间", "text", GROUP_SETTLEMENT, 18, null, false, true);
        add(list, "prepayAmount", "预付款", "money", GROUP_SETTLEMENT, 14, null, false, true);
        add(list, "prepayDate", "预付款时间", "date", GROUP_SETTLEMENT, 14, null, false, true);
        add(list, "payUnit", "付款单位", "text", GROUP_SETTLEMENT, 20, null, false, true);
        add(list, "payMethod", "付款方式", "text", GROUP_SETTLEMENT, 14, null, false, true);
        add(list, "tailAmount", "尾款", "money", GROUP_SETTLEMENT, 14, null, false, true);
        add(list, "tailDate", "尾款时间", "date", GROUP_SETTLEMENT, 14, null, false, true);
        add(list, "refundAmount", "退款金额", "money", GROUP_SETTLEMENT, 14, null, false, true);
        add(list, "refundDate", "退款时间", "date", GROUP_SETTLEMENT, 14, null, false, true);
        add(list, "invoiceStatus", "开票状态", "text", GROUP_SETTLEMENT, 12, null, false, true);
        add(list, "invoiceUnit", "开票单位", "text", GROUP_SETTLEMENT, 20, null, false, true);
        add(list, "invoiceAmount", "开票金额", "money", GROUP_SETTLEMENT, 14, null, false, true);
        add(list, "payRemark", "付款备注", "text", GROUP_SETTLEMENT, 24, null, false, true);

        FIELDS = Collections.unmodifiableList(list);

        Map<String, Field> map = new LinkedHashMap<>();
        Set<String> keys = new HashSet<>();
        for (Field f : list)
        {
            map.put(f.getKey(), f);
            keys.add(f.getKey());
        }
        BY_KEY = Collections.unmodifiableMap(map);
        KEYS = Collections.unmodifiableSet(keys);
    }

    private ProjectExportFieldPool()
    {
    }

    private static void add(List<Field> list, String key, String label, String type, String group,
                            int width, String dictType, boolean defaultProject, boolean defaultSettlement)
    {
        list.add(new Field(key, label, type, group, width, dictType, defaultProject, defaultSettlement));
    }

    /** 全量字段（顺序 = 弹窗默认展示顺序） */
    public static List<Field> all()
    {
        return FIELDS;
    }

    /** 全部合法 key（导出参数白名单，未知 key 一律忽略） */
    public static Set<String> keys()
    {
        return KEYS;
    }

    public static Field get(String key)
    {
        return key == null ? null : BY_KEY.get(key.trim());
    }

    /** 某页面默认勾选的字段 key（按字段池顺序） */
    public static List<String> defaultKeys(String page)
    {
        List<String> list = new ArrayList<>();
        for (Field f : FIELDS)
        {
            if (f.isDefaultVisible(page))
            {
                list.add(f.getKey());
            }
        }
        return list;
    }

    /**
     * 前端弹窗用的列元数据（两页同一份；仅 defaultVisible 随页面不同）。
     *
     * @param page project / settlement；未知值按 project 处理
     */
    public static List<Map<String, Object>> toMeta(String page)
    {
        String p = PAGE_SETTLEMENT.equals(page) ? PAGE_SETTLEMENT : PAGE_PROJECT;
        List<Map<String, Object>> list = new ArrayList<>();
        for (Field f : FIELDS)
        {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", f.getKey());
            m.put("label", f.getLabel());
            m.put("type", f.getType());
            m.put("group", f.getGroup());
            m.put("width", f.getWidth());
            m.put("defaultVisible", f.isDefaultVisible(p));
            list.add(m);
        }
        return list;
    }
}
