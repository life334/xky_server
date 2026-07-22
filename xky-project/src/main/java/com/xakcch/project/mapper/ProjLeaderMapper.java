package com.xakcch.project.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * 项目负责人关联表 数据层（仅用于项目 Service 内部调用）
 *
 * @author liuyonghui
 */
public interface ProjLeaderMapper
{
    /**
     * 批量插入项目负责人
     *
     * @param projectId 项目ID
     * @param userIds 用户ID数组
     * @param createBy 创建者
     * @return 结果
     */
    public int insertProjectLeaders(@Param("projectId") Long projectId, @Param("userIds") Long[] userIds, @Param("createBy") String createBy);

    /**
     * 根据项目ID删除所有负责人关联（逻辑删除）
     *
     * @param projectId 项目ID
     * @return 结果
     */
    public int deleteLeadersByProjectId(Long projectId);

    /**
     * 根据项目ID查询负责人用户ID列表
     *
     * @param projectId 项目ID
     * @return 用户ID数组
     */
    public Long[] selectLeaderIdsByProjectId(Long projectId);
}
