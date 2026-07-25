package com.xakcch.project.domain;

import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.xakcch.common.core.domain.BaseEntity;

/**
 * 资料流转记录表 proj_material_flow
 *
 * @author liuyonghui
 */
public class ProjMaterialFlow extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 资料ID */
    private Long materialId;

    /** 操作类型：领取/归还 */
    private String flowType;

    /** 操作人ID */
    private Long userId;

    /** 担保人ID（首次领取时必填） */
    private Long guarantorId;

    /** 操作时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date operateTime;

    /** 删除标志 */
    private String delFlag;

    // ===== 非持久化字段 =====

    /** 操作人姓名 */
    private String userName;

    /** 担保人姓名 */
    private String guarantorName;

    // ===== getter/setter =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long materialId) { this.materialId = materialId; }

    public String getFlowType() { return flowType; }
    public void setFlowType(String flowType) { this.flowType = flowType; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getGuarantorId() { return guarantorId; }
    public void setGuarantorId(Long guarantorId) { this.guarantorId = guarantorId; }

    public Date getOperateTime() { return operateTime; }
    public void setOperateTime(Date operateTime) { this.operateTime = operateTime; }

    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getGuarantorName() { return guarantorName; }
    public void setGuarantorName(String guarantorName) { this.guarantorName = guarantorName; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("materialId", getMaterialId())
            .append("flowType", getFlowType())
            .append("userId", getUserId())
            .append("guarantorId", getGuarantorId())
            .append("operateTime", getOperateTime())
            .append("remark", getRemark())
            .toString();
    }
}
