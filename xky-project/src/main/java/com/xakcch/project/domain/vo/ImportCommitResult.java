package com.xakcch.project.domain.vo;

import com.xakcch.common.annotation.Excel;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ImportCommitResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long logId;
    private Integer successCount;
    private Integer skippedCount;
    private Integer failedCount;
    private Long costMs;
    private List<RowDetail> failedDetails = new ArrayList<>();
    private List<RowDetail> skippedDetails = new ArrayList<>();

    public Long getLogId() { return logId; }
    public void setLogId(Long v) { this.logId = v; }
    public Integer getSuccessCount() { return successCount; }
    public void setSuccessCount(Integer v) { this.successCount = v; }
    public Integer getSkippedCount() { return skippedCount; }
    public void setSkippedCount(Integer v) { this.skippedCount = v; }
    public Integer getFailedCount() { return failedCount; }
    public void setFailedCount(Integer v) { this.failedCount = v; }
    public Long getCostMs() { return costMs; }
    public void setCostMs(Long v) { this.costMs = v; }
    public List<RowDetail> getFailedDetails() { return failedDetails; }
    public void setFailedDetails(List<RowDetail> v) { this.failedDetails = v; }
    public List<RowDetail> getSkippedDetails() { return skippedDetails; }
    public void setSkippedDetails(List<RowDetail> v) { this.skippedDetails = v; }

    public static class RowDetail implements Serializable {
        private static final long serialVersionUID = 1L;

        @Excel(name = "Excel行号")
        private Integer excelRow;

        @Excel(name = "工程编号")
        private String projectCode;

        @Excel(name = "原因/备注", width = 60)
        private String reason;

        public Integer getExcelRow() { return excelRow; }
        public void setExcelRow(Integer v) { this.excelRow = v; }
        public String getProjectCode() { return projectCode; }
        public void setProjectCode(String v) { this.projectCode = v; }
        public String getReason() { return reason; }
        public void setReason(String v) { this.reason = v; }
    }
}