package com.xakcch.common.utils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;

/**
 * 工作日计算工具类
 * 
 * 日类型约定（与 proj_workday_calendar 表一致）：
 * <ul>
 *   <li>holiday = 法定节假日休息（原本是工作日，放假）</li>
 *   <li>workday = 调休上班日（原本是周末，调休上班）</li>
 *   <li>weekend = 周末休息（默认基线，由"生成"功能批量产生）</li>
 * </ul>
 * 判定规则：
 * <ul>
 *   <li>有记录：holiday/weekend → 休息；workday → 上班</li>
 *   <li>无记录：周一~周五上班，周六~周日休息（降级兜底，未维护的年份自动生效，不报错）</li>
 * </ul>
 *
 * @author xky
 */
public class WorkdayUtils
{
    /** 法定节假日休息 */
    public static final String DAY_TYPE_HOLIDAY = "holiday";
    /** 调休上班日 */
    public static final String DAY_TYPE_WORKDAY = "workday";
    /** 周末休息（默认基线） */
    public static final String DAY_TYPE_WEEKEND = "weekend";

    private WorkdayUtils()
    {
    }

    /**
     * 判断某天是否为工作日
     *
     * @param day 日期（null 返回 false）
     * @param calendarMap 工作日历表 Map&lt;日期, 日类型&gt;，可为 null（无数据时按周末规则降级）
     * @return true=工作日
     */
    public static boolean isWorkday(LocalDate day, Map<LocalDate, String> calendarMap)
    {
        if (day == null)
        {
            return false;
        }
        if (calendarMap != null)
        {
            String type = calendarMap.get(day);
            if (DAY_TYPE_WORKDAY.equals(type))
            {
                return true;
            }
            if (DAY_TYPE_HOLIDAY.equals(type) || DAY_TYPE_WEEKEND.equals(type))
            {
                return false;
            }
        }
        // 无记录 → 默认周一~周五上班
        DayOfWeek dow = day.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
    }

    /**
     * 计算 [start, end] 区间内的工作日天数（含头含尾）
     *
     * @param start 起始日期（含），null 返回 0
     * @param end 结束日期（含），null 返回 0；start 晚于 end 返回 0
     * @param calendarMap 工作日历表，可为 null
     * @return 工作日天数
     */
    public static int countWorkdays(LocalDate start, LocalDate end, Map<LocalDate, String> calendarMap)
    {
        if (start == null || end == null || start.isAfter(end))
        {
            return 0;
        }
        int count = 0;
        LocalDate cur = start;
        while (!cur.isAfter(end))
        {
            if (isWorkday(cur, calendarMap))
            {
                count++;
            }
            cur = cur.plusDays(1);
        }
        return count;
    }

    /**
     * 计算 [start, end] 区间内的工作日天数（Date 版本，含头含尾）
     *
     * @param start 起始日期（含）
     * @param end 结束日期（含）
     * @param calendarMap 工作日历表，可为 null
     * @return 工作日天数
     */
    public static int countWorkdays(Date start, Date end, Map<LocalDate, String> calendarMap)
    {
        if (start == null || end == null)
        {
            return 0;
        }
        return countWorkdays(toLocalDate(start), toLocalDate(end), calendarMap);
    }

    /**
     * java.util.Date → LocalDate（按系统默认时区）
     *
     * @param date 日期，null 返回 null
     * @return LocalDate
     */
    public static LocalDate toLocalDate(Date date)
    {
        if (date == null)
        {
            return null;
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }
}
