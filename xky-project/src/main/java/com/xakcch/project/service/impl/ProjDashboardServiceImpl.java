package com.xakcch.project.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
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
        int overdueCount = dashboardMapper.overdueTaskAlerts().size();
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
        BigDecimal contractTotal = toBig(contractSummary.get("totalamount"));
        BigDecimal receivedAmount = toBig(contractSummary.get("receivedamount"));
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

        return result;
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
}
