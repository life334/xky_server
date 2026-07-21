package com.xakcch.project.mapper;

import java.util.List;
import com.xakcch.project.domain.ProjCategory;

/**
 * 项目类别 数据层
 *
 * @author liuyonghui
 */
public interface ProjCategoryMapper
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
     * 新增项目类别
     *
     * @param category 项目类别
     * @return 结果
     */
    public int insertCategory(ProjCategory category);

    /**
     * 修改项目类别
     *
     * @param category 项目类别
     * @return 结果
     */
    public int updateCategory(ProjCategory category);

    /**
     * 删除项目类别（逻辑删除）
     *
     * @param id 类别ID
     * @return 结果
     */
    public int deleteCategoryById(Long id);

    /**
     * 批量删除项目类别（逻辑删除）
     *
     * @param ids 需要删除的类别ID
     * @return 结果
     */
    public int deleteCategoryByIds(Long[] ids);

    /**
     * 查询是否存在子节点
     *
     * @param id 类别ID
     * @return 子节点数量
     */
    public int hasChildByCategoryId(Long id);

    /**
     * 校验类别名称是否唯一
     *
     * @param category 项目类别
     * @return 项目类别
     */
    public ProjCategory checkCategoryNameUnique(ProjCategory category);

    /**
     * 查询项目类别是否被项目引用
     *
     * @param id 类别ID
     * @return 引用数量
     */
    public int checkCategoryUsedByProject(Long id);
}
