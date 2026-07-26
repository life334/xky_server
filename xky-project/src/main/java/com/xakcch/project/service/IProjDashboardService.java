package com.xakcch.project.service;

import java.util.Map;

/**
 * 首页驾驶舱 业务层
 *
 * @author liuyonghui
 */
public interface IProjDashboardService
{
    /**
     * 获取驾驶舱聚合数据
     *
     * @param period 时间周期（month/quarter/year），影响产值KPI和趋势图
     * @return 聚合数据
     */
    public Map<String, Object> getDashboardData(String period);
}
