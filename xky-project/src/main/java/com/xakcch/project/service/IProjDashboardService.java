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
     * @param beginDate 统计起始日期（yyyy-MM-dd）
     * @param endDate   统计截止日期（yyyy-MM-dd）
     * @return 聚合数据
     */
    public Map<String, Object> getDashboardData(String beginDate, String endDate);
}
