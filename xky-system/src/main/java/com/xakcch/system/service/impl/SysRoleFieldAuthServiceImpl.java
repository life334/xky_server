package com.xakcch.system.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xakcch.common.core.domain.entity.SysRole;
import com.xakcch.common.core.domain.model.LoginUser;
import com.xakcch.common.exception.ServiceException;
import com.xakcch.common.utils.SecurityUtils;
import com.xakcch.common.utils.StringUtils;
import com.xakcch.system.domain.SysRoleFieldAuth;
import com.xakcch.system.mapper.SysRoleFieldAuthMapper;
import com.xakcch.system.service.ISysRoleFieldAuthService;

/**
 * 角色字段权限 业务层实现
 * 
 * @author liuyonghui
 */
@Service
public class SysRoleFieldAuthServiceImpl implements ISysRoleFieldAuthService
{
    /** 权限类型常量 */
    public static final String AUTH_EDITABLE = "1";  // 可见可编辑
    public static final String AUTH_READONLY = "2";  // 可见不可编辑
    public static final String AUTH_HIDDEN   = "3";  // 隐藏

    @Autowired
    private SysRoleFieldAuthMapper fieldAuthMapper;

    @Override
    public List<SysRoleFieldAuth> selectFieldAuthByRoleId(Long roleId)
    {
        return fieldAuthMapper.selectFieldAuthByRoleId(roleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int saveRoleFieldAuth(Long roleId, List<SysRoleFieldAuth> list)
    {
        // 先删后插
        fieldAuthMapper.deleteByRoleId(roleId);
        if (list == null || list.isEmpty())
        {
            return 0;
        }
        // 确保 roleId 统一
        for (SysRoleFieldAuth item : list)
        {
            item.setRoleId(roleId);
        }
        return fieldAuthMapper.batchInsert(list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByRoleId(Long roleId)
    {
        return fieldAuthMapper.deleteByRoleId(roleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByRoleIds(Long[] roleIds)
    {
        return fieldAuthMapper.deleteByRoleIds(roleIds);
    }

    /**
     * 获取当前用户对指定表的所有字段权限 Map
     * 多角色时取最宽松的权限（可见可编辑 > 可见不可编辑 > 隐藏）
     */
    @Override
    public Map<String, String> getCurrentUserFieldAuthMap(String tableName)
    {
        Map<String, String> result = new HashMap<>();
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (loginUser == null || loginUser.getUser().getRoles() == null)
        {
            return result;
        }
        // 超级管理员：所有字段可见可编辑
        if (loginUser.getUser().isAdmin())
        {
            return result; // 返回空 Map，调用方自行判断：空=无限制
        }

        for (SysRole role : loginUser.getUser().getRoles())
        {
            List<SysRoleFieldAuth> authList = fieldAuthMapper.selectFieldAuthByRoleAndTable(role.getRoleId(), tableName);
            for (SysRoleFieldAuth auth : authList)
            {
                String existing = result.get(auth.getFieldKey());
                if (existing == null || isMorePermissive(auth.getAuthType(), existing))
                {
                    result.put(auth.getFieldKey(), auth.getAuthType());
                }
            }
        }
        return result;
    }

    /**
     * 获取当前用户对指定表的可见字段集合
     * 返回 null 表示无限制（全部可见）
     */
    @Override
    public Set<String> getCurrentUserVisibleFields(String tableName)
    {
        Map<String, String> authMap = getCurrentUserFieldAuthMap(tableName);
        if (authMap.isEmpty())
        {
            return null; // null = 全部可见（admin or 无配置）
        }
        Set<String> visibleFields = new HashSet<>();
        for (Map.Entry<String, String> entry : authMap.entrySet())
        {
            if (!AUTH_HIDDEN.equals(entry.getValue()))
            {
                visibleFields.add(entry.getKey());
            }
        }
        return visibleFields;
    }

    /**
     * 获取当前用户对指定表的可编辑字段集合
     * 返回 null 表示无限制（全部可编辑）
     */
    @Override
    public Set<String> getCurrentUserEditableFields(String tableName)
    {
        Map<String, String> authMap = getCurrentUserFieldAuthMap(tableName);
        if (authMap.isEmpty())
        {
            return null; // null = 全部可编辑（admin or 无配置）
        }
        Set<String> editableFields = new HashSet<>();
        for (Map.Entry<String, String> entry : authMap.entrySet())
        {
            if (AUTH_EDITABLE.equals(entry.getValue()))
            {
                editableFields.add(entry.getKey());
            }
        }
        return editableFields;
    }

    /**
     * 校验编辑操作：如果 updateData 中包含只读或隐藏字段的修改，则抛出异常
     */
    @Override
    public void validateEditableFields(Map<String, Object> updateData, String tableName)
    {
        if (updateData == null || updateData.isEmpty())
        {
            return;
        }
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (loginUser == null || loginUser.getUser().isAdmin())
        {
            return;
        }

        Map<String, String> authMap = getCurrentUserFieldAuthMap(tableName);
        for (Map.Entry<String, Object> entry : updateData.entrySet())
        {
            String fieldKey = entry.getKey();
            String authType = authMap.getOrDefault(fieldKey, AUTH_EDITABLE);
            if (AUTH_HIDDEN.equals(authType))
            {
                throw new ServiceException("您没有权限修改字段: " + fieldKey);
            }
            if (AUTH_READONLY.equals(authType))
            {
                throw new ServiceException("字段 " + fieldKey + " 仅可查看，不可编辑");
            }
        }
    }

    // ======== 内部方法 ========

    /**
     * 判断权限 a 是否比 b 更宽松
     * 可见可编辑(1) > 可见不可编辑(2) > 隐藏(3)
     */
    private boolean isMorePermissive(String a, String b)
    {
        return Integer.parseInt(a) < Integer.parseInt(b);
    }
}
