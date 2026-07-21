package com.xakcch.web.controller.project;

import java.util.List;
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
import com.xakcch.common.constant.UserConstants;
import com.xakcch.common.core.controller.BaseController;
import com.xakcch.common.core.domain.AjaxResult;
import com.xakcch.common.core.domain.TreeSelect;
import com.xakcch.common.core.text.Convert;
import com.xakcch.common.enums.BusinessType;
import com.xakcch.common.utils.StringUtils;
import com.xakcch.project.domain.ProjCategory;
import com.xakcch.project.service.IProjCategoryService;

/**
 * 项目类别 信息操作处理
 *
 * @author liuyonghui
 */
@RestController
@RequestMapping("/project/category")
public class ProjCategoryController extends BaseController
{
    @Autowired
    private IProjCategoryService categoryService;

    /**
     * 获取项目类别列表
     */
    @PreAuthorize("@ss.hasPermi('project:category:list')")
    @GetMapping("/list")
    public AjaxResult list(ProjCategory category)
    {
        List<ProjCategory> list = categoryService.selectCategoryList(category);
        return success(list);
    }

    /**
     * 查询项目类别树结构（下拉树用）
     */
    @GetMapping("/treeselect")
    public AjaxResult treeselect(ProjCategory category)
    {
        List<ProjCategory> categories = categoryService.selectCategoryList(category);
        List<TreeSelect> treeSelects = categoryService.buildCategoryTreeSelect(categories);
        return success(treeSelects);
    }

    /**
     * 根据项目类别编号获取详细信息
     */
    @PreAuthorize("@ss.hasPermi('project:category:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable Long id)
    {
        return success(categoryService.selectCategoryById(id));
    }

    /**
     * 新增项目类别
     */
    @PreAuthorize("@ss.hasPermi('project:category:add')")
    @Log(title = "项目类别", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody ProjCategory category)
    {
        if (!categoryService.checkCategoryNameUnique(category))
        {
            return error("新增类别'" + category.getName() + "'失败，类别名称已存在");
        }
        category.setCreateBy(getUsername());
        return toAjax(categoryService.insertCategory(category));
    }

    /**
     * 修改项目类别
     */
    @PreAuthorize("@ss.hasPermi('project:category:edit')")
    @Log(title = "项目类别", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody ProjCategory category)
    {
        if (!categoryService.checkCategoryNameUnique(category))
        {
            return error("修改类别'" + category.getName() + "'失败，类别名称已存在");
        }
        category.setUpdateBy(getUsername());
        return toAjax(categoryService.updateCategory(category));
    }

    /**
     * 删除项目类别
     */
    @PreAuthorize("@ss.hasPermi('project:category:remove')")
    @Log(title = "项目类别", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(categoryService.deleteCategoryByIds(ids));
    }
}
