package com.xakcch.web.controller.project;

import java.util.List;
import java.util.Map;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.xakcch.common.annotation.Log;
import com.xakcch.common.core.controller.BaseController;
import com.xakcch.common.core.domain.AjaxResult;
import com.xakcch.common.core.page.TableDataInfo;
import com.xakcch.common.enums.BusinessType;
import com.xakcch.common.exception.ServiceException;
import com.xakcch.common.utils.SecurityUtils;
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
     * 查询字段去重值列表（高级筛选下拉选项用）
     * 支持字段：clientUnit（委托单位）、engineeringProject（工程项目）
     */
    @GetMapping("/distinctValues")
    public AjaxResult distinctValues(@RequestParam String field)
    {
        List<String> list = projectService.getDistinctValues(field);
        return success(list);
    }

    /**
     * 统计各状态下的项目数量（状态胶囊导航用）
     */
    @GetMapping("/statusCounts")
    public AjaxResult statusCounts()
    {
        List<Map<String, Object>> list = projectService.getStatusCounts();
        return success(list);
    }

    /**
     * 查询项目列表可显隐列的元数据（显隐列面板 + 表格动态渲染用）
     * 物理字段从 information_schema 动态读取，另含 JOIN 字段、系统字段、动态字段
     */
    @GetMapping("/columns")
    public AjaxResult columns()
    {
        return success(projectService.getListColumns());
    }

    /**
     * 根据项目编号获取详细信息
     */
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
        checkClosedProject(project.getId(), "修改");
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
        checkClosedProjects(ids, "删除");
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

    /**
     * 变更项目状态
     */
    @PreAuthorize("@ss.hasPermi('project:project:edit')")
    @Log(title = "项目信息", businessType = BusinessType.UPDATE)
    @PutMapping("/changeStatus/{id}/{status}")
    public AjaxResult changeStatus(@PathVariable Long id, @PathVariable String status)
    {
        checkClosedProject(id, "变更状态");
        return toAjax(projectService.changeProjectStatus(id, status));
    }

    /**
     * 下载导入模板
     */
    @PostMapping("/importTemplate")
    public void importTemplate(HttpServletResponse response)
    {
        ExcelUtil<ProjProject> util = new ExcelUtil<ProjProject>(ProjProject.class);
        util.importTemplateExcel(response, "项目数据");
    }

    /**
     * 导入项目数据（Excel文件上传）
     */
    @PreAuthorize("@ss.hasPermi('project:project:import')")
    @Log(title = "项目信息", businessType = BusinessType.IMPORT)
    @PostMapping("/importData")
    public AjaxResult importData(MultipartFile file, boolean updateSupport) throws Exception
    {
        ExcelUtil<ProjProject> util = new ExcelUtil<ProjProject>(ProjProject.class);
        List<ProjProject> projectList = util.importExcel(file.getInputStream());
        String message = projectService.importProject(projectList, updateSupport, getUsername());
        return AjaxResult.success(message);
    }

    /**
     * 批量新增项目（区域粘贴）
     */
    @PreAuthorize("@ss.hasPermi('project:project:add')")
    @Log(title = "项目信息", businessType = BusinessType.INSERT)
    @PostMapping("/batchAdd")
    public AjaxResult batchAdd(@RequestBody List<ProjProject> projectList)
    {
        int count = projectService.batchInsertProject(projectList, getUsername());
        return AjaxResult.success("成功导入 " + count + " 条数据");
    }

    /**
     * 校验已办结项目仅超级管理员可操作（修改 / 变更状态）
     */
    private void checkClosedProject(Long projectId, String action)
    {
        if (projectId == null || SecurityUtils.isAdmin())
        {
            return;
        }
        ProjProject exist = projectService.selectProjectById(projectId);
        if (exist != null && "closed".equals(exist.getStatus()))
        {
            throw new ServiceException("已办结项目仅超级管理员可" + action);
        }
    }

    /**
     * 校验批量删除中的已办结项目（任一已办结即整体拒绝）
     */
    private void checkClosedProjects(Long[] ids, String action)
    {
        if (SecurityUtils.isAdmin())
        {
            return;
        }
        for (Long id : ids)
        {
            ProjProject exist = projectService.selectProjectById(id);
            if (exist != null && "closed".equals(exist.getStatus()))
            {
                throw new ServiceException("已办结项目仅超级管理员可" + action + "：'" + exist.getProjectName() + "'");
            }
        }
    }
}
