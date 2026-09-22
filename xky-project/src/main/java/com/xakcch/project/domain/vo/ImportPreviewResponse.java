package com.xakcch.project.domain.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ImportPreviewResponse implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String token;
    private Integer totalRows;
    private Integer readyCount;
    private Integer warningCount;
    private Integer errorCount;
    /** 工程编号已存在、导入时将整组跳过的行数（不计入 readyCount） */
    private Integer existsCount = 0;
    /** 涉及的已存在工程编号（去重） */
    private List<String> existsCodes = new ArrayList<>();

    /** 仅补充到账的条目数：不新建项目，只覆盖写入已有项目的到账信息（不计入 readyCount） */
    private Integer payOnlyCount = 0;
    /** 到账页中出现、但本次产值页与库中都匹配不到的工程编号（未写入，供提示，避免静默丢弃） */
    private List<String> unmatchedPayCodes = new ArrayList<>();

    /**
     * 待补到账的「已存在项目」数（去重工程编号）：提交时这些项目的到账信息会被覆盖写入，
     * 而项目/工作量/负责人/任务全部跳过。覆盖两种来源：
     * ① payOnly 行（编号不在本次产值页，只有到账在别的文件里）
     * ② 本文件产值行已存在、且该编号带到账的行
     * 口径与 runCommitAsync 跳过分支实际写付款的范围一致（同编号多行合为一组）。
     */
    private Integer payWriteCount = 0;

    private ProblemSummary problemSummary;

    private List<CategoryOption> categoryOptions = new ArrayList<>();
    private List<BillingOption> billingOptions = new ArrayList<>();

    /** 仅可导入的行数据 */
    private List<ImportPreviewRow> rows = new ArrayList<>();

    /** 问题行明细（供前端页面展示） */
    private List<ProblemRowDetail> problemRows = new ArrayList<>();

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public Integer getTotalRows() { return totalRows; }
    public void setTotalRows(Integer n) { this.totalRows = n; }
    public Integer getReadyCount() { return readyCount; }
    public void setReadyCount(Integer n) { this.readyCount = n; }
    public Integer getWarningCount() { return warningCount; }
    public void setWarningCount(Integer n) { this.warningCount = n; }
    public Integer getErrorCount() { return errorCount; }
    public void setErrorCount(Integer n) { this.errorCount = n; }
    public Integer getExistsCount() { return existsCount; }
    public void setExistsCount(Integer n) { this.existsCount = n; }
    public List<String> getExistsCodes() { return existsCodes; }
    public void setExistsCodes(List<String> c) { this.existsCodes = c; }
    public Integer getPayOnlyCount() { return payOnlyCount; }
    public void setPayOnlyCount(Integer n) { this.payOnlyCount = n; }
    public List<String> getUnmatchedPayCodes() { return unmatchedPayCodes; }
    public void setUnmatchedPayCodes(List<String> c) { this.unmatchedPayCodes = c; }
    public Integer getPayWriteCount() { return payWriteCount; }
    public void setPayWriteCount(Integer n) { this.payWriteCount = n; }
    public ProblemSummary getProblemSummary() { return problemSummary; }
    public void setProblemSummary(ProblemSummary ps) { this.problemSummary = ps; }
    public List<CategoryOption> getCategoryOptions() { return categoryOptions; }
    public void setCategoryOptions(List<CategoryOption> o) { this.categoryOptions = o; }
    public List<BillingOption> getBillingOptions() { return billingOptions; }
    public void setBillingOptions(List<BillingOption> o) { this.billingOptions = o; }
    public List<ImportPreviewRow> getRows() { return rows; }
    public void setRows(List<ImportPreviewRow> r) { this.rows = r; }
    public List<ProblemRowDetail> getProblemRows() { return problemRows; }
    public void setProblemRows(List<ProblemRowDetail> p) { this.problemRows = p; }

    public static class ProblemSummary implements Serializable {
        private String warningDesc;
        private String errorDesc;
        public String getWarningDesc() { return warningDesc; }
        public void setWarningDesc(String v) { this.warningDesc = v; }
        public String getErrorDesc() { return errorDesc; }
        public void setErrorDesc(String v) { this.errorDesc = v; }
    }

    public static class CategoryOption implements Serializable {
        private Long id;
        private String name;
        private Long parentId;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Long getParentId() { return parentId; }
        public void setParentId(Long pid) { this.parentId = pid; }
    }

    public static class BillingOption implements Serializable {
        private Long billingId;
        private Long categoryId;
        private String billingType;
        private String billingCategory;
        private java.math.BigDecimal unitPrice;
        private String priceUnit;
        private java.math.BigDecimal minQuantity;
        public Long getBillingId() { return billingId; }
        public void setBillingId(Long v) { this.billingId = v; }
        public Long getCategoryId() { return categoryId; }
        public void setCategoryId(Long v) { this.categoryId = v; }
        public String getBillingType() { return billingType; }
        public void setBillingType(String v) { this.billingType = v; }
        public String getBillingCategory() { return billingCategory; }
        public void setBillingCategory(String v) { this.billingCategory = v; }
        public java.math.BigDecimal getUnitPrice() { return unitPrice; }
        public void setUnitPrice(java.math.BigDecimal v) { this.unitPrice = v; }
        public String getPriceUnit() { return priceUnit; }
        public void setPriceUnit(String v) { this.priceUnit = v; }
        public java.math.BigDecimal getMinQuantity() { return minQuantity; }
        public void setMinQuantity(java.math.BigDecimal v) { this.minQuantity = v; }
    }
}
