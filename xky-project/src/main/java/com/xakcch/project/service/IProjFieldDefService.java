package com.xakcch.project.service;

import java.util.List;
import com.xakcch.project.domain.ProjFieldDef;

/**
 * 动态字段定义 Service接口
 *
 * @author liuyonghui
 */
public interface IProjFieldDefService
{
    /**
     * 查询动态字段定义列表
     *
     * @param fieldDef 查询条件
     * @return 动态字段定义集合
     */
    public List<ProjFieldDef> selectFieldDefList(ProjFieldDef fieldDef);

    /**
     * 根据ID查询
     *
     * @param id 主键
     * @return 动态字段定义
     */
    public ProjFieldDef selectFieldDefById(Long id);

    /**
     * 新增
     *
     * @param fieldDef 动态字段定义
     * @return 结果
     */
    public int insertFieldDef(ProjFieldDef fieldDef);

    /**
     * 修改
     *
     * @param fieldDef 动态字段定义
     * @return 结果
     */
    public int updateFieldDef(ProjFieldDef fieldDef);

    /**
     * 批量删除（逻辑删除）
     *
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteFieldDefByIds(Long[] ids);

    /**
     * 查询指定表的所有启用动态字段
     *
     * @param tableName 目标表名
     * @return 动态字段列表
     */
    public List<ProjFieldDef> selectFieldDefByTableName(String tableName);
}
