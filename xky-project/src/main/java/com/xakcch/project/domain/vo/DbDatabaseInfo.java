package com.xakcch.project.domain.vo;

import java.io.Serializable;

/**
 * 数据库服务器上的一个库（供「一键恢复」选择目标库 / 管理历史恢复库）
 *
 * @author liuyonghui
 */
public class DbDatabaseInfo implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 库名 */
    private String name;

    /** 库大小（易读文本，由 pg_size_pretty 给出） */
    private String sizeText;

    /** 是否为应用当前连接的库 */
    private boolean current;

    /** 是否允许删除（仅 xky_project_r_ 前缀的历史恢复库；现库永远不允许） */
    private boolean droppable;

    public DbDatabaseInfo()
    {
    }

    public DbDatabaseInfo(String name, String sizeText)
    {
        this.name = name;
        this.sizeText = sizeText;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getSizeText()
    {
        return sizeText;
    }

    public void setSizeText(String sizeText)
    {
        this.sizeText = sizeText;
    }

    public boolean isCurrent()
    {
        return current;
    }

    public void setCurrent(boolean current)
    {
        this.current = current;
    }

    public boolean isDroppable()
    {
        return droppable;
    }

    public void setDroppable(boolean droppable)
    {
        this.droppable = droppable;
    }
}
