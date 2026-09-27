package com.xakcch.project.domain;

/**
 * 首页驾驶舱 v2 公共查询参数（summary / structure / trend / risk 四端点同一套）
 *
 * <p>口径约定（2026-09-27 契约定稿）：
 * <ul>
 *   <li>beginDate/endDate：统计周期；缺省时服务端按「本年」兜底</li>
 *   <li>clientUnit / leaderId / categoryId：轻筛选（单选），作用于项目集合；
 *       到账/产值经 project_id join 回项目后过滤</li>
 *   <li>compareBeginDate / compareEndDate：对比周期；缺省时服务端按「上一等长周期」推算</li>
 * </ul>
 *
 * @author liuyonghui
 */
public class ProjDashboardQuery
{
    /** 统计周期起（yyyy-MM-dd） */
    private String beginDate;

    /** 统计周期止（yyyy-MM-dd） */
    private String endDate;

    /** 对比周期起（可空，前端默认传「上一等长周期」） */
    private String compareBeginDate;

    /** 对比周期止（可空） */
    private String compareEndDate;

    /** 委托单位（单选，可空） */
    private String clientUnit;

    /** 项目负责人（单选，可空） */
    private Long leaderId;

    /** 项目小类（单选，可空） */
    private Long categoryId;

    public String getBeginDate()
    {
        return beginDate;
    }

    public void setBeginDate(String beginDate)
    {
        this.beginDate = beginDate;
    }

    public String getEndDate()
    {
        return endDate;
    }

    public void setEndDate(String endDate)
    {
        this.endDate = endDate;
    }

    public String getCompareBeginDate()
    {
        return compareBeginDate;
    }

    public void setCompareBeginDate(String compareBeginDate)
    {
        this.compareBeginDate = compareBeginDate;
    }

    public String getCompareEndDate()
    {
        return compareEndDate;
    }

    public void setCompareEndDate(String compareEndDate)
    {
        this.compareEndDate = compareEndDate;
    }

    public String getClientUnit()
    {
        return clientUnit;
    }

    public void setClientUnit(String clientUnit)
    {
        this.clientUnit = clientUnit;
    }

    public Long getLeaderId()
    {
        return leaderId;
    }

    public void setLeaderId(Long leaderId)
    {
        this.leaderId = leaderId;
    }

    public Long getCategoryId()
    {
        return categoryId;
    }

    public void setCategoryId(Long categoryId)
    {
        this.categoryId = categoryId;
    }
}
