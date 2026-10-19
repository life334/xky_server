package com.xakcch.project.service;

/**
 * 项目预警（走铃铛，每日扫描一次）
 *
 * <p>三类规则，阈值与接收人全部走 sys_config（SQL 固化，不开放后台填写 SQL）：</p>
 * <ol>
 *   <li>欠款逾期 — 办结且欠款 ≥ 金额阈值、办结天数 ≥ 天数阈值</li>
 *   <li>工期超期 — 手动录入、未办结、越过「安排日期 + 工期要求(工作日)」</li>
 *   <li>未关联合同 — 手动录入、无合同、创建后超过 N 个工作日</li>
 * </ol>
 *
 * <p>幂等：同一项目同一规则对同一接收人只存在一行，每日重扫不重复推送。
 * 自动消解：今日不再命中的项目，其未处理预警置 done。</p>
 *
 * @author liuyonghui
 */
public interface IProjAlertService
{
    /**
     * 扫描并推送全部启用的预警规则（定时任务每日调用；手动触发亦可）
     *
     * @return 各规则「新增+刷新」的行数合计（自动消解的行不计入）
     */
    public int scanAllAlerts();
}
