package com.xakcch.project.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

/**
 * 报表导出-数据查询 Mapper（按筛选条件查询项目台账行）
 *
 * @author liuyonghui
 */
public interface ReportDataMapper
{
    /**
     * 查询项目台账行（一行一项目，JOIN 合同/付款汇总/负责人）
     *
     * @param filter 筛选条件 Map（key 见 ReportFieldPool 字段 key，value 为前端传入值）
     * @return 行数据 List，每行 Map 的 key 为列别名
     */
    List<Map<String, Object>> selectProjectRows(@Param("f") Map<String, Object> filter);
}
