package com.xakcch.web.controller.project;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.xakcch.common.core.controller.BaseController;
import com.xakcch.common.core.domain.AjaxResult;
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

    /**
     * 获取驾驶舱聚合数据
     *
     * @param period 时间周期：month(本月) / quarter(本季) / year(本年)，默认 month
     */
    @GetMapping
    public AjaxResult getDashboard(@RequestParam(defaultValue = "month") String period)
    {
        return AjaxResult.success(dashboardService.getDashboardData(period));
    }
}
