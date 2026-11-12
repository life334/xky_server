package com.xakcch.project.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.xakcch.common.utils.DateUtils;
import com.xakcch.common.utils.DictUtils;
import com.xakcch.common.utils.StringUtils;
import com.xakcch.common.utils.WorkdayUtils;
import com.xakcch.project.domain.ProjPayment;
import com.xakcch.project.domain.ProjProject;
import com.xakcch.project.domain.ProjWorkload;
import com.xakcch.project.mapper.ProjCategoryBillingMapper;
import com.xakcch.project.mapper.ProjPaymentMapper;
import com.xakcch.project.mapper.ProjWorkloadMapper;
import com.xakcch.common.core.domain.entity.SysDictData;
import com.xakcch.system.service.ISysWorkdayCalendarService;

/**
 * 项目导出数据装配器（唯一出处）——项目列表导出 / 费用结算导出 **共用**。
 *
 * <p>背景（2026-11-11）：两个页面的导出可选字段统一到
 * {@link ProjectExportFieldPool}，但取值逻辑原先分散在两处
 * （项目列表走 {@code ProjProjectServiceImpl} 的取值器；结算走 Controller 内的
 * 付款/工作量聚合）。为满足「同一字段两页取值口径一致」，把下面这些逻辑收口到这里：
 * <ul>
 *   <li>批量加载工作量 / 付款（一次查询，禁 N+1）</li>
 *   <li>项目级结算节点：项目字段 + 付款聚合（预付款/尾款/退款/开票/拟上报时间/付款备注）
 *       + 工作量汇总（内部产值 <b>含 6000 保底</b>）</li>
 *   <li>工作量明细文本（一个单元格内多行，按计费类别排序）</li>
 *   <li>字典预载（避免逐单元格读 Redis，见 MEMORY 铁律 #30）</li>
 *   <li>单元格取值格式化（字典转标签 / 日期 / 金额数值）</li>
 * </ul>
 *
 * <p>⚠️ 行粒度不在这里决定：项目列表 = 一项目一行（人级字段由调用方合并），
 *    费用结算 = 一负责人一行（拆行逻辑仍在 Controller）。本类只保证「项目级取值」一致。
 */
@Component
public class ProjectExportAggregator
{
    /** 无计费类别的工作量归入此类别，保证明细不丢行 */
    private static final String UNCATEGORIZED = "未分类";

    /** 内部工作量「管线新测 / 管线修测」合并保底产值（元） */
    private static final BigDecimal INTERNAL_REVISE_MIN = new BigDecimal("6000");

    /** 单个单元格最多列出的明细行数（超出截断并追加「…等 N 项」） */
    private static final int MAX_DETAIL_LINES = 30;

    /** 开票状态码 → 中文：与前端 utils/projStatus.js 的 INVOICE_STATUS_MAP 保持一致 */
    private static final Map<String, String> INVOICE_STATUS_TEXT = buildInvoiceStatusText();

    private static Map<String, String> buildInvoiceStatusText()
    {
        Map<String, String> m = new HashMap<>();
        m.put("invoiced", "已开");
        m.put("pending", "未开");
        m.put("voided", "已作废");
        m.put("cancelled", "已作废");
        m.put("canceled", "已作废");
        m.put("invalid", "已作废");
        return Collections.unmodifiableMap(m);
    }

    @Autowired
    private ProjWorkloadMapper workloadMapper;

    @Autowired
    private ProjPaymentMapper paymentMapper;

    @Autowired
    private ProjCategoryBillingMapper categoryBillingMapper;

    @Autowired
    private ISysWorkdayCalendarService workdayCalendarService;

    // ==================== 批量加载 ====================

    /** 批量加载项目的工作量明细（避免逐项目 N+1） */
    public Map<Long, List<ProjWorkload>> loadWorkloadMap(List<ProjProject> projects)
    {
        if (projects == null || projects.isEmpty())
        {
            return Collections.emptyMap();
        }
        Long[] ids = projects.stream().map(ProjProject::getId).toArray(Long[]::new);
        return workloadMapper.selectWorkloadsByProjectIds(ids).stream()
                .collect(Collectors.groupingBy(ProjWorkload::getProjectId));
    }

    /** 批量加载项目的付款记录 */
    public Map<Long, List<ProjPayment>> loadPaymentMap(List<ProjProject> projects)
    {
        if (projects == null || projects.isEmpty())
        {
            return Collections.emptyMap();
        }
        Long[] ids = projects.stream().map(ProjProject::getId).toArray(Long[]::new);
        return paymentMapper.selectPaymentsByProjectIds(ids).stream()
                .collect(Collectors.groupingBy(ProjPayment::getProjectId));
    }

    /**
     * 计费类别排序字典：来自 proj_category_billing.sort_order。
     * 只是锦上添花：查不到就退化成按类别名排序，不影响导出内容。
     */
    public Map<String, Integer> loadCategoryOrder()
    {
        Map<String, Integer> orderMap = new HashMap<>();
        try
        {
            for (Map<String, Object> row : categoryBillingMapper.selectBillingCategoryOrders())
            {
                Object nameObj = row.get("name") != null ? row.get("name") : row.get("NAME");
                if (nameObj == null)
                {
                    continue;
                }
                Object ordObj = row.get("sortOrder") != null ? row.get("sortOrder") : row.get("sortorder");
                int ord = (ordObj instanceof Number) ? ((Number) ordObj).intValue() : 9999;
                String name = catName(String.valueOf(nameObj));
                Integer old = orderMap.get(name);
                if (old == null || ord < old)
                {
                    orderMap.put(name, ord);
                }
            }
        }
        catch (Exception ignored)
        {
            // 排序字典查不到不影响导出
        }
        return orderMap;
    }

    /**
     * 进行中项目的「总时长」实时计算（整批只拉一次日历，再逐行复用）。
     * 已办结 / 已归档项目用冻结值，不在此处计算。
     */
    public Map<Long, Integer> loadLiveDuration(List<ProjProject> projects)
    {
        Map<Long, Integer> live = new HashMap<>();
        if (projects == null || projects.isEmpty())
        {
            return live;
        }
        List<ProjProject> pending = new ArrayList<>();
        LocalDate earliest = null;
        for (ProjProject p : projects)
        {
            if (!isFinished(p) && p.getAssignDate() != null)
            {
                LocalDate a = WorkdayUtils.toLocalDate(p.getAssignDate());
                if (earliest == null || a.isBefore(earliest))
                {
                    earliest = a;
                }
                pending.add(p);
            }
        }
        if (pending.isEmpty())
        {
            return live;
        }
        LocalDate today = LocalDate.now();
        Map<LocalDate, String> calendarMap = workdayCalendarService.getCalendarMap(earliest, today);
        for (ProjProject p : pending)
        {
            LocalDate a = WorkdayUtils.toLocalDate(p.getAssignDate());
            live.put(p.getId(), WorkdayUtils.countWorkdays(a, today, calendarMap));
        }
        return live;
    }

    /** 已办结 / 已归档：总时长为冻结值 */
    public boolean isFinished(ProjProject p)
    {
        String st = p.getStatus();
        return "closed".equals(st) || "archived".equals(st);
    }

    // ==================== 节点组装 ====================

    /**
     * 组装单个项目的结算节点：项目字段（字段池全部项目级 key）+ 工作量汇总
     * + 付款/发票聚合 + 结算状态。
     *
     * <p>⚠️ {@code internalWorkload}/{@code externalWorkload} 在此处是<b>数字汇总</b>
     * （页面树形表格用）；导出时这两列改用 {@link #buildWorkloadText} 的明细文本，
     * 由调用方覆盖，不要在这里改成文本。</p>
     */
    public Map<String, Object> buildNode(ProjProject p,
        Map<Long, List<ProjWorkload>> workloadMap, Map<Long, List<ProjPayment>> paymentMap)
    {
        Map<String, Object> node = new LinkedHashMap<>();
        List<ProjWorkload> workloads = workloadMap.getOrDefault(p.getId(), Collections.emptyList());
        List<ProjPayment> payments = paymentMap.getOrDefault(p.getId(), Collections.emptyList());

        // ---- 项目基础字段（字段池项目信息分组全量） ----
        node.put("id", "p" + p.getId());
        node.put("projectId", p.getId());
        node.put("projectCode", p.getProjectCode());
        node.put("relatedProjectCode", p.getRelatedProjectCode());
        node.put("projectName", p.getProjectName());
        node.put("clientUnit", p.getClientUnit());
        node.put("clientUnitNature", p.getClientUnitNature());
        node.put("contactName", p.getContactName());
        node.put("contactPhone", p.getContactPhone());
        node.put("projectLocation", p.getProjectLocation());
        node.put("engineeringProject", p.getEngineeringProject());
        node.put("categoryName", p.getCategoryName());
        node.put("status", p.getStatus());
        node.put("projectNature", p.getProjectNature());
        node.put("closeTime", p.getCloseTime());
        node.put("assignDate", p.getAssignDate());
        node.put("durationRequire", p.getDurationRequire());
        node.put("totalDuration", p.getTotalDuration());
        node.put("dataSource", p.getDataSource());
        node.put("remark", p.getRemark());
        node.put("contractName", p.getContractName());
        node.put("contractNo", p.getContractNo());
        node.put("contractAmount", p.getContractAmount());
        node.put("leaderNames", p.getLeaderNames());
        // 拟上报时间（付款笔级：每一笔付款各自一个拟上报月份，去重升序用「、」拼接）
        node.put("planReportTime", payPlanReportMonths(payments));

        // ---- 付款信息（预付款/尾款/退款/开票/付款备注） ----
        fillPaymentInfo(node, payments, p.getPayRemark());

        // ---- 工作量汇总 ----
        BigDecimal totalWorkload = BigDecimal.ZERO;
        BigDecimal totalInternalWorkload = BigDecimal.ZERO;
        BigDecimal totalExternalWorkload = BigDecimal.ZERO;
        BigDecimal totalExternalOutput = BigDecimal.ZERO;
        for (ProjWorkload w : workloads)
        {
            if (w.getWorkload() != null)
            {
                totalWorkload = totalWorkload.add(w.getWorkload());
                if ("internal".equals(w.getBillingType()))
                {
                    totalInternalWorkload = totalInternalWorkload.add(w.getWorkload());
                }
                else if ("external".equals(w.getBillingType()))
                {
                    totalExternalWorkload = totalExternalWorkload.add(w.getWorkload());
                }
            }
            if (w.getExternalOutput() != null)
            {
                totalExternalOutput = totalExternalOutput.add(w.getExternalOutput());
            }
        }
        // 内部产值合计（含「管线新测 + 管线修测」保底 6000）—— 两页统一口径
        BigDecimal totalInternalOutput = calcInternalOutputTotal(workloads);

        node.put("workload", totalWorkload);
        node.put("internalWorkload", totalInternalWorkload);
        node.put("externalWorkload", totalExternalWorkload);
        node.put("internalWorkloadDetail", groupWorkloadDetail(workloads, "internal"));
        node.put("externalWorkloadDetail", groupWorkloadDetail(workloads, "external"));
        node.put("internalOutput", totalInternalOutput);
        node.put("externalOutput", totalExternalOutput);
        node.put("workloadCount", workloads.size());

        int paymentCnt = 0;
        for (ProjPayment pm : payments)
        {
            if ("advance".equals(pm.getPaymentType()) || "final".equals(pm.getPaymentType()))
            {
                paymentCnt++;
            }
        }
        node.put("paymentCount", paymentCnt);

        // 结算状态 + 已收/待收差额（结算总额 = 外部产值合计）
        fillSettlementSummary(node, payments, totalExternalOutput);

        // 开票/付款组合状态：not_invoiced / invoiced_unpaid / invoiced_paid / voided
        String invStatus = (String) node.get("invoiceStatus");
        BigDecimal invAmt = (BigDecimal) node.get("invoiceAmount");
        String invDate = (String) node.get("invoiceDate");
        boolean hasInvoice = (invDate != null && !invDate.isEmpty())
            || (invAmt != null && invAmt.compareTo(BigDecimal.ZERO) > 0);
        boolean isVoided = isVoidedStatus(invStatus);
        BigDecimal recv = (BigDecimal) node.get("receivedAmount");
        boolean hasPaid = recv != null && recv.compareTo(BigDecimal.ZERO) > 0;
        String invoicePaymentStatus;
        if (isVoided)
        {
            invoicePaymentStatus = "voided";
        }
        else if (hasInvoice && !hasPaid)
        {
            invoicePaymentStatus = "invoiced_unpaid";
        }
        else if (hasInvoice && hasPaid)
        {
            invoicePaymentStatus = "invoiced_paid";
        }
        else
        {
            invoicePaymentStatus = "not_invoiced";
        }
        node.put("invoicePaymentStatus", invoicePaymentStatus);
        return node;
    }

    /**
     * 内部产值合计（含保底）：
     * 保底按「栏」= (userId, subItemNo) 各自计算 ——
     * 每栏内部产值 = 该栏其他内部产值 + max(该栏「管线新测」+「管线修测」产值, 6000)；
     * 内部产值合计 = 各栏之和。保底差额只体现在合计中，不改变各行的 internalOutput。
     * ⚠️ 与 ProjWorkloadMapper.xml#outputProjectAgg 同口径（内层分栏保底、外层求和）。
     */
    public BigDecimal calcInternalOutputTotal(List<ProjWorkload> workloads)
    {
        if (workloads == null || workloads.isEmpty())
        {
            return BigDecimal.ZERO;
        }
        Map<String, BigDecimal> laneTotal = new LinkedHashMap<>();
        Map<String, BigDecimal> laneRevise = new LinkedHashMap<>();
        for (ProjWorkload w : workloads)
        {
            if (w.getInternalOutput() == null) continue;
            String key = w.getUserId() + "#" + w.getSubItemNo();
            BigDecimal out = w.getInternalOutput();
            laneTotal.merge(key, out, BigDecimal::add);
            if ("管线新测".equals(w.getBillingCategory()) || "管线修测".equals(w.getBillingCategory()))
            {
                laneRevise.merge(key, out, BigDecimal::add);
            }
        }
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> e : laneTotal.entrySet())
        {
            BigDecimal laneSum = e.getValue();
            BigDecimal reviseSum = laneRevise.get(e.getKey());
            if (reviseSum != null && reviseSum.compareTo(INTERNAL_REVISE_MIN) < 0)
            {
                laneSum = laneSum.subtract(reviseSum).add(INTERNAL_REVISE_MIN);
            }
            total = total.add(laneSum);
        }
        return total;
    }

    /**
     * 一栏的内部产值（套用保底 6000 后）。该栏没有任何内部产值时返回 null（单元格留空）。
     */
    public BigDecimal laneInternalOutput(List<ProjWorkload> lane)
    {
        if (lane == null || lane.isEmpty())
        {
            return null;
        }
        boolean any = false;
        for (ProjWorkload w : lane)
        {
            if (w.getInternalOutput() != null)
            {
                any = true;
                break;
            }
        }
        return any ? calcInternalOutputTotal(lane) : null;
    }

    // ==================== 工作量明细文本 ====================

    /**
     * 工作量明细文本：一个单元格内按工作量行逐条换行，格式 {@code 类别名：2.1千米}。
     * 同一计费类别多条就重复列多行（不合并）；无该类型的工作量返回 null（单元格留空）。
     */
    public String buildWorkloadText(List<ProjWorkload> workloads, String billingType,
                                    Map<String, Integer> catOrder)
    {
        if (workloads == null || workloads.isEmpty())
        {
            return null;
        }
        List<ProjWorkload> list = new ArrayList<>();
        for (ProjWorkload w : workloads)
        {
            if (billingType.equals(w.getBillingType()))
            {
                list.add(w);
            }
        }
        if (list.isEmpty())
        {
            return null;
        }
        Map<String, Integer> order = catOrder == null ? Collections.emptyMap() : catOrder;
        // 按计费类别排序（sort_order）；稳定排序，同类别保持原始录入顺序
        list.sort((a, b) -> {
            int oa = order.getOrDefault(catName(a.getBillingCategory()), 9999);
            int ob = order.getOrDefault(catName(b.getBillingCategory()), 9999);
            return oa != ob ? Integer.compare(oa, ob) : 0;
        });

        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (ProjWorkload w : list)
        {
            if (n == MAX_DETAIL_LINES)
            {
                sb.append("\n…等 ").append(list.size() - n).append(" 项");
                break;
            }
            if (n > 0)
            {
                sb.append('\n');
            }
            sb.append(catName(w.getBillingCategory())).append("：");
            BigDecimal wl = w.getWorkload();
            if (wl != null)
            {
                sb.append(fmtNum(wl));
                String unit = w.getPriceUnit();
                if (unit != null && !unit.trim().isEmpty())
                {
                    sb.append(' ').append(unit.trim());
                }
            }
            n++;
        }
        return sb.toString();
    }

    /** 该登记人某类型的产值合计；一条都没有时返回 null（单元格留空） */
    public BigDecimal sumOutput(List<ProjWorkload> workloads, boolean external)
    {
        if (workloads == null || workloads.isEmpty())
        {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        boolean any = false;
        for (ProjWorkload w : workloads)
        {
            if (external != "external".equals(w.getBillingType()))
            {
                continue;
            }
            any = true;
            BigDecimal v = external ? w.getExternalOutput() : w.getInternalOutput();
            if (v != null)
            {
                sum = sum.add(v);
            }
        }
        return any ? sum : null;
    }

    // ==================== 字典与格式化 ====================

    /**
     * 按导出字段涉及的字典类型一次性加载 value → label 映射。
     *
     * <p>⚠️ 性能要点：{@link DictUtils#getDictLabel} 每次调用都会「Redis 读 + 全量反序列化」，
     * 实测单单元格 ≈8.4ms。批量导出必须先预载映射，循环内纯内存查表。</p>
     */
    public Map<String, Map<String, String>> loadDictMaps(List<String> keys)
    {
        Map<String, Map<String, String>> result = new HashMap<>();
        if (keys == null)
        {
            return result;
        }
        for (String key : keys)
        {
            ProjectExportFieldPool.Field f = ProjectExportFieldPool.get(key);
            if (f == null || f.getDictType() == null || result.containsKey(f.getDictType()))
            {
                continue;
            }
            Map<String, String> map = new HashMap<>();
            List<SysDictData> datas = DictUtils.getDictCache(f.getDictType());
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
            result.put(f.getDictType(), map);
        }
        return result;
    }

    /**
     * 单个单元格取值：字典转标签（预载未命中回落逐次查询）、开票状态转中文、
     * 日期统一 yyyy-MM-dd、金额/数值保留数字（导出右对齐）。
     */
    public Object formatValue(String key, String type, Object raw, Map<String, Map<String, String>> dictMaps)
    {
        if (raw == null || "".equals(raw))
        {
            return "";
        }
        if ("invoiceStatus".equals(key))
        {
            String v = String.valueOf(raw);
            return INVOICE_STATUS_TEXT.getOrDefault(v, INVOICE_STATUS_TEXT.getOrDefault(v.toLowerCase(), v));
        }
        if ("dict".equals(type))
        {
            ProjectExportFieldPool.Field f = ProjectExportFieldPool.get(key);
            String dictType = f == null ? null : f.getDictType();
            if (dictType != null)
            {
                String val = String.valueOf(raw);
                Map<String, String> dm = dictMaps == null ? null : dictMaps.get(dictType);
                String label = (dm == null) ? null : dm.get(val);
                if (label == null)
                {
                    // 预载缺失（字典在导出期间被改过）才回落逐次查询，保证不丢标签
                    label = DictUtils.getDictLabel(dictType, val);
                }
                return (label == null || label.isEmpty()) ? val : label;
            }
            return String.valueOf(raw);
        }
        if ("date".equals(type))
        {
            return (raw instanceof Date)
                    ? DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD, (Date) raw)
                    : String.valueOf(raw);
        }
        if ("duration".equals(type))
        {
            return raw + "天";
        }
        if ("number".equals(type) || "money".equals(type))
        {
            if (raw instanceof Number)
            {
                return raw;
            }
            try
            {
                return new BigDecimal(String.valueOf(raw));
            }
            catch (NumberFormatException e)
            {
                return String.valueOf(raw);
            }
        }
        return String.valueOf(raw);
    }

    /** 数值文本化：去掉多余的零，最多保留 2 位小数（2.10 → 2.1） */
    public String fmtNum(BigDecimal v)
    {
        if (v == null)
        {
            return "";
        }
        BigDecimal s = v.stripTrailingZeros();
        if (s.scale() > 2)
        {
            s = s.setScale(2, RoundingMode.HALF_UP);
        }
        return s.toPlainString();
    }

    // ==================== 内部辅助（与结算 Controller 原实现逐行等价） ====================

    /** 计费类别名归一化：空值统一归入「未分类」，保证明细不丢行 */
    private String catName(String cat)
    {
        return (cat == null || cat.trim().isEmpty()) ? UNCATEGORIZED : cat.trim();
    }

    /**
     * 该项目所有付款笔的「拟上报月份」去重升序拼接（{@code 2025-11、2026-03}）。
     * 某笔未填按口径兜底为其到账时间的年月；退款笔不参与。
     */
    private String payPlanReportMonths(List<ProjPayment> payments)
    {
        if (payments == null || payments.isEmpty())
        {
            return "";
        }
        return payments.stream()
            .filter(pm -> !"refund".equals(pm.getPaymentType()))
            .map(pm -> StringUtils.isNotBlank(pm.getPlanReportMonth()) ? pm.getPlanReportMonth().trim()
                : (pm.getPayTime() != null ? DateUtils.parseDateToStr("yyyy-MM", pm.getPayTime()) : ""))
            .filter(StringUtils::isNotBlank)
            .distinct()
            .sorted()
            .collect(Collectors.joining("、"));
    }

    /**
     * 到账备注：非退款付款笔的 remark 去空、去重后按「；」拼接。
     * 退款笔的 remark 是「退款原因」，不属于到账备注，不参与拼接。
     */
    private String payRemarks(List<ProjPayment> payments)
    {
        if (payments == null || payments.isEmpty())
        {
            return "";
        }
        Set<String> remarks = new LinkedHashSet<>();
        for (ProjPayment pm : payments)
        {
            if ("refund".equals(pm.getPaymentType()))
            {
                continue;
            }
            String r = pm.getRemark();
            if (StringUtils.isNotBlank(r))
            {
                remarks.add(r.trim());
            }
        }
        return String.join("；", remarks);
    }

    /** 填充付款信息（预付款 / 尾款 / 退款 / 开票 / 付款备注） */
    private void fillPaymentInfo(Map<String, Object> node, List<ProjPayment> payments, String projectPayRemark)
    {
        ProjPayment prepay = null;
        ProjPayment tail = null;
        BigDecimal refundAmount = BigDecimal.ZERO;
        Date lastRefundTime = null;
        for (ProjPayment pm : payments)
        {
            if ("advance".equals(pm.getPaymentType()))
            {
                prepay = pm;
            }
            else if ("final".equals(pm.getPaymentType()))
            {
                tail = pm;
            }
            else if ("refund".equals(pm.getPaymentType()))
            {
                if (pm.getAmount() != null)
                {
                    refundAmount = refundAmount.add(pm.getAmount());
                }
                if (pm.getPayTime() != null && (lastRefundTime == null || pm.getPayTime().after(lastRefundTime)))
                {
                    lastRefundTime = pm.getPayTime();
                }
            }
        }
        if (prepay != null)
        {
            node.put("prepayAmount", prepay.getAmount());
            node.put("prepayDate", dateStr(prepay.getPayTime()));
            node.put("payUnit", prepay.getPayUnit());
            node.put("payMethod", prepay.getPayMethod());
        }
        // 「付款备注」：项目级字段优先（proj_project.pay_remark），为空回退到非退款付款笔的 remark
        String payRemarkText = StringUtils.isNotBlank(projectPayRemark)
            ? projectPayRemark.trim() : payRemarks(payments);
        node.put("payRemark", payRemarkText);
        if (tail != null)
        {
            node.put("tailAmount", tail.getAmount());
            node.put("tailDate", dateStr(tail.getPayTime()));
        }
        node.put("refundAmount", refundAmount);
        node.put("refundDate", dateStr(lastRefundTime));
        // 发票信息（预付款优先，无预付款取尾款）
        ProjPayment invoiceSource = prepay != null ? prepay : tail;
        if (invoiceSource != null)
        {
            node.put("invoiceUnit", invoiceSource.getInvoiceUnit());
            node.put("invoiceDate", dateStr(invoiceSource.getInvoiceDate()));
            node.put("invoiceAmount", invoiceSource.getInvoiceAmount());
            node.put("invoiceStatus", invoiceSource.getInvoiceStatus());
        }
    }

    /** 填充结算核对摘要：已收 / 待收差额 / 结算状态（settled=已结清 pending=未结清 overdue=超额） */
    private void fillSettlementSummary(Map<String, Object> node, List<ProjPayment> payments,
                                       BigDecimal settlementAmount)
    {
        BigDecimal receivedAmount = BigDecimal.ZERO;
        for (ProjPayment pm : payments)
        {
            if (pm.getAmount() == null) continue;
            if ("refund".equals(pm.getPaymentType()))
            {
                receivedAmount = receivedAmount.subtract(pm.getAmount());
            }
            else
            {
                receivedAmount = receivedAmount.add(pm.getAmount());
            }
        }
        BigDecimal pendingAmount = settlementAmount.subtract(receivedAmount);

        String settlementStatus;
        if (pendingAmount.compareTo(BigDecimal.ZERO) < 0)
        {
            settlementStatus = "overdue";
        }
        else if (pendingAmount.compareTo(BigDecimal.ZERO) == 0 && settlementAmount.compareTo(BigDecimal.ZERO) > 0)
        {
            settlementStatus = "settled";
        }
        else
        {
            settlementStatus = "pending";
        }
        node.put("receivedAmount", receivedAmount);
        node.put("pendingAmount", pendingAmount);
        node.put("settlementStatus", settlementStatus);
    }

    /**
     * 按计费类别 + 计价单位聚合工作量明细（列表「内部/外部工作量」列悬浮展示构成）
     * 返回 [{ category, unit, unitPrice, value, count }]，按 value 降序、类别名升序
     */
    private List<Map<String, Object>> groupWorkloadDetail(List<ProjWorkload> workloads, String billingType)
    {
        Map<String, Map<String, Object>> agg = new LinkedHashMap<>();
        for (ProjWorkload w : workloads)
        {
            if (!billingType.equals(w.getBillingType())) continue;
            if (w.getWorkload() == null) continue;
            String category = (w.getBillingCategory() == null || w.getBillingCategory().isEmpty())
                ? "未分类" : w.getBillingCategory();
            String unit = w.getPriceUnit() == null ? "" : w.getPriceUnit();
            String key = category + "\u0001" + unit;
            Map<String, Object> item = agg.get(key);
            if (item == null)
            {
                item = new LinkedHashMap<>();
                item.put("category", category);
                item.put("unit", unit);
                item.put("unitPrice", null);
                item.put("value", BigDecimal.ZERO);
                item.put("count", 0);
                agg.put(key, item);
            }
            if (item.get("unitPrice") == null && w.getUnitPrice() != null)
            {
                item.put("unitPrice", w.getUnitPrice());
            }
            item.put("value", ((BigDecimal) item.get("value")).add(w.getWorkload()));
            item.put("count", ((Integer) item.get("count")) + 1);
        }
        List<Map<String, Object>> list = new ArrayList<>(agg.values());
        list.sort((a, b) -> {
            int cmp = ((BigDecimal) b.get("value")).compareTo((BigDecimal) a.get("value"));
            if (cmp != 0) return cmp;
            return ((String) a.get("category")).compareTo((String) b.get("category"));
        });
        return list;
    }

    private static boolean isVoidedStatus(String status)
    {
        return "voided".equals(status) || "已作废".equals(status);
    }

    /** 日期文本化（线程安全，不用共享 SimpleDateFormat） */
    private String dateStr(Date date)
    {
        return date == null ? "" : DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD, date);
    }
}
