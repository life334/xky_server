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
        addField("engineeringProject", "委托任务", "subject", "项目信息", "string", true, null);
        addField("clientUnit", "委托单位", "subject", "项目信息", "string", true, null);
        addField("contactName", "委托联系人", "subject", "项目信息", "string", true, null);
        addField("contactPhone", "联系人电话", "subject", "项目信息", "string", true, null);
        addField("projectLocation", "委托地点", "subject", "项目信息", "string", true, null);
        addField("projectCategoryId", "项目类别", "subject", "项目信息", "select", true, null);
        addField("categoryName", "项目类别名称", "join", "项目信息", "string", false, null);
        addField("projectStatus", "项目状态", "subject", "项目信息", "select", true, PROJECT_STATUS);
        addField("leaderName", "项目负责人", "join", "项目信息", "string", true, null);
        addField("assignDate", "安排日期", "subject", "项目信息", "date", true, null);
        addField("durationRequire", "工期要求(天)", "subject", "项目信息", "number", false, null);
        addField("totalDuration", "总时长(天)", "subject", "项目信息", "number", false, null);
        addField("createTime", "创建时间", "subject", "项目信息", "date", true, null);
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
        addField("lastPayTime", "最近到账时间", "agg", "收款信息", "date", true, null);
        addField("pendingAmount", "未收金额", "agg", "收款信息", "number", true, null);
        addField("totalInvoiceAmount", "开票金额合计", "agg", "收款信息", "number", true, null);
        addField("invoiceFlag", "是否开票", "agg", "收款信息", "select", true,
                toOptions(new String[][]{{"Y", "已开票"}, {"N", "未开票"}}));
        addField("settlementStatus", "结算状态", "agg", "收款信息", "select", true, SETTLE_STATUS);
        addField("debtMonths", "欠款时长(月)", "agg", "收款信息", "number", true, null);
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
