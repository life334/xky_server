package com.xakcch.project.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.xakcch.common.core.domain.BaseEntity;

/**
 * 历史数据导入日志表 proj_import_log
 */
public class ProjImportLog extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long id;

    /** 原始文件名 */
    private String fileName;

    /** 总解析行数 */
    private Integer totalRows;

    /** 成功数 */
    private Integer successCount;

    /** 跳过数（重复工程编号） */
    private Integer skippedCount;

    /** 失败数 */
    private Integer failedCount;

    /** 消耗时长（毫秒） */
    private Long costMs;

    /** 失败明细 JSON 数组 */
    private String failDetails;

    /** 跳过明细 JSON 数组 */
    private String skipDetails;

    /** 状态：running / done */
    private String status;

    /** 删除标志 */
    private String delFlag;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date finishTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public Integer getTotalRows() { return totalRows; }
    public void setTotalRows(Integer totalRows) { this.totalRows = totalRows; }
    public Integer getSuccessCount() { return successCount; }
    public void setSuccessCount(Integer successCount) { this.successCount = successCount; }
    public Integer getSkippedCount() { return skippedCount; }
    public void setSkippedCount(Integer skippedCount) { this.skippedCount = skippedCount; }
    public Integer getFailedCount() { return failedCount; }
    public void setFailedCount(Integer failedCount) { this.failedCount = failedCount; }
    public Long getCostMs() { return costMs; }
    public void setCostMs(Long costMs) { this.costMs = costMs; }
    public String getFailDetails() { return failDetails; }
    public void setFailDetails(String failDetails) { this.failDetails = failDetails; }
    public String getSkipDetails() { return skipDetails; }
    public void setSkipDetails(String skipDetails) { this.skipDetails = skipDetails; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }
    public Date getFinishTime() { return finishTime; }
    public void setFinishTime(Date finishTime) { this.finishTime = finishTime; }
}
