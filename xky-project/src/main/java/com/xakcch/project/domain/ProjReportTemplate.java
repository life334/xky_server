package com.xakcch.project.domain;

import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.xakcch.common.core.domain.BaseEntity;

/**
 * 报表导出-模板表 proj_report_template
 *
 * @author liuyonghui
 */
public class ProjReportTemplate extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 模板ID */
    private Long id;

    /** 模板名称 */
    private String templateName;

    /** 模板类型：builtin 内置 / custom 自定义 */
    private String templateType;

    /** 数据主体（proj_project / proj_task / proj_workload / proj_payment / proj_material / proj_contract） */
    private String subjectTable;

    /** 自定义模板的来源内置模板ID */
    private Long sourceTemplateId;

    /** 内置模板文件（classpath:reportTemplates/xxx 或绝对路径） */
    private String templateFile;

    /** 原始文件名 */
    private String fileName;

    /** 标题行号（0 表示无标题） */
    private Integer titleRow;

    /** 表头行号 */
    private Integer headerRow;

    /** 数据起始行 */
    private Integer dataStartRow;

    /** 是否追加合计行 Y/N */
    private String hasSummaryRow;

    /** 默认筛选条件 JSONB */
    private String defaultFilter;

    /** 删除标志（0正常 2删除） */
    private String delFlag;

    // ===== 非持久化字段 =====

    /** 字段清单（新增/保存自定义模板时随模板提交） */
    private java.util.List<ProjReportField> fieldList;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public String getTemplateName()
    {
        return templateName;
    }

    public void setTemplateName(String templateName)
    {
        this.templateName = templateName;
    }

    public String getTemplateType()
    {
        return templateType;
    }

    public void setTemplateType(String templateType)
    {
        this.templateType = templateType;
    }

    public String getSubjectTable()
    {
        return subjectTable;
    }

    public void setSubjectTable(String subjectTable)
    {
        this.subjectTable = subjectTable;
    }

    public Long getSourceTemplateId()
    {
        return sourceTemplateId;
    }

    public void setSourceTemplateId(Long sourceTemplateId)
    {
        this.sourceTemplateId = sourceTemplateId;
    }

    public String getTemplateFile()
    {
        return templateFile;
    }

    public void setTemplateFile(String templateFile)
    {
        this.templateFile = templateFile;
    }

    public String getFileName()
    {
        return fileName;
    }

    public void setFileName(String fileName)
    {
        this.fileName = fileName;
    }

    public Integer getTitleRow()
    {
        return titleRow;
    }

    public void setTitleRow(Integer titleRow)
    {
        this.titleRow = titleRow;
    }

    public Integer getHeaderRow()
    {
        return headerRow;
    }

    public void setHeaderRow(Integer headerRow)
    {
        this.headerRow = headerRow;
    }

    public Integer getDataStartRow()
    {
        return dataStartRow;
    }

    public void setDataStartRow(Integer dataStartRow)
    {
        this.dataStartRow = dataStartRow;
    }

    public String getHasSummaryRow()
    {
        return hasSummaryRow;
    }

    public void setHasSummaryRow(String hasSummaryRow)
    {
        this.hasSummaryRow = hasSummaryRow;
    }

    public String getDefaultFilter()
    {
        return defaultFilter;
    }

    public void setDefaultFilter(String defaultFilter)
    {
        this.defaultFilter = defaultFilter;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }

    public java.util.List<ProjReportField> getFieldList()
    {
        return fieldList;
    }

    public void setFieldList(java.util.List<ProjReportField> fieldList)
    {
        this.fieldList = fieldList;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("templateName", getTemplateName())
            .append("templateType", getTemplateType())
            .append("subjectTable", getSubjectTable())
            .append("sourceTemplateId", getSourceTemplateId())
            .append("templateFile", getTemplateFile())
            .append("fileName", getFileName())
            .append("titleRow", getTitleRow())
            .append("headerRow", getHeaderRow())
            .append("dataStartRow", getDataStartRow())
            .append("hasSummaryRow", getHasSummaryRow())
            .append("defaultFilter", getDefaultFilter())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("remark", getRemark())
            .toString();
    }
}
