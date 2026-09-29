package com.xakcch.project.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.xakcch.common.core.domain.BaseEntity;

/**
 * 指令性任务规则 proj_mandate_rule
 *
 * <p>按「委托单位关键词」包含匹配（client_unit LIKE %keyword%）自动判定项目性质为指令性任务。
 * 之所以用包含匹配而非精确匹配：线上同一委托单位存在多个名称变体
 * （如「西安市勘察测绘院」「西安市勘察测绘院（管线检测）」「西安市勘察测绘院（发改委供电市政管线）」）。
 *
 * @author liuyonghui
 */
public class ProjMandateRule extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 规则ID */
    private Long id;

    /** 委托单位关键词（包含匹配） */
    private String keyword;

    /** 是否启用：0=启用 1=停用（与若依 sys_dict 约定一致） */
    private String enabled;

    /** 删除标志（0正常 2删除） */
    private String delFlag;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public String getKeyword()
    {
        return keyword;
    }

    public void setKeyword(String keyword)
    {
        this.keyword = keyword;
    }

    public String getEnabled()
    {
        return enabled;
    }

    public void setEnabled(String enabled)
    {
        this.enabled = enabled;
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
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("keyword", getKeyword())
            .append("enabled", getEnabled())
            .append("delFlag", getDelFlag())
            .append("remark", getRemark())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}
