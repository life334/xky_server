package com.xakcch.project.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.xakcch.common.core.domain.BaseEntity;

/**
 * 动态字段定义表 proj_field_def
 *
 * @author liuyonghui
 */
public class ProjFieldDef extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 目标表名 */
    private String tableName;

    /** 字段标识（extra_data JSONB 中的 key） */
    private String fieldKey;

    /** 字段显示名称 */
    private String fieldLabel;

    /** 字段类型（text/number/date/select/datetime/textarea） */
    private String fieldType;

    /** 选项列表（select 类型使用，JSON数组） */
    private String fieldOptions;

    /** 是否必填（0否 1是） */
    private String isRequired;

    /** 排序号 */
    private Integer sortOrder;

    /** 状态（0启用 1停用） */
    private String status;

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

    public String getTableName()
    {
        return tableName;
    }

    public void setTableName(String tableName)
    {
        this.tableName = tableName;
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

    public String getFieldType()
    {
        return fieldType;
    }

    public void setFieldType(String fieldType)
    {
        this.fieldType = fieldType;
    }

    public String getFieldOptions()
    {
        return fieldOptions;
    }

    public void setFieldOptions(String fieldOptions)
    {
        this.fieldOptions = fieldOptions;
    }

    public String getIsRequired()
    {
        return isRequired;
    }

    public void setIsRequired(String isRequired)
    {
        this.isRequired = isRequired;
    }

    public Integer getSortOrder()
    {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder)
    {
        this.sortOrder = sortOrder;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
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
            .append("tableName", getTableName())
            .append("fieldKey", getFieldKey())
            .append("fieldLabel", getFieldLabel())
            .append("fieldType", getFieldType())
            .append("isRequired", getIsRequired())
            .append("sortOrder", getSortOrder())
            .append("status", getStatus())
            .append("delFlag", getDelFlag())
            .toString();
    }
}
