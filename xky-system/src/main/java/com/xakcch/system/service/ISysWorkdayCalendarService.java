package com.xakcch.system.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import com.xakcch.system.domain.SysWorkdayCalendar;

/**
 * 工作日历 业务层接口
 * 
 * @author xky
 */
public interface ISysWorkdayCalendarService
{
    /**
     * 按日期区间查询全部记录（无分页）
     */
    List<SysWorkdayCalendar> selectByDateRange(String startDate, String endDate);

    /**
     * 获取某年维护状态统计
     * 返回 Map：maintained(是否已维护，即存在节假日/调休记录)、holidayCount、workdayCount、weekendCount、total
     */
    Map<String, Object> getYearStatus(int year);

    /**
     * 新增单条（day 冲突时覆盖，幂等）
     */
    int insertOrUpdate(SysWorkdayCalendar record);

    /**
     * 按日期修改
     */
    int updateByDay(SysWorkdayCalendar record);

    /**
     * 按日期删除
     */
    int deleteByDay(String day);

    /**
     * 生成某年全年周末基线（幂等，不覆盖节假日/调休记录）
     * @return 实际新增条数
     */
    int generateWeekendBaseline(int year);

    /**
     * 按日期区间批量录入/覆盖
     * @param overwrite true=覆盖已有记录，false=跳过已有日期
     * @return 实际写入条数
     */
    int batchInsertRange(String startDate, String endDate, String dayType, String remark, boolean overwrite);

    /**
     * 按日期区间批量删除（dayType 为空时删除该区间全部记录）
     * @return 实际删除条数
     */
    int deleteByDateRange(String startDate, String endDate, String dayType);

    /**
     * 获取 [start, end] 区间的工作日历 Map（供项目总时长等业务计算使用）
     * 返回 Map&lt;日期, 日类型&gt;；区间无数据时返回空 Map（调用方按周末规则降级）
     */
    Map<LocalDate, String> getCalendarMap(LocalDate start, LocalDate end);
}
