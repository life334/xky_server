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
     * @param limit  每页条数（null = 不分页，取全量）
     * @param offset 偏移量（配合 limit 使用）
     * @return 行数据 List，每行 Map 的 key 为列别名
     */
    List<Map<String, Object>> selectProjectRows(@Param("f") Map<String, Object> filter,
            @Param("limit") Integer limit, @Param("offset") Integer offset);

    /** 命中行数（与 selectProjectRows 同 joins/where；供预览分页显示命中总数） */
    Long selectProjectRowCount(@Param("f") Map<String, Object> filter);

    /**
     * 单位到账汇总（单位 → 到账合计 + 最近到账时间）。
     * 供「只定未验」(zdyw) 按单位合并时「到账时间」列的汇总描述使用；
     * 独立聚合查询，使分页后每页的汇总仍为该单位全量口径，不因分页失真。
     */
    List<Map<String, Object>> selectUnitPaySummary(@Param("f") Map<String, Object> filter);
}
