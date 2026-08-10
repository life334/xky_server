package com.xakcch.project.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 合同附件版本历史表 proj_contract_attachment_log
 *
 * @author liuyonghui
 */
public class ProjContractAttachmentLog
{
    private static final long serialVersionUID = 1L;

    /** 记录ID */
    private Long id;

    /** 关联附件槽位ID */
    private Long attachmentId;

    /** 关联合同ID */
    private Long contractId;

    /** 该版本文件名 */
    private String fileName;

    /** 该版本文件路径 */
    private String filePath;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 文件扩展名 */
    private String fileType;

    /** 文件归类 */
    private String fileCategory;

    /** 是否签章/盖章版 */
    private String isFinal;

    /** 版本号 */
    private Integer version;

    /** 操作类型（upload/replace/delete/restore） */
    private String action;

    /** 操作人 */
    private String operator;

    /** 操作时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date operateTime;

    /** 操作备注 */
    private String remark;

    // ===== getter/setter =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAttachmentId() { return attachmentId; }
    public void setAttachmentId(Long attachmentId) { this.attachmentId = attachmentId; }

    public Long getContractId() { return contractId; }
    public void setContractId(Long contractId) { this.contractId = contractId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public String getFileCategory() { return fileCategory; }
    public void setFileCategory(String fileCategory) { this.fileCategory = fileCategory; }

    public String getIsFinal() { return isFinal; }
    public void setIsFinal(String isFinal) { this.isFinal = isFinal; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public Date getOperateTime() { return operateTime; }
    public void setOperateTime(Date operateTime) { this.operateTime = operateTime; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("attachmentId", getAttachmentId())
            .append("contractId", getContractId())
            .append("fileName", getFileName())
            .append("version", getVersion())
            .append("action", getAction())
            .toString();
    }
}
