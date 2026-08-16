package com.xakcch.project.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.xakcch.common.core.domain.BaseEntity;

/**
 * 项目类别表 proj_category
 *
 * @author liuyonghui
 */
public class ProjCategory extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 类别ID */
    private Long id;

    /** 父类别ID */
    private Long parentId;

    /** 类别名称 */
    private String name;

    /** 层级（1=大类 2=小类） */
    private Integer level;

    /** 内部默认单价 */
    private BigDecimal internalPrice;

    /** 外部默认单价 */
    private BigDecimal externalPrice;

    /** 排序号 */
    private Integer sortOrder;

    /** 状态（0启用 1停用） */
    private String status;

    /** 删除标志（0正常 2删除） */
    private String delFlag;

    /** 关联规则：0=不关联，1=可选关联，2=必须关联 */
    private Integer linkRule;

    /** 父类别名称（非持久化，列表展示用） */
    private String parentName;

    /** 子类别列表（非持久化，树结构用） */
    private List<ProjCategory> children = new ArrayList<ProjCategory>();

    /** 计费方式列表（非持久化，编辑保存时随类别一并提交） */
    private List<ProjCategoryBilling> billingList;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getParentId()
    {
        return parentId;
    }

    public void setParentId(Long parentId)
    {
        this.parentId = parentId;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public Integer getLevel()
    {
        return level;
    }

    public void setLevel(Integer level)
    {
        this.level = level;
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

    public Integer getSortOrder()
    {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder)
    {
        this.sortOrder = sortOrder;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }

    public Integer getLinkRule()
    {
        return linkRule;
    }

    public void setLinkRule(Integer linkRule)
    {
        this.linkRule = linkRule;
    }

    public String getParentName()
    {
        return parentName;
    }

    public void setParentName(String parentName)
    {
        this.parentName = parentName;
    }

    public List<ProjCategory> getChildren()
    {
        return children;
    }

    public void setChildren(List<ProjCategory> children)
    {
        this.children = children;
    }

    public List<ProjCategoryBilling> getBillingList()
    {
        return billingList;
    }

    public void setBillingList(List<ProjCategoryBilling> billingList)
    {
        this.billingList = billingList;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("parentId", getParentId())
            .append("name", getName())
            .append("level", getLevel())
            .append("internalPrice", getInternalPrice())
            .append("externalPrice", getExternalPrice())
            .append("sortOrder", getSortOrder())
            .append("status", getStatus())
            .append("delFlag", getDelFlag())
            .append("linkRule", getLinkRule())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
