package com.xakcch.web.controller.project;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
import com.xakcch.common.enums.BusinessType;
import com.xakcch.common.exception.ServiceException;
import com.xakcch.project.domain.ProjMandateRule;
import com.xakcch.project.domain.ProjProject;
import com.xakcch.project.service.IProjMandateService;
import com.xakcch.project.service.IProjProjectService;

/**
 * 指令性任务（项目性质 / 委托单位规则）操作处理
 *
 * <p>指令性任务的口径：委托单位命中规则关键词的项目，其外部产值（= 应收账款）不计入
 * 应收 / 全量外部产值，改道到独立的「指令性任务」指标；内部产值照常计算。</p>
 *
 * @author liuyonghui
 */
@RestController
@RequestMapping("/project/mandate")
public class ProjMandateController extends BaseController
{
    @Autowired
    private IProjMandateService mandateService;

    @Autowired
    private IProjProjectService projectService;

    /**
     * 查询规则列表
     */
    @PreAuthorize("@ss.hasPermi('project:project:list')")
    @GetMapping("/ruleList")
    public AjaxResult ruleList(ProjMandateRule rule)
    {
        return success(mandateService.selectRuleList(rule));
    }

    /**
     * 规则预览：启用中的关键词 + 命中项目数 + 其中已是指令性任务的数量
     */
    @PreAuthorize("@ss.hasPermi('project:project:list')")
    @GetMapping("/preview")
    public AjaxResult preview()
    {
        return success(mandateService.preview());
    }

    /**
     * 新增规则
     */
    @PreAuthorize("@ss.hasPermi('project:project:edit')")
    @Log(title = "指令性任务规则", businessType = BusinessType.INSERT)
    @PostMapping("/rule")
    public AjaxResult addRule(@RequestBody ProjMandateRule rule)
    {
        return toAjax(mandateService.insertRule(rule));
    }

    /**
     * 修改规则
     */
    @PreAuthorize("@ss.hasPermi('project:project:edit')")
    @Log(title = "指令性任务规则", businessType = BusinessType.UPDATE)
    @PutMapping("/rule")
    public AjaxResult editRule(@RequestBody ProjMandateRule rule)
    {
        return toAjax(mandateService.updateRule(rule));
    }

    /**
     * 删除规则（逻辑删除）
     */
    @PreAuthorize("@ss.hasPermi('project:project:edit')")
    @Log(title = "指令性任务规则", businessType = BusinessType.DELETE)
    @DeleteMapping("/rule/{ids}")
    public AjaxResult removeRule(@PathVariable Long[] ids)
    {
        return toAjax(mandateService.deleteRuleByIds(ids));
    }

    /**
     * 一键回填存量：把命中规则、当前不是指令性任务的项目批量打标
     */
    @PreAuthorize("@ss.hasPermi('project:project:edit')")
    @Log(title = "指令性任务", businessType = BusinessType.UPDATE)
    @PostMapping("/apply")
    public AjaxResult apply()
    {
        int rows = mandateService.applyToExisting();
        return AjaxResult.success("已标记 " + rows + " 个项目为指令性任务", rows);
    }

    /**
     * 批量设置项目性质
     *
     * <p>两种模式（body.mode）：
     * <ul>
     *   <li>{@code ids}（默认）：{ "ids": [1,2,3], "nature": "mandate" | "normal" } —— 作用于勾选的行</li>
     *   <li>{@code filter}：{ "filter": {…列表筛选参数…}, "nature": "mandate" | "normal" } ——
     *       作用于「当前筛选结果的全部行」；服务端按列表同一口径先查 id 再执行，
     *       避免前端传 id 列表与列表分页口径不一致导致范围偏差</li>
     * </ul>
     *
     * <p>⚠️ 删除操作不提供 filter 模式：批量删除必须逐条显式勾选，不开放高危入口。</p>
     */
    @PreAuthorize("@ss.hasPermi('project:project:edit')")
    @Log(title = "指令性任务", businessType = BusinessType.UPDATE)
    @PutMapping("/nature")
    public AjaxResult setNature(@RequestBody Map<String, Object> body)
    {
        Object nature = body == null ? null : body.get("nature");
        String natureStr = nature == null ? null : nature.toString();
        boolean filterMode = body != null && "filter".equals(String.valueOf(body.get("mode")));

        List<Long> ids = filterMode
                ? projectService.selectProjectIdsByFilter(toProjectQuery(body.get("filter")))
                : toLongList(body.get("ids"));
        if (ids.isEmpty())
        {
            return error("请先勾选要操作的项目");
        }

        int rows = mandateService.setNature(ids, natureStr);
        String action = IProjMandateService.NATURE_MANDATE.equals(natureStr) ? "设为指令性任务" : "取消指令性任务";
        return AjaxResult.success("已" + action + " " + rows + " 个项目", rows);
    }

    /**
     * 筛选条件 Map → 项目查询域对象（filter 模式批量操作用）。
     * <p>
     * ⚠️ 映射必须覆盖列表查询用到的全部字段：漏一个就会静默忽略该条件，
     *    表现为「批量操作影响的范围比列表显示的多」。
     */
    @SuppressWarnings("unchecked")
    private ProjProject toProjectQuery(Object raw)
    {
        ProjProject q = new ProjProject();
        if (!(raw instanceof Map))
        {
            return q;
        }
        Map<String, Object> m = (Map<String, Object>) raw;
        q.setKeyword(str(m.get("keyword")));
        q.setStatus(str(m.get("status")));
        q.setProjectCategoryId(toLong(m.get("projectCategoryId")));
        q.setLeaderId(toLong(m.get("leaderId")));
        q.setContractId(toLong(m.get("contractId")));
        q.setProjectCode(str(m.get("projectCode")));
        q.setProjectName(str(m.get("projectName")));
        q.setEngineeringProject(str(m.get("engineeringProject")));
        q.setContactName(str(m.get("contactName")));
        q.setContactPhone(str(m.get("contactPhone")));
        q.setProjectLocation(str(m.get("projectLocation")));
        q.setClientUnit(str(m.get("clientUnit")));
        q.setClientUnitNature(str(m.get("clientUnitNature")));
        q.setAssignDateBegin(str(m.get("assignDateBegin")));
        q.setAssignDateEnd(str(m.get("assignDateEnd")));
        q.setCloseDateBegin(str(m.get("closeDateBegin")));
        q.setCloseDateEnd(str(m.get("closeDateEnd")));
        q.setNewDateBegin(str(m.get("newDateBegin")));
        q.setNewDateEnd(str(m.get("newDateEnd")));
        q.setContractStatus(str(m.get("contractStatus")));
        q.setOverdue(str(m.get("overdue")));
        q.setProjectNature(str(m.get("projectNature")));
        q.setDataSource(str(m.get("dataSource")));
        q.setWorkloadEntry(str(m.get("workloadEntry")));
        q.setPaymentEntry(str(m.get("paymentEntry")));
        q.setInvoiceUnpaid(str(m.get("invoiceUnpaid")));
        q.setCategoryIncludeNull(str(m.get("categoryIncludeNull")));
        q.setProjectCategoryIds(toLongArray(m.get("projectCategoryIds")));
        // params：日期范围（前端 addDateRange 写入）+ 状态胶囊多选
        Object params = m.get("params");
        if (params instanceof Map)
        {
            Map<String, Object> p = (Map<String, Object>) params;
            if (p.get("beginTime") != null)
            {
                q.getParams().put("beginTime", str(p.get("beginTime")));
            }
            if (p.get("endTime") != null)
            {
                q.getParams().put("endTime", str(p.get("endTime")));
            }
            if (p.get("statusList") instanceof List)
            {
                q.getParams().put("statusList", p.get("statusList"));
            }
        }
        return q;
    }

    /** 任意对象 → 字符串（null / 空串统一为 null，保证 <if test="x != null and x != ''"> 不被误命中） */
    private String str(Object v)
    {
        if (v == null)
        {
            return null;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    /** 任意对象 → Long（非法值返回 null） */
    private Long toLong(Object v)
    {
        String s = str(v);
        if (s == null)
        {
            return null;
        }
        try
        {
            return Long.valueOf(s);
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    /** 任意对象（数组 / List / 逗号串）→ Long[] */
    private Long[] toLongArray(Object v)
    {
        if (v == null)
        {
            return null;
        }
        List<Long> list = new ArrayList<>();
        if (v instanceof List)
        {
            for (Object o : (List<?>) v)
            {
                Long l = toLong(o);
                if (l != null)
                {
                    list.add(l);
                }
            }
        }
        else
        {
            for (String s : String.valueOf(v).split(","))
            {
                Long l = toLong(s);
                if (l != null)
                {
                    list.add(l);
                }
            }
        }
        return list.isEmpty() ? null : list.toArray(new Long[0]);
    }

    /**
     * 自动打标开关（新建 / 导入项目时是否按规则自动判定项目性质）
     *
     * <p>body: { "enabled": true }</p>
     */
    @PreAuthorize("@ss.hasPermi('project:project:edit')")
    @Log(title = "指令性任务规则", businessType = BusinessType.UPDATE)
    @PutMapping("/autoMatch")
    public AjaxResult setAutoMatch(@RequestBody Map<String, Object> body)
    {
        Object v = body == null ? null : body.get("enabled");
        boolean enabled = v != null && ("true".equalsIgnoreCase(v.toString()) || "1".equals(v.toString()));
        mandateService.setAutoMatchEnabled(enabled);
        return AjaxResult.success(enabled ? "已开启自动打标" : "已关闭自动打标");
    }

    /**
     * JSON 数组 → Long 列表（前端传过来可能是 Integer / String，统一转换）
     */
    private List<Long> toLongList(Object raw)
    {
        List<Long> ids = new ArrayList<>();
        if (raw == null)
        {
            return ids;
        }
        if (raw instanceof List)
        {
            for (Object o : (List<?>) raw)
            {
                if (o == null)
                {
                    continue;
                }
                try
                {
                    ids.add(Long.valueOf(o.toString().trim()));
                }
                catch (NumberFormatException e)
                {
                    throw new ServiceException("项目ID格式非法：" + o);
                }
            }
            return ids;
        }
        // 兼容逗号分隔字符串
        for (String s : Arrays.asList(raw.toString().split(",")))
        {
            if (s != null && !s.trim().isEmpty())
            {
                ids.add(Long.valueOf(s.trim()));
            }
        }
        return ids;
    }
}
