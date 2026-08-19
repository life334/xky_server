package com.xakcch.project.domain;

import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.xakcch.common.core.domain.BaseEntity;

/**
 * 回款催收记录表 proj_collection_log
 *
 * @author liuyonghui
 */
public class ProjCollectionLog extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 催收记录ID */
    private Long id;

    /** 项目ID，关联 proj_project.id */
    private Long projectId;

    /** 催收时间 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date collectTime;

    /** 催收方式（电话/函件/上门） */
    private String collectMethod;

    /** 联系人 */
    private String contactName;

    /** 催收结果 */
    private String collectResult;

    /** 下次催收日期（超期未催预警依据） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date nextCollectTime;

    /** 删除标志（0正常 2删除） */
    private String delFlag;

    // ===== 以下为非持久化字段 =====

    /** 工程编号（JOIN proj_project） */
    private String projectCode;

    /** 项目名称（JOIN proj_project） */
    private String projectName;

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

    public Date getCollectTime()
    {
        return collectTime;
    }

    public void setCollectTime(Date collectTime)
    {
        this.collectTime = collectTime;
    }

    public String getCollectMethod()
    {
        return collectMethod;
    }

    public void setCollectMethod(String collectMethod)
    {
        this.collectMethod = collectMethod;
    }

    public String getContactName()
    {
        return contactName;
    }

    public void setContactName(String contactName)
    {
        this.contactName = contactName;
    }

    public String getCollectResult()
    {
        return collectResult;
    }

    public void setCollectResult(String collectResult)
    {
        this.collectResult = collectResult;
    }

    public Date getNextCollectTime()
    {
        return nextCollectTime;
    }

    public void setNextCollectTime(Date nextCollectTime)
    {
        this.nextCollectTime = nextCollectTime;
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

    public String getProjectName()
    {
        return projectName;
    }

    public void setProjectName(String projectName)
    {
        this.projectName = projectName;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("id", getId())
                .append("projectId", getProjectId())
                .append("collectTime", getCollectTime())
                .append("collectMethod", getCollectMethod())
                .append("contactName", getContactName())
                .append("collectResult", getCollectResult())
                .append("nextCollectTime", getNextCollectTime())
                .append("createBy", getCreateBy())
                .append("createTime", getCreateTime())
                .toString();
    }
}
