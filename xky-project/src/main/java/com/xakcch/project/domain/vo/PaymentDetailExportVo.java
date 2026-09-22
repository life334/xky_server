package com.xakcch.project.domain.vo;

import java.math.BigDecimal;
import java.util.Date;
import com.xakcch.common.annotation.Excel;

/**
 * 到账明细导出行（「到账统计」页签导出，与到账汇总同一口径）
 *
 * @author liuyonghui
 */
public class PaymentDetailExportVo
{
    @Excel(name = "工程编号")
    private String projectCode;

    @Excel(name = "项目名称")
    private String projectName;

    @Excel(name = "客户全称")
    private String clientUnit;

    @Excel(name = "付款类型")
    private String paymentType;

    @Excel(name = "金额(元)")
    private BigDecimal amount;

    @Excel(name = "到账时间", dateFormat = "yyyy-MM-dd")
    private Date payTime;

    @Excel(name = "办结时间", dateFormat = "yyyy-MM-dd")
    private Date closeTime;

    @Excel(name = "付款单位")
    private String payUnit;

    @Excel(name = "付款方式")
    private String payMethod;

    @Excel(name = "备注")
    private String remark;

    public PaymentDetailExportVo()
    {
    }

    public PaymentDetailExportVo(String projectCode, String projectName, String clientUnit,
            String paymentType, BigDecimal amount, Date payTime, Date closeTime,
            String payUnit, String payMethod, String remark)
    {
        this.projectCode = projectCode;
        this.projectName = projectName;
        this.clientUnit = clientUnit;
        this.paymentType = paymentType;
        this.amount = amount;
        this.payTime = payTime;
        this.closeTime = closeTime;
        this.payUnit = payUnit;
        this.payMethod = payMethod;
        this.remark = remark;
    }

    public String getProjectCode()
    {
        return projectCode;
    }

    public void setProjectCode(String projectCode)
    {
        this.projectCode = projectCode;
    }

    public String getProjectName()
    {
        return projectName;
    }

    public void setProjectName(String projectName)
    {
        this.projectName = projectName;
    }

    public String getClientUnit()
    {
        return clientUnit;
    }

    public void setClientUnit(String clientUnit)
    {
        this.clientUnit = clientUnit;
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

    public Date getCloseTime()
    {
        return closeTime;
    }

    public void setCloseTime(Date closeTime)
    {
        this.closeTime = closeTime;
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

    public String getRemark()
    {
        return remark;
    }

    public void setRemark(String remark)
    {
        this.remark = remark;
    }
}
