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

    /**
     * 异地（灾备）副本状态：
     * disabled=未配置异地目录；synced=异地已有同名文件且大小一致；missing=异地缺失
     */
    private String secondary;

    /**
     * 文件当前实际所在位置（决定"能不能拿它来恢复"）：
     * local=只在本机备份目录；secondary=只在异地目录；both=两处都有
     *
     * <p>为什么需要这个字段：整机挂掉换新机后，本机备份目录是空的，备份文件只在灾备机的共享目录里。
     * 列表必须把这类文件也列出来，否则一键恢复没有文件可选。</p>
     */
    private String location;

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

    public String getSecondary()
    {
        return secondary;
    }

    public void setSecondary(String secondary)
    {
        this.secondary = secondary;
    }

    public String getLocation()
    {
        return location;
    }

    public void setLocation(String location)
    {
        this.location = location;
    }
}
