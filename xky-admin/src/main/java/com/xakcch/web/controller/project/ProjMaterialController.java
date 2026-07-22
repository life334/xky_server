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
     */
    @PreAuthorize("@ss.hasPermi('project:material:list')")
    @GetMapping("/list")
    public TableDataInfo list(ProjMaterial material)
    {
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
}
