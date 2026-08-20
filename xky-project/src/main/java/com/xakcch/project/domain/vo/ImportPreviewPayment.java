package com.xakcch.project.domain.vo;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

public class ImportPreviewPayment
{
    /** 预付款 / 尾款 */
    private String paymentType;
    private BigDecimal amount;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date payTime;
    private String payMethod;
    private String payUnit;
    /** 数据来源: sheet2预付款 / sheet2尾款 / 备注解析 */
    private String source;
    private String warning;

    public String getPaymentType() { return paymentType; }
    public void setPaymentType(String paymentType) { this.paymentType = paymentType; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public Date getPayTime() { return payTime; }
    public void setPayTime(Date payTime) { this.payTime = payTime; }
    public String getPayMethod() { return payMethod; }
    public void setPayMethod(String payMethod) { this.payMethod = payMethod; }
    public String getPayUnit() { return payUnit; }
    public void setPayUnit(String payUnit) { this.payUnit = payUnit; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getWarning() { return warning; }
    public void setWarning(String warning) { this.warning = warning; }
}
