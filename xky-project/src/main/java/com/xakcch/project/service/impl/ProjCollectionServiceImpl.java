package com.xakcch.project.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.xakcch.project.domain.ProjCollectionLog;
import com.xakcch.project.domain.vo.PaymentDetailExportVo;
import com.xakcch.common.core.domain.entity.SysDictData;
import com.xakcch.common.exception.ServiceException;
import com.xakcch.common.utils.DateUtils;
import com.xakcch.common.utils.DictUtils;
import com.xakcch.project.mapper.ProjCollectionMapper;
import com.xakcch.project.report.DynamicColumnExcelExporter;
import com.xakcch.project.service.IProjCollectionService;

/**
 * 回款管理 服务实现
 *
 * @author liuyonghui
 */
@Service
public class ProjCollectionServiceImpl implements IProjCollectionService
{
    /** 到账统计允许的分组维度（白名单，避免任意值进入 SQL 判断分支） */
    private static final Set<String> SUMMARY_GROUP_BY =
            new HashSet<>(Arrays.asList("none", "month", "quarter", "year", "clientUnit", "leader", "paymentType", "category"));

    /** 单次导出条数上限（与项目 / 合同列表导出一致） */
    private static final int EXPORT_MAX_ROWS = 50000;

    /**
     * 催款清单导出字段池（唯一出处）：导出弹窗的字段清单、导出表头与取值口径全部以此为准。
     * ⚠️ 改动这里 ⇒ 弹窗选项、导出列、取值规则三处同时变，不要在别处另建清单。
     */
    private static final List<Map<String, Object>> EXPORT_COLUMNS = buildExportColumns();

    /** 导出字段 key → 列类型（决定格式化与对齐方式） */
    private static final Map<String, String> EXPORT_TYPES = buildExportTypes();

    /** 导出字段 key → 字典类型（字典列整批预载用） */
    private static final Map<String, String> EXPORT_DICT_TYPES = buildExportDictTypes();

    private static List<Map<String, Object>> buildExportColumns()
    {
        List<Map<String, Object>> list = new ArrayList<>();
        // —— 现状 11 列：默认勾选，保证老用户「点开即导出」的内容与改造前完全一致 ——
        addExportColumn(list, "clientUnit", "客户全称", "text", true, 26);
        addExportColumn(list, "projectCode", "工程编号", "text", true, 18);
        addExportColumn(list, "projectName", "项目名称", "text", true, 28);
        addExportColumn(list, "engineeringProject", "工程项目", "text", true, 22);
        addExportColumn(list, "closeTime", "办结时间", "date", true, 14);
        addExportColumn(list, "receivable", "应收金额(元)", "money", true, 16);
        addExportColumn(list, "received", "已收金额(元)", "money", true, 16);
        addExportColumn(list, "unpaidAmount", "未收金额(元)", "money", true, 16);
        addExportColumn(list, "debtMonths", "账龄(月)", "number", true, 12);
        addExportColumn(list, "lastCollectTime", "最近催收时间", "date", true, 16);
        addExportColumn(list, "collectStatus", "催收状态", "text", true, 14);
        // —— 新增：默认不勾，用户自行勾选 ——
        addExportColumn(list, "contactName", "联系人", "text", false, 14);
        addExportColumn(list, "contactPhone", "联系电话", "text", false, 18);
        addExportColumn(list, "contractNo", "合同编号", "text", false, 20);
        addExportColumn(list, "contractName", "合同名称", "text", false, 26);
        addExportColumn(list, "contractType", "合同类型", "dict", false, 14);
        addExportColumn(list, "contractAmount", "合同金额(元)", "money", false, 16);
        addExportColumn(list, "signDate", "签署日期", "date", false, 14);
        addExportColumn(list, "lastPayTime", "最近到账时间", "date", false, 16);
        addExportColumn(list, "nextCollectTime", "下次催收时间", "date", false, 16);
        addExportColumn(list, "progress", "回款进度(%)", "number", false, 14);
        addExportColumn(list, "lastContactName", "最近催收联系人", "text", false, 16);
        addExportColumn(list, "lastCollectMethod", "最近催收方式", "text", false, 14);
        addExportColumn(list, "lastCollectResult", "最近催收结果", "text", false, 30);
        addExportColumn(list, "categoryName", "项目类别", "text", false, 18);
        addExportColumn(list, "leaderNames", "负责人", "text", false, 18);
        addExportColumn(list, "projectLocation", "工程地点", "text", false, 24);
        addExportColumn(list, "clientUnitNature", "委托单位性质", "dict", false, 16);
        addExportColumn(list, "projectNature", "项目性质", "text", false, 14);
        return Collections.unmodifiableList(list);
    }

    private static void addExportColumn(List<Map<String, Object>> list, String key, String label,
            String type, boolean defaultVisible, int width)
    {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        m.put("type", type);
        m.put("defaultVisible", defaultVisible);
        m.put("width", width);
        list.add(m);
    }

    private static Map<String, String> buildExportTypes()
    {
        Map<String, String> m = new HashMap<>();
        for (Map<String, Object> c : EXPORT_COLUMNS)
        {
            m.put(String.valueOf(c.get("key")), String.valueOf(c.get("type")));
        }
        return Collections.unmodifiableMap(m);
    }

    private static Map<String, String> buildExportDictTypes()
    {
        Map<String, String> m = new HashMap<>();
        m.put("contractType", "proj_contract_type");
        m.put("clientUnitNature", "proj_client_unit_nature");
        return Collections.unmodifiableMap(m);
    }


    @Autowired
    private ProjCollectionMapper collectionMapper;

    @Override
    public List<Map<String, Object>> selectCollectionList(Map<String, Object> params)
    {
        return collectionMapper.selectCollectionList(params);
    }

    @Override
    public List<Map<String, Object>> selectClientCollectionList(Map<String, Object> params)
    {
        return collectionMapper.selectClientCollectionList(params);
    }

    /**
     * 统计卡：项目级口径 + 到账窗口口径 + 指令性任务口径，合并为一份响应
     *
     * ① pendingCount/pendingAmount/overdueCount/unsettledCount —— 由 collectionBase + 公共筛选条件算出（随筛选联动）
     * ② windowReceived/windowPrevReceived —— 到账流水窗口净额（退款负冲），跟随筛选中的项目级子集
     * ③ mandateExternalOutput/mandateProjectCount —— 指令性任务项目的外部产值与项目数
     *    （外部产值不计入应收，故在 ① 中已被排除，这里单独列示以便对账）
     */
    @Override
    public Map<String, Object> selectCollectionStats(Map<String, Object> params)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> projectStats = collectionMapper.selectCollectionStats(params);
        if (projectStats != null)
        {
            result.putAll(projectStats);
        }
        Map<String, Object> receivedStats = collectionMapper.selectReceivedStats(params);
        if (receivedStats != null)
        {
            result.putAll(receivedStats);
        }
        Map<String, Object> mandateStats = collectionMapper.selectMandateExternalStats(params);
        if (mandateStats != null)
        {
            result.putAll(mandateStats);
        }
        // 兜底：SQL 恒返回一行，但防御性补零，避免前端 undefined
        result.putIfAbsent("mandateExternalOutput", BigDecimal.ZERO);
        result.putIfAbsent("mandateProjectCount", 0);
        return result;
    }

    @Override
    public List<Map<String, Object>> selectUnsettledList(Map<String, Object> params)
    {
        return collectionMapper.selectUnsettledList(params);
    }

    @Override
    public List<Map<String, Object>> selectReceivedDetail(Map<String, Object> params)
    {
        return collectionMapper.selectReceivedDetail(params);
    }

    /**
     * 到账统计：合计 + 分组明细
     *
     * 口径：只要有到账流水（del_flag='0'）即计入，退款负冲，不看 received_status（已废弃列）。
     * dimension=closeTime 时按办结时间归属且不限制到账时间（取这批办结项目的累计到账）。
     * ⚠️ 合计查询不参与 groupBy='leader' 的负责人展开，否则合计会被重复累计。
     */
    @Override
    public Map<String, Object> selectPaymentSummary(Map<String, Object> params)
    {
        Map<String, Object> result = new LinkedHashMap<>();

        // 口径归一：payTime=按到账时间 / closeTime=按办结时间 /
        //          planReportTime=按拟上报时间（付款笔级 plan_report_month，每笔归到自己的那个月）
        String rawDimension = str(params.get("dimension"));
        String dimension = "closeTime".equals(rawDimension) ? "closeTime"
            : ("planReportTime".equals(rawDimension) ? "planReportTime" : "payTime");
        params.put("dimension", dimension);

        String groupBy = str(params.get("groupBy"));
        if (!SUMMARY_GROUP_BY.contains(groupBy))
        {
            groupBy = "none";
        }
        params.put("groupBy", groupBy);

        // 合计（先去 leaderJoin，保证合计与分组无关）
        params.remove("leaderJoin");
        Map<String, Object> summary = collectionMapper.selectPaymentSummary(params);
        result.put("summary", summary == null ? new LinkedHashMap<String, Object>() : summary);

        // 分组明细
        if ("none".equals(groupBy))
        {
            result.put("groups", new ArrayList<Map<String, Object>>());
        }
        else
        {
            if ("leader".equals(groupBy))
            {
                // 按负责人展开：一个项目多位负责人时，同一笔到账在每位负责人下各计一次
                params.put("leaderJoin", "1");
            }
            result.put("groups", collectionMapper.selectPaymentSummaryGroups(params));
        }

        result.put("dimension", dimension);
        result.put("groupBy", groupBy);
        return result;
    }

    /**
     * 导出到账明细：与到账汇总同一口径与筛选条件（不分页，忽略分组维度）
     */
    @Override
    public List<PaymentDetailExportVo> selectPaymentExportList(Map<String, Object> params)
    {
        params.remove("groupBy");
        params.remove("leaderJoin");
        List<Map<String, Object>> rows = collectionMapper.selectReceivedDetail(params);
        List<PaymentDetailExportVo> result = new ArrayList<>();
        for (Map<String, Object> row : rows)
        {
            result.add(new PaymentDetailExportVo(
                    str(row.get("projectCode")),
                    str(row.get("projectName")),
                    str(row.get("clientUnit")),
                    paymentTypeText(str(row.get("paymentType"))),
                    num(row.get("amount")),
                    date(row.get("payTime")),
                    date(row.get("closeTime")),
                    str(row.get("payUnit")),
                    str(row.get("payMethod")),
                    str(row.get("remark"))));
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getExportColumns()
    {
        return EXPORT_COLUMNS;
    }

    /**
     * 导出催款清单（可选字段）：与列表页同一套筛选条件，一行一项目，
     * SQL 内已按「客户分组 + 组内账龄降序」排序（催款时同单位的欠款项目挨在一起）。
     */
    @Override
    public Map<String, Object> exportCollection(Map<String, Object> params, List<String> fields)
    {
        Map<String, Object> p = new HashMap<>();
        if (params != null)
        {
            p.putAll(params);
        }
        // 导出与分页无关：清掉分页参数，避免调用方误传导致只导一页
        p.remove("pageNum");
        p.remove("pageSize");
        List<Map<String, Object>> rows = collectionMapper.selectCollectionExportList(p);
        if (rows.size() > EXPORT_MAX_ROWS)
        {
            throw new ServiceException("导出条数过多（" + rows.size() + " 条，上限 "
                    + EXPORT_MAX_ROWS + " 条），请缩小筛选范围后重试");
        }
        return buildExportData(fields, rows);
    }

    /**
     * 按 fields 顺序组装「表头 + 行数据」。
     *
     * <p>⚠️ 列元数据全部取自 {@link #EXPORT_COLUMNS}，未知 key 一律忽略
     *    （既防越权导出内部字段，也防参数拼错导出空列）。</p>
     */
    private Map<String, Object> buildExportData(List<String> fields, List<Map<String, Object>> rows)
    {
        Map<String, Map<String, Object>> metaMap = new LinkedHashMap<>();
        for (Map<String, Object> c : EXPORT_COLUMNS)
        {
            metaMap.put(String.valueOf(c.get("key")), c);
        }
        // 字典列整批预载：每个字典类型只取一次（详见 loadDictMaps 注释）
        Map<String, Map<String, String>> dictMaps = loadDictMaps(fields);

        List<DynamicColumnExcelExporter.Column> headers = new ArrayList<>();
        List<String> validKeys = new ArrayList<>();
        if (fields != null)
        {
            for (String key : fields)
            {
                if (key == null || key.trim().isEmpty())
                {
                    continue;
                }
                String k = key.trim();
                Map<String, Object> meta = metaMap.get(k);
                if (meta == null)
                {
                    continue;
                }
                headers.add(toColumn(meta));
                validKeys.add(k);
            }
        }
        // 未指定字段 / 全部未知 ⇒ 回退为默认可见列（与改造前的 11 列一致）
        if (validKeys.isEmpty())
        {
            for (Map<String, Object> c : EXPORT_COLUMNS)
            {
                if (Boolean.TRUE.equals(c.get("defaultVisible")))
                {
                    headers.add(toColumn(c));
                    validKeys.add(String.valueOf(c.get("key")));
                }
            }
        }

        List<List<Object>> data = new ArrayList<>();
        for (Map<String, Object> row : rows)
        {
            List<Object> line = new ArrayList<>();
            for (String key : validKeys)
            {
                line.add(formatValue(row, key, dictMaps));
            }
            data.add(line);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("headers", headers);
        result.put("rows", data);
        result.put("total", data.size());
        return result;
    }

    /** 列元数据 → 导出列定义（金额 / 数字列右对齐） */
    private DynamicColumnExcelExporter.Column toColumn(Map<String, Object> meta)
    {
        String type = String.valueOf(meta.get("type"));
        boolean numeric = "money".equals(type) || "number".equals(type);
        int width = 16;
        Object w = meta.get("width");
        if (w instanceof Number)
        {
            width = ((Number) w).intValue();
        }
        return new DynamicColumnExcelExporter.Column(String.valueOf(meta.get("label")), width, numeric);
    }

    /**
     * 单个单元格取值：联系人回退、状态 / 性质转文案、字典转标签、日期统一 yyyy-MM-dd
     */
    private Object formatValue(Map<String, Object> row, String key, Map<String, Map<String, String>> dictMaps)
    {
        if ("contactName".equals(key))
        {
            // 项目联系人优先，为空回退合同联系人（与「联系电话」成对回退，避免张冠李戴）
            String v = str(row.get("projectContactName"));
            return v.isEmpty() ? str(row.get("contractContactName")) : v;
        }
        if ("contactPhone".equals(key))
        {
            String name = str(row.get("projectContactName"));
            return name.isEmpty() ? str(row.get("contractContactPhone")) : str(row.get("projectContactPhone"));
        }
        if ("collectStatus".equals(key))
        {
            String v = str(row.get("collectStatus"));
            return v.isEmpty() ? "" : collectStatusText(v);
        }
        if ("projectNature".equals(key))
        {
            return "mandate".equals(str(row.get("projectNature"))) ? "指令性任务" : "常规";
        }
        Object raw = row.get(key);
        if (raw == null || "".equals(raw))
        {
            return "";
        }
        String type = EXPORT_TYPES.get(key);
        if ("date".equals(type))
        {
            return (raw instanceof java.util.Date)
                    ? DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD, (java.util.Date) raw)
                    : String.valueOf(raw);
        }
        if ("dict".equals(type))
        {
            String dictType = EXPORT_DICT_TYPES.get(key);
            Map<String, String> dm = (dictType == null) ? null : dictMaps.get(dictType);
            String val = String.valueOf(raw);
            String label = (dm == null) ? null : dm.get(val);
            if (label == null && dictType != null)
            {
                // 预载缺失（字典在导出期间被改过）才回落逐次查询，保证不丢标签
                label = DictUtils.getDictLabel(dictType, val);
            }
            return (label == null || label.isEmpty()) ? val : label;
        }
        if ("money".equals(type) || "number".equals(type))
        {
            return (raw instanceof Number) ? raw : num(raw);
        }
        return String.valueOf(raw);
    }

    /**
     * 按导出字段涉及到的字典类型，一次性加载 value → label 映射。
     *
     * <p>⚠️ 性能要点：{@link DictUtils#getDictLabel} 每次调用都会「Redis 读 + 全量反序列化
     * + 重建 HashMap」（项目列表导出实测单单元格 ≈8.4ms，3743 行 × 16 字段 = 67s）。
     * 改为预先按字典类型建映射后，逐行纯内存查表。</p>
     */
    private Map<String, Map<String, String>> loadDictMaps(List<String> fields)
    {
        Map<String, Map<String, String>> result = new HashMap<>();
        if (fields == null)
        {
            return result;
        }
        for (String key : fields)
        {
            if (key == null)
            {
                continue;
            }
            String dictType = EXPORT_DICT_TYPES.get(key.trim());
            if (dictType == null || result.containsKey(dictType))
            {
                continue;
            }
            Map<String, String> map = new HashMap<>();
            List<SysDictData> datas = DictUtils.getDictCache(dictType);
            if (datas != null)
            {
                for (SysDictData d : datas)
                {
                    if (d != null && d.getDictValue() != null)
                    {
                        map.put(d.getDictValue(), d.getDictLabel());
                    }
                }
            }
            result.put(dictType, map);
        }
        return result;
    }

    @Override
    public List<ProjCollectionLog> selectLogListByProjectId(Long projectId)
    {
        return collectionMapper.selectLogListByProjectId(projectId);
    }

    @Override
    public int insertLog(ProjCollectionLog log)
    {
        return collectionMapper.insertLog(log);
    }

    @Override
    public int deleteLogByIds(Long[] ids, String updateBy)
    {
        return collectionMapper.deleteLogByIds(ids, updateBy);
    }

    /** 催收状态 → 中文文案 */
    private String collectStatusText(String status)
    {
        if ("overdue".equals(status))
        {
            return "超期未催";
        }
        if ("calling".equals(status))
        {
            return "催收中";
        }
        return "从未催收";
    }

    /** 付款类型 → 中文文案（字典 proj_payment_type：advance/final/refund） */
    private String paymentTypeText(String type)
    {
        if ("advance".equals(type))
        {
            return "预付款";
        }
        if ("final".equals(type))
        {
            return "尾款";
        }
        if ("refund".equals(type))
        {
            return "退款";
        }
        return str(type);
    }

    private String str(Object o)
    {
        return o == null ? "" : o.toString();
    }

    private BigDecimal num(Object o)
    {
        if (o == null)
        {
            return BigDecimal.ZERO;
        }
        if (o instanceof BigDecimal)
        {
            return (BigDecimal) o;
        }
        try
        {
            return new BigDecimal(o.toString());
        }
        catch (Exception e)
        {
            return BigDecimal.ZERO;
        }
    }

    private Date date(Object o)
    {
        if (o instanceof Date)
        {
            return (Date) o;
        }
        return null;
    }
}
