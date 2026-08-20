package com.xakcch.project.domain.vo;

import java.math.BigDecimal;

/** 预览-工作量子项 */
public class ImportPreviewWorkload
{
    /** 计费类型 internal/external */
    private String billingType;
    /** Excel 二级表头原文 */
    private String billingCategoryRaw;
    /** 匹配到的计费类别（ProjCategoryBilling.billingCategory） */
    private String billingCategory;
    /** 匹配到的类别ID（小类） */
    private Long categoryId;
    /** 匹配到的计费类别主键ID proj_category_billing.id */
    private Long billingId;
    /** 匹配度 0-1 */
    private Double score;
    /** 工作量 */
    private BigDecimal workload;
    /** 反推单价 */
    private BigDecimal unitPrice;
    /** 小计产值 */
    private BigDecimal output;
    /** 计价单位 */
    private String priceUnit;
    /** 起步量 */
    private BigDecimal minQuantity;
    /** 警告信息 */
    private String warning;

    public String getBillingType() { return billingType; }
    public void setBillingType(String billingType) { this.billingType = billingType; }
    public String getBillingCategoryRaw() { return billingCategoryRaw; }
    public void setBillingCategoryRaw(String billingCategoryRaw) { this.billingCategoryRaw = billingCategoryRaw; }
    public String getBillingCategory() { return billingCategory; }
    public void setBillingCategory(String billingCategory) { this.billingCategory = billingCategory; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public Long getBillingId() { return billingId; }
    public void setBillingId(Long billingId) { this.billingId = billingId; }
    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }
    public BigDecimal getWorkload() { return workload; }
    public void setWorkload(BigDecimal workload) { this.workload = workload; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getOutput() { return output; }
    public void setOutput(BigDecimal output) { this.output = output; }
    public String getPriceUnit() { return priceUnit; }
    public void setPriceUnit(String priceUnit) { this.priceUnit = priceUnit; }
    public BigDecimal getMinQuantity() { return minQuantity; }
    public void setMinQuantity(BigDecimal minQuantity) { this.minQuantity = minQuantity; }
    public String getWarning() { return warning; }
    public void setWarning(String warning) { this.warning = warning; }
}
