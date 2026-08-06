package com.xakcch.project.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

/**
 * 首页驾驶舱 数据层
 *
 * @author liuyonghui
 */
public interface ProjDashboardMapper
{
    // ===== 项目KPI =====

    /** 在册项目总数 */
    public int countAllProjects();

    /** 指定日期范围内新增项目数 */
    public int countNewProjectsInRange(@Param("beginDate") String beginDate, @Param("endDate") String endDate);

    /** 指定日期范围内办结项目数 */
    public int countCompletedInRange(@Param("beginDate") String beginDate, @Param("endDate") String endDate);

    /** 进行中项目数 */
    public int countActiveProjects();

    // ===== 财务KPI =====

    /** 指定日期范围内到账总额（proj_payment.received_status='received'） */
    public Map<String, Object> sumPeriodPayment(@Param("beginDate") String beginDate, @Param("endDate") String endDate);

    /** 本年累计到账 */
    public Map<String, Object> sumAnnualPayment();

    /** 指定日期范围内产值总额 */
    public Map<String, Object> sumPeriodOutput(@Param("beginDate") String beginDate, @Param("endDate") String endDate);

    /** 本年累计产值 */
    public Map<String, Object> sumAnnualOutput();

    /** 合同总额 + 已到账总额 */
    public Map<String, Object> contractPaymentSummary();

    /** 合同总数 */
    public int countContracts();

    // ===== 预警 =====

    /** 超期任务列表 */
    public List<Map<String, Object>> overdueTaskAlerts();

    /** 资料流转统计 */
    public List<Map<String, Object>> materialFlowStats();

    // ===== 图表数据 =====

    /** 产值与到账趋势（按月） */
    public List<Map<String, Object>> outputPaymentTrend(@Param("beginDate") String beginDate, @Param("endDate") String endDate);

    /** 项目类型产值分布 */
    public List<Map<String, Object>> categoryOutputDist(@Param("beginDate") String beginDate, @Param("endDate") String endDate);

    /** 产值累计趋势（按月） */
    public List<Map<String, Object>> outputCumulativeTrend(@Param("beginDate") String beginDate, @Param("endDate") String endDate);

    /** 项目动态趋势（按月 新增/办结） */
    public List<Map<String, Object>> projectDynamicTrend(@Param("beginDate") String beginDate, @Param("endDate") String endDate);

    /** 合同收款进度列表 */
    public List<Map<String, Object>> contractPaymentList();
}
