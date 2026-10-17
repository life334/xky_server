package com.xakcch.project.domain.vo;

import java.io.Serializable;
import java.util.Date;

/**
 * 数据库备份文件（列表展示用）
 *
 * @author liuyonghui
 */
public class DbBackupFile implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 文件名（不含目录） */
    private String name;

    /** 文件大小（字节） */
    private long size;

    /** 文件大小（易读文本，如 12.4 MB） */
    private String sizeText;

    /** 文件最后修改时间 */
    private Date lastModified;

    public DbBackupFile()
    {
    }

    public DbBackupFile(String name, long size, String sizeText, Date lastModified)
    {
        this.name = name;
        this.size = size;
        this.sizeText = sizeText;
        this.lastModified = lastModified;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public long getSize()
    {
        return size;
    }

    public void setSize(long size)
    {
        this.size = size;
    }

    public String getSizeText()
    {
        return sizeText;
    }

    public void setSizeText(String sizeText)
    {
        this.sizeText = sizeText;
    }

    public Date getLastModified()
    {
        return lastModified;
    }

    public void setLastModified(Date lastModified)
    {
        this.lastModified = lastModified;
    }
}
