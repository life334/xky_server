package com.xakcch.project.mapper;

import java.util.List;
import com.xakcch.project.domain.ProjReportTemplate;

/**
 * 报表模板 Mapper 接口
 *
 * @author liuyonghui
 */
public interface ProjReportTemplateMapper
{
    /** 查询模板列表（可按类型/名称过滤） */
    List<ProjReportTemplate> selectTemplateList(ProjReportTemplate template);

    /** 按ID查询模板 */
    ProjReportTemplate selectTemplateById(Long id);

    /** 新增模板 */
    int insertTemplate(ProjReportTemplate template);

    /** 修改模板 */
    int updateTemplate(ProjReportTemplate template);

    /** 逻辑删除模板 */
    int deleteTemplateById(Long id);
}
