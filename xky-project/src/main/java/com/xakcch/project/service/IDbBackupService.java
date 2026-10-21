package com.xakcch.project.service;

import java.io.File;
import java.util.List;
import java.util.Map;

import com.xakcch.project.domain.vo.DbBackupFile;

/**
 * 数据库备份服务
 *
 * 说明：调用 PostgreSQL 自带的 pg_dump 做逻辑备份（自定义格式 -Fc，可用 pg_restore 恢复）。
 * 连接信息从主数据源（Druid）解析，不在配置文件里重复维护库地址/账号。
 *
 * @author liuyonghui
 */
public interface IDbBackupService
{
    /**
     * 执行一次数据库备份
     *
     * @return { file, filePath, size, sizeText, costMs, host, port, dbName }
     */
    Map<String, Object> runBackup();

    /**
     * 备份文件列表（按时间倒序）
     */
    List<DbBackupFile> listBackups();

    /**
     * 校验并解析备份文件（防路径穿越；文件名非法或文件不存在时返回 null）
     */
    File resolveBackupFile(String name);

    /**
     * 删除指定备份文件
     */
    boolean deleteBackup(String name);

    /**
     * 备份文件存放目录
     */
    String getBackupDir();

    /**
     * 备份开关（配置 xky-project.backup.enabled）
     */
    boolean isEnabled();

    /**
     * pg_dump 可执行文件路径；不可用时返回 null
     * 探测顺序：配置 pgDumpPath → 应用目录下 pgtools/ → PATH → 常见安装目录
     */
    String resolvePgDump();

    /**
     * pg_dump 版本描述（如 "pg_dump (PostgreSQL) 16.2"）；不可用时返回空串
     */
    String pgDumpVersion();

    /**
     * 数据库服务端版本（如 "14.19"）；获取失败返回空串。
     * 用于核对 pg_dump 主版本是否 >= 服务端主版本（旧版 pg_dump 无法备份新版服务端）
     */
    String serverVersion();

    /**
     * pg_restore 可执行文件路径（恢复用，与 pg_dump 同目录）；不可用时返回 null。
     * 仅用于在页面上展示"恢复工具是否就绪"并生成恢复命令，系统本身不执行恢复。
     */
    String resolvePgRestore();

    /**
     * pg_restore 版本描述（如 "pg_restore (PostgreSQL) 16.2"）；不可用时返回空串
     */
    String pgRestoreVersion();

    /**
     * 当前主数据源的连接信息 { host, port, dbName, user }；解析失败返回空 Map。
     * 供页面生成恢复命令时填入真实地址，避免把库地址硬编码在提示里。
     * ⚠️ 不含密码：密码永远不返回前端。
     */
    Map<String, String> connectionInfo();
}
