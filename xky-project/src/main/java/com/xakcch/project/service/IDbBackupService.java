package com.xakcch.project.service;

import java.io.File;
import java.util.List;
import java.util.Map;

import com.xakcch.project.domain.vo.DbBackupFile;
import com.xakcch.project.domain.vo.DbDatabaseInfo;
import com.xakcch.project.domain.vo.DbRestoreStatus;

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
     * 备份文件列表（按时间倒序）。
     *
     * ⚠️ 同时扫描<b>本机备份目录与异地（灾备）目录</b>并按文件名合并：整机挂掉换新机后
     * 本机目录是空的，备份文件只留在灾备共享目录里，只扫本机的话一键恢复将无文件可选。
     * 每条记录用 location 标出文件实际所在位置（local / secondary / both），异地独有的文件同样可恢复。
     */
    List<DbBackupFile> listBackups();

    /**
     * 校验并解析备份文件（防路径穿越；文件名非法或文件不存在时返回 null）。
     * 本机备份目录优先；本机没有时到异地（灾备）目录找。
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
     * 异地（灾备）目录；未启用时返回空串
     */
    String getSecondaryDir();

    /**
     * 异地（灾备）目录当前是否可达（由最近一次 {@link #listBackups()} 刷新）。
     * 不可达 = 灾备机宕机 / 网络中断 / 共享权限不足，页面据此告警。
     */
    boolean isSecondaryReachable();

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
     * 「一键恢复」与页面生成恢复命令都依赖它。
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

    /* ------------------------------------------------------------------ */
    /*                        一键恢复（pg_restore）                        */
    /* ------------------------------------------------------------------ */

    /**
     * 数据库服务器上的库列表（排除模板库），供选择恢复目标与管理历史恢复库
     */
    List<DbDatabaseInfo> listDatabases();

    /**
     * 检查目标库是否可恢复
     *
     * @return { exists, tableCount, sizeText, restorable, reason }
     *         restorable=false 时 reason 说明原因（如"库里已有 N 张表，拒绝覆盖"）
     */
    Map<String, Object> inspectDatabase(String dbName);

    /**
     * 启动一次恢复（异步执行，立即返回）。
     *
     * ⚠️ 安全闸门（缺一不可）：
     * 1. 目标库不存在 → 自动创建（CREATE DATABASE ... TEMPLATE template0）后恢复；
     * 2. 目标库存在但 public 下已有业务表 → 拒绝，绝不做覆盖式恢复
     *    （覆盖现库必须走命令行人工执行，避免应用在线时被清空）；
     * 3. 同一时刻只允许一个恢复任务。
     *
     * @param backupName 备份文件名（必须在备份目录内）
     * @param targetDb   目标库名（小写字母/数字/下划线）
     * @return { started, file, targetDb, createdDb }
     */
    Map<String, Object> startRestore(String backupName, String targetDb);

    /**
     * 当前（或最近一次）恢复任务的状态；页面据此轮询进度
     */
    DbRestoreStatus restoreStatus();

    /**
     * 删除历史恢复库。
     *
     * ⚠️ 只允许删除 xky_project_r_ 前缀的库（即本功能恢复出来的库），
     * 且不允许删除应用当前连接的库 —— 防止一键清掉生产库。
     */
    boolean dropDatabase(String dbName);
}
