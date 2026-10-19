package com.xakcch.web.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import com.xakcch.project.service.IProjAlertService;
import com.xakcch.project.service.IProjNotifyService;

/**
 * 通知中心调度配置
 *
 * 说明：
 * 1. 未使用 Quartz（qrtz% 表未初始化、yml 无配置），采用 Spring 原生 @Scheduled；
 * 2. ⚠️ 多实例部署下该任务会各跑一遍（幂等 UPDATE，无数据风险，仅重复执行），
 *    当前单实例（9997）部署无影响；未来多实例需加分布式锁或迁移 Quartz；
 * 3. 每 5 分钟全量 sweep：待办自动完成 + 超期升级/到期降级。
 * 4. 每日 08:30 预警扫描：欠款逾期 / 工期超期 / 未关联合同，推送到铃铛。
 *
 * ⚠️ 为什么需要高频 sweep：
 *    原实现把 sweepUser() 塞在 summary/list/increment 三个 <b>GET</b> 接口里，
 *    而 summary 被前端 30s 轮询 —— 每 30 秒 4 条 UPDATE，且 GET 带写副作用属语义错误。
 *    现已从这三个接口剥离（list 保留即时 sweep，因其低频且用户可感知），
 *    改由本任务兜底：即使不打开通知面板，待办也会在 5 分钟内自动消/自动升级。
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

    @Autowired
    private IProjAlertService alertService;

    /**
     * 每 5 分钟全量 sweep（替代此前"查询接口内即时 sweep"的写法）
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void periodicNotifySweep()
    {
        try
        {
            int n = notifyService.sweepAll();
            if (n > 0)
            {
                log.info("[notify] 周期sweep完成，影响行数 {}", n);
            }
        }
        catch (Exception ex)
        {
            log.error("[notify] 周期sweep执行失败", ex);
        }
    }

    /**
     * 每日 08:30 预警扫描（上班后第一时间看到昨夜累积的风险）。
     *
     * <p>幂等：同一项目同一规则对同一接收人只存在一行，重复执行只刷新文案，
     * 不会堆重复通知 ⇒ 手动多次触发也是安全的。</p>
     */
    @Scheduled(cron = "0 30 8 * * ?")
    public void dailyAlertScan()
    {
        try
        {
            int n = alertService.scanAllAlerts();
            log.info("[alert] 每日预警扫描完成，写入/刷新 {} 条", n);
        }
        catch (Exception ex)
        {
            log.error("[alert] 每日预警扫描失败", ex);
        }
    }
}
