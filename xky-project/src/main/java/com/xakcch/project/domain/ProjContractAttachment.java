package com.xakcch.project.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.xakcch.common.core.domain.BaseEntity;

/**
 * 合同附件表 proj_contract_attachment
 *
 * @author liuyonghui
 */
public class ProjContractAttachment extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 附件ID */
    private Long id;

    /** 关联合同ID */
    private Long contractId;

    /** 原始文件名 */
    private String fileName;

    /** 服务端存储路径 */
    private String filePath;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 文件扩展名 */
    private String fileType;

    /** 文件归类（contract/supplement/acceptance/invoice/other） */
    private String fileCategory;

    /** 是否签章/盖章版（1=是 0=否） */
    private String isFinal;

    /** 当前版本号 */
    private Integer version;

    /** 排序号 */
    private Integer sortOrder;

    /** 删除标志（0正常 2删除） */
    private String delFlag;

    // ===== getter/setter =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("contractId", getContractId())
            .append("fileName", getFileName())
            .append("filePath", getFilePath())
            .append("fileSize", getFileSize())
            .append("fileType", getFileType())
            .append("fileCategory", getFileCategory())
            .append("isFinal", getIsFinal())
            .append("version", getVersion())
            .append("sortOrder", getSortOrder())
            .append("delFlag", getDelFlag())
            .toString();
    }
}
