package com.xakcch.web.controller.system;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.xakcch.common.annotation.Log;
import com.xakcch.common.core.controller.BaseController;
import com.xakcch.common.core.domain.AjaxResult;
import com.xakcch.common.enums.BusinessType;
import com.xakcch.common.utils.SecurityUtils;
import com.xakcch.common.utils.file.FileUtils;
import com.xakcch.project.domain.vo.DbBackupFile;
import com.xakcch.project.service.IDbBackupService;

/**
 * 数据库备份（列表 / 立即备份 / 下载 / 删除）
 *
 * 权限说明：备份文件等同生产数据副本，⚠️ 仅超级管理员可操作。
 * 这里刻意不加 @PreAuthorize 权限码 —— 新权限码需同步配置菜单角色，
 * 否则所有业务角色都会 403（与工作日历接口同一教训），改为代码内 isAdmin 判断。
 *
 * @author liuyonghui
 */
@RestController
@RequestMapping("/system/dbBackup")
public class DbBackupController extends BaseController
{
    private static final Logger log = LoggerFactory.getLogger(DbBackupController.class);

    @Autowired
    private IDbBackupService dbBackupService;

    /**
     * 备份文件列表 + 环境状态
     */
    @GetMapping("/list")
    public AjaxResult list()
    {
        if (!SecurityUtils.isAdmin())
        {
            return error("仅超级管理员可查看数据库备份");
        }
        List<DbBackupFile> rows = dbBackupService.listBackups();
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("rows", rows);
        data.put("total", rows.size());
        data.put("dir", dbBackupService.getBackupDir());
        data.put("enabled", dbBackupService.isEnabled());
        // 异地（灾备）目录：留空表示未启用，页面据此决定是否展示"异地"列与告警
        String secondaryDir = dbBackupService.getSecondaryDir();
        boolean secondaryEnabled = secondaryDir != null && !secondaryDir.trim().isEmpty();
        data.put("secondaryDir", secondaryEnabled ? secondaryDir : "");
        data.put("secondaryEnabled", secondaryEnabled);
        // 异地目录当前能不能访问（不可达=灾备机宕机/网络断/共享权限不足）—— 页面据此告警
        data.put("secondaryReachable", secondaryEnabled && dbBackupService.isSecondaryReachable());
        String pgDump = dbBackupService.resolvePgDump();
        data.put("pgDump", pgDump == null ? "" : pgDump);
        data.put("pgDumpReady", pgDump != null);
        data.put("pgDumpVersion", dbBackupService.pgDumpVersion());
        // 恢复工具（只用于页面展示"能否恢复"并生成恢复命令，系统本身不执行恢复）
        String pgRestore = dbBackupService.resolvePgRestore();
        data.put("pgRestore", pgRestore == null ? "" : pgRestore);
        data.put("pgRestoreReady", pgRestore != null);
        data.put("pgRestoreVersion", dbBackupService.pgRestoreVersion());
        // 连接信息（不含密码）—— 让页面生成的恢复命令带真实地址，而不是硬编码
        Map<String, String> conn = dbBackupService.connectionInfo();
        data.put("host", conn.getOrDefault("host", ""));
        data.put("port", conn.getOrDefault("port", ""));
        data.put("dbName", conn.getOrDefault("dbName", ""));
        data.put("user", conn.getOrDefault("user", ""));
        data.put("serverVersion", dbBackupService.serverVersion());
        return success(data);
    }

    /**
     * 立即执行一次备份（同步，大库可能耗时较久）
     */
    @Log(title = "数据库备份", businessType = BusinessType.INSERT)
    @PostMapping("/run")
    public AjaxResult run()
    {
        if (!SecurityUtils.isAdmin())
        {
            return error("仅超级管理员可执行数据库备份");
        }
        try
        {
            return success(dbBackupService.runBackup());
        }
        catch (Exception ex)
        {
            log.error("[dbBackup] 手动备份失败", ex);
            return error(ex.getMessage());
        }
    }

    /**
     * 下载备份文件
     *
     * <p>⚠️ 必须同时支持 GET 与 POST：前端统一走 {@code proxy.download()}（RuoYi 全局方法固定用
     * POST + form-urlencoded 传参），只写 {@code @GetMapping} 会直接 405。保留 GET 是为了
     * 浏览器直接开链接下载也能用。</p>
     */
    @RequestMapping(value = "/download", method = { RequestMethod.GET, RequestMethod.POST })
    public void download(String name, HttpServletResponse response)
    {
        if (!SecurityUtils.isAdmin())
        {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        File file = dbBackupService.resolveBackupFile(name);
        if (file == null)
        {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        try
        {
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            FileUtils.setAttachmentResponseHeader(response, file.getName());
            FileUtils.writeBytes(file.getAbsolutePath(), response.getOutputStream());
        }
        catch (Exception ex)
        {
            log.error("[dbBackup] 下载备份文件失败：" + name, ex);
        }
    }

    /**
     * 删除备份文件
     */
    @Log(title = "数据库备份", businessType = BusinessType.DELETE)
    @DeleteMapping("/remove")
    public AjaxResult remove(String name)
    {
        if (!SecurityUtils.isAdmin())
        {
            return error("仅超级管理员可删除数据库备份");
        }
        return toAjax(dbBackupService.deleteBackup(name) ? 1 : 0);
    }

    /* ------------------------------------------------------------------ */
    /*                        一键恢复（pg_restore）                        */
    /* ------------------------------------------------------------------ */

    /**
     * 数据库服务器上的库列表（选择恢复目标 / 管理历史恢复库）
     */
    @GetMapping("/databases")
    public AjaxResult databases()
    {
        if (!SecurityUtils.isAdmin())
        {
            return error("仅超级管理员可执行数据库恢复");
        }
        try
        {
            Map<String, Object> data = new HashMap<String, Object>();
            data.put("rows", dbBackupService.listDatabases());
            data.put("currentDb", dbBackupService.connectionInfo().getOrDefault("dbName", ""));
            return success(data);
        }
        catch (Exception ex)
        {
            log.error("[dbRestore] 读取数据库列表失败", ex);
            return error(ex.getMessage());
        }
    }

    /**
     * 检查目标库能否作为恢复目标（不存在 / 空库 可以；已有业务表一律拒绝）
     */
    @GetMapping("/inspectDb")
    public AjaxResult inspectDb(String db)
    {
        if (!SecurityUtils.isAdmin())
        {
            return error("仅超级管理员可执行数据库恢复");
        }
        try
        {
            return success(dbBackupService.inspectDatabase(db));
        }
        catch (Exception ex)
        {
            return error(ex.getMessage());
        }
    }

    /**
     * 启动恢复（异步执行，立即返回；前端轮询 /restoreStatus 看进度）
     */
    @Log(title = "数据库恢复", businessType = BusinessType.UPDATE)
    @PostMapping("/restore")
    public AjaxResult restore(@RequestBody Map<String, String> body)
    {
        if (!SecurityUtils.isAdmin())
        {
            return error("仅超级管理员可执行数据库恢复");
        }
        try
        {
            String name = body == null ? null : body.get("name");
            String targetDb = body == null ? null : body.get("targetDb");
            return success(dbBackupService.startRestore(name, targetDb));
        }
        catch (Exception ex)
        {
            log.error("[dbRestore] 启动恢复失败", ex);
            return error(ex.getMessage());
        }
    }

    /**
     * 恢复任务状态（轮询）
     */
    @GetMapping("/restoreStatus")
    public AjaxResult restoreStatus()
    {
        if (!SecurityUtils.isAdmin())
        {
            return error("仅超级管理员可查看数据库恢复状态");
        }
        return success(dbBackupService.restoreStatus());
    }

    /**
     * 删除历史恢复库（仅 xky_project_r_ 前缀，现库永不可删）
     */
    @Log(title = "数据库恢复", businessType = BusinessType.DELETE)
    @DeleteMapping("/dropDb")
    public AjaxResult dropDb(String name)
    {
        if (!SecurityUtils.isAdmin())
        {
            return error("仅超级管理员可删除恢复库");
        }
        try
        {
            return toAjax(dbBackupService.dropDatabase(name) ? 1 : 0);
        }
        catch (Exception ex)
        {
            log.error("[dbRestore] 删除恢复库失败：" + name, ex);
            return error(ex.getMessage());
        }
    }
}
