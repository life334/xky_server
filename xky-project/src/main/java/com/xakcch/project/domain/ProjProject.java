package com.xakcch.project.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.xakcch.common.core.domain.BaseEntity;

/**
 * 项目主表 proj_project
 *
 * @author liuyonghui
 */
public class ProjProject extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 项目ID */
    private Long id;

    /** 工程编号 */
    private String projectCode;

    /** 项目名称 */
    private String projectName;

    /** 工程项目（自由文本，用户自行填写） */
    private String engineeringProject;

    /** 项目类别ID（小类），关联 proj_category.id */
    private Long projectCategoryId;

    /** 委托单位 */
    private String clientUnit;

    /** 联系人 */
    private String contactName;

    /** 联系电话 */
    private String contactPhone;

    /** 工程地点 */
    private String projectLocation;

    /** 合同ID，关联 proj_contract.id */
    private Long contractId;

    /** 项目状态 */
    private String status;

    /** 动态字段数据（JSONB） */
    private String extraData;

    /** 删除标志（0正常 2删除） */
    private String delFlag;

    // ===== 以下为非持久化字段，列表/详情展示用 =====

    /** 项目类别名称（JOIN proj_category） */
    private String categoryName;

    /** 合同名称（JOIN proj_contract） */
    private String contractName;

    /** 负责人姓名列表（逗号分隔，JOIN proj_leader + sys_user） */
    private String leaderNames;

    /** 负责人用户ID数组（表单提交用） */
    private Long[] leaderIds;

    // ===== 以下为任务聚合字段（列表展示用） =====

    /** 最早安排日期（MIN proj_task.assign_date） */
    private java.util.Date assignDate;

    /** 工期要求天数（MAX required_finish_date - MIN assign_date） */
    private Integer durationRequire;

    /** 总时长天数（MAX actual_finish_date - MIN assign_date） */
    private Integer totalDuration;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public String getProjectCode()
    {
        return projectCode;
    }

    public void setProjectCode(String projectCode)
    {
        this.projectCode = projectCode;
    }

    public String getProjectName()
    {
        return projectName;
    }

    public void setProjectName(String projectName)
    {
        this.projectName = projectName;
    }

    public String getEngineeringProject()
    {
        return engineeringProject;
    }

    public void setEngineeringProject(String engineeringProject)
    {
        this.engineeringProject = engineeringProject;
    }

    public Long getProjectCategoryId()
    {
        return projectCategoryId;
    }

    public void setProjectCategoryId(Long projectCategoryId)
    {
        this.projectCategoryId = projectCategoryId;
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

    public String getProjectLocation()
    {
        return projectLocation;
    }

    public void setProjectLocation(String projectLocation)
    {
        this.projectLocation = projectLocation;
    }

    public Long getContractId()
    {
        return contractId;
    }

    public void setContractId(Long contractId)
    {
        this.contractId = contractId;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
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

    public String getCategoryName()
    {
        return categoryName;
    }

    public void setCategoryName(String categoryName)
    {
        this.categoryName = categoryName;
    }

    public String getContractName()
    {
        return contractName;
    }

    public void setContractName(String contractName)
    {
        this.contractName = contractName;
    }

    public String getLeaderNames()
    {
        return leaderNames;
    }

    public void setLeaderNames(String leaderNames)
    {
        this.leaderNames = leaderNames;
    }

    public Long[] getLeaderIds()
    {
        return leaderIds;
    }

    public void setLeaderIds(Long[] leaderIds)
    {
        this.leaderIds = leaderIds;
    }

    public java.util.Date getAssignDate()
    {
        return assignDate;
    }

    public void setAssignDate(java.util.Date assignDate)
    {
        this.assignDate = assignDate;
    }

    public Integer getDurationRequire()
    {
        return durationRequire;
    }

    public void setDurationRequire(Integer durationRequire)
    {
        this.durationRequire = durationRequire;
    }

    public Integer getTotalDuration()
    {
        return totalDuration;
    }

    public void setTotalDuration(Integer totalDuration)
    {
        this.totalDuration = totalDuration;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("projectCode", getProjectCode())
            .append("projectName", getProjectName())
            .append("engineeringProject", getEngineeringProject())
            .append("projectCategoryId", getProjectCategoryId())
            .append("clientUnit", getClientUnit())
            .append("contactName", getContactName())
            .append("contactPhone", getContactPhone())
            .append("projectLocation", getProjectLocation())
            .append("contractId", getContractId())
            .append("status", getStatus())
            .append("extraData", getExtraData())
            .append("delFlag", getDelFlag())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .append("assignDate", getAssignDate())
            .append("durationRequire", getDurationRequire())
            .append("totalDuration", getTotalDuration())
            .toString();
    }
}
