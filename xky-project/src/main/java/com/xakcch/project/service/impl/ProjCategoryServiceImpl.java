package com.xakcch.project.service.impl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.xakcch.common.core.domain.TreeSelect;
import com.xakcch.common.exception.ServiceException;
import com.xakcch.common.utils.StringUtils;
import com.xakcch.project.domain.ProjCategory;
import com.xakcch.project.mapper.ProjCategoryMapper;
import com.xakcch.project.service.IProjCategoryService;

/**
 * 项目类别 服务层实现
 *
 * @author liuyonghui
 */
@Service
public class ProjCategoryServiceImpl implements IProjCategoryService
{
    @Autowired
    private ProjCategoryMapper categoryMapper;

    /**
     * 查询项目类别详情
     */
    @Override
    public ProjCategory selectCategoryById(Long id)
    {
        return categoryMapper.selectCategoryById(id);
    }

    /**
     * 查询项目类别列表
     */
    @Override
    public List<ProjCategory> selectCategoryList(ProjCategory category)
    {
        return categoryMapper.selectCategoryList(category);
    }

    /**
     * 查询项目类别树结构
     */
    @Override
    public List<TreeSelect> selectCategoryTreeList(ProjCategory category)
    {
        List<ProjCategory> categories = categoryMapper.selectCategoryList(category);
        return buildCategoryTreeSelect(categories);
    }

    /**
     * 构建前端所需要树结构
     */
    @Override
    public List<ProjCategory> buildCategoryTree(List<ProjCategory> categories)
    {
        List<ProjCategory> returnList = new ArrayList<ProjCategory>();
        List<Long> tempList = categories.stream().map(ProjCategory::getId).collect(Collectors.toList());
        for (ProjCategory category : categories)
        {
            // 如果是顶级节点，遍历该父节点的所有子节点
            if (!tempList.contains(category.getParentId()))
            {
                recursionFn(categories, category);
                returnList.add(category);
            }
        }
        if (returnList.isEmpty())
        {
            returnList = categories;
        }
        return returnList;
    }

    /**
     * 构建前端所需要下拉树结构
     */
    @Override
    public List<TreeSelect> buildCategoryTreeSelect(List<ProjCategory> categories)
    {
        List<ProjCategory> categoryTrees = buildCategoryTree(categories);
        return categoryTrees.stream().map(this::convertToTreeSelect).collect(Collectors.toList());
    }

    /**
     * 将 ProjCategory 转换为 TreeSelect（含递归处理子节点）
     */
    private TreeSelect convertToTreeSelect(ProjCategory category)
    {
        TreeSelect treeSelect = new TreeSelect();
        treeSelect.setId(category.getId());
        treeSelect.setLabel(category.getName());
        treeSelect.setDisabled(!"0".equals(category.getStatus()));

        List<ProjCategory> children = category.getChildren();
        if (children != null && !children.isEmpty())
        {
            treeSelect.setChildren(
                children.stream().map(this::convertToTreeSelect).collect(Collectors.toList())
            );
        }
        return treeSelect;
    }

    /**
     * 递归列表
     */
    private void recursionFn(List<ProjCategory> list, ProjCategory t)
    {
        // 得到子节点列表
        List<ProjCategory> childList = getChildList(list, t);
        t.setChildren(childList);
        for (ProjCategory tChild : childList)
        {
            // 判断是否有子节点
            if (hasChild(list, tChild))
            {
                Iterator<ProjCategory> it = childList.iterator();
                while (it.hasNext())
                {
                    ProjCategory n = it.next();
                    recursionFn(list, n);
                }
            }
        }
    }

    /**
     * 得到子节点列表
     */
    private List<ProjCategory> getChildList(List<ProjCategory> list, ProjCategory t)
    {
        List<ProjCategory> tlist = new ArrayList<ProjCategory>();
        for (ProjCategory n : list)
        {
            if (n.getParentId() != null && n.getParentId().longValue() == t.getId().longValue())
            {
                tlist.add(n);
            }
        }
        return tlist;
    }

    /**
     * 判断是否有子节点
     */
    private boolean hasChild(List<ProjCategory> list, ProjCategory t)
    {
        return getChildList(list, t).size() > 0;
    }

    /**
     * 查询是否存在子节点
     */
    @Override
    public boolean hasChildByCategoryId(Long id)
    {
        int result = categoryMapper.hasChildByCategoryId(id);
        return result > 0;
    }

    /**
     * 查询项目类别是否被项目引用
     */
    @Override
    public boolean checkCategoryUsedByProject(Long id)
    {
        int result = categoryMapper.checkCategoryUsedByProject(id);
        return result > 0;
    }

    /**
     * 校验类别名称是否唯一
     */
    @Override
    public boolean checkCategoryNameUnique(ProjCategory category)
    {
        Long id = category.getId() == null ? -1L : category.getId();
        ProjCategory info = categoryMapper.checkCategoryNameUnique(category);
        if (info != null && info.getId().longValue() != id.longValue())
        {
            return false;
        }
        return true;
    }

    /**
     * 新增保存项目类别
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertCategory(ProjCategory category)
    {
        // 子类不可能有子节点，所以自动设 level
        if (category.getParentId() != null && category.getParentId() > 0)
        {
            category.setLevel(2);
        }
        else
        {
            category.setLevel(1);
            category.setParentId(null);
        }
        return categoryMapper.insertCategory(category);
    }

    /**
     * 修改保存项目类别
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateCategory(ProjCategory category)
    {
        if (category.getParentId() != null && category.getParentId().longValue() == category.getId().longValue())
        {
            throw new ServiceException("不能将自己设为父级");
        }
        return categoryMapper.updateCategory(category);
    }

    /**
     * 删除项目类别
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteCategoryById(Long id)
    {
        // 存在子节点不允许删除
        if (hasChildByCategoryId(id))
        {
            throw new ServiceException("存在子类别，不允许删除");
        }
        // 被项目引用不允许删除
        if (checkCategoryUsedByProject(id))
        {
            throw new ServiceException("该类别已被项目引用，不允许删除");
        }
        return categoryMapper.deleteCategoryById(id);
    }

    /**
     * 批量删除项目类别
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteCategoryByIds(Long[] ids)
    {
        for (Long id : ids)
        {
            deleteCategoryById(id);
        }
        return ids.length;
    }
}
