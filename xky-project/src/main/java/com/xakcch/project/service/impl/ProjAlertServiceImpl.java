package com.xakcch.project.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.xakcch.common.utils.StringUtils;
import com.xakcch.common.utils.WorkdayUtils;
import com.xakcch.project.domain.ProjNotify;
import com.xakcch.project.mapper.ProjAlertMapper;
import com.xakcch.project.service.IProjAlertService;
import com.xakcch.system.service.ISysConfigService;
import com.xakcch.system.service.ISysWorkdayCalendarService;

/**
 * 项目预警扫描与推送
 *
 * <p>核心循环：对每条规则 —— 查候选 → 判定是否真命中 → 对命中项目向接收人 upsert → 消解未命中项。</p>
 *
 * <p>幂等依赖 uk_proj_notify_biz(biz_type, biz_id, receiver_id) where del_flag='0'，
 * 所以「重复扫描」是安全的：已存在的行只刷新文案，不会堆重复通知。</p>
 *
 * @author liuyonghui
 */
@Service
public class ProjAlertServiceImpl implements IProjAlertService
{
    private static final Logger log = LoggerFactory.getLogger(ProjAlertServiceImpl.class);

    // ---------------- sys_config key ----------------
    private static final String C_ENABLED = "notify.alert.enabled";
    /**
     * 规则级开关。默认值来自客户明确要求（2026-10-27）：<b>只保留「工期超期」</b>，
     * 欠款逾期与未关联合同不再扫描 —— 这两类规则在 dev 库一次能命中上千个项目，
     * 给每个接收人堆出 300+ 条雷同预警，铃铛常年挂红、实际无人处理，反而把真正的超期项淹没了。
     * 开关留在 sys_config 里而不是删代码：以后口径想恢复，改配置即可，不用重新发版。
     */
    private static final String C_DEBT_ENABLED = "notify.alert.debt.enabled";
    private static final String C_OVERDUE_ENABLED = "notify.alert.overdue.enabled";
    private static final String C_CM_ENABLED = "notify.alert.contract_missing.enabled";
    private static final String C_DEBT_ROLES = "notify.alert.debt.roles";
    private static final String C_DEBT_USERS = "notify.alert.debt.users";
    private static final String C_DEBT_MIN_AMOUNT = "notify.alert.debt.min_amount";
    private static final String C_DEBT_MIN_DAYS = "notify.alert.debt.min_days";
    private static final String C_OVERDUE_ROLES = "notify.alert.overdue.roles";
    private static final String C_OVERDUE_USERS = "notify.alert.overdue.users";
    private static final String C_CM_ROLES = "notify.alert.contract_missing.roles";
    private static final String C_CM_USERS = "notify.alert.contract_missing.users";
    private static final String C_CM_WORKDAYS = "notify.alert.contract_missing.workdays";

    // ---------------- biz_type ----------------
    private static final String BIZ_DEBT = "alert_debt";
    private static final String BIZ_OVERDUE = "alert_overdue";
    private static final String BIZ_CONTRACT_MISSING = "alert_contract_missing";

    @Autowired
    private ProjAlertMapper alertMapper;

    @Autowired
    private ISysConfigService configService;

    @Autowired
    private ISysWorkdayCalendarService workdayCalendarService;

    @Override
    public int scanAllAlerts()
    {
        if (!"true".equalsIgnoreCase(StringUtils.defaultString(cfg(C_ENABLED), "true").trim()))
        {
            log.info("[alert] 预警总开关关闭，跳过本次扫描");
            return 0;
        }
        LocalDate today = LocalDate.now();
        int total = 0;
        // 规则级开关：停用的规则**既不写新预警、也不消解历史行**（历史垃圾数据由 35 号脚本一次性清理），
        // 避免「关了开关却把库里旧行改成 done，将来重新打开时状态对不上」。
        total += scanDebt(today);
        total += scanOverdue(today);
        total += scanContractMissing(today);
        log.info("[alert] 每日扫描完成，写入/刷新 {} 条", total);
        return total;
    }

    /** 规则开关：配置缺失时用默认值（仅工期超期默认开启） */
    private boolean ruleEnabled(String key, boolean dft)
    {
        String v = cfg(key);
        if (StringUtils.isEmpty(v))
        {
            return dft;
        }
        return "true".equalsIgnoreCase(v.trim());
    }

    // ==================== 规则一：欠款逾期 ====================
    private int scanDebt(LocalDate today)
    {
        if (!ruleEnabled(C_DEBT_ENABLED, false))
        {
            log.info("[alert] 欠款逾期规则已停用（{} = false），跳过", C_DEBT_ENABLED);
            return 0;
        }
        BigDecimal minAmount = new BigDecimal(StringUtils.defaultString(cfg(C_DEBT_MIN_AMOUNT), "10000").trim());
        int minDays = intCfg(C_DEBT_MIN_DAYS, 90);
        List<Map<String, Object>> rows = alertMapper.selectDebtCandidates(minAmount, minDays);
        List<Long> hitIds = new ArrayList<>();
        List<AlertRow> hits = new ArrayList<>();
        for (Map<String, Object> r : rows)
        {
            Long pid = toLong(r.get("projectId"));
            if (pid == null)
            {
                continue;
            }
            hitIds.add(pid);
            BigDecimal amount = toBig(r.get("amount"));
            int days = toInt(r.get("days"));
            hits.add(new AlertRow(pid, str(r.get("projectCode")), str(r.get("projectName")),
                    "项目存在应收欠款",
                    "项目「" + str(r.get("projectName")) + "」（" + str(r.get("projectCode")) + "）已办结 "
                            + days + " 天，仍有应收欠款 ¥" + amount.toPlainString() + " 元，请跟进回款。",
                    // 回款管理是顶级菜单（sys_menu id=9, path='collection'），路由为 /collection
                    "/collection",
                    // 严重度与首页风险区一致：>180天 或 >10万 → high；>90天 → medium
                    (days > 180 || amount.compareTo(new BigDecimal("100000")) > 0) ? "high"
                            : days > 90 ? "medium" : "normal"));
        }
        int n = dispatch(BIZ_DEBT, hits, C_DEBT_ROLES, C_DEBT_USERS);
        int resolved = alertMapper.resolveAlerts(BIZ_DEBT, hitIds);
        log.info("[alert] 欠款逾期：命中 {} 个项目，写入/刷新 {} 条，自动消解 {} 条",
                hitIds.size(), n, resolved);
        return n;
    }

    // ==================== 规则二：工期超期 ====================
    private int scanOverdue(LocalDate today)
    {
        if (!ruleEnabled(C_OVERDUE_ENABLED, true))
        {
            log.info("[alert] 工期超期规则已停用（{} = false），跳过", C_OVERDUE_ENABLED);
            return 0;
        }
        List<Map<String, Object>> rows = alertMapper.selectOverdueCandidates();
        if (rows == null || rows.isEmpty())
        {
            int resolved = alertMapper.resolveAlerts(BIZ_OVERDUE, Collections.emptyList());
            log.info("[alert] 工期超期：无候选，自动消解 {} 条", resolved);
            return 0;
        }
        // 工作日历：从最早的安排日期加载到今天（一次性，避免逐行查）
        LocalDate min = today;
        for (Map<String, Object> r : rows)
        {
            LocalDate d = toLD(r.get("assignDate"));
            if (d != null && d.isBefore(min))
            {
                min = d;
            }
        }
        Map<LocalDate, String> cal = workdayCalendarService.getCalendarMap(min, today);

        List<Long> hitIds = new ArrayList<>();
        List<AlertRow> hits = new ArrayList<>();
        for (Map<String, Object> r : rows)
        {
            Long pid = toLong(r.get("projectId"));
            LocalDate assign = toLD(r.get("assignDate"));
            Integer dr = toIntObj(r.get("durationRequire"));
            if (pid == null || assign == null || dr == null || dr <= 0)
            {
                continue;
            }
            LocalDate deadline = WorkdayUtils.addWorkdays(assign, dr, cal);
            if (!today.isAfter(deadline))
            {
                continue;   // 尚未越过应完成日
            }
            int overdueDays = Math.max(WorkdayUtils.countWorkdays(deadline, today, cal) - 1, 0);
            hitIds.add(pid);
            hits.add(new AlertRow(pid, str(r.get("projectCode")), str(r.get("projectName")),
                    "项目工期超期",
                    "项目「" + str(r.get("projectName")) + "」（" + str(r.get("projectCode"))
                            + "）应完成日为 " + deadline + "，已超期 " + overdueDays + " 个工作日，请尽快办结。",
                    "/project/list",
                    overdueDays > 7 ? "high" : "medium"));
        }
        int n = dispatch(BIZ_OVERDUE, hits, C_OVERDUE_ROLES, C_OVERDUE_USERS);
        int resolved = alertMapper.resolveAlerts(BIZ_OVERDUE, hitIds);
        log.info("[alert] 工期超期：命中 {} 个项目，写入/刷新 {} 条，自动消解 {} 条",
                hitIds.size(), n, resolved);
        return n;
    }

    // ==================== 规则三：未关联合同 ====================
    private int scanContractMissing(LocalDate today)
    {
        if (!ruleEnabled(C_CM_ENABLED, false))
        {
            log.info("[alert] 未关联合同规则已停用（{} = false），跳过", C_CM_ENABLED);
            return 0;
        }
        int threshold = intCfg(C_CM_WORKDAYS, 3);
        List<Map<String, Object>> rows = alertMapper.selectContractMissingCandidates();
        if (rows == null || rows.isEmpty())
        {
            int resolved = alertMapper.resolveAlerts(BIZ_CONTRACT_MISSING, Collections.emptyList());
            log.info("[alert] 未关联合同：无候选，自动消解 {} 条", resolved);
            return 0;
        }
        LocalDate min = today;
        for (Map<String, Object> r : rows)
        {
            LocalDate d = toLD(r.get("createTime"));
            if (d != null && d.isBefore(min))
            {
                min = d;
            }
        }
        Map<LocalDate, String> cal = workdayCalendarService.getCalendarMap(min, today);

        List<Long> hitIds = new ArrayList<>();
        List<AlertRow> hits = new ArrayList<>();
        for (Map<String, Object> r : rows)
        {
            Long pid = toLong(r.get("projectId"));
            LocalDate created = toLD(r.get("createTime"));
            if (pid == null || created == null)
            {
                continue;
            }
            LocalDate deadline = WorkdayUtils.addWorkdays(created, threshold, cal);
            if (!today.isAfter(deadline))
            {
                continue;
            }
            int days = Math.max(WorkdayUtils.countWorkdays(deadline, today, cal) - 1, 0);
            hitIds.add(pid);
            hits.add(new AlertRow(pid, str(r.get("projectCode")), str(r.get("projectName")),
                    "项目未关联合同",
                    "项目「" + str(r.get("projectName")) + "」（" + str(r.get("projectCode"))
                            + "）已录入 " + days + " 个工作日，尚未关联合同，请尽快补录。",
                    "/project/list",
                    days > 7 ? "high" : days > 4 ? "medium" : "normal"));
        }
        int n = dispatch(BIZ_CONTRACT_MISSING, hits, C_CM_ROLES, C_CM_USERS);
        int resolved = alertMapper.resolveAlerts(BIZ_CONTRACT_MISSING, hitIds);
        log.info("[alert] 未关联合同：命中 {} 个项目，写入/刷新 {} 条，自动消解 {} 条",
                hitIds.size(), n, resolved);
        return n;
    }

    // ==================== 通用：接收人解析 + 派发 ====================
    /**
     * 向接收人派发预警。
     *
     * @param rolesKey 接收角色配置 key
     * @param usersKey 接收人配置 key（配置了则覆盖角色）
     */
    private int dispatch(String bizType, List<AlertRow> hits, String rolesKey, String usersKey)
    {
        if (hits == null || hits.isEmpty())
        {
            return 0;
        }
        List<Map<String, Object>> receivers = resolveReceivers(rolesKey, usersKey);
        if (receivers.isEmpty())
        {
            log.warn("[alert] {} 跳过：未解析到任何有效接收人（请检查 {} / {} 配置）", bizType, rolesKey, usersKey);
            return 0;
        }
        int sent = 0;
        for (AlertRow h : hits)
        {
            String routeQuery = "projectCode=" + h.code + "&from=notify";
            for (Map<String, Object> rc : receivers)
            {
                Long userId = toLong(rc.get("userId"));
                if (userId == null)
                {
                    continue;
                }
                ProjNotify n = new ProjNotify();
                n.setBizType(bizType);
                n.setBizId(h.projectId);
                n.setProjectId(h.projectId);
                n.setProjectCode(h.code);
                n.setProjectName(h.name);
                n.setTitle(h.title);
                n.setContent(h.content);
                n.setReceiverId(userId);
                n.setReceiverRoleId(toLong(rc.get("roleId")));
                n.setRoutePath(h.routePath);
                n.setRouteQuery(routeQuery);
                n.setPriority(h.priority);
                n.setStatus("unread");
                n.setCreateBy("system");
                try
                {
                    sent += alertMapper.insertAlert(n);
                }
                catch (Exception ex)
                {
                    // 单行失败不能中断整批扫描（如某行被并发删除）
                    log.error("[alert] 写入失败 bizType={} projectId={} userId={}：{}",
                            bizType, h.projectId, userId, ex.getMessage());
                }
            }
        }
        return sent;
    }

    /**
     * 接收人解析：优先具体人（users 配置非空时覆盖角色），否则按角色群发。
     */
    private List<Map<String, Object>> resolveReceivers(String rolesKey, String usersKey)
    {
        String usersCfg = StringUtils.defaultString(cfg(usersKey), "").trim();
        if (!usersCfg.isEmpty())
        {
            List<Long> uids = Arrays.stream(usersCfg.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .map(s -> {
                        try { return Long.parseLong(s); } catch (Exception e) { return null; }
                    })
                    .filter(java.util.Objects::nonNull).distinct().toList();
            if (!uids.isEmpty())
            {
                List<Map<String, Object>> rs = alertMapper.selectReceiversByUserIds(uids);
                return rs == null ? Collections.emptyList() : rs;
            }
        }
        String rolesCfg = StringUtils.defaultString(cfg(rolesKey), "").trim();
        if (rolesCfg.isEmpty())
        {
            return Collections.emptyList();
        }
        List<String> roleKeys = Arrays.stream(rolesCfg.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
        if (roleKeys.isEmpty())
        {
            return Collections.emptyList();
        }
        List<Map<String, Object>> rs = alertMapper.selectReceiversByRoleKeys(roleKeys);
        return rs == null ? Collections.emptyList() : rs;
    }

    // ==================== 工具 ====================
    /** 单条命中行（与 ProjNotify 解耦，便于组装文案） */
    private static class AlertRow
    {
        Long projectId;
        String code;
        String name;
        String title;
        String content;
        String routePath;
        String priority;

        AlertRow(Long projectId, String code, String name, String title, String content,
                 String routePath, String priority)
        {
            this.projectId = projectId;
            this.code = code;
            this.name = name;
            this.title = title;
            this.content = content;
            this.routePath = routePath;
            this.priority = priority;
        }
    }

    private String cfg(String key)
    {
        try
        {
            return configService.selectConfigByKey(key);
        }
        catch (Exception ex)
        {
            return null;
        }
    }

    private int intCfg(String key, int dft)
    {
        try
        {
            String v = cfg(key);
            return StringUtils.isEmpty(v) ? dft : Integer.parseInt(v.trim());
        }
        catch (Exception ex)
        {
            return dft;
        }
    }

    private String str(Object o)
    {
        return o == null ? "" : String.valueOf(o);
    }

    private Long toLong(Object o)
    {
        if (o == null)
        {
            return null;
        }
        if (o instanceof Number)
        {
            return ((Number) o).longValue();
        }
        try
        {
            return Long.parseLong(o.toString());
        }
        catch (Exception ex)
        {
            return null;
        }
    }

    private int toInt(Object o)
    {
        Integer v = toIntObj(o);
        return v == null ? 0 : v;
    }

    private Integer toIntObj(Object o)
    {
        if (o == null)
        {
            return null;
        }
        if (o instanceof Number)
        {
            return ((Number) o).intValue();
        }
        try
        {
            return Integer.parseInt(o.toString());
        }
        catch (Exception ex)
        {
            return null;
        }
    }

    private BigDecimal toBig(Object o)
    {
        if (o == null)
        {
            return BigDecimal.ZERO;
        }
        if (o instanceof BigDecimal)
        {
            return (BigDecimal) o;
        }
        try
        {
            return new BigDecimal(o.toString());
        }
        catch (Exception ex)
        {
            return BigDecimal.ZERO;
        }
    }

    private LocalDate toLD(Object o)
    {
        if (o == null)
        {
            return null;
        }
        if (o instanceof LocalDate)
        {
            return (LocalDate) o;
        }
        if (o instanceof java.sql.Date)
        {
            return ((java.sql.Date) o).toLocalDate();
        }
        if (o instanceof java.util.Date)
        {
            return new java.sql.Date(((java.util.Date) o).getTime()).toLocalDate();
        }
        try
        {
            return LocalDate.parse(o.toString().substring(0, Math.min(10, o.toString().length())));
        }
        catch (Exception ex)
        {
            return null;
        }
    }
}
