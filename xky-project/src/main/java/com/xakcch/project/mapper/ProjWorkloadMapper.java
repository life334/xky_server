package com.xakcch.project.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.xakcch.project.domain.ProjWorkload;

/**
 * 工作量表 数据层
 *
 * @author liuyonghui
 */
public interface ProjWorkloadMapper
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
     * 新增工作量
     *
     * @param workload 工作量
     * @return 结果
     */
    public int insertWorkload(ProjWorkload workload);

    /**
     * 修改工作量
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
     * 按项目ID查询工作量列表（不分页，用于结算）
     *
     * @param projectId 项目ID
     * @return 工作量集合
     */
    public List<ProjWorkload> selectWorkloadsByProjectId(Long projectId);

    /**
     * 按项目ID数组批量查询工作量列表（用于结算列表，避免 N+1 查询）
     *
     * @param projectIds 项目ID数组
     * @return 工作量集合
     */
    public List<ProjWorkload> selectWorkloadsByProjectIds(@Param("projectIds") Long[] projectIds);

    /**
     * Upsert 工作量（按 project_id + user_id + category_id 唯一）
     *
     * @param workload 工作量
     * @return 结果
     */
    public int upsertWorkload(ProjWorkload workload);

    /**
     * 按项目ID删除工作量（逻辑删除，用于保存前全量替换）
     *
     * @param projectId 项目ID
     * @return 结果
     */
    public int deleteWorkloadsByProjectId(Long projectId);

    /**
     * 按项目ID数组批量删除工作量（逻辑删除，删除项目时级联清理）
     *
     * @param projectIds 项目ID数组
     * @return 结果
     */
    public int deleteWorkloadsByProjectIds(@Param("projectIds") Long[] projectIds);

    /**
     * 查询项目下最大子项序号（用于导入时子项自增）
     *
     * @param projectId 项目ID
     * @return 最大子项序号（无子项时返回 0）
     */
    public Integer selectMaxSubItemNo(Long projectId);

    /**
     * 批量新增工作量（导入时一次性插入，替代逐条 insertWorkload）
     *
     * @param list 工作量列表
     * @return 结果
     */
    public int insertWorkloadBatch(@Param("list") List<ProjWorkload> list);

    /**
     * 单价重算专用：按 id 更新单价/起步量/外部产值/来源/备份价（仅外部工作量重算调用）
     * extraData 为 null 时不覆盖原值。
     *
     * @param id               工作量ID
     * @param unitPrice        新的单价（同时写入 unit_price 与 external_price）
     * @param externalOutput   新的外部产值（起步量取整后 × 工作量）
     * @param priceSource      单价来源（manual=手改价 contract=合同价 imported=导入推导价 dict=字典价）
     * @param minQuantity      新的起步量（可为 null）
     * @param minQuantitySource 起步量来源（manual=手改 contract=合同起步量 dict=字典起步量）
     * @param extraData        extra_data JSON（如 origin_price 备份），可为 null
     * @return 结果
     */
    public int updatePriceById(@Param("id") Long id,
                               @Param("unitPrice") java.math.BigDecimal unitPrice,
                               @Param("externalOutput") java.math.BigDecimal externalOutput,
                               @Param("priceSource") String priceSource,
                               @Param("minQuantity") java.math.BigDecimal minQuantity,
                               @Param("minQuantitySource") String minQuantitySource,
                               @Param("extraData") String extraData);

    /**
     * 产值合计（内部产值 / 外部产值 / 涉及项目数；按 proj_project.close_time 归属）
     */
    public java.util.Map<String, Object> selectOutputSummary(java.util.Map<String, Object> params);

    /**
     * 产值分组明细（按月 / 季 / 年 / 委托单位 / 负责人 / 项目类别）
     */
    public java.util.List<java.util.Map<String, Object>> selectOutputSummaryGroups(java.util.Map<String, Object> params);

    /**
     * 产值明细（下钻：项目级一行，与产值合计同一口径与筛选）
     */
    public java.util.List<java.util.Map<String, Object>> selectOutputDetail(java.util.Map<String, Object> params);
}
