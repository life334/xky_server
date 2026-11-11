package com.xakcch.project.service;

import java.util.List;
import com.xakcch.common.core.domain.TreeSelect;
import com.xakcch.project.domain.ProjCategory;

/**
 * 项目类别 服务层
 *
 * @author liuyonghui
 */
public interface IProjCategoryService
{
    /**
     * 查询项目类别详情
     *
     * @param id 类别ID
     * @return 项目类别
     */
    public ProjCategory selectCategoryById(Long id);

    /**
     * 查询项目类别列表
     *
     * @param category 项目类别
     * @return 项目类别集合
     */
    public List<ProjCategory> selectCategoryList(ProjCategory category);

    /**
     * 查询项目类别树结构
     *
     * @param category 项目类别
     * @return 树结构列表
     */
    public List<TreeSelect> selectCategoryTreeList(ProjCategory category);

    /**
     * 构建前端所需要树结构
     *
     * @param categories 项目类别列表
     * @return 树结构列表
     */
    public List<ProjCategory> buildCategoryTree(List<ProjCategory> categories);

    /**
     * 构建前端所需要下拉树结构
     *
     * @param categories 项目类别列表
     * @return 下拉树结构列表
     */
    public List<TreeSelect> buildCategoryTreeSelect(List<ProjCategory> categories);

    /**
     * 查询是否存在子节点
     *
     * @param id 类别ID
     * @return 结果 true 存在 false 不存在
     */
    public boolean hasChildByCategoryId(Long id);

    /**
     * 查询项目类别是否被项目引用
     *
     * @param id 类别ID
     * @return 结果 true 存在 false 不存在
     */
    public boolean checkCategoryUsedByProject(Long id);

    /**
     * 校验类别名称是否唯一
     *
     * @param category 项目类别
     * @return 结果
     */
    public boolean checkCategoryNameUnique(ProjCategory category);

    /**
     * 新增保存项目类别
     *
     * @param category 项目类别
     * @return 结果
     */
    public int insertCategory(ProjCategory category);

    /**
     * 修改保存项目类别
     *
     * @param category 项目类别
     * @return 结果
     */
    public int updateCategory(ProjCategory category);

    /**
     * 新建类别后，把「类别文本同名、外键为空」的项目自动关联到该类别。
     * 典型场景：粘贴导入时类别尚未创建，项目只写入了文本；事后补建类别即可自动补齐外键。
     * 仅对子类别（level=2）调用；同名子类别不唯一时返回 0（交人工指定，避免挂错）。
     *
     * @param categoryId   类别ID
     * @param categoryName 类别名称
     * @return 回填行数
     */
    public int backfillProjectsByCategoryName(Long categoryId, String categoryName);

    /**
     * 删除项目类别
     *
     * @param id 类别ID
     * @return 结果
     */
    public int deleteCategoryById(Long id);

    /**
     * 批量删除项目类别
     *
     * @param ids 需要删除的类别ID
     * @return 结果
     */
    public int deleteCategoryByIds(Long[] ids);
}
