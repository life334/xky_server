package com.xakcch.web.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import com.xakcch.project.service.IProjNotifyService;

/**
 * 通知中心调度配置
 *
 * 说明：
 * 1. 未使用 Quartz（qrtz% 表未初始化、yml 无配置），采用 Spring 原生 @Scheduled；
 * 2. ⚠️ 多实例部署下该任务会各跑一遍（幂等 UPDATE，无数据风险，仅重复执行），
 *    当前单实例（9997）部署无影响；未来多实例需加分布式锁或迁移 Quartz；
 * 3. 每日 08:00 全量 sweep：待办自动完成 + 超期升级为 high。
 *
 * @author liuyonghui
 */
@Configuration
@EnableScheduling
public class NotifyScheduleConfig
{
    private static final Logger log = LoggerFactory.getLogger(NotifyScheduleConfig.class);

    @Autowired
    private IProjNotifyService notifyService;

    /**
     * 每日 08:00 通知 sweep
     */
    @Scheduled(cron = "0 0 8 * * ?")
    public void dailyNotifySweep()
    {
        try
        {
            int n = notifyService.sweepAll();
            log.info("[notify] 每日sweep完成，影响行数 {}", n);
        }
        catch (Exception ex)
        {
            log.error("[notify] 每日sweep执行失败", ex);
        }
    }
}
