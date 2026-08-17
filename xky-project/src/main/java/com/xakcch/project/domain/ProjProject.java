package com.xakcch.project.domain;

import java.math.BigDecimal;
import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.xakcch.common.annotation.Excel;
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
    @Excel(name = "工程编号")
    private String projectCode;

    /** 项目名称 */
    @Excel(name = "项目名称")
    private String projectName;

    /** 工程项目（自由文本，用户自行填写） */
    @Excel(name = "工程项目")
    private String engineeringProject;

    /** 项目类别ID（小类），关联 proj_category.id */
    private Long projectCategoryId;

    /** 委托单位 */
    @Excel(name = "委托单位")
    private String clientUnit;

    /** 联系人 */
    @Excel(name = "联系人")
    private String contactName;

    /** 联系电话 */
    @Excel(name = "联系电话")
    private String contactPhone;

    /** 工程地点 */
    @Excel(name = "工程地点")
    private String projectLocation;

    /** 合同ID，关联 proj_contract.id */
    private Long contractId;

    /** 项目状态 */
    private String status;

    /** 动态字段数据（JSONB） */
    private String extraData;

    /** 删除标志（0正常 2删除） */
    private String delFlag;

    /** 关联定线项目ID（验线项目关联对应的定线项目） */
    private Long relatedProjectId;

    /** 安排日期 */
    @Excel(name = "安排日期", dateFormat = "yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private java.util.Date assignDate;

    /** 工期要求（天） */
    @Excel(name = "工期要求")
    private Integer durationRequire;

    /** 总时长（天） */
    @Excel(name = "总时长")
    private Integer totalDuration;

    /** 办结时间（状态流转为 closed 时写入，报表/补验线用） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private java.util.Date closeTime;

    // ===== 以下为非持久化字段，列表/详情展示用 =====

    /** 项目类别名称（JOIN proj_category，仅列表展示用，不再参与Excel导入导出） */
    private String categoryName;

    /** 合同名称（JOIN proj_contract） */
    private String contractName;

    /** 关联定线项目工程编号（JOIN proj_project，仅展示用） */
    private String relatedProjectCode;

    /** 负责人姓名列表（逗号分隔，JOIN proj_leader + sys_user） */
    @Excel(name = "项目负责人")
    private String leaderNames;

    /** 负责人用户ID数组（表单提交用） */
    private Long[] leaderIds;

    /** 【导入】备注 → BaseEntity.remark */
    @Excel(name = "备注")
    private String importRemark;

    // ===== 以下为 Excel 导入专用字段（transient，不存入 proj_project） =====

    /** 【导入】分配日期 → proj_task.assign_date */
    @Excel(name = "分配日期", dateFormat = "yyyy-MM-dd")
    private java.util.Date importTaskAssignDate;

    /** 【导入】验收日期 → proj_task.actual_finish_date */
    @Excel(name = "验收日期", dateFormat = "yyyy-MM-dd")
    private java.util.Date importTaskFinishDate;

    /** 【导入】总时长 → proj_task.total_duration */
    @Excel(name = "总时长")
    private Integer importTaskDuration;

    // ===== 以下为列表查询专用字段 =====

    /** 全局关键字搜索（匹配工程编号/项目名称/委托单位/工程项目/联系人/工程地点） */
    private String keyword;

    // ===== 以下为首笔付款字段（表单提交用，非持久化） =====

    /** 首笔付款金额 */
    private transient BigDecimal firstPaymentAmount;

    /** 首笔付款时间 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private transient Date firstPaymentTime;

    /** 首笔付款单位 */
    private transient String firstPaymentUnit;

    /** 首笔付款方式 */
    private transient String firstPaymentMethod;

    /** 负责人筛选（proj_leader.user_id） */
    private Long leaderId;

    /** 安排日期范围起始 */
    private String assignDateBegin;

    /** 安排日期范围结束 */
    private String assignDateEnd;

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

    public Long getRelatedProjectId()
    {
        return relatedProjectId;
    }

    public void setRelatedProjectId(Long relatedProjectId)
    {
        this.relatedProjectId = relatedProjectId;
    }

    public String getRelatedProjectCode()
    {
        return relatedProjectCode;
    }

    public void setRelatedProjectCode(String relatedProjectCode)
    {
        this.relatedProjectCode = relatedProjectCode;
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

    public java.util.Date getCloseTime()
    {
        return closeTime;
    }

    public void setCloseTime(java.util.Date closeTime)
    {
        this.closeTime = closeTime;
    }

    public String getImportRemark()
    {
        return importRemark;
    }

    public void setImportRemark(String importRemark)
    {
        this.importRemark = importRemark;
    }

    public java.util.Date getImportTaskAssignDate()
    {
        return importTaskAssignDate;
    }

    public void setImportTaskAssignDate(java.util.Date importTaskAssignDate)
    {
        this.importTaskAssignDate = importTaskAssignDate;
    }

    public java.util.Date getImportTaskFinishDate()
    {
        return importTaskFinishDate;
    }

    public void setImportTaskFinishDate(java.util.Date importTaskFinishDate)
    {
        this.importTaskFinishDate = importTaskFinishDate;
    }

    public Integer getImportTaskDuration()
    {
        return importTaskDuration;
    }

    public void setImportTaskDuration(Integer importTaskDuration)
    {
        this.importTaskDuration = importTaskDuration;
    }

    public BigDecimal getFirstPaymentAmount() { return firstPaymentAmount; }
    public void setFirstPaymentAmount(BigDecimal firstPaymentAmount) { this.firstPaymentAmount = firstPaymentAmount; }
    public Date getFirstPaymentTime() { return firstPaymentTime; }
    public void setFirstPaymentTime(Date firstPaymentTime) { this.firstPaymentTime = firstPaymentTime; }
    public String getFirstPaymentUnit() { return firstPaymentUnit; }
    public void setFirstPaymentUnit(String firstPaymentUnit) { this.firstPaymentUnit = firstPaymentUnit; }
    public String getFirstPaymentMethod() { return firstPaymentMethod; }
    public void setFirstPaymentMethod(String firstPaymentMethod) { this.firstPaymentMethod = firstPaymentMethod; }

    public String getKeyword()
    {
        return keyword;
    }

    public void setKeyword(String keyword)
    {
        this.keyword = keyword;
    }

    public Long getLeaderId()
    {
        return leaderId;
    }

    public void setLeaderId(Long leaderId)
    {
        this.leaderId = leaderId;
    }

    public String getAssignDateBegin()
    {
        return assignDateBegin;
    }

    public void setAssignDateBegin(String assignDateBegin)
    {
        this.assignDateBegin = assignDateBegin;
    }

    public String getAssignDateEnd()
    {
        return assignDateEnd;
    }

    public void setAssignDateEnd(String assignDateEnd)
    {
        this.assignDateEnd = assignDateEnd;
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
            .append("relatedProjectId", getRelatedProjectId())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .append("importRemark", getImportRemark())
            .append("assignDate", getAssignDate())
            .append("durationRequire", getDurationRequire())
            .append("totalDuration", getTotalDuration())
            .append("closeTime", getCloseTime())
            .append("importTaskAssignDate", getImportTaskAssignDate())
            .append("importTaskFinishDate", getImportTaskFinishDate())
            .append("importTaskDuration", getImportTaskDuration())
            .append("firstPaymentAmount", getFirstPaymentAmount())
            .append("firstPaymentTime", getFirstPaymentTime())
            .append("firstPaymentUnit", getFirstPaymentUnit())
            .toString();
    }
}
