package com.xakcch.project.service;

import java.util.List;
import com.xakcch.project.domain.ProjProject;

/**
 * 项目主表 服务层
 *
 * @author liuyonghui
 */
public interface IProjProjectService
{
    /**
     * 查询项目详情
     *
     * @param id 项目ID
     * @return 项目
     */
    public ProjProject selectProjectById(Long id);

    /**
     * 查询项目列表
     *
     * @param project 查询条件
     * @return 项目集合
     */
    public List<ProjProject> selectProjectList(ProjProject project);

    /**
     * 校验工程编号是否唯一
     *
     * @param project 项目信息
     * @return 结果 true=唯一 false=不唯一
     */
    public boolean checkProjectCodeUnique(ProjProject project);

    /**
     * 新增项目（含负责人关联）
     *
     * @param project 项目
     * @return 结果
     */
    public int insertProject(ProjProject project);

    /**
     * 修改项目（含负责人关联）
     *
     * @param project 项目
     * @return 结果
     */
    public int updateProject(ProjProject project);

    /**
     * 删除项目（含负责人关联清理）
     *
     * @param id 项目ID
     * @return 结果
     */
    public int deleteProjectById(Long id);

    /**
     * 批量删除项目（含负责人关联清理）
     *
     * @param ids 需要删除的项目ID
     * @return 结果
     */
    public int deleteProjectByIds(Long[] ids);
}
