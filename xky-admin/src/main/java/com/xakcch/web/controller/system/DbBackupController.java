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
import org.springframework.web.bind.annotation.RequestMapping;
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
        String pgDump = dbBackupService.resolvePgDump();
        data.put("pgDump", pgDump == null ? "" : pgDump);
        data.put("pgDumpReady", pgDump != null);
        data.put("pgDumpVersion", dbBackupService.pgDumpVersion());
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
     */
    @GetMapping("/download")
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
}
