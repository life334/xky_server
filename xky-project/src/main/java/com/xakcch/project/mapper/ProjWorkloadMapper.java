package com.xakcch.project.mapper;

import java.util.List;
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
     * Upsert 工作量（按 project_id + user_id + category_id 唯一）
     *
     * @param workload 工作量
     * @return 结果
     */
    public int upsertWorkload(ProjWorkload workload);
}
