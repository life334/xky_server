package com.xakcch.project.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.xakcch.common.core.domain.entity.SysUser;

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
     * 根据项目ID数组批量删除负责人关联（逻辑删除，删除项目时级联清理）
     *
     * @param projectIds 项目ID数组
     * @return 结果
     */
    public int deleteLeadersByProjectIds(@Param("projectIds") Long[] projectIds);

    /**
     * 根据项目ID查询负责人用户ID列表
     *
     * @param projectId 项目ID
     * @return 用户ID数组
     */
    public Long[] selectLeaderIdsByProjectId(Long projectId);

    /**
     * 按昵称精确查询用户（不限状态，含停用的影子/离职用户；在职优先）
     *
     * @param nickName 用户昵称（负责人姓名）
     * @return 匹配到的用户，无匹配返回 null
     */
    public SysUser selectUserByNickName(@Param("nickName") String nickName);

    /**
     * 查询项目表已出现过的全部负责人（去重，join sys_user，含停用用户）
     * 用于负责人下拉"已有记录"数据源
     *
     * @return 负责人用户列表（userId/nickName/userName/status）
     */
    public List<SysUser> selectDistinctLeaders();
}
