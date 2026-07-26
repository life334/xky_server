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
    /**
     * 在册项目总数
     */
    public int countAllProjects();

    /**
     * 本月新增项目数（环比基准）
     */
    public int countProjectsThisMonth();

    /**
     * 上月新增项目数（环比基准）
     */
    public int countProjectsLastMonth();

    /**
     * 进行中项目数
     */
    public int countActiveProjects();

    /**
     * 本期产值（内部+外部），按 period 决定时间范围
     *
     * @param period month/quarter/year
     * @return Map: internalOutput, externalOutput
     */
    public Map<String, Object> sumPeriodOutput(@Param("period") String period);

    /**
     * 上期产值（环比基准）
     */
    public Map<String, Object> sumPrevPeriodOutput(@Param("period") String period);

    /**
     * 产值趋势（按 period 粒度聚合）
     *
     * @param period month=按日 / quarter=按月3个 / year=按月12个
     * @return List of Map: label, internalOutput, externalOutput
     */
    public List<Map<String, Object>> outputTrend(@Param("period") String period);

    /**
     * 合同总金额 + 已到账总额
     */
    public Map<String, Object> contractPaymentSummary();

    /**
     * 项目状态分布
     */
    public List<Map<String, Object>> projectStatusDist();

    /**
     * 进行中项目进度 TOP5（完成任务/总任务）
     */
    public List<Map<String, Object>> projectProgressTop5();

    /**
     * 超期任务列表（未完成 + 截止日期已过）
     */
    public List<Map<String, Object>> overdueTaskAlerts();

    /**
     * 资料流转状态统计
     */
    public List<Map<String, Object>> materialFlowStats();

    /**
     * 我的待办任务（当前用户未完成的任务）
     *
     * @param userId 当前登录用户ID
     */
    public List<Map<String, Object>> myTodoTasks(@Param("userId") Long userId);
}
