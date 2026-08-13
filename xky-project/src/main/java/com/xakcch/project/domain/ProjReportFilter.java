package com.xakcch.project.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.xakcch.common.core.domain.BaseEntity;

/**
 * 报表导出-筛选方案表 proj_report_filter
 *
 * @author liuyonghui
 */
public class ProjReportFilter extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 方案ID */
    private Long id;

    /** 方案名称 */
    private String filterName;

    /** 配置 JSONB：{selected:[字段key...], values:{key:value}} */
    private String filterConfig;

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

    public String getFilterName()
    {
        return filterName;
    }

    public void setFilterName(String filterName)
    {
        this.filterName = filterName;
    }

    public String getFilterConfig()
    {
        return filterConfig;
    }

    public void setFilterConfig(String filterConfig)
    {
        this.filterConfig = filterConfig;
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
            .append("filterName", getFilterName())
            .append("filterConfig", getFilterConfig())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .toString();
    }
}
