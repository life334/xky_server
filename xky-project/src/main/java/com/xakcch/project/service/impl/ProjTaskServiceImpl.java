package com.xakcch.project.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.xakcch.project.domain.ProjTask;
import com.xakcch.project.mapper.ProjTaskMapper;
import com.xakcch.project.service.IProjTaskService;

/**
 * 任务表 服务层实现
 *
 * @author liuyonghui
 */
@Service
public class ProjTaskServiceImpl implements IProjTaskService
{
    @Autowired
    private ProjTaskMapper taskMapper;

    /**
     * 查询任务详情
     */
    @Override
    public ProjTask selectTaskById(Long id)
    {
        return taskMapper.selectTaskById(id);
    }

    /**
     * 查询任务列表
     */
    @Override
    public List<ProjTask> selectTaskList(ProjTask task)
    {
        return taskMapper.selectTaskList(task);
    }

    /**
     * 新增任务（单条）
     */
    @Override
    public int insertTask(ProjTask task)
    {
        // 默认状态
        if (task.getStatus() == null || task.getStatus().isEmpty())
        {
            task.setStatus("待开始");
        }
        return taskMapper.insertTask(task);
    }

    /**
     * 批量新增任务（按 userIds 多选执行人拆分）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertTaskBatch(ProjTask task)
    {
        List<Long> userIds = task.getUserIds();
        if (userIds == null || userIds.isEmpty())
        {
            // 退回单条创建
            return insertTask(task);
        }
        int count = 0;
        for (Long uid : userIds)
        {
            task.setUserId(uid);
            // 默认状态
            if (task.getStatus() == null || task.getStatus().isEmpty())
            {
                task.setStatus("待开始");
            }
            taskMapper.insertTask(task);
            count++;
        }
        return count;
    }

    /**
     * 修改任务
     */
    @Override
    public int updateTask(ProjTask task)
    {
        return taskMapper.updateTask(task);
    }

    /**
     * 删除任务
     */
    @Override
    public int deleteTaskById(Long id)
    {
        return taskMapper.deleteTaskById(id);
    }

    /**
     * 批量删除任务
     */
    @Override
    public int deleteTaskByIds(Long[] ids)
    {
        return taskMapper.deleteTaskByIds(ids);
    }
}
