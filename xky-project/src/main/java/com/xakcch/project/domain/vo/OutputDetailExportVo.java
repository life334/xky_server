package com.xakcch.project.domain.vo;

import java.math.BigDecimal;
import java.util.Date;
import com.xakcch.common.annotation.Excel;

/**
 * 产值明细导出行（费用结算页「产值统计」页签导出，与产值汇总同一口径）
 *
 * <p>⚠️ 内部产值与外部产值各自独立，不相加（内部=成本、外部=结算基准，相加无业务含义）。</p>
 *
 * @author liuyonghui
 */
public class OutputDetailExportVo
{
    @Excel(name = "工程编号")
    private String projectCode;

    @Excel(name = "项目名称")
    private String projectName;

    @Excel(name = "委托单位")
    private String clientUnit;

    @Excel(name = "办结时间", dateFormat = "yyyy-MM-dd")
    private Date closeTime;

    @Excel(name = "内部产值(元)")
    private BigDecimal internalOutput;

    @Excel(name = "外部产值(元)")
    private BigDecimal externalOutput;

    /** 项目性质（normal=常规 mandate=指令性任务）；指令性项目的「外部产值」列展示实际录入值但带此标记 */
    @Excel(name = "项目性质", readConverterExp = "normal=常规,mandate=指令性任务")
    private String projectNature;

    public OutputDetailExportVo()
    {
    }

    public OutputDetailExportVo(String projectCode, String projectName, String clientUnit,
            Date closeTime, BigDecimal internalOutput, BigDecimal externalOutput, String projectNature)
    {
        this.projectCode = projectCode;
        this.projectName = projectName;
        this.clientUnit = clientUnit;
        this.closeTime = closeTime;
        this.internalOutput = internalOutput;
        this.externalOutput = externalOutput;
        this.projectNature = projectNature;
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

    public Date getCloseTime()
    {
        return closeTime;
    }

    public void setCloseTime(Date closeTime)
    {
        this.closeTime = closeTime;
    }

    public BigDecimal getInternalOutput()
    {
        return internalOutput;
    }

    public void setInternalOutput(BigDecimal internalOutput)
    {
        this.internalOutput = internalOutput;
    }

    public BigDecimal getExternalOutput()
    {
        return externalOutput;
    }

    public void setExternalOutput(BigDecimal externalOutput)
    {
        this.externalOutput = externalOutput;
    }

    public String getProjectNature()
    {
        return projectNature;
    }

    public void setProjectNature(String projectNature)
    {
        this.projectNature = projectNature;
    }
}
