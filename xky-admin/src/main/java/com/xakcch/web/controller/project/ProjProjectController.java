package com.xakcch.web.controller.project;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
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
import com.xakcch.common.annotation.Log;
import com.xakcch.common.core.controller.BaseController;
import com.xakcch.common.core.domain.AjaxResult;
import com.xakcch.common.core.page.TableDataInfo;
import com.xakcch.common.enums.BusinessType;
import com.xakcch.common.exception.ServiceException;
import com.xakcch.common.utils.DateUtils;
import com.xakcch.common.utils.SecurityUtils;
import com.xakcch.common.utils.file.FileUtils;
import com.xakcch.common.core.domain.entity.SysUser;
import com.xakcch.project.domain.ProjProject;
import com.xakcch.project.report.DynamicColumnExcelExporter;
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
     * 导出项目列表（可选字段 + 可选范围）
     *
     * <p>form 参数：
     * <ul>
     *   <li>{@code fields} 列 key 逗号串，按导出顺序；为空时回退为「默认可见列」</li>
     *   <li>{@code scope}  {@code all}（默认，全部筛选结果）/ {@code selected}（仅已勾选的行）</li>
     *   <li>{@code ids}    {@code scope=selected} 时的项目 id 逗号串</li>
     * </ul>
     *
     * <p>⚠️ 导出严格跟随当前筛选条件（含从首页驾驶舱下钻带入的、界面上看不见的隐藏条件），
     *    弹窗上的「命中 N 条」即最终导出的条数。</p>
     *
     * <p>⚠️ 字段清单以 {@code getListColumns()} 为唯一出处，未知 key 一律忽略
     *    （既防越权导出内部字段，也防参数拼错导致空列）。</p>
     */
    @PreAuthorize("@ss.hasPermi('project:project:export')")
    @Log(title = "项目信息", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, ProjProject project,
                       @RequestParam(required = false) String fields,
                       @RequestParam(required = false) String scope,
                       @RequestParam(required = false) String ids) throws IOException
    {
        List<Long> idScope = null;
        if ("selected".equals(scope))
        {
            idScope = toIdList(ids);
            if (idScope.isEmpty())
            {
                throw new ServiceException("请先勾选要导出的项目");
            }
        }

        List<String> fieldKeys = splitFields(fields);
        if (fieldKeys.isEmpty())
        {
            // 未指定字段 → 回退为默认可见列（与首次进入页面的表格一致）
            for (Map<String, Object> c : projectService.getListColumns())
            {
                if (Boolean.TRUE.equals(c.get("defaultVisible"))
                        && !"dynamic".equals(String.valueOf(c.get("group"))))
                {
                    fieldKeys.add(String.valueOf(c.get("key")));
                }
            }
        }

        Map<String, Object> data = projectService.exportProjects(project, fieldKeys, idScope);

        @SuppressWarnings("unchecked")
        List<DynamicColumnExcelExporter.Column> headers =
                (List<DynamicColumnExcelExporter.Column>) data.get("headers");
        @SuppressWarnings("unchecked")
        List<List<Object>> rows = (List<List<Object>>) data.get("rows");

        String fileName = "项目列表_" + DateUtils.parseDateToStr("yyyyMMddHHmmss", new java.util.Date()) + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        FileUtils.setAttachmentResponseHeader(response, fileName);
        // 不输出标题行：第 1 行即表头，导出的文件可直接用于筛选 / 透视 / 二次导入
        DynamicColumnExcelExporter.export(response.getOutputStream(), headers, rows);
    }

    /** 逗号串 → 项目 id 列表（导出「仅已勾选的行」用） */
    private List<Long> toIdList(String ids)
    {
        List<Long> list = new ArrayList<>();
        if (ids == null || ids.trim().isEmpty())
        {
            return list;
        }
        for (String s : ids.split(","))
        {
            if (s == null || s.trim().isEmpty())
            {
                continue;
            }
            try
            {
                list.add(Long.valueOf(s.trim()));
            }
            catch (NumberFormatException e)
            {
                throw new ServiceException("项目ID格式非法：" + s);
            }
        }
        return list;
    }

    /** 逗号串 → 字段 key 列表（去重保序） */
    private List<String> splitFields(String fields)
    {
        List<String> list = new ArrayList<>();
        if (fields == null || fields.trim().isEmpty())
        {
            return list;
        }
        for (String s : fields.split(","))
        {
            String k = s.trim();
            if (!k.isEmpty() && !list.contains(k))
            {
                list.add(k);
            }
        }
        return list;
    }

    /**
     * 查询字段去重值列表（高级筛选下拉选项用）
     * 支持字段：clientUnit（委托单位）、engineeringProject（项目类别）
     */
    @GetMapping("/distinctValues")
    public AjaxResult distinctValues(@RequestParam String field)
    {
        List<String> list = projectService.getDistinctValues(field);
        return success(list);
    }

    /**
     * 负责人下拉选项：在职项目经理 ∪ 项目表已出现过的负责人（含离职，status='1' 打标记）
     */
    @GetMapping("/leaderOptions")
    public AjaxResult leaderOptions()
    {
        List<SysUser> list = projectService.getLeaderOptions();
        return success(list);
    }

    /**
     * 按姓名获取/创建负责人档案：昵称精确匹配已有用户则返回；
     * 不存在则自动创建停用影子用户（不可登录），用于手动添加离职人员等历史负责人
     */
    @Log(title = "项目信息", businessType = BusinessType.INSERT)
    @PostMapping("/ensureLeader")
    public AjaxResult ensureLeader(@RequestParam String name)
    {
        SysUser u = projectService.ensureLeaderByName(name, getUsername());
        AjaxResult ajax = AjaxResult.success();
        ajax.put("userId", u.getUserId());
        ajax.put("nickName", u.getNickName());
        return ajax;
    }

    /**
     * 查询关联定线候选项目（验线项目选择后，加载同大类下定线项目的工程编号）
     */
    @GetMapping("/relatedCandidates")
    public AjaxResult relatedCandidates(@RequestParam String engineeringProject)
    {
        List<ProjProject> list = projectService.getRelatedCandidates(engineeringProject);
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
    @GetMapping(value = "/{id:[0-9]+}")
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
        // 已办结/已归档项目允许修改项目信息（2026-10-22 用户需求）；
        // 「变更状态」「删除」仍走 checkClosedProject 闸门，避免绕过业务关键动作。
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
    @DeleteMapping("/{ids:[0-9,]+}")
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
     * 人工修正项目办结时间
     *
     * <p>办结时间是产值归属月份、回款应收账期、欠款逾期预警的分片轴。
     * 2026-10-22 起入口并入「项目修改」弹窗，权限随普通编辑 {@code project:project:edit}；
     * 原 {@code project:project:closeTime} 权限位仍保留在 sys_menu，需要收紧时把下面注解改回即可还原。
     */
    @PreAuthorize("@ss.hasPermi('project:project:edit')")
    @Log(title = "项目信息", businessType = BusinessType.UPDATE)
    @PutMapping("/closeTime/{id}")
    public AjaxResult editCloseTime(@PathVariable Long id, @RequestBody Map<String, String> body)
    {
        String closeTimeStr = body == null ? null : body.get("closeTime");
        if (closeTimeStr == null || closeTimeStr.trim().isEmpty())
        {
            return error("请选择新的办结时间");
        }
        // ⚠️ DateUtils.parseDate 解析失败时返回 null（内部吞掉 ParseException），不抛异常，
        //    所以必须判 null 而不是 try/catch，否则会拿着 null 往下走。
        //    支持格式见 DateUtils.parsePatterns，含 "yyyy-MM-dd HH:mm:ss"。
        java.util.Date closeTime = DateUtils.parseDate(closeTimeStr.trim());
        if (closeTime == null)
        {
            return error("办结时间格式非法，应为 yyyy-MM-dd HH:mm:ss");
        }
        return toAjax(projectService.updateProjectCloseTime(id, closeTime));
    }

    /**
     * 批量新增项目（区域粘贴）
     * data 返回 { count: 成功条数, ids: 新写入记录ID列表 }
     */
    @PreAuthorize("@ss.hasPermi('project:project:add')")
    @Log(title = "项目信息", businessType = BusinessType.INSERT)
    @PostMapping("/batchAdd")
    public AjaxResult batchAdd(@RequestBody List<ProjProject> projectList)
    {
        List<Long> insertedIds = projectService.batchInsertProjectReturnIds(projectList, getUsername());
        AjaxResult result = AjaxResult.success("成功导入 " + insertedIds.size() + " 条数据");
        Map<String, Object> data = new HashMap<>();
        data.put("count", insertedIds.size());
        data.put("ids", insertedIds);
        result.put("data", data);
        return result;
    }

    /**
     * 校验已办结/已归档项目仅超级管理员可操作（修改 / 变更状态）
     *
     * <p>⚠️ archived 必须与 closed 同等对待：归档是数据终态，漏判会让任何有编辑权限的人改到归档项目。
     */
    private void checkClosedProject(Long projectId, String action)
    {
        if (projectId == null || SecurityUtils.isAdmin())
        {
            return;
        }
        ProjProject exist = projectService.selectProjectById(projectId);
        if (exist != null && ("closed".equals(exist.getStatus()) || "archived".equals(exist.getStatus())))
        {
            throw new ServiceException("已办结/已归档项目仅超级管理员可" + action);
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
            if (exist != null && ("closed".equals(exist.getStatus()) || "archived".equals(exist.getStatus())))
            {
                throw new ServiceException("已办结/已归档项目仅超级管理员可" + action + "：'" + exist.getProjectName() + "'");
            }
        }
    }
}
