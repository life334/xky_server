package com.xakcch.web.controller.project;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.xakcch.common.core.controller.BaseController;
import com.xakcch.common.core.domain.AjaxResult;
import com.xakcch.project.mapper.ProjAlertLogMapper;
import com.xakcch.project.service.IProjDashboardService;

/**
 * 首页驾驶舱
 *
 * @author liuyonghui
 */
@RestController
@RequestMapping("/project/dashboard")
public class ProjDashboardController extends BaseController
{
    @Autowired
    private IProjDashboardService dashboardService;

    @Autowired
    private ProjAlertLogMapper alertLogMapper;

    /**
     * 获取驾驶舱聚合数据
     *
     * @param beginDate 统计起始日期（yyyy-MM-dd），必填
     * @param endDate   统计截止日期（yyyy-MM-dd），必填
     */
    @GetMapping
    public AjaxResult getDashboard(@RequestParam String beginDate, @RequestParam String endDate)
    {
        return AjaxResult.success(dashboardService.getDashboardData(beginDate, endDate));
    }

    /**
     * 获取未读预警列表（合同超时等）
     */
    @GetMapping("/alerts")
    public AjaxResult getAlerts()
    {
        long count = alertLogMapper.countUnread();
        java.util.List<java.util.Map<String, Object>> list = alertLogMapper.selectUnreadList(20);
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("count", count);
        result.put("list", list);
        return AjaxResult.success(result);
    }

    // ============================================================
    // ===== v2（2026-09-27 契约）：summary / structure / trend / risk =====
    // 公共入参：beginDate/endDate（周期）+ clientUnit/leaderId/categoryId（轻筛选）
    //          + compareBeginDate/compareEndDate（对比周期，缺省按上一等长周期推算）
    // ============================================================

    /**
     * 段1 经营快照：6 磁贴（本年/本月合同额、本期新增/办结/到账/超期，含对比基准）+ KPI + 工期维护率。
     * 唯一首屏阻塞请求。
     */
    @GetMapping("/summary")
    public AjaxResult getSummary(com.xakcch.project.domain.ProjDashboardQuery query)
    {
        return AjaxResult.success(dashboardService.getSummary(query));
    }

    /**
     * 段2+3 业务结构：类型 5 桶统计（数量/占比/合同额/内外产值）、本期办结占比、负责人×类型矩阵。
     */
    @GetMapping("/structure")
    public AjaxResult getStructure(com.xakcch.project.domain.ProjDashboardQuery query)
    {
        return AjaxResult.success(dashboardService.getStructure(query));
    }

    /**
     * 段4 经营走势：外部产值月增量+累计双轴、项目动态三系列（新增/办结/到账）。
     */
    @GetMapping("/trend")
    public AjaxResult getTrend(com.xakcch.project.domain.ProjDashboardQuery query)
    {
        return AjaxResult.success(dashboardService.getTrend(query));
    }

    /**
     * 段5 风险与执行：欠款按年（办结年口径）、风险行动清单（欠款/工期超期/未关联合同 三源合并 top15）、计数。
     */
    @GetMapping("/risk")
    public AjaxResult getRisk(com.xakcch.project.domain.ProjDashboardQuery query)
    {
        return AjaxResult.success(dashboardService.getRisk(query));
    }
}
