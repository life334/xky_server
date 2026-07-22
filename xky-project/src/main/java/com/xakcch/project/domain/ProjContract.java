package com.xakcch.project.domain;

import java.math.BigDecimal;
import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.xakcch.common.core.domain.BaseEntity;

/**
 * 合同表 proj_contract
 *
 * @author liuyonghui
 */
public class ProjContract extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 合同ID */
    private Long id;

    /** 合同编号 */
    private String contractNo;

    /** 合同名称 */
    private String contractName;

    /** 委托单位 */
    private String clientUnit;

    /** 联系人 */
    private String contactName;

    /** 联系电话 */
    private String contactPhone;

    /** 合同类型（字典） */
    private String contractType;

    /** 合同金额 */
    private BigDecimal contractAmount;

    /** 签署日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date signDate;

    /** 委托时间 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date entrustDate;

    /** 审核日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date auditDate;

    /** 用户返回日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date returnDate;

    /** 完成日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date finishDate;

    /** 归档日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date archiveDate;

    /** 归档目录 */
    private String archivePath;

    /** 合同期限 */
    private String contractPeriod;

    /** 支付条件 */
    private String paymentTerms;

    /** 已到账金额 */
    private BigDecimal receivedAmount;

    /** 动态字段数据（JSONB） */
    private String extraData;

    /** 删除标志（0正常 2删除） */
    private String delFlag;

    // ===== getter/setter =====

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public String getContractNo()
    {
        return contractNo;
    }

    public void setContractNo(String contractNo)
    {
        this.contractNo = contractNo;
    }

    public String getContractName()
    {
        return contractName;
    }

    public void setContractName(String contractName)
    {
        this.contractName = contractName;
    }

    public String getClientUnit()
    {
        return clientUnit;
    }

    public void setClientUnit(String clientUnit)
    {
        this.clientUnit = clientUnit;
    }

    public String getContactName()
    {
        return contactName;
    }

    public void setContactName(String contactName)
    {
        this.contactName = contactName;
    }

    public String getContactPhone()
    {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone)
    {
        this.contactPhone = contactPhone;
    }

    public String getContractType()
    {
        return contractType;
    }

    public void setContractType(String contractType)
    {
        this.contractType = contractType;
    }

    public BigDecimal getContractAmount()
    {
        return contractAmount;
    }

    public void setContractAmount(BigDecimal contractAmount)
    {
        this.contractAmount = contractAmount;
    }

    public Date getSignDate()
    {
        return signDate;
    }

    public void setSignDate(Date signDate)
    {
        this.signDate = signDate;
    }

    public Date getEntrustDate()
    {
        return entrustDate;
    }

    public void setEntrustDate(Date entrustDate)
    {
        this.entrustDate = entrustDate;
    }

    public Date getAuditDate()
    {
        return auditDate;
    }

    public void setAuditDate(Date auditDate)
    {
        this.auditDate = auditDate;
    }

    public Date getReturnDate()
    {
        return returnDate;
    }

    public void setReturnDate(Date returnDate)
    {
        this.returnDate = returnDate;
    }

    public Date getFinishDate()
    {
        return finishDate;
    }

    public void setFinishDate(Date finishDate)
    {
        this.finishDate = finishDate;
    }

    public Date getArchiveDate()
    {
        return archiveDate;
    }

    public void setArchiveDate(Date archiveDate)
    {
        this.archiveDate = archiveDate;
    }

    public String getArchivePath()
    {
        return archivePath;
    }

    public void setArchivePath(String archivePath)
    {
        this.archivePath = archivePath;
    }

    public String getContractPeriod()
    {
        return contractPeriod;
    }

    public void setContractPeriod(String contractPeriod)
    {
        this.contractPeriod = contractPeriod;
    }

    public String getPaymentTerms()
    {
        return paymentTerms;
    }

    public void setPaymentTerms(String paymentTerms)
    {
        this.paymentTerms = paymentTerms;
    }

    public BigDecimal getReceivedAmount()
    {
        return receivedAmount;
    }

    public void setReceivedAmount(BigDecimal receivedAmount)
    {
        this.receivedAmount = receivedAmount;
    }

    public String getExtraData()
    {
        return extraData;
    }

    public void setExtraData(String extraData)
    {
        this.extraData = extraData;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("contractNo", getContractNo())
            .append("contractName", getContractName())
            .append("clientUnit", getClientUnit())
            .append("contactName", getContactName())
            .append("contactPhone", getContactPhone())
            .append("contractType", getContractType())
            .append("contractAmount", getContractAmount())
            .append("signDate", getSignDate())
            .append("entrustDate", getEntrustDate())
            .append("auditDate", getAuditDate())
            .append("returnDate", getReturnDate())
            .append("finishDate", getFinishDate())
            .append("archiveDate", getArchiveDate())
            .append("archivePath", getArchivePath())
            .append("contractPeriod", getContractPeriod())
            .append("paymentTerms", getPaymentTerms())
            .append("receivedAmount", getReceivedAmount())
            .append("extraData", getExtraData())
            .append("delFlag", getDelFlag())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
