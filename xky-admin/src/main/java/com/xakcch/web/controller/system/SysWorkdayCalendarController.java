package com.xakcch.web.controller.system;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xakcch.common.core.controller.BaseController;
import com.xakcch.common.core.domain.AjaxResult;
import com.xakcch.system.domain.SysWorkdayBatchRequest;
import com.xakcch.system.domain.SysWorkdayCalendar;
import com.xakcch.system.service.ISysWorkdayCalendarService;

/**
 * 工作日历 Controller
 * 
 * 维护法定节假日 / 调休上班日，供项目总时长（工作日）自动计算使用。
 * 
 * @author xky
 */
@RestController
@RequestMapping("/system/workday")
public class SysWorkdayCalendarController extends BaseController
{
    @Autowired
    private ISysWorkdayCalendarService workdayCalendarService;

    /**
     * 查询某日期区间的全部日历记录（无分页，供页面渲染和前端计算使用）
     * startDate/endDate 可空（默认查全部，建议传年份区间）
     */
    @PreAuthorize("@ss.hasPermi('system:workday:list')")
    @GetMapping("/all")
    public AjaxResult all(@RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate)
    {
        return success(workdayCalendarService.selectByDateRange(startDate, endDate));
    }

    /**
     * 获取某年维护状态（是否已维护 + 各类型数量）
     */
    @PreAuthorize("@ss.hasPermi('system:workday:list')")
    @GetMapping("/status")
    public AjaxResult status(@RequestParam Integer year)
    {
        return success(workdayCalendarService.getYearStatus(year));
    }

    /**
     * 新增单条（同日已存在时覆盖，幂等）
     */
    @PreAuthorize("@ss.hasPermi('system:workday:add')")
    @PostMapping
    public AjaxResult add(@RequestBody SysWorkdayCalendar record)
    {
        return toAjax(workdayCalendarService.insertOrUpdate(record));
    }

    /**
     * 修改单条（按日期）
     */
    @PreAuthorize("@ss.hasPermi('system:workday:edit')")
    @PutMapping
    public AjaxResult edit(@RequestBody SysWorkdayCalendar record)
    {
        return toAjax(workdayCalendarService.updateByDay(record));
    }

    /**
     * 删除单条（day = yyyy-MM-dd）
     */
    @PreAuthorize("@ss.hasPermi('system:workday:remove')")
    @DeleteMapping("/{day}")
    public AjaxResult remove(@PathVariable String day)
    {
        return toAjax(workdayCalendarService.deleteByDay(day));
    }

    /**
     * 生成某年全年周末基线（幂等：不覆盖节假日/调休记录）
     */
    @PreAuthorize("@ss.hasPermi('system:workday:generate')")
    @PostMapping("/generate")
    public AjaxResult generate(@RequestParam Integer year)
    {
        int count = workdayCalendarService.generateWeekendBaseline(year);
        return AjaxResult.success("已生成 " + year + " 年周末基线 " + count + " 条");
    }

    /**
     * 按日期区间批量录入/覆盖（如一次性录入春节假期 2/15-2/23）
     */
    @PreAuthorize("@ss.hasPermi('system:workday:batch')")
    @PostMapping("/batch")
    public AjaxResult batch(@RequestBody SysWorkdayBatchRequest request)
    {
        boolean overwrite = Boolean.TRUE.equals(request.getOverwrite());
        int count = workdayCalendarService.batchInsertRange(request.getStartDate(),
                request.getEndDate(), request.getDayType(), request.getRemark(), overwrite);
        String action = overwrite ? "覆盖" : "写入";
        return AjaxResult.success("批量" + action + "成功，共 " + count + " 条");
    }

    /**
     * 按日期区间批量清除（dayType 为空时清除该区间全部记录）
     */
    @PreAuthorize("@ss.hasPermi('system:workday:remove')")
    @DeleteMapping("/batch")
    public AjaxResult batchRemove(@RequestParam String startDate, @RequestParam String endDate,
            @RequestParam(required = false) String dayType)
    {
        int count = workdayCalendarService.deleteByDateRange(startDate, endDate, dayType);
        return AjaxResult.success("批量清除成功，共删除 " + count + " 条");
    }
}
