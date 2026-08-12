package com.xakcch.system.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 工作日历 pojo（对应表 proj_workday_calendar）
 * 
 * 日类型：holiday=法定节假日休息  workday=调休上班日  weekend=周末休息（默认基线）
 * 
 * @author xky
 */
public class SysWorkdayCalendar
{
    /** 日期（主键） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date day;

    /** 日类型（holiday/workday/weekend） */
    private String dayType;

    /** 备注（如：春节假期、国庆调休等） */
    private String remark;

    private String createBy;
    private Date createTime;
    private String updateBy;
    private Date updateTime;

    public Date getDay()
    {
        return day;
    }

    public void setDay(Date day)
    {
        this.day = day;
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

    public String getCreateBy()
    {
        return createBy;
    }

    public void setCreateBy(String createBy)
    {
        this.createBy = createBy;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public String getUpdateBy()
    {
        return updateBy;
    }

    public void setUpdateBy(String updateBy)
    {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime()
    {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime)
    {
        this.updateTime = updateTime;
    }
}
