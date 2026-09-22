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
    /** 其中「已存在项目仅补写到账」的项目数（successCount 的子集，用于结果页说明） */
    private Integer payWriteCount = 0;
    /** 状态：running（后台导入中）/ done（已完成）/ expired（会话过期） */
    private String status;
    private List<RowDetail> failedDetails = new ArrayList<>();
    private List<RowDetail> skippedDetails = new ArrayList<>();
    /** 仅补写到账（工程编号已存在、到账有变化并已写入）的行明细，与 successCount 对应 */
    private List<RowDetail> payWriteDetails = new ArrayList<>();

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
    public Integer getPayWriteCount() { return payWriteCount; }
    public void setPayWriteCount(Integer v) { this.payWriteCount = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public List<RowDetail> getFailedDetails() { return failedDetails; }
    public void setFailedDetails(List<RowDetail> v) { this.failedDetails = v; }
    public List<RowDetail> getSkippedDetails() { return skippedDetails; }
    public void setSkippedDetails(List<RowDetail> v) { this.skippedDetails = v; }
    public List<RowDetail> getPayWriteDetails() { return payWriteDetails; }
    public void setPayWriteDetails(List<RowDetail> v) { this.payWriteDetails = v; }

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