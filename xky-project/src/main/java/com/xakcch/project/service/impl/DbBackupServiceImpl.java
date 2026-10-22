package com.xakcch.project.service.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.DriverManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.stereotype.Service;

import com.alibaba.druid.pool.DruidDataSource;
import com.xakcch.common.exception.ServiceException;
import com.xakcch.common.utils.StringUtils;
import com.xakcch.project.domain.vo.DbBackupFile;
import com.xakcch.project.domain.vo.DbDatabaseInfo;
import com.xakcch.project.domain.vo.DbRestoreStatus;
import com.xakcch.project.service.IDbBackupService;

/**
 * 数据库备份服务实现
 *
 * 设计要点：
 * 1. 备份依赖 PostgreSQL 客户端的 pg_dump（逻辑备份 -Fc 自定义格式，可用 pg_restore 恢复）；
 *    路径可通过 xky-project.backup.pgDumpPath 配置，留空时自动从 PATH 与常见安装目录探测；
 * 2. 连接信息（host/port/db/user/password）从主数据源 Druid 解析，避免配置里重复维护；
 * 3. 密码通过子进程环境变量 PGPASSWORD 传入，且不出现在命令行参数中（避免 ps 泄露）；
 * 4. 子进程有超时保护，超时/失败会删除半成品文件；成功后按保留天数轮转旧文件；
 * 5. 任何异常都只影响备份本身，不向调用方抛出运行时错误以外的副作用（定时任务已 try-catch）。
 *
 * @author liuyonghui
 */
@Service
public class DbBackupServiceImpl implements IDbBackupService
{
    private static final Logger log = LoggerFactory.getLogger(DbBackupServiceImpl.class);

    /** 备份文件名前缀 */
    private static final String FILE_PREFIX = "xky_project_";
    /** 备份文件扩展名 */
    private static final String FILE_EXT = ".dump";
    /** 文件名时间格式 */
    private static final DateTimeFormatter NAME_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    /** 写入异地目录的最长等待时间（秒） */
    private static final int SECONDARY_COPY_TIMEOUT_SEC = 120;
    /** 列目录 / 探测网络（UNC）文件的最长等待时间（秒）：共享不可达时不能把请求拖死 */
    private static final int NET_IO_TIMEOUT_SEC = 15;
    /** jdbc:postgresql://host:port/db?xxx */
    private static final Pattern PG_URL = Pattern.compile("^jdbc:postgresql://([^:/]+)(?::(\\d+))?/([^?]+)");
    /** pg_dump 探测结果缓存（避免每次遍历磁盘） */
    private volatile String pgDumpCache;
    /** 已探测过标记（区分「未探测」与「探测结果为无」） */
    private volatile boolean pgDumpResolved;
    /** pg_dump 版本缓存 */
    private volatile String pgDumpVersionCache;
    /** pg_restore 探测结果缓存（恢复用；系统本身不执行恢复，仅页面展示与生成命令） */
    private volatile String pgRestoreCache;
    /** pg_restore 已探测标记 */
    private volatile boolean pgRestoreResolved;
    /** pg_restore 版本缓存 */
    private volatile String pgRestoreVersionCache;

    /** 一键恢复出来的库统一用这个前缀，也只有这个前缀的库允许在页面上删除 */
    private static final String RESTORE_DB_PREFIX = "xky_project_r_";
    /** 恢复日志最多保留的行数（pg_restore -v 对每个对象都有一行，大库会有几千行） */
    private static final int RESTORE_LOG_MAX_LINES = 300;
    /** 禁止作为恢复目标的系统库 */
    private static final String[] RESERVED_DBS = new String[] { "postgres", "template0", "template1", "template" };
    /** 恢复任务状态（页面轮询） */
    private volatile DbRestoreStatus restoreStatus;
    /** 恢复任务是否正在执行（同一时刻只允许一个） */
    private volatile boolean restoreRunning;
    /** 恢复任务互斥锁 */
    private final Object restoreLock = new Object();
    /** 最近一次列目录时异地目录是否可达（供页面提示"灾备机连不上"；由 listBackups 刷新） */
    private volatile boolean secondaryReachable;

    @Value("${xky-project.backup.enabled:true}")
    private boolean enabled;

    @Value("${xky-project.backup.dir:D:/xky-db-backup}")
    private String dir;

    @Value("${xky-project.backup.keepDays:7}")
    private int keepDays;

    /** 异地（灾备）目录，留空表示不启用 */
    @Value("${xky-project.backup.secondaryDir:}")
    private String secondaryDir;

    @Value("${xky-project.backup.secondaryKeepDays:30}")
    private int secondaryKeepDays;

    @Value("${xky-project.backup.pgDumpPath:}")
    private String pgDumpPath;

    @Value("${xky-project.backup.timeoutMinutes:30}")
    private int timeoutMinutes;

    @Autowired
    @Qualifier("masterDataSource")
    private DataSource masterDataSource;

    @Override
    public Map<String, Object> runBackup()
    {
        String pgDump = resolvePgDump();
        if (StringUtils.isEmpty(pgDump))
        {
            throw new ServiceException("未找到 pg_dump，无法备份。三种解决方式任选其一：" +
                    "① 把免安装版 pg_dump（pg_dump.exe + libpq.dll + zlib.dll + libssl-3-x64.dll + libcrypto-3-x64.dll，约 7MB）放到应用目录下的 pgtools/ ；" +
                    "② 在 application.yml 配置 xky-project.backup.pgDumpPath 指向 pg_dump.exe 完整路径；" +
                    "③ 安装 PostgreSQL 客户端（或把其 bin 目录加入 PATH）。注意 pg_dump 主版本号需 >= 服务端主版本号。");
        }
        checkVersionCompatibility();
        String[] conn = resolveConnection();
        String host = conn[0];
        String port = conn[1];
        String dbName = conn[2];
        String user = conn[3];
        String password = conn[4];

        File backupDir = ensureDir();
        String fileName = FILE_PREFIX + LocalDateTime.now().format(NAME_FMT) + FILE_EXT;
        File outFile = new File(backupDir, fileName);

        Map<String, Object> result = new HashMap<String, Object>();
        long start = System.currentTimeMillis();
        try
        {
            List<String> cmd = new ArrayList<String>();
            cmd.add(pgDump);
            cmd.add("-h");
            cmd.add(host);
            cmd.add("-p");
            cmd.add(port);
            cmd.add("-U");
            cmd.add(user);
            cmd.add("-d");
            cmd.add(dbName);
            cmd.add("-Fc");
            cmd.add("--no-owner");
            cmd.add("-w");
            cmd.add("-f");
            cmd.add(outFile.getAbsolutePath());

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.environment().put("PGPASSWORD", password == null ? "" : password);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            final StringBuilder output = new StringBuilder();
            Thread reader = new Thread(new Runnable()
            {
                @Override
                public void run()
                {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
                    {
                        String line;
                        while ((line = br.readLine()) != null)
                        {
                            if (output.length() < 4000)
                            {
                                output.append(line).append('\n');
                            }
                        }
                    }
                    catch (Exception ignore)
                    {
                        // 读取失败的细节对备份结果无影响
                    }
                }
            });
            reader.setDaemon(true);
            reader.start();

            boolean finished = process.waitFor(timeoutMinutes > 0 ? timeoutMinutes : 30, TimeUnit.MINUTES);
            if (!finished)
            {
                process.destroyForcibly();
                deleteQuietly(outFile);
                throw new ServiceException("备份超时（超过 " + timeoutMinutes + " 分钟），已终止并清理半成品文件。");
            }
            reader.join(5000L);
            int exitCode = process.exitValue();
            if (exitCode != 0)
            {
                deleteQuietly(outFile);
                String errMsg = output.toString().trim();
                throw new ServiceException("pg_dump 执行失败（退出码 " + exitCode + "）：" + (StringUtils.isEmpty(errMsg) ? "无输出" : errMsg));
            }
            if (!outFile.isFile() || outFile.length() <= 0)
            {
                deleteQuietly(outFile);
                throw new ServiceException("备份文件未生成或为空：" + outFile.getAbsolutePath());
            }

            int removed = rotateOldFiles(backupDir, keepDays);
            // 异地副本：失败只返回原因，绝不影响本次备份结果（灾备点故障不能拖死主备份）
            String secondaryError = copyToSecondary(outFile);
            result.put("file", fileName);
            result.put("filePath", outFile.getAbsolutePath());
            result.put("size", outFile.length());
            result.put("sizeText", formatSize(outFile.length()));
            result.put("costMs", System.currentTimeMillis() - start);
            result.put("host", host + ":" + port);
            result.put("dbName", dbName);
            result.put("removed", removed);
            result.put("secondaryOk", secondaryError == null);
            result.put("secondaryError", secondaryError == null ? "" : secondaryError);
            log.info("[dbBackup] 备份完成：{}（{}），耗时 {}ms，轮转删除 {} 个旧文件",
                    outFile.getAbsolutePath(), formatSize(outFile.length()), System.currentTimeMillis() - start, removed);
            if (secondaryError != null)
            {
                log.error("[dbBackup] 异地副本未写入：{}", secondaryError);
            }
            return result;
        }
        catch (ServiceException ex)
        {
            log.error("[dbBackup] 备份失败：{}", ex.getMessage());
            throw ex;
        }
        catch (Exception ex)
        {
            deleteQuietly(outFile);
            log.error("[dbBackup] 备份异常", ex);
            throw new ServiceException("备份执行异常：" + ex.getMessage());
        }
    }

    /**
     * 版本兼容校验：pg_dump 主版本号必须 >= 服务端主版本号，否则旧版 pg_dump 无法备份新版服务端
     */
    private void checkVersionCompatibility()
    {
        String client = pgDumpVersion();
        String server = serverVersion();
        int cMajor = majorOf(client);
        int sMajor = majorOf(server);
        if (cMajor > 0 && sMajor > 0 && cMajor < sMajor)
        {
            throw new ServiceException("pg_dump 版本过低，无法备份：服务端 PostgreSQL " + server + "，pg_dump " + client
                    + "。请更换主版本号 >= " + sMajor + " 的 pg_dump。");
        }
    }

    /** 从版本串中提取主版本号（如 "pg_dump (PostgreSQL) 16.2" → 16） */
    private int majorOf(String version)
    {
        if (StringUtils.isEmpty(version))
        {
            return -1;
        }
        Matcher m = Pattern.compile("(\\d+)\\.(\\d+)").matcher(version);
        if (m.find())
        {
            try
            {
                return Integer.parseInt(m.group(1));
            }
            catch (NumberFormatException ignore)
            {
                return -1;
            }
        }
        return -1;
    }

    /**
     * 备份文件列表：<b>同时扫本机备份目录与异地（灾备）目录</b>，两处的文件按文件名合并去重。
     *
     * <p>为什么必须合并：整机挂掉换新机后本机备份目录是空的，备份文件只留在灾备机的共享目录里。
     * 只扫本机的话列表为空，一键恢复就没有文件可选 —— 灾备等于白做。
     * 合并后异地独有的文件会标成 location=secondary，同样可以直接一键恢复（恢复时会自动拉回本机）。</p>
     *
     * <p>异地目录不可达（灾备机宕机、UNC 权限不足）时只列本机文件，不报错、不影响页面。</p>
     */
    @Override
    public List<DbBackupFile> listBackups()
    {
        List<DbBackupFile> list = new ArrayList<DbBackupFile>();

        // ① 本机备份目录
        Map<String, File> localMap = new LinkedHashMap<String, File>();
        File backupDir = backupDirOrNull();
        if (backupDir != null)
        {
            collectDumpFiles(backupDir, localMap);
        }

        // ② 异地（灾备）目录：listFiles 返回 null 即视为不可达（超时/不存在/无权限），只列本机
        Map<String, File> secMap = new LinkedHashMap<String, File>();
        boolean secondaryOn = StringUtils.isNotEmpty(secondaryDir);
        File secDir = secondaryOn ? new File(secondaryDir) : null;
        boolean secReachable = false;
        if (secDir != null)
        {
            File[] secFiles = listFilesWithTimeout(secDir);
            if (secFiles != null)
            {
                secReachable = true;
                secondaryReachable = true;
                for (File f : secFiles)
                {
                    if (f.isFile() && f.getName().endsWith(FILE_EXT))
                    {
                        secMap.put(f.getName(), f);
                    }
                }
            }
            else
            {
                secondaryReachable = false;
            }
        }

        // ③ 合并：本机文件在前，异地独有的补在后面
        List<String> names = new ArrayList<String>(localMap.keySet());
        for (String n : secMap.keySet())
        {
            if (!localMap.containsKey(n))
            {
                names.add(n);
            }
        }
        for (String name : names)
        {
            File local = localMap.get(name);
            File remote = secMap.get(name);
            // 展示用哪个文件的信息：本机优先（本机是最快能用的那份）
            File show = local != null ? local : remote;
            DbBackupFile item = new DbBackupFile(name, show.length(), formatSize(show.length()), new Date(show.lastModified()));
            item.setLocation(local != null && remote != null ? "both" : (local != null ? "local" : "secondary"));
            if (!secondaryOn)
            {
                item.setSecondary("disabled");
            }
            else if (!secReachable)
            {
                // 异地目录当前不可达：无法判断副本在不在，标缺失（提示运维去查灾备机）
                item.setSecondary("missing");
            }
            else if (local == null)
            {
                // 本机没有：异地那份是"本体"，不是副本（换新机器后的典型状态）
                item.setSecondary("self");
            }
            else if (remote != null && remote.length() == show.length())
            {
                item.setSecondary("synced");
            }
            else
            {
                item.setSecondary("missing");
            }
            list.add(item);
        }

        list.sort(new Comparator<DbBackupFile>()
        {
            @Override
            public int compare(DbBackupFile a, DbBackupFile b)
            {
                return Long.compare(b.getLastModified().getTime(), a.getLastModified().getTime());
            }
        });
        return list;
    }

    /** 把目录里的 .dump 文件收进 map（key=文件名） */
    private void collectDumpFiles(File dir, Map<String, File> target)
    {
        File[] files = dir.listFiles();
        if (files == null)
        {
            return;
        }
        for (File f : files)
        {
            if (f.isFile() && f.getName().endsWith(FILE_EXT))
            {
                target.put(f.getName(), f);
            }
        }
    }

    /**
     * 查找备份文件：<b>本机目录优先，本机没有时去异地目录找</b>。
     *
     * <p>覆盖的场景：换新机器后本机备份目录为空，运维把灾备共享目录配成 secondaryDir，
     * 页面能直接看到并恢复灾备机上的备份文件。</p>
     */
    @Override
    public File resolveBackupFile(String name)
    {
        if (!isValidBackupName(name))
        {
            return null;
        }
        File backupDir = backupDirOrNull();
        if (backupDir != null)
        {
            File file = fileInDir(backupDir, name);
            if (file != null)
            {
                return file;
            }
        }
        if (StringUtils.isNotEmpty(secondaryDir))
        {
            File file = fileInDir(new File(secondaryDir), name);
            if (file != null)
            {
                return file;
            }
        }
        return null;
    }

    /** 文件名合法性（防路径穿越：只允许字母数字下划线点横杠，且必须是 .dump） */
    private boolean isValidBackupName(String name)
    {
        return StringUtils.isNotEmpty(name) && name.matches("[A-Za-z0-9_.-]+") && !name.contains("..") && name.endsWith(FILE_EXT);
    }

    /** 取目录内的指定文件（存在且未越出目录时返回，否则 null） */
    private File fileInDir(File dir, String name)
    {
        File file = new File(dir, name);
        if (!isFileWithTimeout(file))
        {
            return null;
        }
        try
        {
            // 防路径穿越：真实路径必须在目录内
            String dirPath = dir.getCanonicalPath();
            String filePath = file.getCanonicalPath();
            if (!filePath.startsWith(dirPath + File.separator))
            {
                return null;
            }
        }
        catch (Exception ex)
        {
            return null;
        }
        return file;
    }

    @Override
    public boolean deleteBackup(String name)
    {
        File file = resolveBackupFile(name);
        if (file == null)
        {
            return false;
        }
        boolean ok = file.delete();
        if (ok)
        {
            File copy = secondaryFileOf(name);
            if (copy != null && copy.isFile())
            {
                deleteQuietly(copy);
            }
        }
        log.info("[dbBackup] 删除备份文件 {}：{}", name, ok ? "成功" : "失败");
        return ok;
    }

    @Override
    public String getBackupDir()
    {
        return dir;
    }

    @Override
    public String getSecondaryDir()
    {
        return secondaryDir;
    }

    @Override
    public boolean isSecondaryReachable()
    {
        return secondaryReachable;
    }

    @Override
    public boolean isEnabled()
    {
        return enabled;
    }

    @Override
    public String resolvePgDump()
    {
        if (pgDumpResolved)
        {
            return pgDumpCache;
        }
        synchronized (this)
        {
            if (pgDumpResolved)
            {
                return pgDumpCache;
            }
            String found = null;
            if (StringUtils.isNotEmpty(pgDumpPath))
            {
                File cfg = new File(pgDumpPath);
                found = cfg.isFile() ? pgDumpPath : null;
                if (found == null)
                {
                    log.warn("[dbBackup] 配置的 pgDumpPath 不存在：{}", pgDumpPath);
                }
            }
            if (found == null)
            {
                found = findBundled();
            }
            if (found == null)
            {
                found = findInPath();
            }
            if (found == null)
            {
                found = findInCommonDirs();
            }
            pgDumpCache = found;
            pgDumpResolved = true;
            if (found == null)
            {
                log.warn("[dbBackup] 未探测到 pg_dump，定时备份将跳过；请配置 xky-project.backup.pgDumpPath");
            }
            return found;
        }
    }

    @Override
    public String pgDumpVersion()
    {
        if (pgDumpVersionCache == null)
        {
            synchronized (this)
            {
                if (pgDumpVersionCache == null)
                {
                    pgDumpVersionCache = runVersion(resolvePgDump());
                }
            }
        }
        return pgDumpVersionCache;
    }

    @Override
    public String pgRestoreVersion()
    {
        if (pgRestoreVersionCache == null)
        {
            synchronized (this)
            {
                if (pgRestoreVersionCache == null)
                {
                    pgRestoreVersionCache = runVersion(resolvePgRestore());
                }
            }
        }
        return pgRestoreVersionCache;
    }

    /** 执行 `<可执行文件> --version` 并返回输出；不可用/失败返回空串 */
    private String runVersion(String exePath)
    {
        if (StringUtils.isEmpty(exePath))
        {
            return "";
        }
        try
        {
            Process process = new ProcessBuilder(exePath, "--version").redirectErrorStream(true).start();
            if (process.waitFor(20, TimeUnit.SECONDS) && process.exitValue() == 0)
            {
                try (InputStream in = process.getInputStream())
                {
                    byte[] buf = new byte[512];
                    int n = in.read(buf);
                    if (n > 0)
                    {
                        return new String(buf, 0, n, StandardCharsets.UTF_8).trim();
                    }
                }
            }
        }
        catch (Exception ex)
        {
            log.warn("[dbBackup] 读取版本失败：{}（{}）", exePath, ex.getMessage());
        }
        return "";
    }

    @Override
    public String resolvePgRestore()
    {
        if (pgRestoreResolved)
        {
            return pgRestoreCache;
        }
        synchronized (this)
        {
            if (pgRestoreResolved)
            {
                return pgRestoreCache;
            }
            String found = null;
            // ① 与 pg_dump 同目录（xky-pgtools 内两者是配套放的）
            String pgDump = resolvePgDump();
            if (StringUtils.isNotEmpty(pgDump))
            {
                File sibling = new File(new File(pgDump).getParentFile(), isWindows() ? "pg_restore.exe" : "pg_restore");
                if (sibling.isFile())
                {
                    found = sibling.getAbsolutePath();
                }
            }
            // ② PATH
            if (found == null)
            {
                found = findInPath(isWindows() ? "pg_restore.exe" : "pg_restore");
            }
            pgRestoreCache = found;
            pgRestoreResolved = true;
            return found;
        }
    }

    @Override
    public Map<String, String> connectionInfo()
    {
        Map<String, String> info = new HashMap<String, String>();
        try
        {
            String[] conn = resolveConnection();
            info.put("host", conn[0]);
            info.put("port", conn[1]);
            info.put("dbName", conn[2]);
            info.put("user", conn[3]);
        }
        catch (Exception ex)
        {
            // 非 Druid 数据源 / 非 PostgreSQL 时拿不到 —— 页面会退化为占位符展示命令
            log.warn("[dbBackup] 解析数据库连接信息失败：{}", ex.getMessage());
        }
        return info;
    }

    @Override
    public String serverVersion()
    {
        try (Connection conn = masterDataSource.getConnection();
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("show server_version"))
        {
            return rs.next() ? rs.getString(1) : "";
        }
        catch (Exception ex)
        {
            log.warn("[dbBackup] 读取数据库服务端版本失败：{}", ex.getMessage());
            return "";
        }
    }

    /* ------------------------------------------------------------------ */
    /*                        一键恢复（pg_restore）                        */
    /* ------------------------------------------------------------------ */

    @Override
    public List<DbDatabaseInfo> listDatabases()
    {
        List<DbDatabaseInfo> list = new ArrayList<DbDatabaseInfo>();
        String[] conn = resolveConnection();
        final String currentDb = conn[2];
        Connection c = null;
        Statement st = null;
        ResultSet rs = null;
        try
        {
            c = connect(conn[0], conn[1], "postgres", conn[3], conn[4]);
            st = c.createStatement();
            rs = st.executeQuery("select datname, pg_size_pretty(pg_database_size(datname)) from pg_database where datistemplate = false order by datname");
            while (rs.next())
            {
                String name = rs.getString(1);
                DbDatabaseInfo info = new DbDatabaseInfo(name, rs.getString(2));
                info.setCurrent(name.equals(currentDb));
                // 只允许删本功能恢复出来的库，且现库永远不可删 —— 防止一键清掉生产库
                info.setDroppable(name.startsWith(RESTORE_DB_PREFIX) && !info.isCurrent());
                list.add(info);
            }
        }
        catch (ServiceException ex)
        {
            throw ex;
        }
        catch (Exception ex)
        {
            log.error("[dbRestore] 读取数据库列表失败：" + ex.getMessage(), ex);
            throw new ServiceException("读取数据库列表失败：" + ex.getMessage());
        }
        finally
        {
            closeQuietly(rs, st, c);
        }
        return list;
    }

    @Override
    public Map<String, Object> inspectDatabase(String dbName)
    {
        validateDbName(dbName);
        Map<String, Object> r = new HashMap<String, Object>();
        r.put("dbName", dbName);
        boolean exists = databaseExists(dbName);
        r.put("exists", exists);
        if (!exists)
        {
            r.put("tableCount", 0);
            r.put("sizeText", "-");
            r.put("restorable", Boolean.TRUE);
            r.put("reason", "库不存在，恢复时会自动创建");
            return r;
        }
        int tables = countTables(dbName);
        r.put("tableCount", tables);
        r.put("sizeText", databaseSize(dbName));
        if (tables > 0)
        {
            r.put("restorable", Boolean.FALSE);
            r.put("reason", "该库里已有 " + tables + " 张业务表，不能覆盖。请改用新建库；确需覆盖现库请停应用后走命令行。");
        }
        else
        {
            r.put("restorable", Boolean.TRUE);
            r.put("reason", "空库，可直接恢复");
        }
        return r;
    }

    @Override
    public Map<String, Object> startRestore(final String backupName, final String targetDb)
    {
        if (restoreRunning)
        {
            throw new ServiceException("已有恢复任务正在执行，请等它结束后再发起");
        }
        synchronized (restoreLock)
        {
            if (restoreRunning)
            {
                throw new ServiceException("已有恢复任务正在执行，请等它结束后再发起");
            }
            String pgRestore = resolvePgRestore();
            if (StringUtils.isEmpty(pgRestore))
            {
                throw new ServiceException("未找到 pg_restore，无法恢复。把 pg_restore.exe 放到与 pg_dump 相同的目录（如应用目录下的 xky-pgtools/）后重启后端即可。");
            }
            validateDbName(targetDb);
            File file = resolveBackupFile(backupName);
            if (file == null)
            {
                throw new ServiceException("备份文件不存在或文件名非法：" + backupName);
            }
            boolean exists = databaseExists(targetDb);
            if (exists && countTables(targetDb) > 0)
            {
                throw new ServiceException("目标库 " + targetDb + " 里已有业务数据，拒绝覆盖恢复。请改用新建库。");
            }
            final boolean createDb = !exists;
            final String exe = pgRestore;
            // 文件只在灾备机共享目录里时（换新机器的典型场景）先拉回本机，避免恢复途中网络断开
            final File dump = ensureLocalCopy(file);

            DbRestoreStatus st = new DbRestoreStatus();
            st.setRunning(true);
            st.setPhase("准备中");
            st.setFile(dump.getName());
            st.setTargetDb(targetDb);
            st.setCreatedDb(createDb);
            st.setStartedAt(new Date());
            st.setLog("");
            restoreStatus = st;
            restoreRunning = true;

            Thread worker = new Thread(new Runnable()
            {
                @Override
                public void run()
                {
                    runRestore(exe, dump, targetDb, createDb);
                }
            }, "xky-db-restore");
            worker.setDaemon(true);
            worker.start();

            Map<String, Object> r = new HashMap<String, Object>();
            r.put("started", Boolean.TRUE);
            r.put("file", dump.getName());
            r.put("targetDb", targetDb);
            r.put("createdDb", createDb);
            log.info("[dbRestore] 开始恢复：{} -> {}（{}）", dump.getName(), targetDb, createDb ? "自动建库" : "已有空库");
            return r;
        }
    }

    @Override
    public DbRestoreStatus restoreStatus()
    {
        DbRestoreStatus st = restoreStatus;
        if (st == null)
        {
            st = new DbRestoreStatus();
            st.setRunning(false);
            st.setPhase("尚未执行过恢复");
        }
        return st;
    }

    @Override
    public boolean dropDatabase(String dbName)
    {
        if (StringUtils.isEmpty(dbName) || !dbName.startsWith(RESTORE_DB_PREFIX))
        {
            throw new ServiceException("只允许删除 " + RESTORE_DB_PREFIX + " 前缀的历史恢复库");
        }
        String[] conn = resolveConnection();
        if (dbName.equals(conn[2]))
        {
            throw new ServiceException("不能删除应用当前使用的库：" + dbName);
        }
        if (!databaseExists(dbName))
        {
            return false;
        }
        Connection c = null;
        Statement st = null;
        try
        {
            // DROP DATABASE 前必须先断开该库的残留连接，否则会一直挂着或报 "being accessed by other users"
            c = connect(conn[0], conn[1], "postgres", conn[3], conn[4]);
            st = c.createStatement();
            st.execute("select pg_terminate_backend(pid) from pg_stat_activity where datname = '" + dbName + "' and pid <> pg_backend_pid()");
            st.execute("drop database if exists " + dbName);
            log.info("[dbRestore] 已删除历史恢复库 {}", dbName);
            return true;
        }
        catch (Exception ex)
        {
            log.error("[dbRestore] 删除库 " + dbName + " 失败：" + ex.getMessage(), ex);
            throw new ServiceException("删除库失败：" + ex.getMessage());
        }
        finally
        {
            closeQuietly(null, st, c);
        }
    }

    /**
     * 恢复主流程（在工作线程里执行，结果写回 {@link #restoreStatus}）
     */
    private void runRestore(String pgRestore, File dump, String targetDb, boolean createDb)
    {
        DbRestoreStatus st = restoreStatus;
        final List<String> lines = Collections.synchronizedList(new ArrayList<String>());
        long start = System.currentTimeMillis();
        try
        {
            String[] conn = resolveConnection();
            if (createDb)
            {
                st.setPhase("创建目标库 " + targetDb);
                createDatabase(conn, targetDb);
                log.info("[dbRestore] 目标库已创建：{}", targetDb);
            }
            st.setPhase("恢复数据中");

            List<String> cmd = new ArrayList<String>();
            cmd.add(pgRestore);
            cmd.add("-h");
            cmd.add(conn[0]);
            cmd.add("-p");
            cmd.add(conn[1]);
            cmd.add("-U");
            cmd.add(conn[3]);
            cmd.add("-d");
            cmd.add(targetDb);
            // -j 4 并发：目标库是新建/空的，失败删库重来即可，不需要 --single-transaction
            // （--single-transaction 与 -j 互斥，且与 --clean 一起用在现库上风险过高，故本功能不做覆盖式恢复）
            cmd.add("-j");
            cmd.add("4");
            cmd.add("--no-owner");
            cmd.add("--exit-on-error");
            cmd.add("-v");
            cmd.add(dump.getAbsolutePath());

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.environment().put("PGPASSWORD", conn[4] == null ? "" : conn[4]);
            pb.redirectErrorStream(true);
            final Process process = pb.start();

            Thread reader = new Thread(new Runnable()
            {
                @Override
                public void run()
                {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
                    {
                        String line;
                        int n = 0;
                        while ((line = br.readLine()) != null)
                        {
                            lines.add(line);
                            // 每 20 行刷新一次展示，避免每行都拼一次字符串
                            if (++n % 20 == 0)
                            {
                                restoreStatus.setLog(tailText(lines));
                            }
                        }
                    }
                    catch (Exception ignore)
                    {
                        // 读不到日志不影响恢复结果
                    }
                }
            });
            reader.setDaemon(true);
            reader.start();

            int timeout = timeoutMinutes > 0 ? timeoutMinutes : 30;
            boolean finished = process.waitFor(timeout, TimeUnit.MINUTES);
            if (!finished)
            {
                process.destroyForcibly();
                throw new ServiceException("恢复超时（超过 " + timeout + " 分钟），已终止");
            }
            reader.join(5000L);
            int exitCode = process.exitValue();
            if (exitCode != 0)
            {
                throw new ServiceException("pg_restore 执行失败（退出码 " + exitCode + "）");
            }

            st.setPhase("校验结果");
            int tables = countTables(targetDb);
            st.setTableCount(tables);
            st.setSuccess(Boolean.TRUE);
            st.setPhase("恢复完成");
            st.setMessage("已恢复到库 " + targetDb + "，共 " + tables + " 张表" + (createDb ? "（该库为本次新建）" : ""));
            log.info("[dbRestore] 恢复完成：{} -> {}，{} 张表，耗时 {}ms", dump.getName(), targetDb, tables, System.currentTimeMillis() - start);
        }
        catch (Exception ex)
        {
            st.setSuccess(Boolean.FALSE);
            st.setPhase("恢复失败");
            String msg = ex.getMessage();
            if (createDb)
            {
                msg = msg + "。目标库是本次新建的，可在下方「恢复库管理」里删掉后重试。";
            }
            st.setMessage(msg);
            log.error("[dbRestore] 恢复失败：" + msg, ex);
        }
        finally
        {
            st.setFinishedAt(new Date());
            st.setCostMs(System.currentTimeMillis() - start);
            st.setLog(tailText(lines));
            st.setRunning(false);
            restoreRunning = false;
        }
    }

    /** 取日志最后 RESTORE_LOG_MAX_LINES 行拼成文本 */
    private String tailText(List<String> lines)
    {
        int from = Math.max(0, lines.size() - RESTORE_LOG_MAX_LINES);
        StringBuilder sb = new StringBuilder();
        synchronized (lines)
        {
            for (int i = from; i < lines.size(); i++)
            {
                sb.append(lines.get(i)).append('\n');
            }
        }
        return sb.toString();
    }

    /** 目标库名校验：只允许小写字母/数字/下划线，避开系统库 */
    private void validateDbName(String dbName)
    {
        if (StringUtils.isEmpty(dbName))
        {
            throw new ServiceException("目标库名不能为空");
        }
        if (!dbName.matches("[a-z][a-z0-9_]*"))
        {
            throw new ServiceException("目标库名只能是小写字母、数字、下划线，且以字母开头（当前：" + dbName + "）");
        }
        if (dbName.length() > 63)
        {
            throw new ServiceException("目标库名过长（最多 63 个字符）");
        }
        for (String reserved : RESERVED_DBS)
        {
            if (reserved.equals(dbName))
            {
                throw new ServiceException("不能用系统库 " + dbName + " 作为恢复目标");
            }
        }
    }

    /** 建库（TEMPLATE template0：保证编码/排序规则干净，不受模板库自定义对象影响） */
    private void createDatabase(String[] conn, String dbName)
    {
        Connection c = null;
        Statement st = null;
        try
        {
            c = connect(conn[0], conn[1], "postgres", conn[3], conn[4]);
            st = c.createStatement();
            st.execute("create database " + dbName + " template template0");
        }
        catch (Exception ex)
        {
            throw new ServiceException("创建目标库失败：" + ex.getMessage());
        }
        finally
        {
            closeQuietly(null, st, c);
        }
    }

    /** 目标库是否存在 */
    private boolean databaseExists(String dbName)
    {
        Connection c = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try
        {
            String[] conn = resolveConnection();
            c = connect(conn[0], conn[1], "postgres", conn[3], conn[4]);
            ps = c.prepareStatement("select 1 from pg_database where datname = ?");
            ps.setString(1, dbName);
            rs = ps.executeQuery();
            return rs.next();
        }
        catch (Exception ex)
        {
            throw new ServiceException("检查目标库是否存在失败：" + ex.getMessage());
        }
        finally
        {
            closeQuietly(rs, ps, c);
        }
    }

    /** 目标库 public schema 下的业务表数量（恢复闸门：>0 即拒绝） */
    private int countTables(String dbName)
    {
        Connection c = null;
        Statement st = null;
        ResultSet rs = null;
        try
        {
            String[] conn = resolveConnection();
            c = connect(conn[0], conn[1], dbName, conn[3], conn[4]);
            st = c.createStatement();
            rs = st.executeQuery("select count(*) from information_schema.tables where table_schema = 'public' and table_type = 'BASE TABLE'");
            return rs.next() ? rs.getInt(1) : 0;
        }
        catch (Exception ex)
        {
            log.warn("[dbRestore] 统计库 {} 的表数量失败：{}", dbName, ex.getMessage());
            return -1;
        }
        finally
        {
            closeQuietly(rs, st, c);
        }
    }

    /** 库大小（易读文本） */
    private String databaseSize(String dbName)
    {
        Connection c = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try
        {
            String[] conn = resolveConnection();
            c = connect(conn[0], conn[1], "postgres", conn[3], conn[4]);
            ps = c.prepareStatement("select pg_size_pretty(pg_database_size(?))");
            ps.setString(1, dbName);
            rs = ps.executeQuery();
            return rs.next() ? rs.getString(1) : "-";
        }
        catch (Exception ex)
        {
            return "-";
        }
        finally
        {
            closeQuietly(rs, ps, c);
        }
    }

    /**
     * 直连指定库（不走连接池：建库/删库/查 pg_database 都是运维动作，
     * 用一次即关的独立连接最干净，也不会受到连接池事务状态影响）
     */
    private Connection connect(String host, String port, String dbName, String user, String password) throws Exception
    {
        String url = "jdbc:postgresql://" + host + ":" + port + "/" + dbName;
        return DriverManager.getConnection(url, user, password);
    }

    private void closeQuietly(ResultSet rs, Statement st, Connection c)
    {
        try
        {
            if (rs != null)
            {
                rs.close();
            }
        }
        catch (Exception ignore)
        {
            // ignore
        }
        try
        {
            if (st != null)
            {
                st.close();
            }
        }
        catch (Exception ignore)
        {
            // ignore
        }
        try
        {
            if (c != null)
            {
                c.close();
            }
        }
        catch (Exception ignore)
        {
            // ignore
        }
    }

    /**
     * 免安装（绿色）pg_dump：随应用一起放置的工具目录（与 jar 同级）。
     * 仓库里的目录名为 xky-pgtools，同时兼容 pgtools / tools/pgtools / bin/pgtools 三种习惯命名。
     * 只需 pg_dump.exe + libpq.dll + zlib.dll + libssl-3-x64.dll + libcrypto-3-x64.dll 五个文件（约 7MB），
     * 无需安装 PostgreSQL 客户端。（pg_restore.exe 放在同目录即可被 resolvePgRestore 自动识别）
     */
    private String findBundled()
    {
        String exe = isWindows() ? "pg_dump.exe" : "pg_dump";
        List<File> bases = appBaseDirs();
        for (File base : bases)
        {
            for (String sub : new String[] { "xky-pgtools", "pgtools", "tools/xky-pgtools", "tools/pgtools", "bin/xky-pgtools", "bin/pgtools" })
            {
                File f = new File(new File(base, sub), exe);
                if (f.isFile())
                {
                    log.info("[dbBackup] 使用免安装 pg_dump：{}", f.getAbsolutePath());
                    return f.getAbsolutePath();
                }
            }
        }
        // 未命中时把找过的位置打出来，省得现场猜（只打一次，不影响正常流程）
        StringBuilder tried = new StringBuilder();
        for (File base : bases)
        {
            if (tried.length() > 0)
            {
                tried.append(" 或 ");
            }
            tried.append(base.getAbsolutePath()).append("/xky-pgtools");
        }
        log.info("[dbBackup] 应用目录下未找到 pg_dump，已尝试：{}", tried);
        return null;
    }

    /**
     * 应用基准目录候选（按优先级）：
     * ① Spring Boot 的 ApplicationHome —— jar 部署时准确给出 <b>jar 所在目录</b>
     *    （fat jar 下本类是经 BOOT-INF/lib/xky-project-x.jar 加载的，CodeSource 是嵌套 jar 的 URI，
     *     new File(URI) 会直接抛异常，所以不能只靠 ②）
     * ② 代码来源目录：普通 jar / 开发期 classes 目录，以及它们的上级
     * ③ sun.java.command 里的 jar 路径（不依赖 Spring 的兜底）
     * ④ 进程工作目录 user.dir —— ⚠️ 以 Windows 服务/计划任务启动时它常是 C:/Windows/System32，
     *    所以不能只靠它，故排在最后
     */
    private List<File> appBaseDirs()
    {
        List<File> bases = new ArrayList<File>();
        // ① Spring Boot 运行时（jar 部署最主要的一条）
        try
        {
            File home = new ApplicationHome(DbBackupServiceImpl.class).getDir();
            if (home != null)
            {
                bases.add(home);
                if (home.getParentFile() != null)
                {
                    bases.add(home.getParentFile());
                }
            }
        }
        catch (Throwable ignore)
        {
            // 非 Spring 环境或版本差异时忽略
        }
        // ② 代码来源目录（开发期 classes、普通 jar）
        try
        {
            File code = new File(DbBackupServiceImpl.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (code.exists())
            {
                File dir = code.isFile() ? code.getParentFile() : code;
                if (dir != null)
                {
                    bases.add(dir);
                    if (dir.getParentFile() != null)
                    {
                        bases.add(dir.getParentFile());
                    }
                }
            }
        }
        catch (Throwable ignore)
        {
            // fat jar 下 URI 形如 jar:nested:/path/app.jar/!BOOT-INF/lib/x.jar!/，转 File 必然失败，正常现象
        }
        String userDir = System.getProperty("user.dir");
        // ③ java -jar 的启动参数兜底
        try
        {
            String cmd = System.getProperty("sun.java.command");
            if (StringUtils.isNotEmpty(cmd))
            {
                String first = cmd.trim().split("\\s+")[0];
                if (first.toLowerCase().endsWith(".jar"))
                {
                    File jf = new File(first);
                    if (!jf.isAbsolute() && StringUtils.isNotEmpty(userDir))
                    {
                        jf = new File(userDir, first);
                    }
                    File parent = jf.getAbsoluteFile().getParentFile();
                    if (parent != null)
                    {
                        bases.add(parent);
                    }
                }
            }
        }
        catch (Throwable ignore)
        {
            // 取不到就跳过
        }
        // ④ 当前工作目录
        if (StringUtils.isNotEmpty(userDir))
        {
            bases.add(new File(userDir));
        }
        // 去重（保持顺序）
        List<File> uniq = new ArrayList<File>();
        for (File b : bases)
        {
            boolean dup = false;
            for (File u : uniq)
            {
                if (u.equals(b))
                {
                    dup = true;
                    break;
                }
            }
            if (!dup)
            {
                uniq.add(b);
            }
        }
        return uniq;
    }

    /** 从 PATH 环境变量中查找 pg_dump */
    private String findInPath()
    {
        return findInPath(isWindows() ? "pg_dump.exe" : "pg_dump");
    }

    /** 从 PATH 环境变量中查找指定可执行文件 */
    private String findInPath(String exe)
    {
        String pathEnv = System.getenv("PATH");
        if (StringUtils.isEmpty(pathEnv))
        {
            return null;
        }
        for (String p : pathEnv.split(Pattern.quote(File.pathSeparator)))
        {
            if (StringUtils.isEmpty(p))
            {
                continue;
            }
            File f = new File(p, exe);
            if (f.isFile())
            {
                return f.getAbsolutePath();
            }
        }
        return null;
    }

    /** 从常见安装目录查找 pg_dump（按版本号倒序，优先使用高版本） */
    private String findInCommonDirs()
    {
        if (!isWindows())
        {
            for (String p : new String[] { "/usr/bin/pg_dump", "/usr/local/bin/pg_dump", "/usr/pgsql-16/bin/pg_dump", "/usr/pgsql-15/bin/pg_dump" })
            {
                File f = new File(p);
                if (f.isFile())
                {
                    return f.getAbsolutePath();
                }
            }
            return null;
        }
        String[] roots = new String[] { "C:/Program Files/PostgreSQL", "C:/Program Files (x86)/PostgreSQL", "D:/Program Files/PostgreSQL", "E:/Program Files/PostgreSQL" };
        for (String root : roots)
        {
            File rootDir = new File(root);
            File[] vers = rootDir.listFiles();
            if (vers == null)
            {
                continue;
            }
            List<File> dirs = new ArrayList<File>(Arrays.asList(vers));
            dirs.sort(new Comparator<File>()
            {
                @Override
                public int compare(File a, File b)
                {
                    return b.getName().compareTo(a.getName());
                }
            });
            for (File v : dirs)
            {
                if (!v.isDirectory())
                {
                    continue;
                }
                File f = new File(v, "bin/pg_dump.exe");
                if (f.isFile())
                {
                    return f.getAbsolutePath();
                }
            }
        }
        return null;
    }

    /** 从主数据源解析连接信息：{ host, port, dbName, user, password } */
    private String[] resolveConnection()
    {
        DataSource ds = masterDataSource;
        if (!(ds instanceof DruidDataSource))
        {
            throw new ServiceException("主数据源类型不是 DruidDataSource，无法解析数据库连接信息");
        }
        DruidDataSource dds = (DruidDataSource) ds;
        String url = dds.getUrl();
        if (StringUtils.isEmpty(url))
        {
            throw new ServiceException("未能获取主数据源 jdbc url，无法备份");
        }
        Matcher m = PG_URL.matcher(url);
        if (!m.find())
        {
            throw new ServiceException("当前数据库不是 PostgreSQL（url=" + url + "），暂不支持自动备份");
        }
        String host = m.group(1);
        String port = StringUtils.isEmpty(m.group(2)) ? "5432" : m.group(2);
        String dbName = m.group(3);
        String user = dds.getUsername();
        String password = dds.getPassword();
        if (StringUtils.isEmpty(user))
        {
            throw new ServiceException("未能获取数据库用户名，无法备份");
        }
        return new String[] { host, port, dbName, user, password };
    }

    /** 确保备份目录存在且可写 */
    private File ensureDir()
    {
        File backupDir = new File(dir);
        if (!backupDir.exists() && !backupDir.mkdirs())
        {
            throw new ServiceException("备份目录无法创建：" + backupDir.getAbsolutePath() + "（请检查 xky-project.backup.dir 配置与写权限）");
        }
        if (!backupDir.isDirectory() || !backupDir.canWrite())
        {
            throw new ServiceException("备份目录不可用或不可写：" + backupDir.getAbsolutePath());
        }
        return backupDir;
    }

    /** 备份目录（不存在时返回 null，供只读方法使用） */
    private File backupDirOrNull()
    {
        if (StringUtils.isEmpty(dir))
        {
            return null;
        }
        File backupDir = new File(dir);
        return backupDir.isDirectory() ? backupDir : null;
    }

    /** 轮转：删除超过保留天数的备份文件，返回删除数量 */
    private int rotateOldFiles(File backupDir, int days)
    {
        if (days <= 0)
        {
            return 0;
        }
        long deadline = System.currentTimeMillis() - days * 24L * 3600L * 1000L;
        File[] files = backupDir.listFiles();
        if (files == null)
        {
            return 0;
        }
        int removed = 0;
        for (File f : files)
        {
            if (f.isFile() && f.getName().endsWith(FILE_EXT) && f.lastModified() < deadline)
            {
                if (f.delete())
                {
                    removed++;
                }
            }
        }
        return removed;
    }

    /**
     * 把刚生成的备份文件复制到异地（灾备）目录
     *
     * @return null 表示成功（含"未启用异地备份"），非 null 为失败原因
     */
    private String copyToSecondary(File file)
    {
        if (StringUtils.isEmpty(secondaryDir))
        {
            return null;
        }
        File targetDir = new File(secondaryDir);
        try
        {
            if (!targetDir.isDirectory() && !targetDir.mkdirs())
            {
                return "异地目录不可用或无法创建：" + secondaryDir;
            }
            File target = new File(targetDir, file.getName());
            copyWithTimeout(file, target);
            if (!target.isFile() || target.length() != file.length())
            {
                return "异地副本校验失败（文件缺失或大小不一致）";
            }
            int removed = rotateOldFiles(targetDir, secondaryKeepDays);
            if (removed > 0)
            {
                log.info("[dbBackup] 异地目录轮转删除 {} 个旧文件", removed);
            }
            return null;
        }
        catch (Exception ex)
        {
            return "写入异地目录失败：" + ex.getMessage();
        }
    }

    /**
     * 带超时的文件复制。
     *
     * <p>为什么不直接在主线程 {@code Files.copy}：异地目录通常是网络共享，目标机宕机或网络中断时
     * 复制可能长时间无响应，而 {@code @Scheduled} 默认是单线程执行器——一旦挂住，后续所有定时任务
     * （不止备份）都会被卡死。所以复制单独起线程并限时，超时即放弃本次异地副本。</p>
     */
    private void copyWithTimeout(final File src, final File dst) throws Exception
    {
        ExecutorService pool = Executors.newSingleThreadExecutor(new ThreadFactory()
        {
            @Override
            public Thread newThread(Runnable r)
            {
                Thread t = new Thread(r, "xky-backup-copy");
                t.setDaemon(true);
                return t;
            }
        });
        try
        {
            Future<?> future = pool.submit(new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        Files.copy(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                    catch (IOException ex)
                    {
                        throw new IllegalStateException(ex);
                    }
                }
            });
            future.get(SECONDARY_COPY_TIMEOUT_SEC, TimeUnit.SECONDS);
        }
        catch (TimeoutException ex)
        {
            throw new ServiceException("复制超过 " + SECONDARY_COPY_TIMEOUT_SEC + " 秒无响应（异地目录不可达？）");
        }
        finally
        {
            pool.shutdownNow();
        }
    }

    /**
     * 带超时地列目录。
     *
     * <p>异地目录通常是 UNC 网络共享，灾备机宕机 / 网络中断 / 权限不足时 {@code listFiles()}
     * 可能长时间不返回。列表接口是同步 HTTP 请求，不能等它，所以限时 {@link #NET_IO_TIMEOUT_SEC} 秒，
     * 超时返回 null（调用方按"异地不可达"处理，只列本机文件）。</p>
     */
    private File[] listFilesWithTimeout(final File dir)
    {
        ExecutorService pool = Executors.newSingleThreadExecutor(new ThreadFactory()
        {
            @Override
            public Thread newThread(Runnable r)
            {
                Thread t = new Thread(r, "xky-backup-list");
                t.setDaemon(true);
                return t;
            }
        });
        try
        {
            Future<File[]> future = pool.submit(new Callable<File[]>()
            {
                @Override
                public File[] call()
                {
                    return dir.listFiles();
                }
            });
            return future.get(NET_IO_TIMEOUT_SEC, TimeUnit.SECONDS);
        }
        catch (Exception ex)
        {
            log.warn("[dbBackup] 列目录超时或失败（{}）：{}", dir.getPath(), ex.getMessage());
            return null;
        }
        finally
        {
            pool.shutdownNow();
        }
    }

    /** 带超时地判断"是不是一个非空文件"（同上，避免 UNC 挂住请求） */
    private boolean isFileWithTimeout(final File file)
    {
        ExecutorService pool = Executors.newSingleThreadExecutor(new ThreadFactory()
        {
            @Override
            public Thread newThread(Runnable r)
            {
                Thread t = new Thread(r, "xky-backup-probe");
                t.setDaemon(true);
                return t;
            }
        });
        try
        {
            Future<Boolean> future = pool.submit(new Callable<Boolean>()
            {
                @Override
                public Boolean call()
                {
                    return Boolean.valueOf(file.isFile() && file.length() > 0);
                }
            });
            return Boolean.TRUE.equals(future.get(NET_IO_TIMEOUT_SEC, TimeUnit.SECONDS));
        }
        catch (Exception ex)
        {
            log.warn("[dbBackup] 探测文件超时或失败（{}）：{}", file.getPath(), ex.getMessage());
            return false;
        }
        finally
        {
            pool.shutdownNow();
        }
    }

    /**
     * 恢复前把「只在异地」的备份文件拉回本机备份目录。
     *
     * <p>为什么不直接把 UNC 路径交给 pg_restore：恢复过程可能持续数分钟，中途网络抖动 /
     * 共享断开会让恢复直接失败，还得从头再来。先复制回本机，恢复过程就只依赖本地磁盘。</p>
     *
     * <p>复制失败（本机目录不可写、共享只读等）时退回直接用异地路径，不阻断恢复。</p>
     */
    private File ensureLocalCopy(File file)
    {
        File backupDir = backupDirOrNull();
        if (backupDir == null)
        {
            try
            {
                backupDir = ensureDir();
            }
            catch (Exception ex)
            {
                log.warn("[dbRestore] 本机备份目录不可用，直接使用原路径恢复：{}", ex.getMessage());
                return file;
            }
        }
        try
        {
            if (file.getCanonicalPath().startsWith(backupDir.getCanonicalPath() + File.separator))
            {
                return file;
            }
        }
        catch (Exception ex)
        {
            return file;
        }
        File target = new File(backupDir, file.getName());
        try
        {
            copyWithTimeout(file, target);
            if (target.isFile() && target.length() == file.length())
            {
                log.info("[dbRestore] 已从异地目录取回备份文件到本机：{}", target.getAbsolutePath());
                return target;
            }
            log.warn("[dbRestore] 取回的备份文件校验失败（大小不一致），改用原路径恢复");
        }
        catch (Exception ex)
        {
            log.warn("[dbRestore] 从异地目录取回备份文件失败，改用原路径恢复：{}", ex.getMessage());
        }
        return file;
    }

    /** 异地目录中的同名副本；未启用异地备份或目录当前不可用时返回 null */
    private File secondaryFileOf(String name)
    {
        if (StringUtils.isEmpty(secondaryDir))
        {
            return null;
        }
        File targetDir = new File(secondaryDir);
        if (!targetDir.isDirectory())
        {
            return null;
        }
        return new File(targetDir, name);
    }

    /** 文件大小易读文本 */
    private String formatSize(long size)
    {
        if (size < 1024)
        {
            return size + " B";
        }
        DecimalFormat df = new DecimalFormat("#.##");
        double kb = size / 1024.0;
        if (kb < 1024)
        {
            return df.format(kb) + " KB";
        }
        double mb = kb / 1024.0;
        if (mb < 1024)
        {
            return df.format(mb) + " MB";
        }
        return df.format(mb / 1024.0) + " GB";
    }

    private void deleteQuietly(File file)
    {
        try
        {
            if (file != null && file.exists())
            {
                file.delete();
            }
        }
        catch (Exception ignore)
        {
            // 清理失败不影响主流程
        }
    }

    private boolean isWindows()
    {
        String os = System.getProperty("os.name");
        return os != null && os.toLowerCase().contains("win");
    }
}
