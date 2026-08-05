package com.xakcch.project.service;

import java.util.List;
import java.util.Map;
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

    /**
     * 办结项目（状态改为已办结，不可逆）
     *
     * @param id 项目ID
     * @return 结果
     */
    public int completeProject(Long id);

    /**
     * 变更项目状态（校验流转规则）
     *
     * @param id 项目ID
     * @param targetStatus 目标状态
     * @return 结果
     */
    public int changeProjectStatus(Long id, String targetStatus);

    /**
     * 导入项目数据（Excel文件解析后批量插入）
     *
     * @param projectList 项目列表
     * @param isUpdateSupport 是否更新已存在数据
     * @param operName 操作人
     * @return 结果消息
     */
    public String importProject(List<ProjProject> projectList, Boolean isUpdateSupport, String operName);

    /**
     * 批量新增项目（区域粘贴）
     *
     * @param projectList 项目列表
     * @param operName 操作人
     * @return 成功数量
     */
    public int batchInsertProject(List<ProjProject> projectList, String operName);

    /**
     * 统计各状态下的项目数量
     *
     * @return [{ status: "ongoing", cnt: 15 }, ...]
     */
    public List<Map<String, Object>> getStatusCounts();

    /**
     * 查询某字段的去重值列表（高级筛选下拉选项用）
     *
     * @param field 字段标识：clientUnit / engineeringProject
     * @return 去重字符串列表
     */
    public List<String> getDistinctValues(String field);
}
