package com.xakcch.project.report;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.xakcch.project.domain.ProjFieldDef;
import com.xakcch.project.domain.ProjReportField;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

/**
 * 报表字段池注册中心
 *
 * <p>字段池 = 固定字段（项目/合同/聚合）+ 动态字段（proj_field_def），按数据主体组织。
 * 每个字段元数据：key / label / source / group / type(string|date|number|select) / filterable / options。</p>
 *
 * @author liuyonghui
 */
public class ReportFieldPool
{
    /** 数据主体：项目 */
    public static final String SUBJECT_PROJECT = "proj_project";

    /** 项目状态字典 */
    private static final Map<String, String> PROJECT_STATUS = new LinkedHashMap<>();
    /** 结算状态字典 */
    private static final Map<String, String> SETTLE_STATUS = new LinkedHashMap<>();

    static
    {
        PROJECT_STATUS.put("ongoing", "进行中");
        PROJECT_STATUS.put("closed", "已办结");
        PROJECT_STATUS.put("archived", "已归档");

        SETTLE_STATUS.put("settled", "已结清");
        SETTLE_STATUS.put("pending", "未结清");
        SETTLE_STATUS.put("overdue", "超额");
    }

    /** 固定字段池（主体=项目） */
    private static final List<Map<String, Object>> FIXED_FIELDS = new ArrayList<>();

    static
    {
        addField("rowNo", "序号", "agg", "项目信息", "number", false, null);
        addField("projectCode", "工程编号", "subject", "项目信息", "string", true, null);
        addField("projectName", "项目名称", "subject", "项目信息", "string", true, null);
        addField("engineeringProject", "工程项目", "subject", "项目信息", "string", true, null);
        addField("clientUnit", "委托单位", "subject", "项目信息", "string", true, null);
        addField("contactName", "联系人", "subject", "项目信息", "string", true, null);
        addField("contactPhone", "联系电话", "subject", "项目信息", "string", true, null);
        addField("projectLocation", "工程地点", "subject", "项目信息", "string", true, null);
        addField("projectCategoryId", "项目类别", "subject", "项目信息", "select", true, null);
        addField("categoryName", "项目类别名称", "join", "项目信息", "string", false, null);
        addField("projectStatus", "项目状态", "subject", "项目信息", "select", true, PROJECT_STATUS);
        addField("leaderName", "负责人", "join", "项目信息", "string", true, null);
        addField("assignDate", "安排日期", "subject", "项目信息", "date", true, null);
        addField("durationRequire", "工期要求(天)", "subject", "项目信息", "number", false, null);
        addField("totalDuration", "总时长(天)", "subject", "项目信息", "number", false, null);
        addField("createTime", "创建时间", "subject", "项目信息", "date", true, null);
        // 办结时间：状态流转 closed 时写入；支持筛选（指定时间段内办结的项目）
        addField("closeTime", "办结时间", "subject", "项目信息", "date", true, null);
        addField("remark", "备注", "subject", "项目信息", "string", true, null);
        addField("deptName", "所属部门", "agg", "项目信息", "string", false, null);

        addField("contractNo", "合同编号", "join", "合同信息", "string", true, null);
        addField("contractName", "合同名称", "join", "合同信息", "string", true, null);
        addField("contractAmount", "合同金额", "join", "合同信息", "number", true, null);
        addField("signDate", "签订时间", "join", "合同信息", "date", true, null);
        addField("entrustDate", "委托时间", "join", "合同信息", "date", true, null);
        addField("auditDate", "审核时间", "join", "合同信息", "date", true, null);
        addField("finishDate", "完工时间", "join", "合同信息", "date", true, null);
        addField("archiveDate", "归档时间", "join", "合同信息", "date", true, null);
        addField("contractPeriod", "合同工期", "join", "合同信息", "string", true, null);
        addField("paymentTerms", "付款方式", "join", "合同信息", "string", true, null);
        addField("isSettled", "是否结算", "join", "合同信息", "select", true,
                toOptions(new String[][]{{"0", "未结算"}, {"1", "已结算"}}));

        addField("receivedAmount", "已收金额", "agg", "收款信息", "number", true, null);
        addField("refundAmount", "退款金额", "agg", "收款信息", "number", true, null);
        addField("lastPayTime", "最近到账时间", "agg", "收款信息", "date", true, null);
        addField("pendingAmount", "合同未收金额", "agg", "收款信息", "number", true, null);
        addField("totalInvoiceAmount", "开票金额合计", "agg", "收款信息", "number", true, null);
        addField("invoiceFlag", "是否开票", "agg", "收款信息", "select", true,
                toOptions(new String[][]{{"Y", "已开票"}, {"N", "未开票"}}));
        addField("settlementStatus", "合同结算状态", "agg", "收款信息", "select", true, SETTLE_STATUS);
        addField("debtMonths", "欠款时长(月)", "agg", "收款信息", "number", true, null);

        // 内置模板计算列：需预留 = 到账金额 × 2/3；需补为模板占位列（暂恒为空）
        addField("reservedAmount", "需预留", "agg", "收款信息", "number", false, null);
        addField("needSupplement", "需补", "agg", "收款信息", "string", false, null);
        // 管线验线占位字段（内置模板「只定未验及补之前扣除项目」专用）：
        // 验线尚未开展，系统编号/上报时间在报表中固定为空，resolveValue 恒返回 null
        addField("verifySystemNo", "系统编号(验线占位)", "agg", "管线验线", "string", false, null);
        addField("verifyReportTime", "上报时间(验线占位)", "agg", "管线验线", "date", false, null);
        // 管线定线上报领导时间（年-月）：内置模板「只定未验及补之前扣除项目」col4 专用，
        // 已上报取 proj_report_submit_log 锁定时间（yyyy-MM）；未上报固定显示当前年月
        addField("submitTimeYm", "上报时间", "subject", "管线定线", "date", false, null);
        // 兼容旧自定义模板：基于创建时间的上报时间占位
        addField("createTimeYm", "上报时间(年-月)", "agg", "管线定线", "date", false, null);
        // 补验线报表（byx_report）专用字段：关联工程编号 / 关联工程上报领导时间
        // （办结时间 closeTime 已移至「项目信息」组并开放筛选）
        addField("relatedProjectCode", "关联工程编号", "subject", "管线定线", "string", false, null);
        addField("relatedLastSubmitTime", "关联工程上报时间", "subject", "管线定线", "date", false, null);

        // 项目维度结算字段：结算金额 = 外部产值合计；内部产值仅内部参考，不与外部产值相加
        addField("projectSettleAmount", "项目结算金额", "agg", "项目结算", "number", true, null);
        addField("projectInternalOutput", "项目内部产值", "agg", "项目结算", "number", true, null);
        addField("projectReceivedAmount", "项目已收金额", "agg", "项目结算", "number", true, null);
        addField("projectPendingAmount", "项目未收金额", "agg", "项目结算", "number", true, null);
        addField("projectArrearsAmount", "项目欠款金额", "agg", "项目结算", "number", true, null);
        addField("projectOverpaidAmount", "项目挂账金额", "agg", "项目结算", "number", true, null);
        addField("projectSettleStatus", "项目结算状态", "agg", "项目结算", "select", true, SETTLE_STATUS);
    }

    private static void addField(String key, String label, String source, String group,
            String type, boolean filterable, Map<String, String> options)
    {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("key", key);
        f.put("label", label);
        f.put("source", source);
        f.put("group", group);
        f.put("type", type);
        f.put("filterable", filterable);
        f.put("options", options == null ? new LinkedHashMap<>() : options);
        FIXED_FIELDS.add(f);
    }

    private static Map<String, String> toOptions(String[][] kv)
    {
        Map<String, String> m = new LinkedHashMap<>();
        for (String[] e : kv)
        {
            m.put(e[0], e[1]);
        }
        return m;
    }

    /**
     * 获取字段池（固定字段 + 动态字段），按组返回
     *
     * @param dynamicFields 动态字段定义（proj_field_def，table_name=主体），可为 null
     */
    public static List<Map<String, Object>> getFieldPool(List<ProjFieldDef> dynamicFields)
    {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> f : FIXED_FIELDS)
        {
            result.add(new LinkedHashMap<>(f));
        }
        if (dynamicFields != null)
        {
            for (ProjFieldDef d : dynamicFields)
            {
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("key", "dynamic." + d.getFieldKey());
                f.put("label", d.getFieldLabel());
                f.put("source", "dynamic");
                f.put("group", "动态字段");
                f.put("type", d.getFieldType() == null ? "string" : d.getFieldType());
                f.put("filterable", true);
                f.put("options", new LinkedHashMap<>());
                result.add(f);
            }
        }
        return result;
    }

    /**
     * 解析一行数据中某字段的值（含聚合计算与字典转换）
     *
     * @param field 模板字段定义
     * @param row   查询行 Map（key 为 ReportDataMapper 列别名）
     * @return 单元格值（String / BigDecimal / Date，null 则导出空）
     */
    public static Object resolveValue(ProjReportField field, Map<String, Object> row)
    {
        if (field == null || row == null)
        {
            return null;
        }
        String key = field.getFieldKey();
        if (key == null)
        {
            return null;
        }
        // 兼容带前缀的旧键（project.xxx / contract.xxx / agg.xxx），统一规约为字段池无前缀键
        key = normalizeKey(key);

        // 动态字段：从项目 extra_data JSONB 解析（dynamic. 前缀保留）
        if (key.startsWith("dynamic."))
        {
            String dynKey = key.substring("dynamic.".length());
            String extra = str(row.get("projectExtraData"));
            if (extra != null && !extra.isEmpty())
            {
                try
                {
                    JSONObject json = JSON.parseObject(extra);
                    return json == null ? null : json.get(dynKey);
                }
                catch (Exception ignore)
                {
                    return null;
                }
            }
            return null;
        }

        // 聚合计算字段
        if ("rowNo".equals(key))
        {
            Object no = row.get("rowNo");
            return no == null ? null : no;
        }
        if ("lastPayTime".equals(key))
        {
            return row.get("lastPayTime");
        }
        if ("pendingAmount".equals(key))
        {
            BigDecimal amount = num(row.get("contractAmount"));
            BigDecimal received = num(row.get("receivedAmount"));
            if (amount == null)
            {
                return null;
            }
            return amount.subtract(received == null ? BigDecimal.ZERO : received);
        }
        if ("settlementStatus".equals(key))
        {
            BigDecimal amount = num(row.get("contractAmount"));
            BigDecimal received = num(row.get("receivedAmount"));
            if (amount == null)
            {
                return "未结清";
            }
            int cmp = received == null ? -1 : received.compareTo(amount);
            return cmp > 0 ? "超额" : (cmp == 0 ? "已结清" : "未结清");
        }
        if ("invoiceFlag".equals(key))
        {
            BigDecimal invoice = num(row.get("totalInvoiceAmount"));
            return invoice != null && invoice.compareTo(BigDecimal.ZERO) > 0 ? "已开票" : "未开票";
        }
        // 项目结算金额（= 外部产值合计，SQL 已聚合）与项目内部产值直接取行值
        if ("projectSettleAmount".equals(key))
        {
            return row.get("projectSettleAmount");
        }
        if ("projectInternalOutput".equals(key))
        {
            return row.get("projectInternalOutput");
        }
        if ("projectReceivedAmount".equals(key))
        {
            return row.get("receivedAmount");
        }
        if ("projectPendingAmount".equals(key))
        {
            BigDecimal settle = num(row.get("projectSettleAmount"));
            BigDecimal received = num(row.get("receivedAmount"));
            if (settle == null)
            {
                return null;
            }
            return settle.subtract(received == null ? BigDecimal.ZERO : received);
        }
        // 项目欠款金额 / 项目挂账金额：SQL 已按业务口径算好（挂账仅限已结算且有外部产值的项目）
        if ("projectArrearsAmount".equals(key))
        {
            return row.get("projectArrearsAmount");
        }
        if ("projectOverpaidAmount".equals(key))
        {
            return row.get("projectOverpaidAmount");
        }
        if ("projectSettleStatus".equals(key))
        {
            BigDecimal settle = num(row.get("projectSettleAmount"));
            BigDecimal received = num(row.get("receivedAmount"));
            if (settle == null || settle.compareTo(BigDecimal.ZERO) == 0)
            {
                return "未结清";
            }
            int cmp = received == null ? -1 : received.compareTo(settle);
            return cmp > 0 ? "超额" : (cmp == 0 ? "已结清" : "未结清");
        }
        if ("debtMonths".equals(key))
        {
            Date finish = date(row.get("finishDate"));
            if (finish == null)
            {
                return null;
            }
            long days = (System.currentTimeMillis() - finish.getTime()) / 86400000L;
            if (days < 0)
            {
                days = 0;
            }
            return BigDecimal.valueOf(days / 30.44).setScale(1, BigDecimal.ROUND_HALF_UP);
        }
        // 需预留 = 到账金额 × 2/3（内置模板「验线（2/3）」计算列）
        if ("reservedAmount".equals(key))
        {
            BigDecimal received = num(row.get("receivedAmount"));
            if (received == null)
            {
                return null;
            }
            return received.multiply(new BigDecimal("2"))
                    .divide(new BigDecimal("3"), 2, BigDecimal.ROUND_HALF_UP);
        }
        // 需补：模板占位列，暂恒为空
        if ("needSupplement".equals(key))
        {
            return null;
        }
        // 管线验线占位列：验线未开展，系统编号/上报时间在报表中固定为空
        if ("verifySystemNo".equals(key) || "verifyReportTime".equals(key))
        {
            return null;
        }
        // 管线定线上报领导时间（年-月）：已上报取锁定时间；未上报固定显示当前年月
        // （月度上报节奏：本月导出即上报，上报时间=当前月，故未上报记录预显当前月）
        if ("submitTimeYm".equals(key))
        {
            Date submit = date(row.get("submitTime"));
            return new java.text.SimpleDateFormat("yyyy-MM").format(submit == null ? new Date() : submit);
        }
        // 管线定线上报时间（年-月）：取创建时间格式化为 yyyy-MM（如 2026-08）
        if ("createTimeYm".equals(key))
        {
            Date create = date(row.get("createTime"));
            return create == null ? null : new java.text.SimpleDateFormat("yyyy-MM").format(create);
        }
        // 补验线：关联工程编号直接取行值
        if ("relatedProjectCode".equals(key))
        {
            return row.get("relatedProjectCode");
        }
        // 补验线：关联工程上一次上报领导时间（年-月），未上报为空
        if ("relatedLastSubmitTime".equals(key))
        {
            Date submit = date(row.get("relatedLastSubmitTime"));
            return submit == null ? null : new java.text.SimpleDateFormat("yyyy-MM").format(submit);
        }
        // 补验线：办结时间（年-月）
        if ("closeTime".equals(key))
        {
            Date close = date(row.get("closeTime"));
            return close == null ? null : new java.text.SimpleDateFormat("yyyy-MM").format(close);
        }

        // 字典转换
        if ("projectStatus".equals(key))
        {
            String v = str(row.get(key));
            return v == null ? null : PROJECT_STATUS.getOrDefault(v, v);
        }

        // 固定值字段
        if ("deptName".equals(key))
        {
            return "地下空间工程中心";
        }

        // 直接取行值
        return row.get(key);
    }

    /** 键规范化：剥离 project./contract./agg. 前缀（dynamic. 保留） */
    private static String normalizeKey(String key)
    {
        if (key.startsWith("dynamic."))
        {
            return key;
        }
        int dot = key.indexOf('.');
        if (dot > 0 && (key.startsWith("project.") || key.startsWith("contract.") || key.startsWith("agg.")))
        {
            return key.substring(dot + 1);
        }
        return key;
    }

    private static String str(Object o)
    {
        return o == null ? null : o.toString();
    }

    private static BigDecimal num(Object o)
    {
        if (o == null)
        {
            return null;
        }
        if (o instanceof BigDecimal)
        {
            return (BigDecimal) o;
        }
        if (o instanceof Number)
        {
            return new BigDecimal(o.toString());
        }
        try
        {
            return new BigDecimal(o.toString());
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private static Date date(Object o)
    {
        if (o instanceof Date)
        {
            return (Date) o;
        }
        return null;
    }
}
