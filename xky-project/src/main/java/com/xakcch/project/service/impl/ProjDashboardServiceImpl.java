package com.xakcch.project.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.xakcch.common.utils.SecurityUtils;
import com.xakcch.project.mapper.ProjDashboardMapper;
import com.xakcch.project.service.IProjDashboardService;

/**
 * 首页驾驶舱 业务实现
 *
 * @author liuyonghui
 */
@Service
public class ProjDashboardServiceImpl implements IProjDashboardService
{
    @Autowired
    private ProjDashboardMapper dashboardMapper;

    @Override
    public Map<String, Object> getDashboardData(String period)
    {
        Map<String, Object> result = new HashMap<>();

        // ===== 1. KPI 卡片区 =====
        Map<String, Object> kpis = new HashMap<>();

        // 在册项目 + 环比
        int projectCount = dashboardMapper.countAllProjects();
        int thisMonthProjects = dashboardMapper.countProjectsThisMonth();
        int lastMonthProjects = dashboardMapper.countProjectsLastMonth();
        kpis.put("projectCount", projectCount);
        kpis.put("projectTrend", calcTrend(thisMonthProjects, lastMonthProjects));

        // 进行中项目
        int activeCount = dashboardMapper.countActiveProjects();
        kpis.put("activeProjectCount", activeCount);
        kpis.put("activeRatio", projectCount > 0
                ? Math.round(activeCount * 100.0 / projectCount * 10) / 10.0 : 0);

        // 本期产值 + 环比
        Map<String, Object> periodOutput = dashboardMapper.sumPeriodOutput(period);
        Map<String, Object> prevOutput = dashboardMapper.sumPrevPeriodOutput(period);
        BigDecimal totalOutput = toBig(periodOutput.get("output"));
        BigDecimal prevTotal = toBig(prevOutput.get("output"));
        kpis.put("periodOutput", totalOutput);
        kpis.put("outputTrend", calcTrend(totalOutput, prevTotal));

        // 合同总额 + 待收款
        Map<String, Object> contractSummary = dashboardMapper.contractPaymentSummary();
        BigDecimal contractTotal = toBig(contractSummary.get("totalamount"));
        BigDecimal receivedAmount = toBig(contractSummary.get("receivedamount"));
        BigDecimal pendingPayment = contractTotal.subtract(receivedAmount);
        kpis.put("contractTotalAmount", contractTotal);
        kpis.put("pendingPayment", pendingPayment);

        // 预警计数 = 超期任务数 + 待领取资料数
        List<Map<String, Object>> overdueTasks = dashboardMapper.overdueTaskAlerts();
        List<Map<String, Object>> materialStats = dashboardMapper.materialFlowStats();
        int pendingMaterial = 0;
        for (Map<String, Object> m : materialStats)
        {
            if ("pending".equals(m.get("name")))
            {
                pendingMaterial = ((Number) m.get("value")).intValue();
            }
        }
        kpis.put("alertCount", overdueTasks.size() + pendingMaterial);

        result.put("kpis", kpis);

        // ===== 2. 产值趋势图 =====
        List<Map<String, Object>> trendData = dashboardMapper.outputTrend(period);
        List<String> trendLabels = new ArrayList<>();
        List<BigDecimal> trendValues = new ArrayList<>();
        for (Map<String, Object> row : trendData)
        {
            trendLabels.add((String) row.get("label"));
            trendValues.add(toBig(row.get("output")));
        }
        Map<String, Object> outputTrend = new HashMap<>();
        outputTrend.put("labels", trendLabels);
        outputTrend.put("values", trendValues);
        result.put("outputTrend", outputTrend);

        // ===== 3. 项目状态分布 =====
        result.put("projectStatusDist", dashboardMapper.projectStatusDist());

        // ===== 4. 项目进度 TOP5 =====
        result.put("projectProgress", dashboardMapper.projectProgressTop5());

        // ===== 5. 任务预警 =====
        result.put("taskAlerts", overdueTasks);

        // ===== 6. 资料流转 =====
        Map<String, Object> materialFlow = new HashMap<>();
        materialFlow.put("stats", materialStats);
        int pendingReceive = 0, pendingReturn = 0, guaranteed = 0;
        for (Map<String, Object> m : materialStats)
        {
            String status = (String) m.get("name");
            int count = ((Number) m.get("value")).intValue();
            if ("pending".equals(status)) pendingReceive = count;
            else if ("received".equals(status)) pendingReturn = count;
            else if ("returned".equals(status)) guaranteed = count;
        }
        materialFlow.put("pendingReceive", pendingReceive);
        materialFlow.put("pendingReturn", pendingReturn);
        materialFlow.put("returned", guaranteed);
        result.put("materialFlow", materialFlow);

        // ===== 7. 合同收款进度 =====
        Map<String, Object> contractPayment = new HashMap<>();
        contractPayment.put("totalAmount", contractTotal);
        contractPayment.put("receivedAmount", receivedAmount);
        contractPayment.put("pendingAmount", pendingPayment);
        result.put("contractPayment", contractPayment);

        // ===== 8. 我的待办 =====
        Long userId = SecurityUtils.getUserId();
        result.put("myTodos", dashboardMapper.myTodoTasks(userId));

        return result;
    }

    /**
     * 计算环比百分比（正数=增长，负数=下降，null=无对比数据）
     */
    private Double calcTrend(Number current, Number prev)
    {
        double cur = current == null ? 0 : current.doubleValue();
        double prv = prev == null ? 0 : prev.doubleValue();
        if (prv == 0)
        {
            return cur > 0 ? 100.0 : 0.0;
        }
        return Math.round((cur - prv) / prv * 1000.0) / 10.0;
    }

    /**
     * 安全转换 BigDecimal（兼容不同 key 大小写）
     */
    private BigDecimal toBig(Object val)
    {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal) return (BigDecimal) val;
        if (val instanceof Number) return new BigDecimal(val.toString());
        try { return new BigDecimal(val.toString()); }
        catch (Exception e) { return BigDecimal.ZERO; }
    }
}
