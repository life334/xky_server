package com.xakcch.project.mapper;

import java.util.List;
import com.xakcch.project.domain.ProjFieldDef;

/**
 * 动态字段定义 数据层
 *
 * @author liuyonghui
 */
public interface ProjFieldDefMapper
{
    /**
     * 查询指定表的所有启用动态字段
     *
     * @param tableName 目标表名
     * @return 动态字段列表
     */
    public List<ProjFieldDef> selectFieldDefByTableName(String tableName);
}
