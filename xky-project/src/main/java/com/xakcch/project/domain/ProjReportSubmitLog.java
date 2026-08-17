package com.xakcch.project.domain;

import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.xakcch.common.core.domain.BaseEntity;

/**
 * 报表上报记录表 proj_report_submit_log
 *
 * <p>每条工程编号一行，project_code 唯一索引锁定上报时间——上报后不可再次更新。
 * 补验线报表「管线定线上报时间」取关联工程编号在本表的上一次上报时间。
 *
 * @author liuyonghui
 */
public class ProjReportSubmitLog extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 记录ID */
    private Long id;

    /** 工程编号（zdyw 报表系统编号） */
    private String projectCode;

    /** 项目名称（冗余，便于展示） */
    private String projectName;

    /** 单位名称（冗余） */
    private String unitName;

    /** 真实上报领导时间（now()，锁定不可修改） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date submitTime;

    /** 操作人 */
    private String submitBy;

    /** 所属批次ID */
    private Long batchId;

    /** 批次号（JOIN 展示用） */
    private String batchNo;

    /** 删除标志（0正常 2删除） */
    private String delFlag;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public String getProjectCode()
    {
        return projectCode;
    }

    public void setProjectCode(String projectCode)
    {
        this.projectCode = projectCode;
    }

    public String getProjectName()
    {
        return projectName;
    }

    public void setProjectName(String projectName)
    {
        this.projectName = projectName;
    }

    public String getUnitName()
    {
        return unitName;
    }

    public void setUnitName(String unitName)
    {
        this.unitName = unitName;
    }

    public Date getSubmitTime()
    {
        return submitTime;
    }

    public void setSubmitTime(Date submitTime)
    {
        this.submitTime = submitTime;
    }

    public String getSubmitBy()
    {
        return submitBy;
    }

    public void setSubmitBy(String submitBy)
    {
        this.submitBy = submitBy;
    }

    public Long getBatchId()
    {
        return batchId;
    }

    public void setBatchId(Long batchId)
    {
        this.batchId = batchId;
    }

    public String getBatchNo()
    {
        return batchNo;
    }

    public void setBatchNo(String batchNo)
    {
        this.batchNo = batchNo;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("projectCode", getProjectCode())
            .append("projectName", getProjectName())
            .append("unitName", getUnitName())
            .append("submitTime", getSubmitTime())
            .append("submitBy", getSubmitBy())
            .append("batchId", getBatchId())
            .append("batchNo", getBatchNo())
            .toString();
    }
}
