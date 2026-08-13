package com.xakcch.project.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.xakcch.common.core.domain.BaseEntity;

/**
 * 报表导出-模板字段表 proj_report_field
 *
 * @author liuyonghui
 */
public class ProjReportField extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 字段ID */
    private Long id;

    /** 所属模板ID */
    private Long templateId;

    /** 字段键（project.client_unit / agg.receivedAmount / dynamic.xxx） */
    private String fieldKey;

    /** 列名（表头显示名） */
    private String fieldLabel;

    /** 字段来源：subject 主体 / join 关联带出 / agg 聚合计算 / dynamic 动态字段 */
    private String fieldSource;

    /** 关联表（join 类型使用） */
    private String joinTable;

    /** 排序 */
    private Integer sortOrder;

    /** 列宽（字符数） */
    private Integer width;

    /** 内置模板对应列序号（1-based，仅内置模板使用） */
    private Integer columnIndex;

    /** 多级表头分组名（NULL=一级表头，有值=二级表头属于该分组） */
    private String headerGroup;

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

    public Long getTemplateId()
    {
        return templateId;
    }

    public void setTemplateId(Long templateId)
    {
        this.templateId = templateId;
    }

    public String getFieldKey()
    {
        return fieldKey;
    }

    public void setFieldKey(String fieldKey)
    {
        this.fieldKey = fieldKey;
    }

    public String getFieldLabel()
    {
        return fieldLabel;
    }

    public void setFieldLabel(String fieldLabel)
    {
        this.fieldLabel = fieldLabel;
    }

    public String getFieldSource()
    {
        return fieldSource;
    }

    public void setFieldSource(String fieldSource)
    {
        this.fieldSource = fieldSource;
    }

    public String getJoinTable()
    {
        return joinTable;
    }

    public void setJoinTable(String joinTable)
    {
        this.joinTable = joinTable;
    }

    public Integer getSortOrder()
    {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder)
    {
        this.sortOrder = sortOrder;
    }

    public Integer getWidth()
    {
        return width;
    }

    public void setWidth(Integer width)
    {
        this.width = width;
    }

    public Integer getColumnIndex()
    {
        return columnIndex;
    }

    public void setColumnIndex(Integer columnIndex)
    {
        this.columnIndex = columnIndex;
    }

    public String getHeaderGroup()
    {
        return headerGroup;
    }

    public void setHeaderGroup(String headerGroup)
    {
        this.headerGroup = headerGroup;
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
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("templateId", getTemplateId())
            .append("fieldKey", getFieldKey())
            .append("fieldLabel", getFieldLabel())
            .append("fieldSource", getFieldSource())
            .append("joinTable", getJoinTable())
            .append("sortOrder", getSortOrder())
            .append("width", getWidth())
            .append("columnIndex", getColumnIndex())
            .append("headerGroup", getHeaderGroup())
            .toString();
    }
}
