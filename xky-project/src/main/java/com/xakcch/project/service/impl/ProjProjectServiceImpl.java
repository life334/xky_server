package com.xakcch.project.service.impl;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.xakcch.common.exception.ServiceException;
import com.xakcch.common.utils.SecurityUtils;
import com.xakcch.project.domain.ProjCategory;
import com.xakcch.project.domain.ProjProject;
import com.xakcch.project.mapper.ProjCategoryMapper;
import com.xakcch.project.mapper.ProjLeaderMapper;
import com.xakcch.project.mapper.ProjProjectMapper;
import com.xakcch.project.service.IProjProjectService;
import com.xakcch.system.mapper.SysUserMapper;
import com.xakcch.common.core.domain.entity.SysUser;

/**
 * 项目主表 服务层实现
 *
 * @author liuyonghui
 */
@Service
public class ProjProjectServiceImpl implements IProjProjectService
{
    /** 状态流转规则：key=当前状态, value=允许流转到的目标状态列表 */
    private static final Map<String, List<String>> STATUS_FLOW = new HashMap<>();
    static
    {
        STATUS_FLOW.put("待开始", Arrays.asList("进行中"));
        STATUS_FLOW.put("进行中", Arrays.asList("已暂停", "已完成", "已取消"));
        STATUS_FLOW.put("已暂停", Arrays.asList("进行中"));
        STATUS_FLOW.put("已完成", Arrays.asList("已办结"));
        STATUS_FLOW.put("已办结", Arrays.asList());
        STATUS_FLOW.put("已取消", Arrays.asList());
    }

    @Autowired
    private ProjProjectMapper projectMapper;

    @Autowired
    private ProjLeaderMapper leaderMapper;

    @Autowired
    private ProjCategoryMapper categoryMapper;

    @Autowired
    private SysUserMapper userMapper;

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

    /**
     * 办结项目（状态改为已办结，不可逆）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int completeProject(Long id)
    {
        ProjProject project = projectMapper.selectProjectById(id);
        if (project == null)
        {
            throw new ServiceException("项目不存在");
        }
        if ("已办结".equals(project.getStatus()))
        {
            throw new ServiceException("该项目已办结，不能重复操作");
        }
        return projectMapper.completeProject(id);
    }

    /**
     * 变更项目状态（校验流转规则）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int changeProjectStatus(Long id, String targetStatus)
    {
        ProjProject project = projectMapper.selectProjectById(id);
        if (project == null)
        {
            throw new ServiceException("项目不存在");
        }
        String currentStatus = project.getStatus();
        if (currentStatus == null || currentStatus.isEmpty())
        {
            throw new ServiceException("项目当前状态异常");
        }
        if (currentStatus.equals(targetStatus))
        {
            throw new ServiceException("项目已处于该状态");
        }
        List<String> allowed = STATUS_FLOW.get(currentStatus);
        if (allowed == null || !allowed.contains(targetStatus))
        {
            throw new ServiceException("状态不允许从[" + currentStatus + "]变更为[" + targetStatus + "]");
        }
        return projectMapper.updateProjectStatus(id, targetStatus);
    }

    /**
     * 导入项目数据（Excel文件解析后批量插入）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String importProject(List<ProjProject> projectList, Boolean isUpdateSupport, String operName)
    {
        if (projectList == null || projectList.isEmpty())
        {
            throw new ServiceException("导入数据不能为空");
        }
        int successNum = 0;
        int failureNum = 0;
        StringBuilder successMsg = new StringBuilder();
        StringBuilder failureMsg = new StringBuilder();
        for (ProjProject project : projectList)
        {
            try
            {
                // 校验工程编号
                if (project.getProjectCode() == null || project.getProjectCode().isEmpty())
                {
                    failureNum++;
                    failureMsg.append("<br/>工程编号为空，跳过");
                    continue;
                }
                // 检查重复
                ProjProject existing = projectMapper.checkProjectCodeUnique(project);
                if (existing != null)
                {
                    if (isUpdateSupport)
                    {
                        project.setId(existing.getId());
                        project.setUpdateBy(operName);
                        resolveCategoryAndLeaders(project);
                        updateProject(project);
                        successNum++;
                        successMsg.append("<br/>更新成功：" + project.getProjectCode());
                    }
                    else
                    {
                        failureNum++;
                        failureMsg.append("<br/>工程编号已存在：" + project.getProjectCode());
                    }
                    continue;
                }
                // 新增
                project.setStatus("进行中");
                project.setCreateBy(operName);
                resolveCategoryAndLeaders(project);
                insertProject(project);
                successNum++;
                successMsg.append("<br/>新增成功：" + project.getProjectCode());
            }
            catch (Exception e)
            {
                failureNum++;
                failureMsg.append("<br/>" + project.getProjectCode() + " 导入失败：" + e.getMessage());
            }
        }
        if (failureNum > 0)
        {
            failureMsg.insert(0, "导入失败！共 " + failureNum + " 条数据格式不正确");
            throw new ServiceException(failureMsg.toString());
        }
        successMsg.insert(0, "导入成功！共 " + successNum + " 条");
        return successMsg.toString();
    }

    /**
     * 批量新增项目（区域粘贴）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchInsertProject(List<ProjProject> projectList, String operName)
    {
        if (projectList == null || projectList.isEmpty())
        {
            throw new ServiceException("批量数据不能为空");
        }
        int count = 0;
        for (ProjProject project : projectList)
        {
            if (project.getProjectCode() == null || project.getProjectCode().isEmpty())
            {
                continue;
            }
            // 跳过已存在的工程编号
            ProjProject existing = projectMapper.checkProjectCodeUnique(project);
            if (existing != null)
            {
                continue;
            }
            project.setStatus("进行中");
            project.setCreateBy(operName);
            resolveCategoryAndLeaders(project);
            projectMapper.insertProject(project);
            // 插入负责人关联
            Long[] leaderIds = project.getLeaderIds();
            if (leaderIds != null && leaderIds.length > 0)
            {
                leaderMapper.insertProjectLeaders(project.getId(), leaderIds, operName);
            }
            count++;
        }
        return count;
    }

    /**
     * 解析类别名称→ID、负责人姓名→用户ID
     */
    private void resolveCategoryAndLeaders(ProjProject project)
    {
        // 类别名称 → ID
        if (project.getCategoryName() != null && !project.getCategoryName().isEmpty())
        {
            ProjCategory category = categoryMapper.selectCategoryByName(project.getCategoryName());
            if (category != null)
            {
                project.setProjectCategoryId(category.getId());
            }
        }
        // 负责人姓名 → 用户ID（支持逗号分隔多人）
        if (project.getLeaderNames() != null && !project.getLeaderNames().isEmpty())
        {
            String[] names = project.getLeaderNames().split("[,，]");
            java.util.List<Long> userIds = new java.util.ArrayList<>();
            for (String name : names)
            {
                String trimmed = name.trim();
                if (trimmed.isEmpty())
                {
                    continue;
                }
                SysUser user = userMapper.selectUserByUserName(trimmed);
                if (user != null)
                {
                    userIds.add(user.getUserId());
                }
            }
            if (!userIds.isEmpty())
            {
                project.setLeaderIds(userIds.toArray(new Long[0]));
            }
        }
    }
}
