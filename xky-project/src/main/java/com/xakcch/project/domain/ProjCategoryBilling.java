package com.xakcch.project.domain;

import java.math.BigDecimal;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.xakcch.common.core.domain.BaseEntity;

/**
 * 项目类别计费方式表 proj_category_billing
 *
 * @author liuyonghui
 */
public class ProjCategoryBilling extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 类别ID（小类） */
    private Long categoryId;

    /** 计费类型（internal=内部 external=外部） */
    private String billingType;

    /** 计费类别（如：常规测绘、加急测绘） */
    private String billingCategory;

    /** 单价（元） */
    private BigDecimal unitPrice;

    /** 计价单位（如：平方公里、公里、宗） */
    private String priceUnit;

    /** 起步量（最低计价数量，不足按起步量计算） */
    private BigDecimal minQuantity;

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

    public Long getCategoryId()
    {
        return categoryId;
    }

    public void setCategoryId(Long categoryId)
    {
        this.categoryId = categoryId;
    }

    public String getBillingType()
    {
        return billingType;
    }

    public void setBillingType(String billingType)
    {
        this.billingType = billingType;
    }

    public String getBillingCategory()
    {
        return billingCategory;
    }

    public void setBillingCategory(String billingCategory)
    {
        this.billingCategory = billingCategory;
    }

    public BigDecimal getUnitPrice()
    {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice)
    {
        this.unitPrice = unitPrice;
    }

    public String getPriceUnit()
    {
        return priceUnit;
    }

    public void setPriceUnit(String priceUnit)
    {
        this.priceUnit = priceUnit;
    }

    public BigDecimal getMinQuantity()
    {
        return minQuantity;
    }

    public void setMinQuantity(BigDecimal minQuantity)
    {
        this.minQuantity = minQuantity;
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
            .append("categoryId", getCategoryId())
            .append("billingType", getBillingType())
            .append("billingCategory", getBillingCategory())
            .append("unitPrice", getUnitPrice())
            .append("priceUnit", getPriceUnit())
            .append("minQuantity", getMinQuantity())
            .append("sortOrder", getSortOrder())
            .append("status", getStatus())
            .append("delFlag", getDelFlag())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}
