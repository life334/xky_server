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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.xakcch.common.annotation.Log;
import com.xakcch.common.core.controller.BaseController;
import com.xakcch.common.core.domain.AjaxResult;
import com.xakcch.common.enums.BusinessType;
import com.xakcch.project.domain.ProjReportFilter;
import com.xakcch.project.domain.ProjReportLog;
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

    /** 导出报表（文件流） */
    @PreAuthorize("@ss.hasPermi('report:report:export')")
    @Log(title = "报表导出", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(@RequestBody Map<String, Object> body, HttpServletResponse response)
    {
        Long templateId = Long.valueOf(String.valueOf(body.get("templateId")));
        @SuppressWarnings("unchecked")
        Map<String, Object> filter = (Map<String, Object>) body.get("filter");
        reportService.exportReport(templateId, filter, response);
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
}
