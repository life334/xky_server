package com.xakcch.project.service;

import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletResponse;
import com.xakcch.project.domain.ProjReportField;
import com.xakcch.project.domain.ProjReportFilter;
import com.xakcch.project.domain.ProjReportLog;
import com.xakcch.project.domain.ProjReportSubmitBatch;
import com.xakcch.project.domain.ProjReportSubmitLog;
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

    /** 保存模板默认筛选条件（default_filter JSONB） */
    int saveTemplateDefaultFilter(Long id, String defaultFilter);

    // ==================== 筛选方案 ====================

    /** 筛选方案列表 */
    List<ProjReportFilter> listFilters(ProjReportFilter query);

    /** 筛选方案详情 */
    ProjReportFilter getFilter(Long id);

    /** 保存筛选方案（新建/修改） */
    Long saveFilter(ProjReportFilter filter);

    /** 删除筛选方案 */
    int deleteFilter(Long id);

    /** 重命名筛选方案（仅创建者可重命名，只更新 filter_name 不清空条件） */
    int renameFilter(Long id, String filterName);

    // ==================== 字段池 ====================

    /** 字段池（固定字段 + 动态字段，按组） */
    List<Map<String, Object>> getFieldPool();

    // ==================== 导出 ====================

    /** 导出前预览：命中行数 + 前 50 行数据 */
    Map<String, Object> preview(Long templateId, Map<String, Object> filter);

    /** 导出报表（内置模板原样填充 / 自定义模板动态列） */
    void exportReport(Long templateId, Map<String, Object> filter, HttpServletResponse response);

    /** 导出报表（projectCodes 非空时仅导出勾选工程编号；预览勾选过滤用） */
    void exportReport(Long templateId, Map<String, Object> filter, List<String> projectCodes,
            HttpServletResponse response);

    /** 按配置直接导出（不保存模板，临时使用） */
    void exportByConfig(ProjReportTemplate template, Map<String, Object> filter, HttpServletResponse response);

    // ==================== 导出历史 ====================

    /** 导出历史列表 */
    List<ProjReportLog> listLogs(ProjReportLog query);

    /** 一键重导（按历史记录重新导出同一范围） */
    void reExport(Long logId, HttpServletResponse response);

    /** 删除导出历史 */
    int deleteLog(Long id);

    // ==================== 上报领导 ====================

    /**
     * 导出并上报领导：按勾选工程编号生成快照文件 + 记录批次与上报时间
     * （UNIQUE(project_code) 锁定上报时间，已上报记录跳过不修改）
     *
     * @return { batchId, batchNo, newCount, skippedCount, totalCount, snapshotFileName }
     */
    Map<String, Object> submitReport(Long templateId, Map<String, Object> filter,
            List<String> projectCodes, String remark);

    /** 上报批次列表 */
    List<ProjReportSubmitBatch> listSubmitBatches(ProjReportSubmitBatch query);

    /** 上报批次详情（含批次内记录） */
    ProjReportSubmitBatch getSubmitBatch(Long id);

    /** 上报记录列表 */
    List<ProjReportSubmitLog> listSubmitLogs(ProjReportSubmitLog query);

    /** 批量查询工程编号的上报状态（供前端标记已上报行） */
    List<ProjReportSubmitLog> listSubmittedStatus(List<String> projectCodes);

    /** 下载批次快照文件 */
    void downloadSnapshot(Long batchId, HttpServletResponse response);

    /** 删除批次（逻辑删，同时逻辑删批次内记录；仅超管） */
    int deleteSubmitBatch(Long id);

    /**
     * 修改历史补录记录的上报时间（仅超管；仅 batch_id 为空的历史补录行可改）
     *
     * <p>背景：历史数据导入时已按规则（有尾款取尾款到账时间、无尾款取预付款到账时间）
     * 补录过上报时间。业务规则是「一个定线项目只允许上报一次」，故不存在重新上报的路径，
     * 若导入时取错（如到账年份录错），只能在此处修正该历史行的上报时间。</p>
     *
     * @param submitTime 形如 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss
     * @return 1 表示已修改；0 表示记录不存在或不是历史补录行
     */
    int updateSubmitLogTime(Long id, String submitTime);

    /**
     * 删除单条上报记录（仅超管）
     *
     * <p>⚠️ 本系统实际不允许「删除后重新上报」：唯一索引 uk_submit_log_code 为整表唯一
     * （不含 del_flag 谓词），软删后该工程编号仍占位，重报会被 on conflict do nothing 静默跳过。
     * 即「一个定线项目只允许上报一次」由库层强制，删除只抹掉记录、不释放编号。</p>
     */
    int deleteSubmitLog(Long id);
}
