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
import com.xakcch.project.domain.ProjProject;
import com.xakcch.project.service.IProjProjectService;

/**
 * 项目主表 信息操作处理
 *
 * @author liuyonghui
 */
@RestController
@RequestMapping("/project/project")
public class ProjProjectController extends BaseController
{
    @Autowired
    private IProjProjectService projectService;

    /**
     * 查询项目列表（分页）
     */
    @PreAuthorize("@ss.hasPermi('project:project:list')")
    @GetMapping("/list")
    public TableDataInfo list(ProjProject project)
    {
        startPage();
        List<ProjProject> list = projectService.selectProjectList(project);
        return getDataTable(list);
    }

    /**
     * 导出项目列表
     */
    @PreAuthorize("@ss.hasPermi('project:project:export')")
    @Log(title = "项目信息", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, ProjProject project)
    {
        List<ProjProject> list = projectService.selectProjectList(project);
        ExcelUtil<ProjProject> util = new ExcelUtil<ProjProject>(ProjProject.class);
        util.exportExcel(response, list, "项目信息数据");
    }

    /**
     * 根据项目编号获取详细信息
     */
    @PreAuthorize("@ss.hasPermi('project:project:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable Long id)
    {
        return success(projectService.selectProjectById(id));
    }

    /**
     * 新增项目
     */
    @PreAuthorize("@ss.hasPermi('project:project:add')")
    @Log(title = "项目信息", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody ProjProject project)
    {
        if (!projectService.checkProjectCodeUnique(project))
        {
            return error("新增项目'" + project.getProjectName() + "'失败，工程编号已存在");
        }
        project.setCreateBy(getUsername());
        return toAjax(projectService.insertProject(project));
    }

    /**
     * 修改项目
     */
    @PreAuthorize("@ss.hasPermi('project:project:edit')")
    @Log(title = "项目信息", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody ProjProject project)
    {
        if (!projectService.checkProjectCodeUnique(project))
        {
            return error("修改项目'" + project.getProjectName() + "'失败，工程编号已存在");
        }
        project.setUpdateBy(getUsername());
        return toAjax(projectService.updateProject(project));
    }

    /**
     * 删除项目
     */
    @PreAuthorize("@ss.hasPermi('project:project:remove')")
    @Log(title = "项目信息", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(projectService.deleteProjectByIds(ids));
    }

    /**
     * 办结项目（状态改为已办结，不可逆）
     */
    @PreAuthorize("@ss.hasPermi('project:project:complete')")
    @Log(title = "项目信息", businessType = BusinessType.UPDATE)
    @PutMapping("/complete/{id}")
    public AjaxResult complete(@PathVariable Long id)
    {
        return toAjax(projectService.completeProject(id));
    }
}
