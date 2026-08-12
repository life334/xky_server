package com.xakcch.system.domain;

/**
 * 工作日历批量录入请求对象
 * 
 * 按日期区间批量录入/覆盖：区间内每一天都写入一条 dayType 记录
 * 
 * @author xky
 */
public class SysWorkdayBatchRequest
{
    /** 起始日期（yyyy-MM-dd，含） */
    private String startDate;

    /** 结束日期（yyyy-MM-dd，含） */
    private String endDate;

    /** 日类型（holiday/workday） */
    private String dayType;

    /** 备注（如：春节假期） */
    private String remark;

    /** 是否覆盖已有记录（true=覆盖 false=跳过已有日期，默认 false） */
    private Boolean overwrite;

    public String getStartDate()
    {
        return startDate;
    }

    public void setStartDate(String startDate)
    {
        this.startDate = startDate;
    }

    public String getEndDate()
    {
        return endDate;
    }

    public void setEndDate(String endDate)
    {
        this.endDate = endDate;
    }

    public String getDayType()
    {
        return dayType;
    }

    public void setDayType(String dayType)
    {
        this.dayType = dayType;
    }

    public String getRemark()
    {
        return remark;
    }

    public void setRemark(String remark)
    {
        this.remark = remark;
    }

    public Boolean getOverwrite()
    {
        return overwrite;
    }

    public void setOverwrite(Boolean overwrite)
    {
        this.overwrite = overwrite;
    }
}
