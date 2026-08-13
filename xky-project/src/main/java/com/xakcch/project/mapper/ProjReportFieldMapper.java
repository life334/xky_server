package com.xakcch.project.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.xakcch.project.domain.ProjReportField;

/**
 * 报表模板字段 Mapper 接口
 *
 * @author liuyonghui
 */
public interface ProjReportFieldMapper
{
    /** 按模板ID查询字段清单（按 sort_order 排序） */
    List<ProjReportField> selectFieldsByTemplateId(Long templateId);

    /** 批量插入字段 */
    int batchInsertFields(@Param("templateId") Long templateId, @Param("fields") List<ProjReportField> fields);

    /** 按模板ID删除字段（重存时先删后插） */
    int deleteFieldsByTemplateId(Long templateId);
}
