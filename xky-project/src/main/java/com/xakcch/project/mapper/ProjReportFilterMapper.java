package com.xakcch.project.mapper;

import java.util.List;
import com.xakcch.project.domain.ProjReportFilter;

/**
 * 报表筛选方案 Mapper 接口
 *
 * @author liuyonghui
 */
public interface ProjReportFilterMapper
{
    /** 查询方案列表 */
    List<ProjReportFilter> selectFilterList(ProjReportFilter filter);

    /** 按ID查询方案 */
    ProjReportFilter selectFilterById(Long id);

    /** 新增方案 */
    int insertFilter(ProjReportFilter filter);

    /** 修改方案 */
    int updateFilter(ProjReportFilter filter);

    /** 逻辑删除方案 */
    int deleteFilterById(Long id);
}
