package com.xakcch.web.controller.project;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.xakcch.common.annotation.Log;
import com.xakcch.common.core.controller.BaseController;
import com.xakcch.common.core.domain.AjaxResult;
import com.xakcch.common.core.page.TableDataInfo;
import com.xakcch.common.enums.BusinessType;
import com.xakcch.project.domain.ProjFieldDef;
import com.xakcch.project.service.IProjFieldDefService;

/**
 * 动态字段定义 Controller
 *
 * @author liuyonghui
 */
@RestController
@RequestMapping("/system/fieldDef")
public class ProjFieldDefController extends BaseController
{
    @Autowired
    private IProjFieldDefService fieldDefService;

    /**
     * 查询动态字段定义列表
     */
    @PreAuthorize("@ss.hasPermi('system:fieldDef:list')")
    @GetMapping("/list")
    public TableDataInfo list(ProjFieldDef fieldDef)
    {
        startPage();
        List<ProjFieldDef> list = fieldDefService.selectFieldDefList(fieldDef);
        return getDataTable(list);
    }

    /**
     * 根据ID查询
     */
    @PreAuthorize("@ss.hasPermi('system:fieldDef:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable Long id)
    {
        return success(fieldDefService.selectFieldDefById(id));
    }

    /**
     * 新增
     */
    @PreAuthorize("@ss.hasPermi('system:fieldDef:add')")
    @Log(title = "动态字段定义", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody ProjFieldDef fieldDef)
    {
        return toAjax(fieldDefService.insertFieldDef(fieldDef));
    }

    /**
     * 修改
     */
    @PreAuthorize("@ss.hasPermi('system:fieldDef:edit')")
    @Log(title = "动态字段定义", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody ProjFieldDef fieldDef)
    {
        return toAjax(fieldDefService.updateFieldDef(fieldDef));
    }

    /**
     * 删除
     */
    @PreAuthorize("@ss.hasPermi('system:fieldDef:remove')")
    @Log(title = "动态字段定义", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(fieldDefService.deleteFieldDefByIds(ids));
    }

    /**
     * 根据表名查询已启用的动态字段（供业务页面动态渲染使用）
     */
    @GetMapping("/byTable/{tableName}")
    public AjaxResult byTable(@PathVariable String tableName)
    {
        return success(fieldDefService.selectFieldDefByTableName(tableName));
    }
}
