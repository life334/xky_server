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
import com.xakcch.project.domain.ProjTask;
import com.xakcch.project.service.IProjTaskService;

/**
 * 任务表 信息操作处理
 *
 * @author liuyonghui
 */
@RestController
@RequestMapping("/project/task")
public class ProjTaskController extends BaseController
{
    @Autowired
    private IProjTaskService taskService;

    /**
     * 查询任务列表（分页）
     */
    @PreAuthorize("@ss.hasPermi('project:task:list')")
    @GetMapping("/list")
    public TableDataInfo list(ProjTask task)
    {
        startPage();
        List<ProjTask> list = taskService.selectTaskList(task);
        return getDataTable(list);
    }

    /**
     * 导出任务列表
     */
    @PreAuthorize("@ss.hasPermi('project:task:export')")
    @Log(title = "任务信息", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, ProjTask task)
    {
        List<ProjTask> list = taskService.selectTaskList(task);
        ExcelUtil<ProjTask> util = new ExcelUtil<ProjTask>(ProjTask.class);
        util.exportExcel(response, list, "任务信息数据");
    }

    /**
     * 根据任务编号获取详细信息
     */
    @PreAuthorize("@ss.hasPermi('project:task:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable Long id)
    {
        return success(taskService.selectTaskById(id));
    }

    /**
     * 新增任务（支持多选执行人批量创建）
     */
    @PreAuthorize("@ss.hasPermi('project:task:add')")
    @Log(title = "任务信息", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody ProjTask task)
    {
        task.setCreateBy(getUsername());
        int count = taskService.insertTaskBatch(task);
        return success("成功创建 " + count + " 条任务");
    }

    /**
     * 修改任务
     */
    @PreAuthorize("@ss.hasPermi('project:task:edit')")
    @Log(title = "任务信息", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody ProjTask task)
    {
        task.setUpdateBy(getUsername());
        return toAjax(taskService.updateTask(task));
    }

    /**
     * 删除任务
     */
    @PreAuthorize("@ss.hasPermi('project:task:remove')")
    @Log(title = "任务信息", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(taskService.deleteTaskByIds(ids));
    }
}
