package com.xakcch.project.domain.vo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;

public class ImportPreviewRow
{
    private Integer excelRow;
    private String projectCode;
    private String clientUnit;
    private String engineeringProject;
    private String projectLocation;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date finishDate;

    /** 项目类别小类Id（自动匹配或用户选） */
    private Long projectCategoryId;
    private String projectCategoryName;
    private Double projectCategoryScore;

    /** 负责人姓名 */
    private String leaderName;
    private Long leaderId;
    private Double leaderScore;

    /** 内部产值合计（Excel） */
    private BigDecimal internalTotalFromExcel;
    /** 外部产值合计（Excel） */
    private BigDecimal externalTotalFromExcel;
    /** 反推合计（已尾差对齐） */
    private BigDecimal internalTotalCalced;
    private BigDecimal externalTotalCalced;

    /** 资料领取时间（sheet1） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date materialSubmitTime;

    /** 工作量子项 */
    private List<ImportPreviewWorkload> workloads = new ArrayList<>();

    /** 付款项 */
    private List<ImportPreviewPayment> payments = new ArrayList<>();

    /** 该项目是否重复（跳过） */
    private boolean duplicate;

    /** 错误 */
    private List<String> errors = new ArrayList<>();
    /** 警告 */
    private List<String> warnings = new ArrayList<>();

    public Integer getExcelRow() { return excelRow; }
    public void setExcelRow(Integer excelRow) { this.excelRow = excelRow; }
    public String getProjectCode() { return projectCode; }
    public void setProjectCode(String projectCode) { this.projectCode = projectCode; }
    public String getClientUnit() { return clientUnit; }
    public void setClientUnit(String clientUnit) { this.clientUnit = clientUnit; }
    public String getEngineeringProject() { return engineeringProject; }
    public void setEngineeringProject(String engineeringProject) { this.engineeringProject = engineeringProject; }
    public String getProjectLocation() { return projectLocation; }
    public void setProjectLocation(String projectLocation) { this.projectLocation = projectLocation; }
    public Date getFinishDate() { return finishDate; }
    public void setFinishDate(Date finishDate) { this.finishDate = finishDate; }
    public Long getProjectCategoryId() { return projectCategoryId; }
    public void setProjectCategoryId(Long projectCategoryId) { this.projectCategoryId = projectCategoryId; }
    public String getProjectCategoryName() { return projectCategoryName; }
    public void setProjectCategoryName(String projectCategoryName) { this.projectCategoryName = projectCategoryName; }
    public Double getProjectCategoryScore() { return projectCategoryScore; }
    public void setProjectCategoryScore(Double projectCategoryScore) { this.projectCategoryScore = projectCategoryScore; }
    public String getLeaderName() { return leaderName; }
    public void setLeaderName(String leaderName) { this.leaderName = leaderName; }
    public Long getLeaderId() { return leaderId; }
    public void setLeaderId(Long leaderId) { this.leaderId = leaderId; }
    public Double getLeaderScore() { return leaderScore; }
    public void setLeaderScore(Double leaderScore) { this.leaderScore = leaderScore; }
    public BigDecimal getInternalTotalFromExcel() { return internalTotalFromExcel; }
    public void setInternalTotalFromExcel(BigDecimal v) { this.internalTotalFromExcel = v; }
    public BigDecimal getExternalTotalFromExcel() { return externalTotalFromExcel; }
    public void setExternalTotalFromExcel(BigDecimal v) { this.externalTotalFromExcel = v; }
    public BigDecimal getInternalTotalCalced() { return internalTotalCalced; }
    public void setInternalTotalCalced(BigDecimal v) { this.internalTotalCalced = v; }
    public BigDecimal getExternalTotalCalced() { return externalTotalCalced; }
    public void setExternalTotalCalced(BigDecimal v) { this.externalTotalCalced = v; }
    public Date getMaterialSubmitTime() { return materialSubmitTime; }
    public void setMaterialSubmitTime(Date v) { this.materialSubmitTime = v; }
    public List<ImportPreviewWorkload> getWorkloads() { return workloads; }
    public void setWorkloads(List<ImportPreviewWorkload> w) { this.workloads = w; }
    public List<ImportPreviewPayment> getPayments() { return payments; }
    public void setPayments(List<ImportPreviewPayment> p) { this.payments = p; }
    public boolean isDuplicate() { return duplicate; }
    public void setDuplicate(boolean duplicate) { this.duplicate = duplicate; }
    public List<String> getErrors() { return errors; }
    public void setErrors(List<String> errors) { this.errors = errors; }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
}
