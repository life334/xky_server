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
    /** 项目性质字典（proj_project.project_nature）：normal=市场性任务（外部产值计入应收）；mandate=指令性任务 */
    private static final Map<String, String> PROJECT_NATURE = new LinkedHashMap<>();

    static
    {
        PROJECT_STATUS.put("ongoing", "进行中");
        PROJECT_STATUS.put("closed", "已办结");
        PROJECT_STATUS.put("archived", "已归档");

        SETTLE_STATUS.put("settled", "已结清");
        SETTLE_STATUS.put("pending", "未结清");
        SETTLE_STATUS.put("overdue", "超额");

        PROJECT_NATURE.put("normal", "市场性任务");
        PROJECT_NATURE.put("mandate", "指令性任务");
    }

    /** 固定字段池（主体=项目） */
    private static final List<Map<String, Object>> FIXED_FIELDS = new ArrayList<>();

    static
    {
        addField("rowNo", "序号", "agg", "项目信息", "number", false, null);
        addField("projectCode", "工程编号", "subject", "项目信息", "string", true, null);
        addField("projectName", "项目名称", "subject", "项目信息", "string", true, null);
        // 项目类别（历史文本列 engineering_project）：仅作「列/字段设计器」可选，**不作为筛选字段** ——
        // 它与 projectCategoryId 同语义（项目所属类别）且显示名相同，若同时进入左侧「可选筛选字段」会造成重复。
        // 报表筛选统一走规范外键 projectCategoryId（按类别 id 精确匹配）。
        addField("engineeringProject", "项目类别", "subject", "项目信息", "string", false, null);
        addField("clientUnit", "委托单位", "subject", "项目信息", "string", true, null);
        addField("contactName", "联系人", "subject", "项目信息", "string", true, null);
        addField("contactPhone", "联系电话", "subject", "项目信息", "string", true, null);
        addField("projectLocation", "工程地点", "subject", "项目信息", "string", true, null);
        addField("projectCategoryId", "项目类别", "subject", "项目信息", "select", true, null);
        addField("categoryName", "项目类别名称", "join", "项目信息", "string", false, null);
        addField("projectStatus", "项目状态", "subject", "项目信息", "select", true, PROJECT_STATUS);
        // 项目性质（proj_project.project_nature，非空默认 'normal'）：
        //   与「委托单位性质」(client_unit_nature，字典 proj_client_unit_nature) 是两个独立字段，不要混。
        //   filterable=true ⇒ 报表页「筛选设置 → 项目信息」可勾选，预览/导出同一套 SQL（ReportDataMapper#rwc_projectNature）。
        //   作为列输出时 resolveValue 会把 normal/mandate 转成中文（否则会直接显示英文码值）。
        addField("projectNature", "项目性质", "subject", "项目信息", "select", true, PROJECT_NATURE);
        addField("leaderName", "负责人", "join", "项目信息", "string", true, null);
        addField("assignDate", "安排日期", "subject", "项目信息", "date", true, null);
        addField("durationRequire", "工期要求(天)", "subject", "项目信息", "number", false, null);
        addField("totalDuration", "总时长(天)", "subject", "项目信息", "number", false, null);
        addField("createTime", "创建时间", "subject", "项目信息", "date", true, null);
        // 办结时间：状态流转 closed 时写入；支持筛选（指定时间段内办结的项目）
        addField("closeTime", "办结时间", "subject", "项目信息", "date", true, null);
        // 拟上报时间（2026-10-29 起为「付款笔级」proj_payment.plan_report_month，YYYY-MM）：
        //   一个项目可能有多笔付款 ⇒ 单元格显示各笔月份去重升序拼接（如「2025-11、2026-03」），因此类型为 string 而非 date。
        //   筛选控件的键是 planReportTime（daterange → planReportTimeBegin/End）。
        //   筛选走 ReportDataMapper#rwc_planReportTime（exists 子查询，存在某笔月份落在区间内即命中）；
        //   金额列另由 pay_sum 的 payPlanMonthCond 按同一区间过滤；未填该筛选时两者都不生效（不过滤、金额全算）。
        addField("planReportTime", "拟上报时间", "subject", "项目信息", "string", true, null);
        // 项目备注（proj_project.remark）：与「到账备注」是两个不同来源，标签加「项目」前缀以便区分
        // （已保存模板存的是当时的名称快照，改名不会影响已有模板的列名显示）
        addField("remark", "项目备注", "subject", "项目信息", "string", true, null);
        addField("deptName", "所属部门", "agg", "项目信息", "string", false, null);

        addField("contractNo", "合同编号", "join", "合同信息", "string", true, null);
        addField("contractName", "合同名称", "join", "合同信息", "string", true, null);
        addField("contractAmount", "合同金额", "join", "合同信息", "number", true, null);
        addField("signDate", "签订时间", "join", "合同信息", "date", true, null);
        addField("entrustDate", "委托时间", "join", "合同信息", "date", true, null);
        addField("archiveDate", "归档时间", "join", "合同信息", "date", true, null);
        addField("contractPeriod", "合同工期", "join", "合同信息", "string", true, null);
        addField("paymentTerms", "付款方式", "join", "合同信息", "string", true, null);
        addField("isSettled", "是否结算", "join", "合同信息", "select", true,
                toOptions(new String[][]{{"0", "未结算"}, {"1", "已结算"}}));

        addField("receivedAmount", "已收金额", "agg", "收款信息", "number", true, null);
        // 到账备注（proj_payment.remark）：业务上一个项目通常只维护一条，多笔到账时备注内容
        //   多为复制粘贴的同一句 ⇒ SQL 用 string_agg(DISTINCT ...) 去重后用「；」拼接，不会重复显示。
        //   不可筛选（filterable=false）：未进入前端「筛选设置」面板，如需筛选要另加 exists 子查询。
        //   取值走 resolveValue 末尾的「直接取行值」分支，ReportDataMapper 已 select 别名 "paymentRemark"。
        addField("paymentRemark", "到账备注", "agg", "收款信息", "string", false, null);
        addField("refundAmount", "退款金额", "agg", "收款信息", "number", true, null);
        addField("lastPayTime", "最近到账时间", "agg", "收款信息", "date", true, null);
        addField("pendingAmount", "合同未收金额", "agg", "收款信息", "number", true, null);
        addField("totalInvoiceAmount", "开票金额合计", "agg", "收款信息", "number", true, null);
        addField("invoiceFlag", "是否开票", "agg", "收款信息", "select", true,
                toOptions(new String[][]{{"Y", "已开票"}, {"N", "未开票"}}));
        addField("settlementStatus", "合同结算状态", "agg", "收款信息", "select", true, SETTLE_STATUS);
        addField("debtMonths", "欠款时长(月)", "agg", "收款信息", "number", true, null);

        // 内置模板「验线（2/3）」计算列：到账金额 × 2/3，赋值随模板对调（见 resolveValue）
        //   模板1 zdyw：需预留=到账×2/3，需补=空；模板6 byx：需预留=空，需补=到账×2/3
        addField("reservedAmount", "需预留", "agg", "收款信息", "number", false, null);
        addField("needSupplement", "需补", "agg", "收款信息", "number", false, null);
        // 管线验线占位字段（内置模板「只定未验及补之前扣除项目」专用）：
        // 验线尚未开展，系统编号/上报时间在报表中固定为空，resolveValue 恒返回 null
        addField("verifySystemNo", "系统编号(验线占位)", "agg", "管线验线", "string", false, null);
        addField("verifyReportTime", "上报时间(验线占位)", "agg", "管线验线", "date", false, null);
        // 管线定线上报领导时间（年-月）：内置模板「只定未验及补之前扣除项目」col4 专用，
        // 唯一来源：上报记录（proj_report_submit_log.submit_time，含历史补录行）
        // ⚠️ 2026-10-13 起不再由尾款/预付款到账时间派生；无记录时也不回退「当前年月」
        addField("submitTimeYm", "上报时间", "subject", "管线定线", "date", false, null);
        // 上报状态（已上报 / 未上报）：判定依据与「上报时间」列严格同源
        //   （有上报记录 = 已上报；2026-10-13 起不再含到账时间，见 ReportDataMapper.xml#reportSubmitExpr）
        // filterable=true ⇒ 报表页「筛选设置」可勾选它按状态过滤，预览 / 导出 / 上报批次三处同口径
        addField("submitStatus", "上报状态", "subject", "管线定线", "select", true,
                toOptions(new String[][]{{"submitted", "已上报"}, {"unsubmitted", "未上报"}}));
        // 兼容旧自定义模板：基于创建时间的上报时间占位
        addField("createTimeYm", "上报时间(年-月)", "agg", "管线定线", "date", false, null);
        // 补验线报表（byx_report）专用字段：关联工程编号 / 关联工程上报领导时间
        // （办结时间 closeTime 已移至「项目信息」组并开放筛选）
        addField("relatedProjectCode", "关联工程编号", "subject", "管线定线", "string", false, null);
        addField("relatedLastSubmitTime", "关联工程上报时间", "subject", "管线定线", "date", false, null);
        // 关联定线项目「时间段」筛选字段（模板5「验线上报产值统计表」/ 模板6「管线定验线报表（补验线）」）：
        //   判定日期 = 关联定线项目的时间段（系统关联项目取其创建时间；手工输入的编号取其编号里的年份）
        //   详见 ReportDataMapper.xml#relatedProjStartExpr / relatedProjEndExpr
        //   仅作筛选字段（filterable=true），未映射为报表列
        addField("relatedProjectCreate", "关联定线项目时间", "subject", "管线定线", "date", true, null);

        // 项目维度结算字段：结算金额 = 外部产值合计；内部产值仅内部参考，不与外部产值相加
        addField("projectSettleAmount", "项目结算金额", "agg", "项目结算", "number", true, null);
        addField("projectInternalOutput", "项目内部产值", "agg", "项目结算", "number", true, null);
        addField("projectReceivedAmount", "项目已收金额", "agg", "项目结算", "number", true, null);
        addField("projectPendingAmount", "项目未收金额", "agg", "项目结算", "number", true, null);
        addField("projectArrearsAmount", "项目欠款金额", "agg", "项目结算", "number", true, null);
        addField("projectOverpaidAmount", "项目挂账金额", "agg", "项目结算", "number", true, null);
        addField("projectSettleStatus", "项目结算状态", "agg", "项目结算", "select", true, SETTLE_STATUS);

        // 模板4「市场性任务到账收入确认表」(scsr_report) 专用列：
        // 入库情况/到账确认 赋空；项目工作量取外部工作量文字描述；质量情况/安全事故记录固定值
        addField("storageStatus", "入库情况", "subject", "项目信息", "string", false, null);
        addField("workloadDesc", "项目工作量", "agg", "项目结算", "string", false, null);
        addField("qualityStatus", "质量情况", "agg", "项目结算", "string", false, null);
        addField("safetyRecord", "安全事故记录", "agg", "项目结算", "string", false, null);
        addField("paymentConfirm", "到账确认", "agg", "收款信息", "string", false, null);

        // 应收账款对账情况统计表(yhdz)专用列：
        // 是否对账=固定"是"；是否发函=固定"否"；对账时间=按客户全称哈希确定性选取当月工作日
        addField("isReconciled", "是否对账", "agg", "对账信息", "string", false, null);
        addField("isSentLetter", "是否发函", "agg", "对账信息", "string", false, null);
        addField("reconcileTime", "对账时间", "agg", "对账信息", "string", false, null);
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
     * 输出列名覆盖表：设计器里为区分来源用了带前缀的字段名（如「项目备注」「到账备注」），
     * 但预览表头与导出 Excel 的列名一律只显示「备注」（客户要求，不带前缀）。
     */
    private static final Map<String, String> OUTPUT_LABEL_OVERRIDES = new LinkedHashMap<>();

    static
    {
        OUTPUT_LABEL_OVERRIDES.put("remark", "备注");
        OUTPUT_LABEL_OVERRIDES.put("paymentRemark", "备注");
    }

    /** 字段池标签（设计器显示名）；未注册返回 null */
    public static String poolLabel(String key)
    {
        if (key == null)
        {
            return null;
        }
        for (Map<String, Object> f : FIXED_FIELDS)
        {
            if (key.equals(f.get("key")))
            {
                return (String) f.get("label");
            }
        }
        return null;
    }

    /**
     * 输出列名覆盖值（预览表头 / 导出 Excel 列名）；无覆盖返回 null。
     * 仅在模板字段的标签仍等于字段池标签（即用户未自定义列名）时使用，避免覆盖模板2
     * 「未回款原因及应对措施」这类历史自定义列名。
     */
    public static String outputLabelOverride(String key)
    {
        return key == null ? null : OUTPUT_LABEL_OVERRIDES.get(key);
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
        return resolveValue(field, row, null);
    }

    /**
     * 见两参重载。submitMonthFallback：未上报且无到账时「上报时间」列的兜底年月（yyyy-MM），
     * 仅「导出并上报领导」场景传值；传 null 时保持留空（避免编造）。
     */
    public static Object resolveValue(ProjReportField field, Map<String, Object> row, String submitMonthFallback)
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
        // 模板3「应收账款与客户对账」列语义定制：
        //   合同金额：单价合同 → 文本"单价合同"；总价合同 → 合同总金额（多项目由导出层合并单元格）；无合同 → 空
        //   欠款金额：项目结算金额（外部产值合计）- 已收金额；未结算（外部产值为空/0）→ 空
        boolean isYhdz = field.getTemplateId() != null && field.getTemplateId() == 3L;
        // 项目名称列 = 该项目的项目类别字段值（模板3专属，不影响其他模板）
        if ("projectName".equals(key) && isYhdz)
        {
            return row.get("engineeringProject");
        }
        if ("contractAmount".equals(key) && isYhdz)
        {
            if ("unit".equals(str(row.get("contractType"))))
            {
                return "单价合同";
            }
            return row.get("contractAmount");
        }
        if ("pendingAmount".equals(key) && isYhdz)
        {
            BigDecimal settle = num(row.get("projectSettleAmount"));
            if (settle == null || settle.compareTo(BigDecimal.ZERO) <= 0)
            {
                return null;
            }
            BigDecimal received = num(row.get("receivedAmount"));
            return settle.subtract(received == null ? BigDecimal.ZERO : received);
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
        // 模板4「市场性任务到账收入确认表」列语义定制：
        //   项目类别列 = 项目类别字段值；到账金额 0 显示空；发票情况仅在到账>0 时显示已/未开票
        boolean isScsr = field.getTemplateId() != null && (field.getTemplateId() == 4L || field.getTemplateId() == 7L);
        if ("categoryName".equals(key) && isScsr)
        {
            return row.get("engineeringProject");
        }
        switch (key) {
            case "receivedAmount" -> {
                // 指令性任务：到账为 0/空 → 回退取「项目原始外部产值」（判定收口在 effectiveReceivedAmount）
                BigDecimal received = effectiveReceivedAmount(row);
                if (isScsr && (received == null || received.compareTo(BigDecimal.ZERO) == 0)) {
                    return null;
                }
                return received;
            }
            case "invoiceFlag" -> {
                BigDecimal invoice = num(row.get("totalInvoiceAmount"));
                if (isScsr) {
                    BigDecimal received = effectiveReceivedAmount(row);
                    if (received == null || received.compareTo(BigDecimal.ZERO) == 0) {
                        return null;
                    }
                }
                return invoice != null && invoice.compareTo(BigDecimal.ZERO) > 0 ? "已开票" : "未开票";
            }

            // 项目结算金额（= 外部产值合计，SQL 已聚合）与项目内部产值直接取行值
            case "projectSettleAmount" -> {
                return row.get("projectSettleAmount");
            }
            case "projectInternalOutput" -> {
                return row.get("projectInternalOutput");
            }
            case "projectReceivedAmount" -> {
                return row.get("receivedAmount");
            }
            case "projectPendingAmount" -> {
                BigDecimal settle = num(row.get("projectSettleAmount"));
                BigDecimal received = num(row.get("receivedAmount"));
                if (settle == null) {
                    return null;
                }
                return settle.subtract(received == null ? BigDecimal.ZERO : received);
            }

            // 项目欠款金额 / 项目挂账金额：SQL 已按业务口径算好（挂账仅限已结算且有外部产值的项目）
            case "projectArrearsAmount" -> {
                return row.get("projectArrearsAmount");
            }
            case "projectOverpaidAmount" -> {
                return row.get("projectOverpaidAmount");
            }
            case "projectSettleStatus" -> {
                BigDecimal settle = num(row.get("projectSettleAmount"));
                BigDecimal received = num(row.get("receivedAmount"));
                if (settle == null || settle.compareTo(BigDecimal.ZERO) == 0) {
                    return "未结清";
                }
                int cmp = received == null ? -1 : received.compareTo(settle);
                return cmp > 0 ? "超额" : (cmp == 0 ? "已结清" : "未结清");
            }
            case "debtMonths" -> {
                // 应收账期：项目办结时间 → 当前日期，按月计算（30.44天/月），保留1位小数
                Date close = date(row.get("closeTime"));
                if (close == null) {
                    return null;
                }
                long days = (System.currentTimeMillis() - close.getTime()) / 86400000L;
                if (days < 0) {
                    days = 0;
                }
                return BigDecimal.valueOf(days / 30.44).setScale(1, BigDecimal.ROUND_HALF_UP);
            }
        }
        // 「验线（2/3）」计算列：有效到账金额 × 2/3（保留两位，四舍五入）
        // 赋值随模板不同对调：
        //   - 模板1「只定未验及补之前扣除项目」(zdyw)：需预留 = 到账×2/3，需补 = 空
        //   - 模板6「补验线」(byx)：需预留 = 空，需补 = 到账×2/3
        // ⚠️ 「到账」取有效到账（指令性任务到账=0 时已回退为原始外部产值，见 effectiveReceivedAmount）
        //    ⇒ 需预留/需补跟随兜底值，与「到账金额」列同一口径
        boolean isByx = field.getTemplateId() != null && field.getTemplateId() == 6L;
        if ("reservedAmount".equals(key))
        {
            if (isByx)
            {
                return null;
            }
            BigDecimal received = effectiveReceivedAmount(row);
            if (received == null)
            {
                return null;
            }
            return received.multiply(new BigDecimal("2"))
                    .divide(new BigDecimal("3"), 2, BigDecimal.ROUND_HALF_UP);
        }
        if ("needSupplement".equals(key))
        {
            if (!isByx)
            {
                return null;
            }
            BigDecimal received = effectiveReceivedAmount(row);
            if (received == null)
            {
                return null;
            }
            return received.multiply(new BigDecimal("2"))
                    .divide(new BigDecimal("3"), 2, BigDecimal.ROUND_HALF_UP);
        }
        // 管线验线占位列：验线未开展，系统编号/上报时间在报表中固定为空
        if ("verifySystemNo".equals(key) || "verifyReportTime".equals(key))
        {
            return null;
        }
        // 管线定线「上报时间」（年-月）= 系统上报记录月份（唯一来源）：
        //   1) 有上报记录（proj_report_submit_log.submit_time，含历史导入补录行）→ 取该记录时间
        //      （终身上报一次不可改；补录行亦视为历史已上报）
        //   2) 无记录 → 仅当本次为「导出并上报领导」（勾选上报）时，按报表年月填充
        //   3) 无记录且非上报场景 → 空
        // ⚠️ 2026-10-13 起：不再把尾款/预付款到账时间当作上报时间（到账 ≠ 上报）
        if ("submitTimeYm".equals(key))
        {
            Date submit = date(row.get("submitTime"));
            if (submit != null)
            {
                return new java.text.SimpleDateFormat("yyyy-MM").format(submit);
            }
            // 无上报记录：仅当本次为「导出并上报领导」（勾选上报）时，按报表年月填充；
            // 否则保持留空（不回退当前年月，避免导出表格里出现编造的上报时间）
            return (submitMonthFallback == null || submitMonthFallback.isEmpty()) ? null : submitMonthFallback;
        }
        // 上报状态：直接取 SQL 计算列 submitStatus（与「上报时间」列同一表达式，不可能不一致）
        // 该字段也可被勾为导出列，故返回中文取值而非 submitted/unsubmitted
        if ("submitStatus".equals(key))
        {
            String v = str(row.get("submitStatus"));
            if (v == null || v.isEmpty())
            {
                return null;
            }
            return "submitted".equals(v) ? "已上报" : "未上报";
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
        // 模板4「市场性任务到账收入确认表」专用列：
        // 入库情况 / 到账确认 —— 系统无对应数据，固定为空
        if ("storageStatus".equals(key) || "paymentConfirm".equals(key))
        {
            return null;
        }
        // 项目工作量 —— 外部工作量文字描述（SQL w_desc 子查询聚合为「类别名:工作量+单位」）
        if ("workloadDesc".equals(key))
        {
            return row.get("workloadDesc");
        }
        // 质量情况 / 安全事故记录 —— 客户模板固定值
        if ("qualityStatus".equals(key))
        {
            return "良";
        }
        if ("safetyRecord".equals(key))
        {
            return "无";
        }

        // 应收账款对账统计表(yhdz)专用列
        if ("isReconciled".equals(key))
        {
            return "是";
        }
        if ("isSentLetter".equals(key))
        {
            return "否";
        }
        if ("reconcileTime".equals(key))
        {
            return resolveReconcileTime(row.get("clientUnit"));
        }

        // 字典转换
        if ("projectStatus".equals(key))
        {
            String v = str(row.get(key));
            return v == null ? null : PROJECT_STATUS.getOrDefault(v, v);
        }

        // 项目性质（normal / mandate）：作为列输出时同样要转中文，否则 Excel 里会显示英文码值
        if ("projectNature".equals(key))
        {
            String v = str(row.get(key));
            if (v == null || v.isEmpty())
            {
                return "市场性任务";   // 与表默认值 'normal' 对齐（project_nature 为非空列，空值只可能是历史脏数据）
            }
            return PROJECT_NATURE.getOrDefault(v, v);
        }

        // 是否结算（字符标志位 0/1，非字典字段）：必须转中文，否则导出/预览会直接显示 0 或 1
        if ("isSettled".equals(key))
        {
            String v = str(row.get(key));
            if (v == null || v.isEmpty())
            {
                return null;
            }
            return "1".equals(v.trim()) ? "已结算" : "未结算";
        }

        // 固定值字段
        if ("deptName".equals(key))
        {
            return "地下空间工程中心";
        }

        // 直接取行值
        return row.get(key);
    }

    /**
     * 「有效到账金额」：常规取到账金额（row.receivedAmount）；
     * 当项目为<b>指令性任务</b>（proj_project.project_nature = 'mandate'）且到账为 0/空时，
     * 回退取<b>项目原始外部产值</b>（proj_workload.external_output 合计；
     * 该值在报表 SQL 的 w_sum 子查询内<b>未</b>剔除 mandate，别名 projectRawExternalOutput）。
     *
     * <p>口径唯一收口于此，以下三处共用，保证预览 / 导出 / 上报快照完全一致：</p>
     * <ul>
     *   <li>模板 1/4/5/6/7「到账金额」列取值</li>
     *   <li>模板 4/7「发票情况」列的「到账为 0 ⇒ 留空」判定</li>
     *   <li>模板 1/6「需预留 / 需补」= 有效到账 × 2/3</li>
     * </ul>
     */
    private static BigDecimal effectiveReceivedAmount(Map<String, Object> row)
    {
        BigDecimal received = num(row.get("receivedAmount"));
        if (received != null && received.signum() != 0)
        {
            return received;
        }
        if ("mandate".equals(str(row.get("projectNature"))))
        {
            BigDecimal raw = num(row.get("projectRawExternalOutput"));
            if (raw != null && raw.signum() != 0)
            {
                return raw;
            }
        }
        return received;
    }

    /**
     * 对账时间：按客户全称哈希确定性选取当月工作日（同一客户每次导出结果一致）。
     * 格式 "2026年7月15日"，范围 = 当月1日至今天的工作日（周一至周五）。
     */
    private static String resolveReconcileTime(Object clientObj)
    {
        String client = str(clientObj);
        if (client == null || client.isEmpty())
        {
            return null;
        }
        java.util.Calendar now = java.util.Calendar.getInstance();
        int year = now.get(java.util.Calendar.YEAR);
        int month = now.get(java.util.Calendar.MONTH);       // 0-based
        int today = now.get(java.util.Calendar.DAY_OF_MONTH);

        // 收集当月工作日（周一至周五，不超过今天）
        java.util.List<Integer> workingDays = new java.util.ArrayList<>();
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(year, month, 1);
        int maxDay = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH);
        for (int d = 1; d <= Math.min(today, maxDay); d++)
        {
            cal.set(java.util.Calendar.DAY_OF_MONTH, d);
            int dow = cal.get(java.util.Calendar.DAY_OF_WEEK);
            if (dow != java.util.Calendar.SATURDAY && dow != java.util.Calendar.SUNDAY)
            {
                workingDays.add(d);
            }
        }
        if (workingDays.isEmpty())
        {
            return null;
        }
        // 同一客户全称 → 同一工作日（哈希取模，确定性）
        int idx = Math.floorMod(client.hashCode(), workingDays.size());
        int day = workingDays.get(idx);
        return year + "年" + (month + 1) + "月" + day + "日";
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
