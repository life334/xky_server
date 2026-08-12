package com.xakcch.project.domain;

import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.xakcch.common.core.domain.BaseEntity;

/**
 * 资料提交表 proj_material
 *
 * @author liuyonghui
 */
public class ProjMaterial extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 资料ID */
    private Long id;

    /** 项目ID，关联 proj_project.id */
    private Long projectId;

    /** 提交时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date submitTime;

    /** 联系人 */
    private String contactName;

    /** 联系电话 */
    private String contactPhone;

    /** 成果类型（字典 proj_material_result_type） */
    private String resultType;

    /** 目录（用户自由填写） */
    private String archiveDir;

    /** 资料状态：待领取/已领取/已归还 */
    private String status;

    /** 提交状态（字典 proj_material_submit_status） */
    private String submitStatus;

    /** 是否担保（Y需要担保人 N不需要） */
    private String guarantorFlag;

    /** 担保人ID（关联 sys_user.user_id） */
    private Long guarantorId;

    /** 动态字段数据（JSONB） */
    private String extraData;

    /** 删除标志（0正常 2删除） */
    private String delFlag;

    // ===== 以下为非持久化字段，列表/详情展示用 =====

    /** 工程编号（JOIN proj_project） */
    private String projectCode;

    /** 委托任务（JOIN proj_project） */
    private String engineeringProject;

    /** 工程地点（JOIN proj_project） */
    private String projectLocation;

    /** 项目名称（JOIN proj_project） */
    private String projectName;

    // ===== 以下为 transient 查询参数（不入库） =====

    /** 全局搜索关键词 */
    private transient String keyword;

    /** 提交时间范围-开始 */
    private transient Date submitTimeBegin;

    /** 提交时间范围-结束 */
    private transient Date submitTimeEnd;

    // ===== getter/setter =====

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getProjectId()
    {
        return projectId;
    }

    public void setProjectId(Long projectId)
    {
        this.projectId = projectId;
    }

    public Date getSubmitTime()
    {
        return submitTime;
    }

    public void setSubmitTime(Date submitTime)
    {
        this.submitTime = submitTime;
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

    public String getResultType()
    {
        return resultType;
    }

    public void setResultType(String resultType)
    {
        this.resultType = resultType;
    }

    public String getArchiveDir()
    {
        return archiveDir;
    }

    public void setArchiveDir(String archiveDir)
    {
        this.archiveDir = archiveDir;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getSubmitStatus()
    {
        return submitStatus;
    }

    public void setSubmitStatus(String submitStatus)
    {
        this.submitStatus = submitStatus;
    }

    public String getGuarantorFlag()
    {
        return guarantorFlag;
    }

    public void setGuarantorFlag(String guarantorFlag)
    {
        this.guarantorFlag = guarantorFlag;
    }

    public Long getGuarantorId()
    {
        return guarantorId;
    }

    public void setGuarantorId(Long guarantorId)
    {
        this.guarantorId = guarantorId;
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

    public String getProjectCode()
    {
        return projectCode;
    }

    public void setProjectCode(String projectCode)
    {
        this.projectCode = projectCode;
    }

    public String getEngineeringProject()
    {
        return engineeringProject;
    }

    public void setEngineeringProject(String engineeringProject)
    {
        this.engineeringProject = engineeringProject;
    }

    public String getProjectLocation()
    {
        return projectLocation;
    }

    public void setProjectLocation(String projectLocation)
    {
        this.projectLocation = projectLocation;
    }

    public String getProjectName()
    {
        return projectName;
    }

    public void setProjectName(String projectName)
    {
        this.projectName = projectName;
    }

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public Date getSubmitTimeBegin() { return submitTimeBegin; }
    public void setSubmitTimeBegin(Date submitTimeBegin) { this.submitTimeBegin = submitTimeBegin; }
    public Date getSubmitTimeEnd() { return submitTimeEnd; }
    public void setSubmitTimeEnd(Date submitTimeEnd) { this.submitTimeEnd = submitTimeEnd; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("projectId", getProjectId())
            .append("projectCode", getProjectCode())
            .append("engineeringProject", getEngineeringProject())
            .append("projectLocation", getProjectLocation())
            .append("submitTime", getSubmitTime())
            .append("contactName", getContactName())
            .append("contactPhone", getContactPhone())
            .append("resultType", getResultType())
            .append("archiveDir", getArchiveDir())
            .append("status", getStatus())
            .append("guarantorFlag", getGuarantorFlag())
            .append("guarantorId", getGuarantorId())
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
