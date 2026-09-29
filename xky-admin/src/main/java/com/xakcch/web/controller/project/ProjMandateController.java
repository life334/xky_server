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
import com.xakcch.project.service.IProjMandateService;

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
     * 批量设置项目性质（项目列表勾选后调用）
     *
     * <p>body: { "ids": [1,2,3], "nature": "mandate" | "normal" }</p>
     */
    @PreAuthorize("@ss.hasPermi('project:project:edit')")
    @Log(title = "指令性任务", businessType = BusinessType.UPDATE)
    @PutMapping("/nature")
    public AjaxResult setNature(@RequestBody Map<String, Object> body)
    {
        Object rawIds = body == null ? null : body.get("ids");
        List<Long> ids = toLongList(rawIds);
        Object nature = body == null ? null : body.get("nature");
        int rows = mandateService.setNature(ids, nature == null ? null : nature.toString());
        String action = IProjMandateService.NATURE_MANDATE.equals(String.valueOf(nature)) ? "设为指令性任务" : "取消指令性任务";
        return AjaxResult.success("已" + action + " " + rows + " 个项目", rows);
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
