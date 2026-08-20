package com.xakcch.project.domain;

import java.math.BigDecimal;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.xakcch.common.core.domain.BaseEntity;

/**
 * 合同单价明细表 proj_contract_price
 *
 * @author liuyonghui
 */
public class ProjContractPrice extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 合同ID */
    private Long contractId;

    /** 项目类别ID（小类） */
    private Long categoryId;

    /** 合同单价（不区分内/外） */
    private BigDecimal price;

    // ===== 展示字段（JOIN 带出，不持久化） =====

    /** 类别名称 */
    private String categoryName;

    /** 类别层级（1=大类 2=小类） */
    private Integer categoryLevel;

    /** 父类别ID */
    private Long parentId;

    /** 父类别名称 */
    private String parentName;

    /** 字典默认内部单价 */
    private BigDecimal dictInternalPrice;

    /** 字典默认外部单价 */
    private BigDecimal dictExternalPrice;

    // ===== 计费方式维度字段（JOIN 带出，不持久化） =====

    /** 计费方式ID（关联 proj_category_billing.id） */
    private Long billingId;

    /** 计费类型（internal=内部 external=外部） */
    private String billingType;

    /** 计费类别（如：常规测绘、加急测绘） */
    private String billingCategory;

    /** 字典单价（proj_category_billing.unit_price，只读展示） */
    private BigDecimal dictUnitPrice;

    /** 计价单位（如：平方公里、公里、宗） */
    private String priceUnit;

    /** 起步量 */
    private BigDecimal minQuantity;

    // ===== getter/setter =====

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getContractId()
    {
        return contractId;
    }

    public void setContractId(Long contractId)
    {
        this.contractId = contractId;
    }

    public Long getCategoryId()
    {
        return categoryId;
    }

    public void setCategoryId(Long categoryId)
    {
        this.categoryId = categoryId;
    }

    public BigDecimal getPrice()
    {
        return price;
    }

    public void setPrice(BigDecimal price)
    {
        this.price = price;
    }

    public String getCategoryName()
    {
        return categoryName;
    }

    public void setCategoryName(String categoryName)
    {
        this.categoryName = categoryName;
    }

    public Integer getCategoryLevel()
    {
        return categoryLevel;
    }

    public void setCategoryLevel(Integer categoryLevel)
    {
        this.categoryLevel = categoryLevel;
    }

    public Long getParentId()
    {
        return parentId;
    }

    public void setParentId(Long parentId)
    {
        this.parentId = parentId;
    }

    public String getParentName()
    {
        return parentName;
    }

    public void setParentName(String parentName)
    {
        this.parentName = parentName;
    }

    public BigDecimal getDictInternalPrice()
    {
        return dictInternalPrice;
    }

    public void setDictInternalPrice(BigDecimal dictInternalPrice)
    {
        this.dictInternalPrice = dictInternalPrice;
    }

    public BigDecimal getDictExternalPrice()
    {
        return dictExternalPrice;
    }

    public void setDictExternalPrice(BigDecimal dictExternalPrice)
    {
        this.dictExternalPrice = dictExternalPrice;
    }

    public Long getBillingId()
    {
        return billingId;
    }

    public void setBillingId(Long billingId)
    {
        this.billingId = billingId;
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

    public BigDecimal getDictUnitPrice()
    {
        return dictUnitPrice;
    }

    public void setDictUnitPrice(BigDecimal dictUnitPrice)
    {
        this.dictUnitPrice = dictUnitPrice;
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

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("contractId", getContractId())
            .append("categoryId", getCategoryId())
            .append("price", getPrice())
            .toString();
    }
}
