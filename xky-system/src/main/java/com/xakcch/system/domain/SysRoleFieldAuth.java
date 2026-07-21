package com.xakcch.system.domain;

/**
 * 角色字段权限配置 pojo
 * 
 * @author liuyonghui
 */
public class SysRoleFieldAuth
{
    private Long id;
    private Long roleId;
    private String tableName;
    private String fieldKey;
    private String authType;

    // ======== getter / setter ========

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

    public String getFieldKey() { return fieldKey; }
    public void setFieldKey(String fieldKey) { this.fieldKey = fieldKey; }

    public String getAuthType() { return authType; }
    public void setAuthType(String authType) { this.authType = authType; }
}
