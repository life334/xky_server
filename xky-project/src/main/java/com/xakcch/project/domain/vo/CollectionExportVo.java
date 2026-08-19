package com.xakcch.project.domain.vo;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.xakcch.common.annotation.Excel;

/**
 * 催款清单导出行（按客户分组的欠款明细）
 *
 * @author liuyonghui
 */
public class CollectionExportVo
{
    @Excel(name = "客户全称")
    private String clientUnit;

    @Excel(name = "工程编号")
    private String projectCode;

    @Excel(name = "项目名称")
    private String projectName;

    @Excel(name = "工程项目")
    private String engineeringProject;

    @Excel(name = "办结时间", dateFormat = "yyyy-MM-dd")
    private Date closeTime;

    @Excel(name = "应收金额(元)")
    private BigDecimal receivable;

    @Excel(name = "已收金额(元)")
    private BigDecimal received;

    @Excel(name = "未收金额(元)")
    private BigDecimal unpaidAmount;

    @Excel(name = "账龄(月)")
    private BigDecimal debtMonths;

    @Excel(name = "最近催收时间", dateFormat = "yyyy-MM-dd")
    private Date lastCollectTime;

    @Excel(name = "催收状态")
    private String collectStatus;

    public CollectionExportVo()
    {
    }

    public CollectionExportVo(String clientUnit, String projectCode, String projectName,
            String engineeringProject, Date closeTime, BigDecimal receivable, BigDecimal received,
            BigDecimal unpaidAmount, BigDecimal debtMonths, Date lastCollectTime, String collectStatus)
    {
        this.clientUnit = clientUnit;
        this.projectCode = projectCode;
        this.projectName = projectName;
        this.engineeringProject = engineeringProject;
        this.closeTime = closeTime;
        this.receivable = receivable;
        this.received = received;
        this.unpaidAmount = unpaidAmount;
        this.debtMonths = debtMonths;
        this.lastCollectTime = lastCollectTime;
        this.collectStatus = collectStatus;
    }

    public String getClientUnit()
    {
        return clientUnit;
    }

    public void setClientUnit(String clientUnit)
    {
        this.clientUnit = clientUnit;
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

    public String getEngineeringProject()
    {
        return engineeringProject;
    }

    public void setEngineeringProject(String engineeringProject)
    {
        this.engineeringProject = engineeringProject;
    }

    public Date getCloseTime()
    {
        return closeTime;
    }

    public void setCloseTime(Date closeTime)
    {
        this.closeTime = closeTime;
    }

    public BigDecimal getReceivable()
    {
        return receivable;
    }

    public void setReceivable(BigDecimal receivable)
    {
        this.receivable = receivable;
    }

    public BigDecimal getReceived()
    {
        return received;
    }

    public void setReceived(BigDecimal received)
    {
        this.received = received;
    }

    public BigDecimal getUnpaidAmount()
    {
        return unpaidAmount;
    }

    public void setUnpaidAmount(BigDecimal unpaidAmount)
    {
        this.unpaidAmount = unpaidAmount;
    }

    public BigDecimal getDebtMonths()
    {
        return debtMonths;
    }

    public void setDebtMonths(BigDecimal debtMonths)
    {
        this.debtMonths = debtMonths;
    }

    public Date getLastCollectTime()
    {
        return lastCollectTime;
    }

    public void setLastCollectTime(Date lastCollectTime)
    {
        this.lastCollectTime = lastCollectTime;
    }

    public String getCollectStatus()
    {
        return collectStatus;
    }

    public void setCollectStatus(String collectStatus)
    {
        this.collectStatus = collectStatus;
    }
}
