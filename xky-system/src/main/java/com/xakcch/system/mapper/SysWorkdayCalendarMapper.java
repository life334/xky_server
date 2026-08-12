package com.xakcch.system.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.xakcch.system.domain.SysWorkdayCalendar;

/**
 * 工作日历 数据层
 * 
 * @author xky
 */
public interface SysWorkdayCalendarMapper
{
    /**
     * 按日期区间查询全部记录（无分页，供页面渲染和前端计算使用）
     * startDate/endDate 可为空（yyyy-MM-dd）
     */
    List<SysWorkdayCalendar> selectByDateRange(@Param("startDate") String startDate,
            @Param("endDate") String endDate);

    /**
     * 统计某日期区间内各日类型数量，返回 List<{dayType, cnt}>
     */
    List<Map<String, Object>> countByDateRange(@Param("startDate") String startDate,
            @Param("endDate") String endDate);

    /**
     * 新增单条（day 冲突时覆盖为 upsert）
     */
    int insertOrUpdate(SysWorkdayCalendar record);

    /**
     * 按日期修改（day 为唯一键，不允许改日期本身）
     */
    int updateByDay(SysWorkdayCalendar record);

    /**
     * 按日期删除
     */
    int deleteByDay(@Param("day") String day);

    /**
     * 生成某年周末基线（幂等：已有记录跳过，不覆盖节假日/调休）
     * 通过 generate_series 一次写入全年所有周六/周日
     */
    int insertWeekendBaseline(@Param("startDate") String startDate,
            @Param("endDate") String endDate, @Param("createBy") String createBy);

    /**
     * 批量录入日期区间（跳过已有日期）
     */
    int batchInsertRange(@Param("startDate") String startDate, @Param("endDate") String endDate,
            @Param("dayType") String dayType, @Param("remark") String remark,
            @Param("createBy") String createBy);

    /**
     * 批量覆盖日期区间（已有日期更新 dayType/remark）
     */
    int batchUpsertRange(@Param("startDate") String startDate, @Param("endDate") String endDate,
            @Param("dayType") String dayType, @Param("remark") String remark,
            @Param("createBy") String createBy);

    /**
     * 按日期区间批量删除（dayType 为空时删除该区间全部记录）
     * @return 实际删除条数
     */
    int deleteByDateRange(@Param("startDate") String startDate, @Param("endDate") String endDate,
            @Param("dayType") String dayType);
}
