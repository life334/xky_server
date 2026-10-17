package com.xakcch.project.service.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.alibaba.druid.pool.DruidDataSource;
import com.xakcch.common.exception.ServiceException;
import com.xakcch.common.utils.StringUtils;
import com.xakcch.project.domain.vo.DbBackupFile;
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
    /** jdbc:postgresql://host:port/db?xxx */
    private static final Pattern PG_URL = Pattern.compile("^jdbc:postgresql://([^:/]+)(?::(\\d+))?/([^?]+)");
    /** pg_dump 探测结果缓存（避免每次遍历磁盘） */
    private volatile String pgDumpCache;
    /** 已探测过标记（区分「未探测」与「探测结果为无」） */
    private volatile boolean pgDumpResolved;
    /** pg_dump 版本缓存 */
    private volatile String pgDumpVersionCache;

    @Value("${xky-project.backup.enabled:true}")
    private boolean enabled;

    @Value("${xky-project.backup.dir:D:/xky-db-backup}")
    private String dir;

    @Value("${xky-project.backup.keepDays:7}")
    private int keepDays;

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

            int removed = rotateOldFiles(backupDir);
            result.put("file", fileName);
            result.put("filePath", outFile.getAbsolutePath());
            result.put("size", outFile.length());
            result.put("sizeText", formatSize(outFile.length()));
            result.put("costMs", System.currentTimeMillis() - start);
            result.put("host", host + ":" + port);
            result.put("dbName", dbName);
            result.put("removed", removed);
            log.info("[dbBackup] 备份完成：{}（{}），耗时 {}ms，轮转删除 {} 个旧文件",
                    outFile.getAbsolutePath(), formatSize(outFile.length()), System.currentTimeMillis() - start, removed);
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

    @Override
    public List<DbBackupFile> listBackups()
    {
        List<DbBackupFile> list = new ArrayList<DbBackupFile>();
        File backupDir = backupDirOrNull();
        if (backupDir == null)
        {
            return list;
        }
        File[] files = backupDir.listFiles();
        if (files == null)
        {
            return list;
        }
        for (File f : files)
        {
            if (f.isFile() && f.getName().endsWith(FILE_EXT))
            {
                list.add(new DbBackupFile(f.getName(), f.length(), formatSize(f.length()), new Date(f.lastModified())));
            }
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

    @Override
    public File resolveBackupFile(String name)
    {
        if (StringUtils.isEmpty(name) || !name.matches("[A-Za-z0-9_.-]+") || !name.endsWith(FILE_EXT) || name.contains(".."))
        {
            return null;
        }
        File backupDir = backupDirOrNull();
        if (backupDir == null)
        {
            return null;
        }
        File file = new File(backupDir, name);
        if (!file.isFile())
        {
            return null;
        }
        try
        {
            // 防路径穿越：真实路径必须在备份目录内
            String dirPath = backupDir.getCanonicalPath();
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
        log.info("[dbBackup] 删除备份文件 {}：{}", name, ok ? "成功" : "失败");
        return ok;
    }

    @Override
    public String getBackupDir()
    {
        return dir;
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
        if (pgDumpVersionCache != null)
        {
            return pgDumpVersionCache;
        }
        synchronized (this)
        {
            if (pgDumpVersionCache != null)
            {
                return pgDumpVersionCache;
            }
            String version = "";
            String pgDump = resolvePgDump();
            if (StringUtils.isNotEmpty(pgDump))
            {
                try
                {
                    Process process = new ProcessBuilder(pgDump, "--version").redirectErrorStream(true).start();
                    if (process.waitFor(20, TimeUnit.SECONDS) && process.exitValue() == 0)
                    {
                        InputStream in = process.getInputStream();
                        byte[] buf = new byte[512];
                        int n = in.read(buf);
                        if (n > 0)
                        {
                            version = new String(buf, 0, n, StandardCharsets.UTF_8).trim();
                        }
                        in.close();
                    }
                }
                catch (Exception ex)
                {
                    log.warn("[dbBackup] 读取 pg_dump 版本失败：{}", ex.getMessage());
                }
            }
            pgDumpVersionCache = version;
            return version;
        }
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

    /**
     * 免安装（绿色）pg_dump：随应用一起放置的 pgtools 目录。
     * 只需 pg_dump.exe + libpq.dll + zlib.dll + libssl-3-x64.dll + libcrypto-3-x64.dll 五个文件（约 7MB），
     * 无需安装 PostgreSQL 客户端。
     */
    private String findBundled()
    {
        String exe = isWindows() ? "pg_dump.exe" : "pg_dump";
        for (File base : appBaseDirs())
        {
            for (String sub : new String[] { "pgtools", "tools/pgtools", "bin/pgtools" })
            {
                File f = new File(new File(base, sub), exe);
                if (f.isFile())
                {
                    log.info("[dbBackup] 使用免安装 pg_dump：{}", f.getAbsolutePath());
                    return f.getAbsolutePath();
                }
            }
        }
        return null;
    }

    /**
     * 应用基准目录候选（按优先级）：
     * ① 代码来源目录：jar 运行时 = <b>jar 所在目录</b>，开发期 = classes 目录
     * ② 代码来源目录的上级（开发期 classes → target）
     * ③ 进程工作目录 user.dir —— ⚠️ 以 Windows 服务/计划任务启动时它常是 C:/Windows/System32，
     *    所以不能只靠它，故排在最后
     */
    private List<File> appBaseDirs()
    {
        List<File> bases = new ArrayList<File>();
        try
        {
            File code = new File(DbBackupServiceImpl.class.getProtectionDomain().getCodeSource().getLocation().toURI());
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
        catch (Exception ignore)
        {
            // 解析不到代码位置时退化为仅用工作目录
        }
        String userDir = System.getProperty("user.dir");
        if (StringUtils.isNotEmpty(userDir))
        {
            bases.add(new File(userDir));
        }
        return bases;
    }

    /** 从 PATH 环境变量中查找 pg_dump */
    private String findInPath()
    {
        String pathEnv = System.getenv("PATH");
        if (StringUtils.isEmpty(pathEnv))
        {
            return null;
        }
        String exe = isWindows() ? "pg_dump.exe" : "pg_dump";
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
    private int rotateOldFiles(File backupDir)
    {
        if (keepDays <= 0)
        {
            return 0;
        }
        long deadline = System.currentTimeMillis() - keepDays * 24L * 3600L * 1000L;
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
