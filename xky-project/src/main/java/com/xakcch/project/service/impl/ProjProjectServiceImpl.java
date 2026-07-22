package com.xakcch.project.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.xakcch.common.exception.ServiceException;
import com.xakcch.common.utils.SecurityUtils;
import com.xakcch.project.domain.ProjProject;
import com.xakcch.project.mapper.ProjLeaderMapper;
import com.xakcch.project.mapper.ProjProjectMapper;
import com.xakcch.project.service.IProjProjectService;

/**
 * 项目主表 服务层实现
 *
 * @author liuyonghui
 */
@Service
public class ProjProjectServiceImpl implements IProjProjectService
{
    @Autowired
    private ProjProjectMapper projectMapper;

    @Autowired
    private ProjLeaderMapper leaderMapper;

    /**
     * 查询项目详情
     */
    @Override
    public ProjProject selectProjectById(Long id)
    {
        ProjProject project = projectMapper.selectProjectById(id);
        if (project != null)
        {
            // 回填负责人ID列表，供前端表单回显
            project.setLeaderIds(leaderMapper.selectLeaderIdsByProjectId(id));
        }
        return project;
    }

    /**
     * 查询项目列表
     */
    @Override
    public List<ProjProject> selectProjectList(ProjProject project)
    {
        return projectMapper.selectProjectList(project);
    }

    /**
     * 校验工程编号是否唯一
     */
    @Override
    public boolean checkProjectCodeUnique(ProjProject project)
    {
        Long id = project.getId() == null ? -1L : project.getId();
        ProjProject info = projectMapper.checkProjectCodeUnique(project);
        if (info != null && info.getId().longValue() != id.longValue())
        {
            return false;
        }
        return true;
    }

    /**
     * 新增项目（含负责人关联）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertProject(ProjProject project)
    {
        // 默认状态
        if (project.getStatus() == null || project.getStatus().isEmpty())
        {
            project.setStatus("进行中");
        }
        int rows = projectMapper.insertProject(project);

        // 插入负责人关联
        Long[] leaderIds = project.getLeaderIds();
        if (leaderIds != null && leaderIds.length > 0)
        {
            leaderMapper.insertProjectLeaders(project.getId(), leaderIds, project.getCreateBy());
        }
        return rows;
    }

    /**
     * 修改项目（含负责人关联）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateProject(ProjProject project)
    {
        int rows = projectMapper.updateProject(project);

        // 先删后插，更新负责人关联
        leaderMapper.deleteLeadersByProjectId(project.getId());
        Long[] leaderIds = project.getLeaderIds();
        if (leaderIds != null && leaderIds.length > 0)
        {
            leaderMapper.insertProjectLeaders(project.getId(), leaderIds, project.getUpdateBy());
        }
        return rows;
    }

    /**
     * 删除项目（含负责人关联清理）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteProjectById(Long id)
    {
        leaderMapper.deleteLeadersByProjectId(id);
        return projectMapper.deleteProjectById(id);
    }

    /**
     * 批量删除项目（含负责人关联清理）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteProjectByIds(Long[] ids)
    {
        for (Long id : ids)
        {
            leaderMapper.deleteLeadersByProjectId(id);
        }
        return projectMapper.deleteProjectByIds(ids);
    }
}
