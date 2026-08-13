package com.xakcch.project.domain;

import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.xakcch.common.core.domain.BaseEntity;

/**
 * 报表导出-导出历史表 proj_report_log
 *
 * @author liuyonghui
 */
public class ProjReportLog extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 日志ID */
    private Long id;

    /** 使用的模板ID */
    private Long templateId;

    /** 模板名称快照 */
    private String templateName;

    /** 筛选方案名称快照 */
    private String filterName;

    /** 筛选条件快照 JSONB */
    private String filterConfig;

    /** 导出行数 */
    private Integer rowCount;

    /** 操作人 */
    private String exportBy;

    /** 导出时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date exportTime;

    /** 导出文件名 */
    private String fileName;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
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

    public String getFilterName()
    {
        return filterName;
    }

    public void setFilterName(String filterName)
    {
        this.filterName = filterName;
    }

    public String getFilterConfig()
    {
        return filterConfig;
    }

    public void setFilterConfig(String filterConfig)
    {
        this.filterConfig = filterConfig;
    }

    public Integer getRowCount()
    {
        return rowCount;
    }

    public void setRowCount(Integer rowCount)
    {
        this.rowCount = rowCount;
    }

    public String getExportBy()
    {
        return exportBy;
    }

    public void setExportBy(String exportBy)
    {
        this.exportBy = exportBy;
    }

    public Date getExportTime()
    {
        return exportTime;
    }

    public void setExportTime(Date exportTime)
    {
        this.exportTime = exportTime;
    }

    public String getFileName()
    {
        return fileName;
    }

    public void setFileName(String fileName)
    {
        this.fileName = fileName;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("templateId", getTemplateId())
            .append("templateName", getTemplateName())
            .append("filterName", getFilterName())
            .append("rowCount", getRowCount())
            .append("exportBy", getExportBy())
            .append("exportTime", getExportTime())
            .append("fileName", getFileName())
            .toString();
    }
}
