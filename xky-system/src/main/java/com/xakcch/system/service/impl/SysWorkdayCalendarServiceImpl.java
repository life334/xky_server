package com.xakcch.system.service.impl;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.xakcch.common.exception.ServiceException;
import com.xakcch.common.utils.SecurityUtils;
import com.xakcch.common.utils.StringUtils;
import com.xakcch.common.utils.WorkdayUtils;
import com.xakcch.system.domain.SysWorkdayCalendar;
import com.xakcch.system.mapper.SysWorkdayCalendarMapper;
import com.xakcch.system.service.ISysWorkdayCalendarService;

/**
 * 工作日历 业务层实现
 * 
 * @author xky
 */
@Service
public class SysWorkdayCalendarServiceImpl implements ISysWorkdayCalendarService
{
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private SysWorkdayCalendarMapper workdayCalendarMapper;

    @Override
    public List<SysWorkdayCalendar> selectByDateRange(String startDate, String endDate)
    {
        return workdayCalendarMapper.selectByDateRange(startDate, endDate);
    }

    @Override
    public Map<String, Object> getYearStatus(int year)
    {
        String startDate = year + "-01-01";
        String endDate = year + "-12-31";
        List<Map<String, Object>> counts = workdayCalendarMapper.countByDateRange(startDate, endDate);

        Map<String, Object> result = new HashMap<>();
        int holidayCount = 0;
        int workdayCount = 0;
        int weekendCount = 0;
        for (Map<String, Object> row : counts)
        {
            String dayType = (String) row.get("dayType");
            int cnt = ((Number) row.get("cnt")).intValue();
            if (WorkdayUtils.DAY_TYPE_HOLIDAY.equals(dayType))
            {
                holidayCount = cnt;
            }
            else if (WorkdayUtils.DAY_TYPE_WORKDAY.equals(dayType))
            {
                workdayCount = cnt;
            }
            else if (WorkdayUtils.DAY_TYPE_WEEKEND.equals(dayType))
            {
                weekendCount = cnt;
            }
        }
        // 已维护 = 存在节假日或调休记录（周末基线本身不算维护）
        boolean maintained = holidayCount > 0 || workdayCount > 0;
        result.put("maintained", maintained);
        result.put("holidayCount", holidayCount);
        result.put("workdayCount", workdayCount);
        result.put("weekendCount", weekendCount);
        result.put("total", holidayCount + workdayCount + weekendCount);
        return result;
    }

    @Override
    public int insertOrUpdate(SysWorkdayCalendar record)
    {
        if (record == null || record.getDay() == null)
        {
            throw new ServiceException("日期不能为空");
        }
        if (StringUtils.isEmpty(record.getDayType()))
        {
            throw new ServiceException("日类型不能为空");
        }
        validateDayType(record.getDayType());
        String username = SecurityUtils.getUsername();
        record.setCreateBy(username);
        record.setUpdateBy(username);
        return workdayCalendarMapper.insertOrUpdate(record);
    }

    @Override
    public int updateByDay(SysWorkdayCalendar record)
    {
        if (record == null || record.getDay() == null)
        {
            throw new ServiceException("日期不能为空");
        }
        if (StringUtils.isEmpty(record.getDayType()))
        {
            throw new ServiceException("日类型不能为空");
        }
        validateDayType(record.getDayType());
        record.setUpdateBy(SecurityUtils.getUsername());
        return workdayCalendarMapper.updateByDay(record);
    }

    @Override
    public int deleteByDay(String day)
    {
        if (StringUtils.isEmpty(day))
        {
            throw new ServiceException("日期不能为空");
        }
        return workdayCalendarMapper.deleteByDay(day);
    }

    @Override
    public int generateWeekendBaseline(int year)
    {
        if (year < 2000 || year > 2100)
        {
            throw new ServiceException("年份超出支持范围（2000-2100）");
        }
        String startDate = year + "-01-01";
        String endDate = year + "-12-31";
        return workdayCalendarMapper.insertWeekendBaseline(startDate, endDate, SecurityUtils.getUsername());
    }

    @Override
    public int batchInsertRange(String startDate, String endDate, String dayType, String remark, boolean overwrite)
    {
        if (StringUtils.isEmpty(startDate) || StringUtils.isEmpty(endDate))
        {
            throw new ServiceException("起始/结束日期不能为空");
        }
        if (StringUtils.isEmpty(dayType))
        {
            throw new ServiceException("日类型不能为空");
        }
        validateDayType(dayType);
        LocalDate start = LocalDate.parse(startDate, FMT);
        LocalDate end = LocalDate.parse(endDate, FMT);
        if (start.isAfter(end))
        {
            throw new ServiceException("起始日期不能晚于结束日期");
        }
        // 区间天数上限保护（防止误操作全选 100 年）
        long days = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        if (days > 400)
        {
            throw new ServiceException("批量录入区间超过 400 天，请拆分录入");
        }
        String username = SecurityUtils.getUsername();
        if (overwrite)
        {
            return workdayCalendarMapper.batchUpsertRange(startDate, endDate, dayType, remark, username);
        }
        return workdayCalendarMapper.batchInsertRange(startDate, endDate, dayType, remark, username);
    }

    @Override
    public int deleteByDateRange(String startDate, String endDate, String dayType)
    {
        if (StringUtils.isEmpty(startDate) || StringUtils.isEmpty(endDate))
        {
            throw new ServiceException("起始/结束日期不能为空");
        }
        LocalDate start = LocalDate.parse(startDate, FMT);
        LocalDate end = LocalDate.parse(endDate, FMT);
        if (start.isAfter(end))
        {
            throw new ServiceException("起始日期不能晚于结束日期");
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        if (days > 400)
        {
            throw new ServiceException("批量清除区间超过 400 天，请缩小范围");
        }
        // dayType 非空时校验合法性
        if (StringUtils.isNotEmpty(dayType))
        {
            validateDayType(dayType);
        }
        return workdayCalendarMapper.deleteByDateRange(startDate, endDate, dayType);
    }

    @Override
    public Map<LocalDate, String> getCalendarMap(LocalDate start, LocalDate end)
    {
        Map<LocalDate, String> map = new HashMap<>();
        if (start == null || end == null)
        {
            return map;
        }
        List<SysWorkdayCalendar> list = workdayCalendarMapper.selectByDateRange(
                start.format(FMT), end.format(FMT));
        for (SysWorkdayCalendar record : list)
        {
            map.put(WorkdayUtils.toLocalDate(record.getDay()), record.getDayType());
        }
        return map;
    }

    /**
     * 校验日类型合法性
     */
    private void validateDayType(String dayType)
    {
        if (!WorkdayUtils.DAY_TYPE_HOLIDAY.equals(dayType)
                && !WorkdayUtils.DAY_TYPE_WORKDAY.equals(dayType)
                && !WorkdayUtils.DAY_TYPE_WEEKEND.equals(dayType))
        {
            throw new ServiceException("日类型不合法，仅支持 holiday/workday/weekend");
        }
    }
}
