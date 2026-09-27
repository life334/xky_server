package com.xakcch.project.service;

import java.util.List;
import com.xakcch.project.domain.ProjWorkload;

/**
 * 工作量 业务层
 *
 * @author liuyonghui
 */
public interface IProjWorkloadService
{
    /**
     * 查询工作量列表（分页）
     *
     * @param workload 查询条件
     * @return 工作量集合
     */
    public List<ProjWorkload> selectWorkloadList(ProjWorkload workload);

    /**
     * 根据ID查询工作量
     *
     * @param id 工作量ID
     * @return 工作量
     */
    public ProjWorkload selectWorkloadById(Long id);

    /**
     * 新增工作量（自动计算产值）
     *
     * @param workload 工作量
     * @return 结果
     */
    public int insertWorkload(ProjWorkload workload);

    /**
     * 修改工作量（自动计算产值）
     *
     * @param workload 工作量
     * @return 结果
     */
    public int updateWorkload(ProjWorkload workload);

    /**
     * 批量删除工作量（逻辑删除）
     *
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteWorkloadByIds(Long[] ids);

    /**
     * 产值统计：合计（内部产值 / 外部产值 / 涉及项目数）+ 分组明细
     *
     * <p>归属时间 = 项目办结时间（close_time）；内外产值不相加。</p>
     *
     * @param params 查询条件（groupBy / begin / end / keyword / clientUnit / projectCategoryId / leaderId）
     * @return { summary: {...}, groups: [...] }
     */
    public java.util.Map<String, Object> selectOutputSummary(java.util.Map<String, Object> params);

    /**
     * 产值明细（下钻：项目级一行，与产值合计同一口径与筛选；分页由 Controller 控制）
     */
    public java.util.List<java.util.Map<String, Object>> selectOutputDetail(java.util.Map<String, Object> params);

    /**
     * 导出产值明细（与产值合计同一口径与筛选，不分页）
     */
    public java.util.List<com.xakcch.project.domain.vo.OutputDetailExportVo> selectOutputExportList(java.util.Map<String, Object> params);
}
