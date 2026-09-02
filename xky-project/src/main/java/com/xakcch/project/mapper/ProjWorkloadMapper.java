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
}
