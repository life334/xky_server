package com.xakcch.project.service;

import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletResponse;
import com.xakcch.project.domain.ProjReportField;
import com.xakcch.project.domain.ProjReportFilter;
import com.xakcch.project.domain.ProjReportLog;
import com.xakcch.project.domain.ProjReportTemplate;

/**
 * 报表导出 业务层接口
 *
 * @author liuyonghui
 */
public interface IProjReportService
{
    // ==================== 模板 ====================

    /** 模板列表 */
    List<ProjReportTemplate> listTemplates(ProjReportTemplate query);

    /** 模板详情（含字段清单） */
    ProjReportTemplate getTemplate(Long id);

    /** 保存模板（新建/修改 + 字段清单重存） */
    Long saveTemplate(ProjReportTemplate template);

    /** 删除模板（逻辑删 + 字段逻辑删） */
    int deleteTemplate(Long id);

    /** 从内置模板复制为自定义模板 */
    Long copyTemplate(Long id, String newName);

    /** 模板字段清单 */
    List<ProjReportField> listTemplateFields(Long templateId);

    // ==================== 筛选方案 ====================

    /** 筛选方案列表 */
    List<ProjReportFilter> listFilters(ProjReportFilter query);

    /** 筛选方案详情 */
    ProjReportFilter getFilter(Long id);

    /** 保存筛选方案（新建/修改） */
    Long saveFilter(ProjReportFilter filter);

    /** 删除筛选方案 */
    int deleteFilter(Long id);

    // ==================== 字段池 ====================

    /** 字段池（固定字段 + 动态字段，按组） */
    List<Map<String, Object>> getFieldPool();

    // ==================== 导出 ====================

    /** 导出前预览：命中行数 + 前 50 行数据 */
    Map<String, Object> preview(Long templateId, Map<String, Object> filter);

    /** 导出报表（内置模板原样填充 / 自定义模板动态列） */
    void exportReport(Long templateId, Map<String, Object> filter, HttpServletResponse response);

    /** 按配置直接导出（不保存模板，临时使用） */
    void exportByConfig(ProjReportTemplate template, Map<String, Object> filter, HttpServletResponse response);

    // ==================== 导出历史 ====================

    /** 导出历史列表 */
    List<ProjReportLog> listLogs(ProjReportLog query);

    /** 一键重导（按历史记录重新导出同一范围） */
    void reExport(Long logId, HttpServletResponse response);

    /** 删除导出历史 */
    int deleteLog(Long id);
}
