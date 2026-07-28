package com.xakcch.project.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.xakcch.project.domain.ProjTask;

/**
 * 任务表 数据层
 *
 * @author liuyonghui
 */
public interface ProjTaskMapper
{
    /**
     * 查询任务详情（含项目名、执行人名）
     *
     * @param id 任务ID
     * @return 任务
     */
    public ProjTask selectTaskById(Long id);

    /**
     * 查询任务列表（分页，含关联信息）
     *
     * @param task 查询条件
     * @return 任务集合
     */
    public List<ProjTask> selectTaskList(ProjTask task);

    /**
     * 新增任务
     *
     * @param task 任务
     * @return 结果
     */
    public int insertTask(ProjTask task);

    /**
     * 修改任务
     *
     * @param task 任务
     * @return 结果
     */
    public int updateTask(ProjTask task);

    /**
     * 删除任务（逻辑删除）
     *
     * @param id 任务ID
     * @return 结果
     */
    public int deleteTaskById(Long id);

    /**
     * 批量删除任务（逻辑删除）
     *
     * @param ids 需要删除的任务ID
     * @return 结果
     */
    public int deleteTaskByIds(Long[] ids);

    /**
     * 查询项目下所有任务的执行人ID（用于负责人变更时增量同步）
     *
     * @param projectId 项目ID
     * @return 执行人ID列表
     */
    public List<Long> selectTaskUserIdsByProjectId(Long projectId);

    /**
     * 逻辑删除指定项目下指定执行人的任务（负责人被移除时调用）
     *
     * @param projectId 项目ID
     * @param userIds   要删除任务的执行人ID列表
     * @return 结果
     */
    public int deleteTaskByProjectIdAndUserIds(@Param("projectId") Long projectId, @Param("userIds") List<Long> userIds);
}
