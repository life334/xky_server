package com.xakcch.web.controller.project;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
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
import com.xakcch.common.utils.poi.ExcelUtil;
import com.xakcch.project.domain.ProjWorkload;
import com.xakcch.project.service.IProjWorkloadService;

/**
 * 工作量 信息操作处理
 *
 * @author liuyonghui
 */
@RestController
@RequestMapping("/project/workload")
public class ProjWorkloadController extends BaseController
{
    @Autowired
    private IProjWorkloadService workloadService;

    /**
     * 查询工作量列表（分页）
     */
    @PreAuthorize("@ss.hasPermi('project:workload:list')")
    @GetMapping("/list")
    public TableDataInfo list(ProjWorkload workload)
    {
        startPage();
        List<ProjWorkload> list = workloadService.selectWorkloadList(workload);
        return getDataTable(list);
    }

    /**
     * 导出工作量列表
     */
    @PreAuthorize("@ss.hasPermi('project:workload:export')")
    @Log(title = "工作量信息", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, ProjWorkload workload)
    {
        List<ProjWorkload> list = workloadService.selectWorkloadList(workload);
        ExcelUtil<ProjWorkload> util = new ExcelUtil<ProjWorkload>(ProjWorkload.class);
        util.exportExcel(response, list, "工作量信息数据");
    }

    /**
     * 根据ID获取详细信息
     */
    @PreAuthorize("@ss.hasPermi('project:workload:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable Long id)
    {
        return success(workloadService.selectWorkloadById(id));
    }

    /**
     * 新增工作量
     */
    @PreAuthorize("@ss.hasPermi('project:workload:add')")
    @Log(title = "工作量信息", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody ProjWorkload workload)
    {
        workload.setCreateBy(getUsername());
        return toAjax(workloadService.insertWorkload(workload));
    }

    /**
     * 修改工作量
     */
    @PreAuthorize("@ss.hasPermi('project:workload:edit')")
    @Log(title = "工作量信息", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody ProjWorkload workload)
    {
        workload.setUpdateBy(getUsername());
        return toAjax(workloadService.updateWorkload(workload));
    }

    /**
     * 删除工作量
     */
    @PreAuthorize("@ss.hasPermi('project:workload:remove')")
    @Log(title = "工作量信息", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(workloadService.deleteWorkloadByIds(ids));
    }
}
