package com.xakcch.system.mapper;

import java.util.List;
import com.xakcch.system.domain.SysRoleFieldAuth;

/**
 * 角色字段权限 数据层
 * 
 * @author liuyonghui
 */
public interface SysRoleFieldAuthMapper
{
    /**
     * 根据角色ID查询字段权限列表
     */
    List<SysRoleFieldAuth> selectFieldAuthByRoleId(Long roleId);

    /**
     * 根据角色ID + 表名 查询字段权限列表
     */
    List<SysRoleFieldAuth> selectFieldAuthByRoleAndTable(Long roleId, String tableName);

    /**
     * 批量新增字段权限
     */
    int batchInsert(List<SysRoleFieldAuth> list);

    /**
     * 根据角色ID删除所有字段权限
     */
    int deleteByRoleId(Long roleId);

    /**
     * 根据角色ID数组批量删除
     */
    int deleteByRoleIds(Long[] roleIds);
}
