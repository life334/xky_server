package com.xakcch.project.service;

import java.util.Map;
import com.xakcch.project.domain.ProjDashboardQuery;

/**
 * 首页驾驶舱 业务层
 *
 * @author liuyonghui
 */
public interface IProjDashboardService
{
    /**
     * 获取驾驶舱聚合数据（旧接口，保留作回滚垫，新首页验收后下线）
     *
     * @param beginDate 统计起始日期（yyyy-MM-dd）
     * @param endDate 统计截止日期（yyyy-MM-dd）
     * @return 聚合数据
     */
    public Map<String, Object> getDashboardData(String beginDate, String endDate);

    /**
     * v2 段1 经营快照：6 磁贴（合同额/新增/办结/到账/超期，含对比基准）+ KPI + 工期维护率
     *
     * @param query 公共查询参数（周期 + 轻筛选 + 对比周期）
     * @return 快照数据
     */
    public Map<String, Object> getSummary(ProjDashboardQuery query);

    /**
     * v2 段2+3 业务结构：类型 5 桶统计 / 本期办结占比 / 负责人×类型矩阵
     *
     * @param query 公共查询参数
     * @return 结构数据
     */
    public Map<String, Object> getStructure(ProjDashboardQuery query);

    /**
     * v2 段4 经营走势：外部产值月增量+累计双轴 / 项目动态三系列（新增/办结/到账）
     *
     * @param query 公共查询参数
     * @return 走势数据
     */
    public Map<String, Object> getTrend(ProjDashboardQuery query);

    /**
     * v2 段5 风险与执行：欠款按年 / 风险行动清单（欠款/工期超期/未关联合同 三源合并）
     *
     * @param query 公共查询参数
     * @return 风险数据
     */
    public Map<String, Object> getRisk(ProjDashboardQuery query);

    /**
     * 在办超期项目 id 集合（工作日口径，仅手动录入 data_source='manual'，与契约 §6 三口径一致）。
     * 供项目列表页 overdue 筛选下钻复用——工作日算术只允许存在于驾驶舱实现这一处。
     *
     * @return 超期项目 id 列表（close_time 为空且今天已越过应完成线的项目）
     */
    public java.util.List<Long> getOngoingOverdueProjectIds();
}
