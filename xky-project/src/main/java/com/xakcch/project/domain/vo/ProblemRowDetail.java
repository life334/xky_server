package com.xakcch.project.domain.vo;

import com.xakcch.common.annotation.Excel;
import java.io.Serializable;

/** 预览问题行明细（用于下载Excel） */
public class ProblemRowDetail implements Serializable
{
    private static final long serialVersionUID = 1L;

    @Excel(name = "Excel行号")
    private Integer excelRow;

    @Excel(name = "工程编号")
    private String projectCode;

    @Excel(name = "委托单位")
    private String clientUnit;

    @Excel(name = "委托任务")
    private String engineeringProject;

    @Excel(name = "负责人")
    private String leaderName;

    @Excel(name = "问题类型", width = 30)
    private String problemType;

    @Excel(name = "问题详情", width = 50)
    private String problemDetail;

    @Excel(name = "解决建议", width = 50)
    private String suggestion;

    public Integer getExcelRow() { return excelRow; }
    public void setExcelRow(Integer v) { this.excelRow = v; }
    public String getProjectCode() { return projectCode; }
    public void setProjectCode(String v) { this.projectCode = v; }
    public String getClientUnit() { return clientUnit; }
    public void setClientUnit(String v) { this.clientUnit = v; }
    public String getEngineeringProject() { return engineeringProject; }
    public void setEngineeringProject(String v) { this.engineeringProject = v; }
    public String getLeaderName() { return leaderName; }
    public void setLeaderName(String v) { this.leaderName = v; }
    public String getProblemType() { return problemType; }
    public void setProblemType(String v) { this.problemType = v; }
    public String getProblemDetail() { return problemDetail; }
    public void setProblemDetail(String v) { this.problemDetail = v; }
    public String getSuggestion() { return suggestion; }
    public void setSuggestion(String v) { this.suggestion = v; }
}
