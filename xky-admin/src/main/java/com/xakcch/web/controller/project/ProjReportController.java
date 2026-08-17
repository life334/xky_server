package com.xakcch.web.controller.project;

import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.xakcch.common.annotation.Log;
import com.xakcch.common.core.controller.BaseController;
import com.xakcch.common.core.domain.AjaxResult;
import com.xakcch.common.core.page.TableDataInfo;
import com.xakcch.common.enums.BusinessType;
import com.xakcch.common.utils.StringUtils;
import com.xakcch.project.domain.ProjReportFilter;
import com.xakcch.project.domain.ProjReportLog;
import com.xakcch.project.domain.ProjReportSubmitBatch;
import com.xakcch.project.domain.ProjReportSubmitLog;
import com.xakcch.project.domain.ProjReportTemplate;
import com.xakcch.project.service.IProjReportService;

/**
 * 报表导出 信息操作处理
 *
 * @author liuyonghui
 */
@RestController
@RequestMapping("/report")
public class ProjReportController extends BaseController
{
    @Autowired
    private IProjReportService reportService;

    // ==================== 字段池 ====================

    /** 字段池（固定字段 + 动态字段，按组） */
    @PreAuthorize("@ss.hasPermi('report:report:list')")
    @GetMapping("/fieldPool")
    public AjaxResult fieldPool()
    {
        return AjaxResult.success(reportService.getFieldPool());
    }

    // ==================== 模板 ====================

    /** 模板列表（内置 + 自定义） */
    @PreAuthorize("@ss.hasPermi('report:report:list')")
    @GetMapping("/template/list")
    public AjaxResult templateList(ProjReportTemplate query)
    {
        return AjaxResult.success(reportService.listTemplates(query));
    }

    /** 模板详情（含字段清单） */
    @PreAuthorize("@ss.hasPermi('report:report:list')")
    @GetMapping("/template/{id}")
    public AjaxResult templateDetail(@PathVariable Long id)
    {
        return AjaxResult.success(reportService.getTemplate(id));
    }

    /** 保存模板（新建/修改，含字段清单） */
    @PreAuthorize("@ss.hasPermi('report:report:template')")
    @Log(title = "报表模板", businessType = BusinessType.INSERT)
    @PostMapping("/template")
    public AjaxResult saveTemplate(@RequestBody ProjReportTemplate template)
    {
        return AjaxResult.success(reportService.saveTemplate(template));
    }

    /** 删除模板 */
    @PreAuthorize("@ss.hasPermi('report:report:template')")
    @Log(title = "报表模板", businessType = BusinessType.DELETE)
    @DeleteMapping("/template/{id}")
    public AjaxResult deleteTemplate(@PathVariable Long id)
    {
        return AjaxResult.success(reportService.deleteTemplate(id));
    }

    /** 从内置模板复制为自定义模板 */
    @PreAuthorize("@ss.hasPermi('report:report:template')")
    @PostMapping("/template/{id}/copy")
    public AjaxResult copyTemplate(@PathVariable Long id, @RequestBody Map<String, String> body)
    {
        String name = body == null ? null : body.get("name");
        return AjaxResult.success(reportService.copyTemplate(id, name));
    }

    /** 保存模板默认筛选条件（default_filter JSONB） */
    @PreAuthorize("@ss.hasPermi('report:report:filter')")
    @Log(title = "报表模板", businessType = BusinessType.UPDATE)
    @PutMapping("/template/{id}/defaultFilter")
    public AjaxResult saveTemplateDefaultFilter(@PathVariable Long id, @RequestBody Map<String, String> body)
    {
        String defaultFilter = body == null ? null : body.get("defaultFilter");
        return AjaxResult.success(reportService.saveTemplateDefaultFilter(id, defaultFilter));
    }

    // ==================== 筛选方案 ====================

    /** 筛选方案列表 */
    @PreAuthorize("@ss.hasPermi('report:report:list')")
    @GetMapping("/filter/list")
    public AjaxResult filterList(ProjReportFilter query)
    {
        return AjaxResult.success(reportService.listFilters(query));
    }

    /** 筛选方案详情 */
    @PreAuthorize("@ss.hasPermi('report:report:list')")
    @GetMapping("/filter/{id}")
    public AjaxResult filterDetail(@PathVariable Long id)
    {
        return AjaxResult.success(reportService.getFilter(id));
    }

    /** 保存筛选方案（新建/修改） */
    @PreAuthorize("@ss.hasPermi('report:report:filter')")
    @Log(title = "报表筛选方案", businessType = BusinessType.INSERT)
    @PostMapping("/filter")
    public AjaxResult saveFilter(@RequestBody ProjReportFilter filter)
    {
        return AjaxResult.success(reportService.saveFilter(filter));
    }

    /** 删除筛选方案 */
    @PreAuthorize("@ss.hasPermi('report:report:filter')")
    @Log(title = "报表筛选方案", businessType = BusinessType.DELETE)
    @DeleteMapping("/filter/{id}")
    public AjaxResult deleteFilter(@PathVariable Long id)
    {
        return AjaxResult.success(reportService.deleteFilter(id));
    }

    /** 重命名筛选方案（仅创建者可重命名） */
    @PreAuthorize("@ss.hasPermi('report:report:filter')")
    @PutMapping("/filter/{id}/rename")
    public AjaxResult renameFilter(@PathVariable Long id, @RequestBody Map<String, String> body)
    {
        String filterName = body == null ? null : body.get("filterName");
        if (StringUtils.isEmpty(filterName))
        {
            return AjaxResult.error("方案名称不能为空");
        }
        return AjaxResult.success(reportService.renameFilter(id, filterName.trim()));
    }

    // ==================== 导出 ====================

    /** 导出前预览：命中行数 + 前 50 行 */
    @PreAuthorize("@ss.hasPermi('report:report:list')")
    @PostMapping("/preview")
    public AjaxResult preview(@RequestBody Map<String, Object> body)
    {
        Long templateId = Long.valueOf(String.valueOf(body.get("templateId")));
        @SuppressWarnings("unchecked")
        Map<String, Object> filter = (Map<String, Object>) body.get("filter");
        return AjaxResult.success(reportService.preview(templateId, filter));
    }

    /** 导出报表（文件流；projectCodes 非空时仅导出勾选工程编号） */
    @PreAuthorize("@ss.hasPermi('report:report:export')")
    @Log(title = "报表导出", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(@RequestBody Map<String, Object> body, HttpServletResponse response)
    {
        Long templateId = Long.valueOf(String.valueOf(body.get("templateId")));
        @SuppressWarnings("unchecked")
        Map<String, Object> filter = (Map<String, Object>) body.get("filter");
        @SuppressWarnings("unchecked")
        List<String> projectCodes = (List<String>) body.get("projectCodes");
        reportService.exportReport(templateId, filter, projectCodes, response);
    }

    /** 按配置直接导出（不保存模板，临时使用） */
    @PreAuthorize("@ss.hasPermi('report:report:export')")
    @Log(title = "报表导出", businessType = BusinessType.EXPORT)
    @PostMapping("/exportByConfig")
    public void exportByConfig(@RequestBody Map<String, Object> body, HttpServletResponse response)
    {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        ProjReportTemplate template = mapper.convertValue(body.get("template"), ProjReportTemplate.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> filter = (Map<String, Object>) body.get("filter");
        reportService.exportByConfig(template, filter, response);
    }

    // ==================== 导出历史 ====================

    /** 导出历史列表 */
    @PreAuthorize("@ss.hasPermi('report:report:log')")
    @GetMapping("/log/list")
    public AjaxResult logList(ProjReportLog query)
    {
        return AjaxResult.success(reportService.listLogs(query));
    }

    /** 一键重导 */
    @PreAuthorize("@ss.hasPermi('report:report:export')")
    @PostMapping("/log/{id}/reExport")
    public void reExport(@PathVariable Long id, HttpServletResponse response)
    {
        reportService.reExport(id, response);
    }

    /** 删除导出历史 */
    @PreAuthorize("@ss.hasPermi('report:report:log')")
    @DeleteMapping("/log/{id}")
    public AjaxResult deleteLog(@PathVariable Long id)
    {
        return AjaxResult.success(reportService.deleteLog(id));
    }

    // ==================== 上报领导 ====================

    /** 导出并上报领导：勾选工程编号 + 备注 → 快照留档 + 记录级上报时间（已上报锁定跳过） */
    @PreAuthorize("@ss.hasPermi('report:report:export')")
    @Log(title = "报表上报", businessType = BusinessType.EXPORT)
    @PostMapping("/submit")
    public AjaxResult submit(@RequestBody Map<String, Object> body)
    {
        Long templateId = Long.valueOf(String.valueOf(body.get("templateId")));
        @SuppressWarnings("unchecked")
        Map<String, Object> filter = (Map<String, Object>) body.get("filter");
        @SuppressWarnings("unchecked")
        List<String> projectCodes = (List<String>) body.get("projectCodes");
        String remark = body.get("remark") == null ? null : body.get("remark").toString();
        return AjaxResult.success(reportService.submitReport(templateId, filter, projectCodes, remark));
    }

    /** 上报批次列表 */
    @PreAuthorize("@ss.hasPermi('report:report:log')")
    @GetMapping("/submit/batch/list")
    public TableDataInfo submitBatchList(ProjReportSubmitBatch query)
    {
        startPage();
        List<ProjReportSubmitBatch> list = reportService.listSubmitBatches(query);
        return getDataTable(list);
    }

    /** 上报批次详情（含批次内记录） */
    @PreAuthorize("@ss.hasPermi('report:report:log')")
    @GetMapping("/submit/batch/{id}")
    public AjaxResult submitBatchDetail(@PathVariable Long id)
    {
        return AjaxResult.success(reportService.getSubmitBatch(id));
    }

    /** 下载批次快照文件（上报当时的报表文件） */
    @PreAuthorize("@ss.hasPermi('report:report:log')")
    @GetMapping("/submit/batch/{id}/snapshot")
    public void submitBatchSnapshot(@PathVariable Long id, HttpServletResponse response)
    {
        reportService.downloadSnapshot(id, response);
    }

    /** 删除上报批次（仅管理员） */
    @PreAuthorize("@ss.hasPermi('report:report:log')")
    @Log(title = "报表上报", businessType = BusinessType.DELETE)
    @DeleteMapping("/submit/batch/{id}")
    public AjaxResult deleteSubmitBatch(@PathVariable Long id)
    {
        return AjaxResult.success(reportService.deleteSubmitBatch(id));
    }

    /** 上报记录列表 */
    @PreAuthorize("@ss.hasPermi('report:report:log')")
    @GetMapping("/submit/log/list")
    public TableDataInfo submitLogList(ProjReportSubmitLog query)
    {
        startPage();
        List<ProjReportSubmitLog> list = reportService.listSubmitLogs(query);
        return getDataTable(list);
    }

    /** 批量查询工程编号上报状态（预览标记已上报行） */
    @PreAuthorize("@ss.hasPermi('report:report:list')")
    @PostMapping("/submit/status")
    public AjaxResult submitStatus(@RequestBody Map<String, Object> body)
    {
        @SuppressWarnings("unchecked")
        List<String> projectCodes = (List<String>) body.get("projectCodes");
        return AjaxResult.success(reportService.listSubmittedStatus(projectCodes));
    }

    /** 删除单条上报记录（仅管理员；删除后该工程编号可重新上报） */
    @PreAuthorize("@ss.hasPermi('report:report:log')")
    @Log(title = "报表上报", businessType = BusinessType.DELETE)
    @DeleteMapping("/submit/log/{id}")
    public AjaxResult deleteSubmitLog(@PathVariable Long id)
    {
        return AjaxResult.success(reportService.deleteSubmitLog(id));
    }
}
