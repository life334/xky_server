package com.xakcch.project.domain;

import java.math.BigDecimal;
import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.xakcch.common.core.domain.BaseEntity;

/**
 * 付款记录表 proj_payment
 *
 * @author liuyonghui
 */
public class ProjPayment extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 付款记录ID */
    private Long id;

    /** 项目ID，关联 proj_project.id */
    private Long projectId;

    /** 付款类型（预付款/尾款/进度款） */
    private String paymentType;

    /** 金额 */
    private BigDecimal amount;

    /** 付款时间 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date payTime;

    /** 付款单位 */
    private String payUnit;

    /** 付款方式 */
    private String payMethod;

    /** 发票号码 */
    private String invoiceNo;

    /** 开票日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date invoiceDate;

    /** 开票金额 */
    private BigDecimal invoiceAmount;

    /** 开票状态（英文码值）：pending 未开 / invoiced 已开 / voided 已作废 —— 中文由前端映射 */
    private String invoiceStatus;

    /**
     * 到账状态（字典 proj_payment_received_status）
     *
     * ⚠️ 已废弃（2026-09-22）：没有任何界面可写/可改该列，写入只会落到默认值 pending（语义退化为「非导入写入」）。
     * 业务上「填了金额 + 到账时间 = 已到账」，到账判定统一看 payTime；本字段仅保留列与映射，不参与任何统计与展示。
     */
    @JsonIgnore
    private String receivedStatus;

    /** 动态字段数据（JSONB） */
    private String extraData;

    /** 删除标志（0正常 2删除） */
    private String delFlag;

    // ===== 以下为非持久化字段，列表/详情展示用 =====

    /** 项目名称（JOIN proj_project） */
    private String projectName;

    // ===== getter/setter =====

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getProjectId()
    {
        return projectId;
    }

    public void setProjectId(Long projectId)
    {
        this.projectId = projectId;
    }

    public String getPaymentType()
    {
        return paymentType;
    }

    public void setPaymentType(String paymentType)
    {
        this.paymentType = paymentType;
    }

    public BigDecimal getAmount()
    {
        return amount;
    }

    public void setAmount(BigDecimal amount)
    {
        this.amount = amount;
    }

    public Date getPayTime()
    {
        return payTime;
    }

    public void setPayTime(Date payTime)
    {
        this.payTime = payTime;
    }

    public String getPayUnit()
    {
        return payUnit;
    }

    public void setPayUnit(String payUnit)
    {
        this.payUnit = payUnit;
    }

    public String getPayMethod()
    {
        return payMethod;
    }

    public void setPayMethod(String payMethod)
    {
        this.payMethod = payMethod;
    }

    public String getInvoiceNo() { return invoiceNo; }
    public void setInvoiceNo(String invoiceNo) { this.invoiceNo = invoiceNo; }

    public Date getInvoiceDate() { return invoiceDate; }
    public void setInvoiceDate(Date invoiceDate) { this.invoiceDate = invoiceDate; }

    public BigDecimal getInvoiceAmount() { return invoiceAmount; }
    public void setInvoiceAmount(BigDecimal invoiceAmount) { this.invoiceAmount = invoiceAmount; }

    public String getInvoiceStatus() { return invoiceStatus; }
    public void setInvoiceStatus(String invoiceStatus) { this.invoiceStatus = invoiceStatus; }

    public String getReceivedStatus() { return receivedStatus; }
    public void setReceivedStatus(String receivedStatus) { this.receivedStatus = receivedStatus; }

    public String getExtraData()
    {
        return extraData;
    }

    public void setExtraData(String extraData)
    {
        this.extraData = extraData;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }

    public String getProjectName()
    {
        return projectName;
    }

    public void setProjectName(String projectName)
    {
        this.projectName = projectName;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("projectId", getProjectId())
            .append("paymentType", getPaymentType())
            .append("amount", getAmount())
            .append("payTime", getPayTime())
            .append("payUnit", getPayUnit())
            .append("payMethod", getPayMethod())
            .append("invoiceNo", getInvoiceNo())
            .append("invoiceDate", getInvoiceDate())
            .append("invoiceAmount", getInvoiceAmount())
            .append("invoiceStatus", getInvoiceStatus())
            .append("extraData", getExtraData())
            .append("delFlag", getDelFlag())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
