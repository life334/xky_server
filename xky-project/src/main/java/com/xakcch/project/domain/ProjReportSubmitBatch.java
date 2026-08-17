package com.xakcch.project.domain;

import java.util.Date;
import java.util.List;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.xakcch.common.core.domain.BaseEntity;

/**
 * 报表上报批次表 proj_report_submit_batch
 *
 * <p>一次"导出并上报领导"操作 = 一个批次。批次记录本次筛选范围 + 快照文件路径，
 * 便于事后下载对质。记录级上报时间存 proj_report_submit_log（UNIQUE 锁定）。
 *
 * @author liuyonghui
 */
public class ProjReportSubmitBatch extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 批次ID */
    private Long id;

    /** 批次号：SB+yyyyMMdd+3位序号 */
    private String batchNo;

    /** 上报所用模板ID */
    private Long templateId;

    /** 模板名称（JOIN 展示用） */
    private String templateName;

    /** 批次上报时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date submitTime;

    /** 操作人 */
    private String submitBy;

    /** 本次上报工程条数 */
    private Integer projectCount;

    /** 本次筛选范围描述 */
    private String filterDesc;

    /** 上报快照文件（服务器路径） */
    private String snapshotFile;

    /** 备注 */
    private String remark;

    /** 删除标志（0正常 2删除） */
    private String delFlag;

    /** 批次内上报记录（详情页用） */
    private List<ProjReportSubmitLog> logs;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public String getBatchNo()
    {
        return batchNo;
    }

    public void setBatchNo(String batchNo)
    {
        this.batchNo = batchNo;
    }

    public Long getTemplateId()
    {
        return templateId;
    }

    public void setTemplateId(Long templateId)
    {
        this.templateId = templateId;
    }

    public String getTemplateName()
    {
        return templateName;
    }

    public void setTemplateName(String templateName)
    {
        this.templateName = templateName;
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

    public Integer getProjectCount()
    {
        return projectCount;
    }

    public void setProjectCount(Integer projectCount)
    {
        this.projectCount = projectCount;
    }

    public String getFilterDesc()
    {
        return filterDesc;
    }

    public void setFilterDesc(String filterDesc)
    {
        this.filterDesc = filterDesc;
    }

    public String getSnapshotFile()
    {
        return snapshotFile;
    }

    public void setSnapshotFile(String snapshotFile)
    {
        this.snapshotFile = snapshotFile;
    }

    public String getRemark()
    {
        return remark;
    }

    public void setRemark(String remark)
    {
        this.remark = remark;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }

    public List<ProjReportSubmitLog> getLogs()
    {
        return logs;
    }

    public void setLogs(List<ProjReportSubmitLog> logs)
    {
        this.logs = logs;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("batchNo", getBatchNo())
            .append("templateId", getTemplateId())
            .append("templateName", getTemplateName())
            .append("submitTime", getSubmitTime())
            .append("submitBy", getSubmitBy())
            .append("projectCount", getProjectCount())
            .append("filterDesc", getFilterDesc())
            .append("snapshotFile", getSnapshotFile())
            .append("remark", getRemark())
            .toString();
    }
}
