package com.xakcch.project.service;

import java.util.List;
import com.xakcch.project.domain.ProjTask;

/**
 * 任务表 服务层
 *
 * @author liuyonghui
 */
public interface IProjTaskService
{
    /**
     * 查询任务详情
     *
     * @param id 任务ID
     * @return 任务
     */
    public ProjTask selectTaskById(Long id);

    /**
     * 查询任务列表
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
     * 批量新增任务（按 userIds 多选执行人拆分）
     *
     * @param task 任务（含 userIds 列表）
     * @return 创建条数
     */
    public int insertTaskBatch(ProjTask task);

    /**
     * 修改任务
     *
     * @param task 任务
     * @return 结果
     */
    public int updateTask(ProjTask task);

    /**
     * 删除任务
     *
     * @param id 任务ID
     * @return 结果
     */
    public int deleteTaskById(Long id);

    /**
     * 批量删除任务
     *
     * @param ids 需要删除的任务ID
     * @return 结果
     */
    public int deleteTaskByIds(Long[] ids);
}
