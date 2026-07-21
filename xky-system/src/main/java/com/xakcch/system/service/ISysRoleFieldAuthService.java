package com.xakcch.system.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import com.xakcch.system.domain.SysRoleFieldAuth;

/**
 * 角色字段权限 业务层接口
 * 
 * @author liuyonghui
 */
public interface ISysRoleFieldAuthService
{
    /**
     * 根据角色ID查询字段权限列表
     */
    List<SysRoleFieldAuth> selectFieldAuthByRoleId(Long roleId);

    /**
     * 批量保存角色的字段权限（先删后插）
     */
    int saveRoleFieldAuth(Long roleId, List<SysRoleFieldAuth> list);

    /**
     * 根据角色ID删除所有字段权限
     */
    int deleteByRoleId(Long roleId);

    /**
     * 根据角色ID数组批量删除
     */
    int deleteByRoleIds(Long[] roleIds);

    /**
     * 获取当前用户对指定表的所有字段权限 Map（fieldKey → authType）
     * 如果用户有多个角色，取最宽松的权限（可见可编辑 > 可见不可编辑 > 隐藏）
     */
    Map<String, String> getCurrentUserFieldAuthMap(String tableName);

    /**
     * 获取当前用户对指定表的可见字段集合
     */
    Set<String> getCurrentUserVisibleFields(String tableName);

    /**
     * 获取当前用户对指定表的可编辑字段集合
     */
    Set<String> getCurrentUserEditableFields(String tableName);

    /**
     * 校验编辑操作：如果 updateData 中包含只读或隐藏字段的修改，则抛出异常
     */
    void validateEditableFields(Map<String, Object> updateData, String tableName);
}
