package com.xakcch.web.core;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import com.xakcch.project.service.IDbBackupService;

/**
 * 数据库定时备份调度
 *
 * 说明：
 * 1. 与通知 sweep 一致，采用 Spring 原生 @Scheduled（Quartz 未启用）；
 * 2. ⚠️ 多实例部署时该任务会在每个实例各跑一次（各写各的文件，无数据风险，仅冗余）；
 *    当前单实例部署无影响，未来多实例需加分布式锁或迁移 Quartz；
 * 3. 备份依赖服务器上的 pg_dump；未安装/未配置时仅记录告警日志并跳过，不影响应用运行。
 *
 * @author liuyonghui
 */
@Configuration
public class DbBackupScheduleConfig
{
    private static final Logger log = LoggerFactory.getLogger(DbBackupScheduleConfig.class);

    @Autowired
    private IDbBackupService dbBackupService;

    /**
     * 每日 23:00 数据库备份（可通过 xky-project.backup.cron 调整）
     */
    @Scheduled(cron = "${xky-project.backup.cron:0 0 23 * * ?}")
    public void dailyDbBackup()
    {
        if (!dbBackupService.isEnabled())
        {
            log.info("[dbBackup] 定时备份已关闭（xky-project.backup.enabled=false），跳过");
            return;
        }
        try
        {
            Map<String, Object> r = dbBackupService.runBackup();
            log.info("[dbBackup] 定时备份完成：{}（{}）", r.get("file"), r.get("sizeText"));
        }
        catch (Exception ex)
        {
            log.error("[dbBackup] 定时备份失败：" + ex.getMessage(), ex);
        }
    }
}
