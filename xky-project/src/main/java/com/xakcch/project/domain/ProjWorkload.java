package com.xakcch.project.domain;

import java.math.BigDecimal;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.xakcch.common.core.domain.BaseEntity;

/**
 * 工作量表 proj_workload
 *
 * @author liuyonghui
 */
public class ProjWorkload extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 工作量ID */
    private Long id;

    /** 项目ID，关联 proj_project.id */
    private Long projectId;

    /** 执行人用户ID，关联 sys_user.user_id */
    private Long userId;

    /** 项目类别ID（小类），关联 proj_category.id */
    private Long categoryId;

    /** 内部工作量 */
    private BigDecimal internalWorkload;

    /** 外部工作量 */
    private BigDecimal externalWorkload;

    /** 采用的内部单价 */
    private BigDecimal internalPrice;

    /** 采用的外部单价 */
    private BigDecimal externalPrice;

    /** 内部产值（工作量×内部单价，自动计算） */
    private BigDecimal internalOutput;

    /** 外部产值（工作量×外部单价，自动计算） */
    private BigDecimal externalOutput;

    /** 单价来源（contract=合同价 dict=字典默认价 manual=手动覆盖） */
    private String priceSource;

    /** 动态字段数据（JSONB） */
    private String extraData;

    /** 删除标志（0正常 2删除） */
    private String delFlag;

    // ===== 以下为非持久化字段，列表/详情展示用 =====

    /** 项目名称（JOIN proj_project） */
    private String projectName;

    /** 执行人姓名（JOIN sys_user） */
    private String userName;

    /** 类别名称（JOIN proj_category） */
    private String categoryName;

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

    public Long getCategoryId()
    {
        return categoryId;
    }

    public void setCategoryId(Long categoryId)
    {
        this.categoryId = categoryId;
    }

    public BigDecimal getInternalWorkload()
    {
        return internalWorkload;
    }

    public void setInternalWorkload(BigDecimal internalWorkload)
    {
        this.internalWorkload = internalWorkload;
    }

    public BigDecimal getExternalWorkload()
    {
        return externalWorkload;
    }

    public void setExternalWorkload(BigDecimal externalWorkload)
    {
        this.externalWorkload = externalWorkload;
    }

    public BigDecimal getInternalPrice()
    {
        return internalPrice;
    }

    public void setInternalPrice(BigDecimal internalPrice)
    {
        this.internalPrice = internalPrice;
    }

    public BigDecimal getExternalPrice()
    {
        return externalPrice;
    }

    public void setExternalPrice(BigDecimal externalPrice)
    {
        this.externalPrice = externalPrice;
    }

    public BigDecimal getInternalOutput()
    {
        return internalOutput;
    }

    public void setInternalOutput(BigDecimal internalOutput)
    {
        this.internalOutput = internalOutput;
    }

    public BigDecimal getExternalOutput()
    {
        return externalOutput;
    }

    public void setExternalOutput(BigDecimal externalOutput)
    {
        this.externalOutput = externalOutput;
    }

    public String getPriceSource()
    {
        return priceSource;
    }

    public void setPriceSource(String priceSource)
    {
        this.priceSource = priceSource;
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

    public String getCategoryName()
    {
        return categoryName;
    }

    public void setCategoryName(String categoryName)
    {
        this.categoryName = categoryName;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("projectId", getProjectId())
            .append("userId", getUserId())
            .append("categoryId", getCategoryId())
            .append("internalWorkload", getInternalWorkload())
            .append("externalWorkload", getExternalWorkload())
            .append("internalPrice", getInternalPrice())
            .append("externalPrice", getExternalPrice())
            .append("internalOutput", getInternalOutput())
            .append("externalOutput", getExternalOutput())
            .append("priceSource", getPriceSource())
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
