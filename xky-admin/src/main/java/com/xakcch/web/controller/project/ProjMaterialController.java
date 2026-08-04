package com.xakcch.web.controller.project;

import java.util.Arrays;
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
import com.xakcch.common.core.domain.model.LoginUser;
import com.xakcch.common.core.page.TableDataInfo;
import com.xakcch.common.enums.BusinessType;
import com.xakcch.common.utils.SecurityUtils;
import com.xakcch.common.utils.poi.ExcelUtil;
import com.xakcch.project.domain.ProjMaterial;
import com.xakcch.project.service.IProjMaterialService;

/**
 * 资料提交 信息操作处理
 *
 * @author liuyonghui
 */
@RestController
@RequestMapping("/project/material")
public class ProjMaterialController extends BaseController
{
    @Autowired
    private IProjMaterialService materialService;

    /**
     * 查询资料提交列表（分页）
     * 默认只显示"已办结"和"已归档"项目的资料，传 projectStatus=all 显示全部
     */
    @PreAuthorize("@ss.hasPermi('project:material:list')")
    @GetMapping("/list")
    public TableDataInfo list(ProjMaterial material,
        @RequestParam(required = false) String projectStatus)
    {
        if (projectStatus == null || projectStatus.isEmpty())
        {
            projectStatus = "closed,archived";
        }
        if (!"all".equals(projectStatus))
        {
            material.getParams().put("statusList", Arrays.asList(projectStatus.split(",")));
        }
        startPage();
        List<ProjMaterial> list = materialService.selectMaterialList(material);
        return getDataTable(list);
    }

    /**
     * 导出资料提交
     */
    @PreAuthorize("@ss.hasPermi('project:material:export')")
    @Log(title = "资料提交", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, ProjMaterial material)
    {
        List<ProjMaterial> list = materialService.selectMaterialList(material);
        ExcelUtil<ProjMaterial> util = new ExcelUtil<ProjMaterial>(ProjMaterial.class);
        util.exportExcel(response, list, "资料提交数据");
    }

    /**
     * 根据ID获取详细信息
     */
    @PreAuthorize("@ss.hasPermi('project:material:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable Long id)
    {
        return success(materialService.selectMaterialById(id));
    }

    /**
     * 新增资料提交
     */
    @PreAuthorize("@ss.hasPermi('project:material:add')")
    @Log(title = "资料提交", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody ProjMaterial material)
    {
        material.setCreateBy(getUsername());
        if (material.getStatus() == null) material.setStatus("pending");
        return toAjax(materialService.insertMaterial(material));
    }

    /**
     * 修改资料提交
     */
    @PreAuthorize("@ss.hasPermi('project:material:edit')")
    @Log(title = "资料提交", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody ProjMaterial material)
    {
        material.setUpdateBy(getUsername());
        return toAjax(materialService.updateMaterial(material));
    }

    /**
     * 删除资料提交
     */
    @PreAuthorize("@ss.hasPermi('project:material:remove')")
    @Log(title = "资料提交", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(materialService.deleteMaterialByIds(ids));
    }

    /**
     * 领取资料
     */
    @PreAuthorize("@ss.hasPermi('project:material:borrow')")
    @Log(title = "资料领取", businessType = BusinessType.UPDATE)
    @PutMapping("/borrow/{id}")
    public AjaxResult borrow(@PathVariable Long id, @RequestBody Map<String, Object> params)
    {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        Long userId = loginUser.getUser().getUserId();
        String userName = loginUser.getUser().getNickName();
        Long guarantorId = params.get("guarantorId") != null
            ? Long.valueOf(params.get("guarantorId").toString()) : null;
        String remark = params.get("remark") != null ? params.get("remark").toString() : null;
        materialService.borrowMaterial(id, guarantorId, remark, userId, userName);
        return success();
    }

    /**
     * 归还资料
     */
    @PreAuthorize("@ss.hasPermi('project:material:return')")
    @Log(title = "资料归还", businessType = BusinessType.UPDATE)
    @PutMapping("/return/{id}")
    public AjaxResult returnMaterial(@PathVariable Long id, @RequestBody Map<String, Object> params)
    {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        Long userId = loginUser.getUser().getUserId();
        String userName = loginUser.getUser().getNickName();
        String remark = params.get("remark") != null ? params.get("remark").toString() : null;
        materialService.returnMaterial(id, remark, userId, userName);
        return success();
    }

    /**
     * 查询流转记录
     */
    @PreAuthorize("@ss.hasPermi('project:material:query')")
    @GetMapping("/flow/{id}")
    public AjaxResult flowList(@PathVariable Long id)
    {
        return success(materialService.getFlowList(id));
    }
}
