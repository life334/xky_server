package com.xakcch.project.domain;

import java.util.List;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.xakcch.common.core.domain.BaseEntity;

/**
 * 任务表 proj_task
 *
 * @author liuyonghui
 */
public class ProjTask extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 任务ID */
    private Long id;

    /** 项目ID，关联 proj_project.id */
    private Long projectId;

    /** 执行人用户ID，关联 sys_user.user_id */
    private Long userId;

    /** 任务名称 */
    private String taskName;

    /** 安排日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private java.util.Date assignDate;

    /** 要求完成日期（计划截止） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private java.util.Date requiredFinishDate;

    /** 实际完成日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private java.util.Date actualFinishDate;

    /** 工期要求 */
    private String durationRequire;

    /** 总时长（天） */
    private Integer totalDuration;

    /** 任务状态 */
    private String status;

    /** 动态字段数据（JSONB） */
    private String extraData;

    /** 删除标志（0正常 2删除） */
    private String delFlag;

    // ===== 以下为非持久化字段，列表/详情展示用 =====

    /** 项目名称（JOIN proj_project） */
    private String projectName;

    /** 执行人姓名（JOIN sys_user） */
    private String userName;

    /** 多选执行人ID列表（非持久化，前端批量新增用） */
    private List<Long> userIds;

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

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getTaskName()
    {
        return taskName;
    }

    public void setTaskName(String taskName)
    {
        this.taskName = taskName;
    }

    public java.util.Date getAssignDate()
    {
        return assignDate;
    }

    public void setAssignDate(java.util.Date assignDate)
    {
        this.assignDate = assignDate;
    }

    public java.util.Date getRequiredFinishDate()
    {
        return requiredFinishDate;
    }

    public void setRequiredFinishDate(java.util.Date requiredFinishDate)
    {
        this.requiredFinishDate = requiredFinishDate;
    }

    public java.util.Date getActualFinishDate()
    {
        return actualFinishDate;
    }

    public void setActualFinishDate(java.util.Date actualFinishDate)
    {
        this.actualFinishDate = actualFinishDate;
    }

    public String getDurationRequire()
    {
        return durationRequire;
    }

    public void setDurationRequire(String durationRequire)
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

    public String getProjectName()
    {
        return projectName;
    }

    public void setProjectName(String projectName)
    {
        this.projectName = projectName;
    }

    public String getUserName()
    {
        return userName;
    }

    public void setUserName(String userName)
    {
        this.userName = userName;
    }

    public List<Long> getUserIds()
    {
        return userIds;
    }

    public void setUserIds(List<Long> userIds)
    {
        this.userIds = userIds;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("projectId", getProjectId())
            .append("userId", getUserId())
            .append("taskName", getTaskName())
            .append("assignDate", getAssignDate())
            .append("requiredFinishDate", getRequiredFinishDate())
            .append("actualFinishDate", getActualFinishDate())
            .append("durationRequire", getDurationRequire())
            .append("totalDuration", getTotalDuration())
            .append("status", getStatus())
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
