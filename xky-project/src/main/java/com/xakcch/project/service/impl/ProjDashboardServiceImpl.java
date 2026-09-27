package com.xakcch.project.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.xakcch.common.utils.WorkdayUtils;
import com.xakcch.project.domain.ProjDashboardQuery;
import com.xakcch.project.mapper.ProjDashboardMapper;
import com.xakcch.project.service.IProjDashboardService;
import com.xakcch.system.service.ISysWorkdayCalendarService;

/**
 * 首页驾驶舱 业务实现
 *
 * <p>v2（2026-09-27 契约）：summary / structure / trend / risk 四端点。
 * 口径与分层铁律见 ProjDashboardMapper.xml v2 段头注释：
 * 工作日日期算术只存在于 WorkdayUtils 一处，Java 层用 calendarMap 计算，
 * SQL 只拉候选行；超期预警仅针对 data_source='manual' 的手动录入项目。
 *
 * @author liuyonghui
 */
@Service
public class ProjDashboardServiceImpl implements IProjDashboardService
{
    // ===== 类型 5 桶映射（后端唯一出处，契约 §5；类别按 name 精确匹配，对不上一律 qita） =====
    private static final String BUCKET_DINGXIAN = "dingxian";
    private static final String BUCKET_YINXIAN = "yinxian";
    private static final String BUCKET_GUANXIANTU = "guanxiantu";
    private static final String BUCKET_SHICE = "shice";
    private static final String BUCKET_QITA = "qita";

    private static final String[] BUCKET_ORDER = {
            BUCKET_DINGXIAN, BUCKET_YINXIAN, BUCKET_GUANXIANTU, BUCKET_SHICE, BUCKET_QITA };

    /** 类别名 → 桶（工程大类下的「实测」不映射，落其它） */
    private static final Map<String, String> CATEGORY_NAME_TO_BUCKET = new HashMap<>();
    static
    {
        CATEGORY_NAME_TO_BUCKET.put("管线定线", BUCKET_DINGXIAN);
        CATEGORY_NAME_TO_BUCKET.put("管线验线", BUCKET_YINXIAN);
        CATEGORY_NAME_TO_BUCKET.put("管线图测量", BUCKET_GUANXIANTU);
        CATEGORY_NAME_TO_BUCKET.put("管线实测", BUCKET_SHICE);
    }

    /** 桶 → 展示名（LinkedHashMap 保证顺序） */
    private static final Map<String, String> BUCKET_LABEL = new LinkedHashMap<>();
    static
    {
        BUCKET_LABEL.put(BUCKET_DINGXIAN, "定线");
        BUCKET_LABEL.put(BUCKET_YINXIAN, "验线");
        BUCKET_LABEL.put(BUCKET_GUANXIANTU, "管线图");
        BUCKET_LABEL.put(BUCKET_SHICE, "实测");
        BUCKET_LABEL.put(BUCKET_QITA, "其它");
    }

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private ProjDashboardMapper dashboardMapper;

    @Autowired
    private ISysWorkdayCalendarService workdayCalendarService;

    /** 需求7「录入 N 个工作日后未关联合同」阈值（契约 §8 定稿 3，留配置开关） */
    @Value("${dashboard.contractMissingWorkdays:3}")
    private int contractMissingWorkdays;

    // ============================================================
    // ===== 旧接口（保留作回滚垫，新首页验收后下线） =====
    // ============================================================

    @Override
    public Map<String, Object> getDashboardData(String beginDate, String endDate)
    {
        Map<String, Object> result = new HashMap<>();

        // ===== 基础计数 =====
        int allProjects = dashboardMapper.countAllProjects();
        int activeCount = dashboardMapper.countActiveProjects();
        int newInRange = dashboardMapper.countNewProjectsInRange(beginDate, endDate);
        int completedInRange = dashboardMapper.countCompletedInRange(beginDate, endDate);

        // ===== 1. KPIs =====
        Map<String, Object> kpis = new HashMap<>();
        kpis.put("newProjects", newInRange);
        kpis.put("completedProjects", completedInRange);
        // 办结率 = 本期办结 / 在册总数
        kpis.put("completedRate", allProjects > 0
                ? Math.round(completedInRange * 1000.0 / allProjects) / 10.0 : 0);
        kpis.put("activeProjectCount", activeCount);
        kpis.put("activeRatio", allProjects > 0
                ? Math.round(activeCount * 1000.0 / allProjects) / 10.0 : 0);

        // 预警计数（超期任务 + 待领取资料）
        // 注意：overdueTaskAlerts() 带 LIMIT 10，仅用于列表展示；计数必须走独立 count
        int overdueCount = dashboardMapper.countOverdueTasks();
        int pendingMaterial = 0;
        for (Map<String, Object> m : dashboardMapper.materialFlowStats())
        {
            if ("pending".equals(m.get("name")))
            {
                pendingMaterial = ((Number) m.get("value")).intValue();
            }
        }
        kpis.put("alertCount", overdueCount + pendingMaterial);
        kpis.put("overdueCount", overdueCount);
        kpis.put("pendingMaterialCount", pendingMaterial);

        result.put("kpis", kpis);

        // ===== 2. 财务指标 =====
        BigDecimal periodPayment = toBig(dashboardMapper.sumPeriodPayment(beginDate, endDate).get("amount"));
        BigDecimal annualPayment = toBig(dashboardMapper.sumAnnualPayment().get("amount"));
        BigDecimal periodOutput = toBig(dashboardMapper.sumPeriodOutput(beginDate, endDate).get("amount"));
        BigDecimal annualOutput = toBig(dashboardMapper.sumAnnualOutput().get("amount"));

        Map<String, Object> contractSummary = dashboardMapper.contractPaymentSummary();
        BigDecimal contractTotal = toBig(contractSummary.get("totalAmount"));
        BigDecimal receivedAmount = toBig(contractSummary.get("receivedAmount"));
        BigDecimal pendingPayment = contractTotal.subtract(receivedAmount);
        int contractCount = dashboardMapper.countContracts();

        Map<String, Object> finance = new HashMap<>();
        finance.put("periodPayment", periodPayment);
        finance.put("annualPayment", annualPayment);
        finance.put("paymentAnnualRatio", calcPct(periodPayment, annualPayment));
        finance.put("periodOutput", periodOutput);
        finance.put("annualOutput", annualOutput);
        finance.put("outputMonthlyRatio", calcPct(periodOutput, annualOutput));
        finance.put("contractTotalAmount", contractTotal);
        finance.put("pendingPayment", pendingPayment);
        finance.put("contractCount", contractCount);

        result.put("finance", finance);

        // ===== 3. 合同收款汇总（供回款率计算） =====
        Map<String, Object> contractPayment = new HashMap<>();
        contractPayment.put("totalAmount", contractTotal);
        contractPayment.put("receivedAmount", receivedAmount);
        result.put("contractPayment", contractPayment);

        // ===== 4. 图表数据 =====
        result.put("outputPaymentTrend", dashboardMapper.outputPaymentTrend(beginDate, endDate));
        result.put("categoryOutputDist", dashboardMapper.categoryOutputDist(beginDate, endDate));
        result.put("outputCumulativeTrend", dashboardMapper.outputCumulativeTrend(beginDate, endDate));
        result.put("projectDynamicTrend", dashboardMapper.projectDynamicTrend(beginDate, endDate));
        result.put("contractPaymentList", dashboardMapper.contractPaymentList());
        // 项目产值排行 TOP10（累计外部产值，全周期，替代原「合同收款进度」）
        result.put("projectOutputTop", dashboardMapper.projectOutputTop());

        return result;
    }

    // ============================================================
    // ===== v2 · summary（段1 六磁贴，唯一首屏阻塞） =====
    // ============================================================

    @Override
    public Map<String, Object> getSummary(ProjDashboardQuery query)
    {
        normalizeQuery(query);
        Map<String, Object> data = new HashMap<>();

        // ---- 快照六磁贴 ----
        Map<String, Object> snapshot = new LinkedHashMap<>();

        // 合同额（本年固定本年、本月固定本月，不随周期；同比/环比由 SQL 四值一查）
        Map<String, Object> ct = dashboardMapper.contractAmountBySignDate(query);
        BigDecimal annual = toBig(ct.get("annual"));
        BigDecimal annualPrev = toBig(ct.get("annualPrev"));
        BigDecimal month = toBig(ct.get("month"));
        BigDecimal monthPrev = toBig(ct.get("monthPrev"));
        snapshot.put("annualContract", metricWithDelta(annual, annualPrev, "同比"));
        snapshot.put("monthContract", metricWithDelta(month, monthPrev, "环比"));

        // 新增/办结（四值一查，新增按 assign_date——修正原 create_time 口径）
        Map<String, Object> ps = dashboardMapper.periodProjectStats(query);
        snapshot.put("periodNew", metricWithPrev(toInt(ps.get("periodNew")), toInt(ps.get("periodNewPrev"))));
        snapshot.put("periodCompleted", metricWithPrev(toInt(ps.get("periodCompleted")), toInt(ps.get("periodCompletedPrev"))));

        // 到账（pay_time 非空，refund 负冲）
        Map<String, Object> pay = dashboardMapper.periodPaymentStats(query);
        snapshot.put("periodPayment", metricWithPrev(toBig(pay.get("periodPayment")), toBig(pay.get("periodPaymentPrev"))));

        // 本期超期（超期线落在周期内且已被越过，工作日口径；对比期同式）
        List<Map<String, Object>> odRows = dashboardMapper.selectOverdueCandidates(query);
        List<OverdueItem> odItems = buildOverdueItems(odRows);
        int periodOverdue = countOverdueInPeriod(odItems, query.getBeginDate(), query.getEndDate());
        int periodOverduePrev = countOverdueInPeriod(odItems, query.getCompareBeginDate(), query.getCompareEndDate());
        snapshot.put("periodOverdue", metricWithPrev(periodOverdue, periodOverduePrev));

        data.put("snapshot", snapshot);

        // ---- KPI ----
        Map<String, Object> kpiStats = dashboardMapper.projectKpiStats(query);
        int all = toInt(kpiStats.get("all"));
        int active = toInt(kpiStats.get("active"));
        int periodCompleted = toInt(ps.get("periodCompleted"));
        Map<String, Object> kpi = new HashMap<>();
        kpi.put("activeProjects", active);
        kpi.put("allProjects", all);
        kpi.put("completedRate", all > 0 ? Math.round(periodCompleted * 1000.0 / all) / 10.0 : 0);
        data.put("kpi", kpi);

        // ---- meta：工期要求维护率（仅手动项目；前端据此显示「工期要求未维护」提示态）----
        Map<String, Object> ms = dashboardMapper.manualDurationStats(query);
        int manualTotal = toInt(ms.get("total"));
        int maintained = toInt(ms.get("maintained"));
        Map<String, Object> meta = new HashMap<>();
        meta.put("durationMaintainedRate", manualTotal > 0
                ? Math.round(maintained * 1000.0 / manualTotal) / 10.0 : 0.0);
        data.put("meta", meta);

        return data;
    }

    // ============================================================
    // ===== v2 · structure（段2 类型画像 + 段3 负责人矩阵） =====
    // ============================================================

    @Override
    public Map<String, Object> getStructure(ProjDashboardQuery query)
    {
        normalizeQuery(query);
        Map<String, Object> data = new HashMap<>();

        // 类别 → 桶映射（一次查全量，契约 §5）
        Map<String, String> catNameToBucket = loadCategoryBucketMap();

        // ---- 类型桶统计（5 指标一查，Java 组桶，恒 5 行）----
        List<Map<String, Object>> rows = dashboardMapper.bucketProjectStats(query);
        Map<String, Map<String, Object>> buckets = initBucketTemplates();
        long totalCount = 0;
        long totalClosed = 0;
        for (Map<String, Object> r : rows)
        {
            String bucket = bucketOf(catNameToBucket, r.get("categoryId"));
            Map<String, Object> b = buckets.get(bucket);
            long cnt = toLong(r.get("count"));
            long closed = toLong(r.get("closedCount"));
            b.put("count", toLong(b.get("count")) + cnt);
            b.put("closedCount", toLong(b.get("closedCount")) + closed);
            b.put("internalOutput", toBig(b.get("internalOutput")).add(toBig(r.get("internalOutput"))));
            b.put("externalOutput", toBig(b.get("externalOutput")).add(toBig(r.get("externalOutput"))));
            b.put("contractAmount", toBig(b.get("contractAmount")).add(toBig(r.get("contractAmount"))));
            totalCount += cnt;
            totalClosed += closed;
        }
        List<Map<String, Object>> categoryStats = new ArrayList<>();
        List<Map<String, Object>> closedByBucket = new ArrayList<>();
        // 主类型桶 → 小类 id（用于前端下钻；四个主类型与对应小类一一映射，qita 为 null）
        Map<String, String> bucketMainCategoryId = buildBucketMainCategoryId();
        for (String bk : BUCKET_ORDER)
        {
            Map<String, Object> b = buckets.get(bk);
            long cnt = toLong(b.get("count"));
            long closed = toLong(b.get("closedCount"));
            Map<String, Object> stat = new LinkedHashMap<>();
            stat.put("bucket", bk);
            stat.put("bucketName", BUCKET_LABEL.get(bk));
            stat.put("categoryId", bucketMainCategoryId.get(bk));
            stat.put("count", cnt);
            stat.put("ratio", totalCount > 0 ? Math.round(cnt * 1000.0 / totalCount) / 10.0 : 0.0);
            stat.put("contractAmount", b.get("contractAmount"));
            stat.put("internalOutput", b.get("internalOutput"));
            stat.put("externalOutput", b.get("externalOutput"));
            categoryStats.add(stat);

            Map<String, Object> cb = new LinkedHashMap<>();
            cb.put("bucket", bk);
            cb.put("bucketName", BUCKET_LABEL.get(bk));
            cb.put("count", closed);
            cb.put("ratio", totalClosed > 0 ? Math.round(closed * 1000.0 / totalClosed) / 10.0 : 0.0);
            closedByBucket.add(cb);
        }
        data.put("categoryStats", categoryStats);
        data.put("closedByBucket", closedByBucket);

        // ---- 负责人 × 类型矩阵（多人项目允许重复计数，契约 §2）----
        List<Map<String, Object>> matrixRows = dashboardMapper.leaderBucketMatrix(query);
        Map<String, String> leaderNames = new HashMap<>();
        for (Map<String, Object> r : dashboardMapper.selectLeaderNames())
        {
            leaderNames.put(String.valueOf(r.get("userId")), String.valueOf(r.get("nickName")));
        }
        // leaderId → {bucket → cell}
        Map<String, Map<String, Map<String, Object>>> leaderCells = new LinkedHashMap<>();
        for (Map<String, Object> r : matrixRows)
        {
            String leaderId = String.valueOf(r.get("leaderId"));
            String bucket = bucketOf(catNameToBucket, r.get("categoryId"));
            Map<String, Map<String, Object>> cells = leaderCells.computeIfAbsent(leaderId, k -> new LinkedHashMap<>());
            Map<String, Object> cell = cells.computeIfAbsent(bucket, k -> newCell());
            cell.put("count", toLong(cell.get("count")) + toLong(r.get("count")));
            cell.put("internalOutput", toBig(cell.get("internalOutput")).add(toBig(r.get("internalOutput"))));
            cell.put("externalOutput", toBig(cell.get("externalOutput")).add(toBig(r.get("externalOutput"))));
        }
        List<Map<String, Object>> leaders = new ArrayList<>();
        for (Map.Entry<String, Map<String, Map<String, Object>>> e : leaderCells.entrySet())
        {
            Map<String, Object> leader = new LinkedHashMap<>();
            leader.put("leaderId", Long.valueOf(e.getKey()));
            leader.put("leaderName", leaderNames.getOrDefault(e.getKey(), "未知"));
            Map<String, Object> cells = new LinkedHashMap<>();
            long totalCnt = 0;
            BigDecimal totalInternal = BigDecimal.ZERO;
            BigDecimal totalExternal = BigDecimal.ZERO;
            for (String bk : BUCKET_ORDER)
            {
                Map<String, Object> cell = e.getValue().getOrDefault(bk, newCell());
                cells.put(bk, cell);
                totalCnt += toLong(cell.get("count"));
                totalInternal = totalInternal.add(toBig(cell.get("internalOutput")));
                totalExternal = totalExternal.add(toBig(cell.get("externalOutput")));
            }
            leader.put("cells", cells);
            leader.put("totalCount", totalCnt);
            leader.put("totalInternal", totalInternal);
            leader.put("totalExternal", totalExternal);
            leaders.add(leader);
        }
        // 按项目总数降序
        leaders.sort((a, b) -> Long.compare((Long) b.get("totalCount"), (Long) a.get("totalCount")));

        Map<String, Object> leaderMatrix = new LinkedHashMap<>();
        leaderMatrix.put("buckets", BUCKET_ORDER);
        Map<String, Object> bucketNames = new LinkedHashMap<>();
        for (String bk : BUCKET_ORDER)
        {
            bucketNames.put(bk, BUCKET_LABEL.get(bk));
        }
        leaderMatrix.put("bucketNames", bucketNames);
        leaderMatrix.put("leaders", leaders);
        data.put("leaderMatrix", leaderMatrix);

        // ---- meta：external_output 未填行数 ----
        Map<String, Object> meta = new HashMap<>();
        meta.put("externalOutputMissingCount", dashboardMapper.countExternalOutputMissing(query));
        data.put("meta", meta);

        return data;
    }

    // ============================================================
    // ===== v2 · trend（段4 产值累计双轴 + 项目动态三系列） =====
    // ============================================================

    @Override
    public Map<String, Object> getTrend(ProjDashboardQuery query)
    {
        normalizeQuery(query);
        Map<String, Object> data = new HashMap<>();

        List<Map<String, Object>> cumRows = dashboardMapper.outputCumulativeTrendFiltered(query);
        List<Map<String, Object>> cumulativeOutput = new ArrayList<>();
        for (Map<String, Object> r : cumRows)
        {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", r.get("label"));
            m.put("monthly", toBig(r.get("monthly")));
            m.put("cumulative", toBig(r.get("cumulative")));
            cumulativeOutput.add(m);
        }
        data.put("cumulativeOutput", cumulativeOutput);

        List<Map<String, Object>> dynRows = dashboardMapper.projectDynamicTrendFiltered(query);
        List<Map<String, Object>> dynamicTrend = new ArrayList<>();
        for (Map<String, Object> r : dynRows)
        {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", r.get("label"));
            m.put("newProjects", toInt(r.get("newProjects")));
            m.put("completedProjects", toInt(r.get("completedProjects")));
            m.put("paymentAmount", toBig(r.get("paymentAmount")));
            dynamicTrend.add(m);
        }
        data.put("dynamicTrend", dynamicTrend);

        return data;
    }

    // ============================================================
    // ===== v2 · risk（段5 欠款按年 + 风险行动清单三源合并） =====
    // ============================================================

    @Override
    public Map<String, Object> getRisk(ProjDashboardQuery query)
    {
        normalizeQuery(query);
        Map<String, Object> data = new HashMap<>();
        LocalDate today = LocalDate.now();

        // ---- 欠款按年（办结年；先项目级 GREATEST 再按年 SUM 防正负互抵）----
        List<Map<String, Object>> debtYearRows = dashboardMapper.debtStatsByYear(query);
        List<Map<String, Object>> debtByYear = new ArrayList<>();
        int minYear = today.getYear();
        for (Map<String, Object> r : debtYearRows)
        {
            try
            {
                minYear = Math.min(minYear, Integer.parseInt(String.valueOf(r.get("year"))));
            }
            catch (Exception ignore)
            {
                // ignore
            }
        }
        Map<String, Map<String, Object>> byYear = new HashMap<>();
        for (Map<String, Object> r : debtYearRows)
        {
            byYear.put(String.valueOf(r.get("year")), r);
        }
        for (int y = minYear; y <= today.getYear(); y++)
        {
            Map<String, Object> r = byYear.get(String.valueOf(y));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("year", String.valueOf(y));
            BigDecimal outputBase = r == null ? BigDecimal.ZERO : toBig(r.get("outputBase"));
            BigDecimal received = r == null ? BigDecimal.ZERO : toBig(r.get("received"));
            BigDecimal debt = r == null ? BigDecimal.ZERO : toBig(r.get("debt"));
            m.put("outputBase", outputBase);
            m.put("received", received);
            m.put("debt", debt);
            m.put("collectionRate", outputBase.compareTo(BigDecimal.ZERO) == 0 ? 0.0
                    : Math.round(received.multiply(new BigDecimal("1000"))
                            .divide(outputBase, 4, RoundingMode.HALF_UP).doubleValue()) / 10.0);
            debtByYear.add(m);
        }
        data.put("debtByYear", debtByYear);

        // ---- 风险行动清单：三源合并 ----
        List<Map<String, Object>> riskItems = new ArrayList<>();

        // 源1 欠款逾期（办结且外产值−到账>0，按欠款天数降序 top5）
        List<Map<String, Object>> debtRows = dashboardMapper.debtProjectCandidates(query);
        int debtCount = debtRows.size();
        List<Map<String, Object>> debtItems = new ArrayList<>();
        for (Map<String, Object> r : debtRows)
        {
            LocalDate close = toLD(r.get("closeTime"));
            if (close == null)
            {
                continue;
            }
            int days = (int) ChronoUnit.DAYS.between(close, today);
            BigDecimal amount = toBig(r.get("amount"));
            Map<String, Object> item = baseRiskItem("debt", r, days, amount);
            item.put("severity", (days > 180 || amount.compareTo(new BigDecimal("100000")) > 0) ? "high"
                    : days > 90 ? "medium" : "low");
            item.put("hint", "办结 " + days + " 天，欠款 ¥" + amount.toPlainString() + " 元");
            debtItems.add(item);
        }
        debtItems.sort((a, b) -> Integer.compare((Integer) b.get("days"), (Integer) a.get("days")));
        riskItems.addAll(debtItems.size() > 5 ? debtItems.subList(0, 5) : debtItems);

        // 源2 工期超期（工作日口径，仅手动录入；在办+已办结合并，按超期工作日数降序 top5）
        List<OverdueItem> odItems = buildOverdueItems(dashboardMapper.selectOverdueCandidates(query));
        List<OverdueItem> crossed = new ArrayList<>();
        for (OverdueItem o : odItems)
        {
            if (o.crossed)
            {
                crossed.add(o);
            }
        }
        int overdueCount = crossed.size();
        crossed.sort((a, b) -> Integer.compare(b.overdueDays, a.overdueDays));
        List<OverdueItem> odTop = crossed.size() > 5 ? crossed.subList(0, 5) : crossed;
        for (OverdueItem o : odTop)
        {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", "overdue");
            item.put("severity", o.overdueDays > 15 ? "high" : o.overdueDays > 7 ? "medium" : "low");
            item.put("projectId", o.projectId);
            item.put("projectCode", o.projectCode);
            item.put("projectName", o.projectName);
            item.put("clientUnit", o.clientUnit);
            item.put("leaderNames", o.leaderNames);
            item.put("days", o.overdueDays);
            item.put("amount", BigDecimal.ZERO);
            item.put("hint", (o.closed ? "已办结时超期 " : "超期 ") + o.overdueDays + " 个工作日");
            riskItems.add(item);
        }

        // 源3 未关联合同（录入 N 个工作日后仍未关联，N=contractMissingWorkdays；仅手动录入）
        List<Map<String, Object>> cmRows = dashboardMapper.contractMissingCandidates(query);
        List<Map<String, Object>> cmItems = new ArrayList<>();
        Map<LocalDate, String> cal = loadCalendarMapFor(cmRows, "createTime", today);
        for (Map<String, Object> r : cmRows)
        {
            LocalDate created = toLD(r.get("createTime"));
            if (created == null)
            {
                continue;
            }
            LocalDate deadline = WorkdayUtils.addWorkdays(created, contractMissingWorkdays, cal);
            if (!today.isAfter(deadline))
            {
                continue;
            }
            int days = WorkdayUtils.countWorkdays(deadline, today, cal) - 1;
            if (days < 0)
            {
                days = 0;
            }
            Map<String, Object> item = baseRiskItem("contractMissing", r, days, BigDecimal.ZERO);
            item.put("severity", days > 7 ? "high" : days > 4 ? "medium" : "low");
            item.put("hint", "录入 " + days + " 个工作日，尚未关联合同");
            cmItems.add(item);
        }
        int contractMissingCount = cmItems.size();
        cmItems.sort((a, b) -> Integer.compare((Integer) b.get("days"), (Integer) a.get("days")));
        riskItems.addAll(cmItems.size() > 5 ? cmItems.subList(0, 5) : cmItems);

        // 三源合并按严重度排序，cap 15
        riskItems.sort((a, b) -> {
            int ra = severityRank((String) a.get("severity"));
            int rb = severityRank((String) b.get("severity"));
            if (ra != rb)
            {
                return ra - rb;
            }
            return Integer.compare((Integer) b.get("days"), (Integer) a.get("days"));
        });
        if (riskItems.size() > 15)
        {
            riskItems = riskItems.subList(0, 15);
        }
        data.put("riskItems", riskItems);

        // ---- counts ----
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("debtCount", debtCount);
        counts.put("overdueCount", overdueCount);
        counts.put("contractMissingCount", contractMissingCount);
        counts.put("alertCount", getAlertCount());
        data.put("counts", counts);

        // ---- 段5 右卡：项目产值排行 TOP10（复用旧接口 SQL，零新增） ----
        data.put("projectOutputTop", dashboardMapper.projectOutputTop());

        return data;
    }

    /**
     * 在办超期项目 id 集合（工作日口径；供项目列表页 overdue 筛选复用，逻辑唯一出处）
     */
    @Override
    public List<Long> getOngoingOverdueProjectIds()
    {
        List<Long> ids = new ArrayList<>();
        List<OverdueItem> items = buildOverdueItems(dashboardMapper.selectOverdueCandidates(new ProjDashboardQuery()));
        for (OverdueItem o : items)
        {
            // 在办超期：未办结 且 今天已越过应完成线
            if (o.crossed && !o.closed)
            {
                if (o.projectId instanceof Number)
                {
                    ids.add(Long.valueOf(((Number) o.projectId).longValue()));
                }
            }
        }
        return ids;
    }

    // ============================================================
    // ===== v2 私有辅助 =====
    // ============================================================

    /** 补齐默认周期与对比周期（缺省本年 / 上一等长周期） */
    private void normalizeQuery(ProjDashboardQuery q)
    {
        LocalDate today = LocalDate.now();
        LocalDate begin = parseDate(q.getBeginDate(), today.withDayOfYear(1));
        LocalDate end = parseDate(q.getEndDate(), today.withDayOfYear(today.lengthOfYear()));
        q.setBeginDate(begin.format(DTF));
        q.setEndDate(end.format(DTF));
        LocalDate cb;
        LocalDate ce;
        if (notBlank(q.getCompareBeginDate()) && notBlank(q.getCompareEndDate()))
        {
            cb = parseDate(q.getCompareBeginDate(), begin.minusDays(1));
            ce = parseDate(q.getCompareEndDate(), begin.minusDays(1));
        }
        else
        {
            // 上一等长周期：紧贴本期之前，长度与本期相同（含头含尾天数）
            long days = ChronoUnit.DAYS.between(begin, end) + 1;
            ce = begin.minusDays(1);
            cb = ce.minusDays(days - 1);
        }
        q.setCompareBeginDate(cb.format(DTF));
        q.setCompareEndDate(ce.format(DTF));
    }

    /** 金额型磁贴（value + prev + deltaPct + deltaLabel） */
    private Map<String, Object> metricWithDelta(BigDecimal value, BigDecimal prev, String deltaLabel)
    {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", value);
        m.put("prev", prev);
        m.put("deltaPct", pctChange(value, prev));
        m.put("deltaLabel", deltaLabel);
        return m;
    }

    /** 数值型磁贴（value + prev） */
    private Map<String, Object> metricWithPrev(Object value, Object prev)
    {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", value);
        m.put("prev", prev);
        return m;
    }

    /** 变化百分比：prev 为 0 时返回 null（前端不显示箭头） */
    private Double pctChange(BigDecimal cur, BigDecimal prev)
    {
        if (prev == null || prev.compareTo(BigDecimal.ZERO) == 0)
        {
            return null;
        }
        BigDecimal diff = cur.subtract(prev);
        return diff.multiply(new BigDecimal("1000"))
                .divide(prev, 4, RoundingMode.HALF_UP)
                .divide(new BigDecimal("10"), 1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /** 超期项（deadline 已按工作日算出；closed 表示办结时已越过应完成线） */
    private static class OverdueItem
    {
        Object projectId;
        String projectCode;
        String projectName;
        String clientUnit;
        String leaderNames;
        boolean closed;
        LocalDate deadline;
        boolean crossed;
        int overdueDays;
    }

    /**
     * 超期候选行 → 超期项（工作日口径；超期天数 = countWorkdays(deadline, 参照日) - 1）
     * 在办参照今天；已办结参照办结时间。日期算术全部走 WorkdayUtils + 工作日历。
     */
    private List<OverdueItem> buildOverdueItems(List<Map<String, Object>> rows)
    {
        List<OverdueItem> items = new ArrayList<>();
        if (rows == null || rows.isEmpty())
        {
            return items;
        }
        LocalDate today = LocalDate.now();
        Map<LocalDate, String> cal = loadCalendarMapFor(rows, "assignDate", today);
        for (Map<String, Object> r : rows)
        {
            LocalDate assign = toLD(r.get("assignDate"));
            Integer dr = toIntObj(r.get("durationRequire"));
            if (assign == null || dr == null || dr <= 0)
            {
                continue;
            }
            OverdueItem item = new OverdueItem();
            item.projectId = r.get("id");
            item.projectCode = String.valueOf(r.get("projectCode"));
            item.projectName = String.valueOf(r.get("projectName"));
            item.clientUnit = String.valueOf(r.get("clientUnit"));
            item.leaderNames = String.valueOf(r.get("leaderNames"));
            item.deadline = WorkdayUtils.addWorkdays(assign, dr, cal);
            LocalDate close = toLD(r.get("closeTime"));
            if (close != null)
            {
                item.closed = true;
                item.crossed = item.deadline.isBefore(close);
                item.overdueDays = item.crossed ? Math.max(WorkdayUtils.countWorkdays(item.deadline, close, cal) - 1, 0) : 0;
            }
            else
            {
                item.crossed = today.isAfter(item.deadline);
                item.overdueDays = item.crossed ? Math.max(WorkdayUtils.countWorkdays(item.deadline, today, cal) - 1, 0) : 0;
            }
            items.add(item);
        }
        return items;
    }

    /** 周期内超期数：超期线落在 [begin, min(today, end)] 且已被越过 */
    private int countOverdueInPeriod(List<OverdueItem> items, String beginStr, String endStr)
    {
        LocalDate begin = parseDate(beginStr, LocalDate.now().withDayOfYear(1));
        LocalDate end = parseDate(endStr, LocalDate.now().withDayOfYear(LocalDate.now().lengthOfYear()));
        LocalDate today = LocalDate.now();
        LocalDate upper = end.isAfter(today) ? today : end;
        int count = 0;
        for (OverdueItem o : items)
        {
            if (o.crossed
                    && !o.deadline.isBefore(begin)
                    && !o.deadline.isAfter(upper))
            {
                count++;
            }
        }
        return count;
    }

    /** 工作日历 map：候选行日期字段最小值 → 今天 */
    private Map<LocalDate, String> loadCalendarMapFor(List<Map<String, Object>> rows, String dateKey, LocalDate today)
    {
        LocalDate min = today;
        for (Map<String, Object> r : rows)
        {
            LocalDate d = toLD(r.get(dateKey));
            if (d != null && d.isBefore(min))
            {
                min = d;
            }
        }
        return workdayCalendarService.getCalendarMap(min, today);
    }

    /** 类别名 → 桶（一次查全量；映射不上落 qita） */
    private Map<String, String> loadCategoryBucketMap()
    {
        Map<String, String> idToBucket = new HashMap<>();
        for (Map<String, Object> r : dashboardMapper.selectCategoryList())
        {
            String name = String.valueOf(r.get("name"));
            String bucket = CATEGORY_NAME_TO_BUCKET.getOrDefault(name, BUCKET_QITA);
            idToBucket.put(String.valueOf(r.get("id")), bucket);
        }
        return idToBucket;
    }

    /** 主类型桶 → 小类 id（四个主类型与同名小类一一映射；qita 无单一 id） */
    private Map<String, String> buildBucketMainCategoryId()
    {
        Map<String, String> bucketMainId = new HashMap<>();
        for (Map<String, Object> r : dashboardMapper.selectCategoryList())
        {
            String name = String.valueOf(r.get("name"));
            String bucket = CATEGORY_NAME_TO_BUCKET.get(name);
            if (bucket != null)
            {
                bucketMainId.putIfAbsent(bucket, String.valueOf(r.get("id")));
            }
        }
        return bucketMainId;
    }

    private String bucketOf(Map<String, String> idToBucket, Object categoryId)
    {
        if (categoryId == null)
        {
            return BUCKET_QITA;
        }
        return idToBucket.getOrDefault(String.valueOf(categoryId), BUCKET_QITA);
    }

    /** 5 桶模板（key 顺序即展示顺序） */
    private Map<String, Map<String, Object>> initBucketTemplates()
    {
        Map<String, Map<String, Object>> t = new LinkedHashMap<>();
        for (String bk : BUCKET_ORDER)
        {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("count", 0L);
            b.put("closedCount", 0L);
            b.put("internalOutput", BigDecimal.ZERO);
            b.put("externalOutput", BigDecimal.ZERO);
            b.put("contractAmount", BigDecimal.ZERO);
            t.put(bk, b);
        }
        return t;
    }

    private Map<String, Object> newCell()
    {
        Map<String, Object> cell = new LinkedHashMap<>();
        cell.put("count", 0L);
        cell.put("internalOutput", BigDecimal.ZERO);
        cell.put("externalOutput", BigDecimal.ZERO);
        return cell;
    }

    /** 风险条目公共字段 */
    private Map<String, Object> baseRiskItem(String type, Map<String, Object> r, int days, BigDecimal amount)
    {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", type);
        item.put("projectId", r.get("projectId"));
        item.put("projectCode", String.valueOf(r.get("projectCode")));
        item.put("projectName", String.valueOf(r.get("projectName")));
        item.put("clientUnit", String.valueOf(r.get("clientUnit")));
        String leaders = r.get("leaderNames") == null ? "" : String.valueOf(r.get("leaderNames"));
        item.put("leaderNames", "null".equals(leaders) ? "" : leaders);
        item.put("days", days);
        item.put("amount", amount);
        return item;
    }

    private int severityRank(String severity)
    {
        if ("high".equals(severity))
        {
            return 0;
        }
        return "medium".equals(severity) ? 1 : 2;
    }

    /** 待办预警计数（超期任务 + 待领取资料，沿用旧接口口径） */
    private int getAlertCount()
    {
        int overdueCount = dashboardMapper.countOverdueTasks();
        int pendingMaterial = 0;
        for (Map<String, Object> m : dashboardMapper.materialFlowStats())
        {
            if ("pending".equals(m.get("name")))
            {
                pendingMaterial = ((Number) m.get("value")).intValue();
            }
        }
        return overdueCount + pendingMaterial;
    }

    private boolean notBlank(String s)
    {
        return s != null && !s.trim().isEmpty();
    }

    private LocalDate parseDate(String s, LocalDate fallback)
    {
        if (notBlank(s))
        {
            try
            {
                return LocalDate.parse(s.trim(), DTF);
            }
            catch (Exception ignore)
            {
                // 解析失败走兜底
            }
        }
        return fallback;
    }

    /** 候选行日期字段 → LocalDate（兼容 Date/Timestamp/LocalDate/LocalDateTime/字符串） */
    private LocalDate toLD(Object v)
    {
        if (v == null)
        {
            return null;
        }
        if (v instanceof LocalDate)
        {
            return (LocalDate) v;
        }
        if (v instanceof java.time.LocalDateTime)
        {
            return ((java.time.LocalDateTime) v).toLocalDate();
        }
        if (v instanceof java.util.Date)
        {
            return WorkdayUtils.toLocalDate((java.util.Date) v);
        }
        try
        {
            String s = v.toString();
            return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /** 计算百分比（除数为0返回0） */
    private Double calcPct(BigDecimal part, BigDecimal total)
    {
        if (total.compareTo(BigDecimal.ZERO) == 0) return 0.0;
        return part.divide(total, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /** 安全转 BigDecimal（兼容不同key大小写） */
    private BigDecimal toBig(Object val)
    {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal) return (BigDecimal) val;
        if (val instanceof Number) return new BigDecimal(val.toString());
        try { return new BigDecimal(val.toString()); }
        catch (Exception e) { return BigDecimal.ZERO; }
    }

    private int toInt(Object val)
    {
        if (val == null) return 0;
        if (val instanceof Number) return ((Number) val).intValue();
        try { return Integer.parseInt(val.toString()); }
        catch (Exception e) { return 0; }
    }

    private Integer toIntObj(Object val)
    {
        if (val == null) return null;
        if (val instanceof Number) return Integer.valueOf(((Number) val).intValue());
        try { return Integer.valueOf(val.toString()); }
        catch (Exception e) { return null; }
    }

    private long toLong(Object val)
    {
        if (val == null) return 0L;
        if (val instanceof Number) return ((Number) val).longValue();
        try { return Long.parseLong(val.toString()); }
        catch (Exception e) { return 0L; }
    }
}
