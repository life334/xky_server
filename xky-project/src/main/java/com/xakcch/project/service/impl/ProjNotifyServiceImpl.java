package com.xakcch.project.service.impl;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.xakcch.common.exception.ServiceException;
import com.xakcch.common.utils.StringUtils;
import com.xakcch.project.domain.ProjContract;
import com.xakcch.project.domain.ProjNotify;
import com.xakcch.project.domain.ProjProject;
import com.xakcch.project.domain.vo.NotifySummaryVo;
import com.xakcch.project.mapper.ProjContractMapper;
import com.xakcch.project.mapper.ProjNotifyMapper;
import com.xakcch.project.mapper.ProjProjectMapper;
import com.xakcch.project.service.IProjNotifyService;
import com.xakcch.system.service.ISysConfigService;

/**
 * 站内待办与消息 服务实现
 *
 * @author liuyonghui
 */
@Service
public class ProjNotifyServiceImpl implements IProjNotifyService
{
    private static final Logger log = LoggerFactory.getLogger(ProjNotifyServiceImpl.class);

    /** 办结事件接收角色（sys_config 可配） */
    private static final String CONFIG_RECEIVERS = "notify.receivers.project_closed";
    private static final String CONFIG_POPUP_ENABLED = "notify.popup.enabled";
    private static final String CONFIG_POPUP_START = "notify.popup.work.start";
    private static final String CONFIG_POPUP_END = "notify.popup.work.end";
    private static final String CONFIG_POPUP_WORKDAY = "notify.popup.workday.only";
    private static final String CONFIG_POPUP_DURATION = "notify.popup.duration.seconds";
    private static final String CONFIG_POPUP_THROTTLE = "notify.popup.throttle.minutes";
    private static final String CONFIG_TODO_DUE = "notify.todo.due.days";

    /** role_key → 待办业务种类；不在表内的角色收消息 */
    private static final Map<String, String> TODO_KIND_BY_ROLE = Map.of(
            "role-doc", "doc",
            "role-product", "settle");

    /** 档案管理员角色 key（合同待签署待办接收方） */
    private static final String ROLE_DOC = "role-doc";

    /** 合同新增事件接收角色（sys_config 可配，默认 role-doc） */
    private static final String CONFIG_RECEIVERS_CONTRACT = "notify.receivers.contract_created";

    @Autowired
    private ProjNotifyMapper notifyMapper;

    @Autowired
    private ProjProjectMapper projectMapper;

    @Autowired
    private ProjContractMapper contractMapper;

    @Autowired
    private ISysConfigService configService;

    /**
     * 项目办结事件派发（在事务提交后调用）。
     * role-doc → 待办（登记资料，todo_kind=doc，落地 /material）
     * role-product → 待办（录入结算，todo_kind=settle，落地 /settlement）
     * 其余配置内角色 → 消息（落地 /project/list）
     */
    @Override
    public void onProjectClosed(Long projectId, String operator)
    {
        ProjProject project = projectMapper.selectProjectById(projectId);
        if (project == null)
        {
            log.warn("[notify] 办结派发跳过：项目不存在 id={}", projectId);
            return;
        }
        String roleCfg = configService.selectConfigByKey(CONFIG_RECEIVERS);
        if (StringUtils.isEmpty(roleCfg))
        {
            roleCfg = "role-doc,role-product,role-admin,role-qua";
        }
        List<String> roleKeys = Arrays.stream(roleCfg.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
        if (roleKeys.isEmpty())
        {
            return;
        }
        List<Map<String, Object>> receivers = notifyMapper.selectReceiversByRoleKeys(roleKeys);
        if (receivers == null || receivers.isEmpty())
        {
            log.info("[notify] 办结派发跳过：角色 {} 下无有效用户", roleKeys);
            return;
        }
        String code = StringUtils.defaultString(project.getProjectCode(), String.valueOf(projectId));
        String name = StringUtils.defaultString(project.getProjectName(), code);
        String routeQuery = "projectCode=" + code + "&from=notify";
        // due_time 在派发时一次性写入（超期判定的唯一口径）；循环内复用，避免逐条读配置
        Date dueTime = buildDueTime();
        int sent = 0;
        for (Map<String, Object> rc : receivers)
        {
            Long userId = toLong(rc.get("userId"));
            Long roleId = toLong(rc.get("roleId"));
            String roleKey = (String) rc.get("roleKey");
            if (userId == null)
            {
                continue;
            }
            ProjNotify n = new ProjNotify();
            n.setBizType("project_closed");
            n.setBizId(projectId);
            n.setProjectId(projectId);
            n.setProjectCode(code);
            n.setProjectName(name);
            n.setReceiverId(userId);
            n.setReceiverRoleId(roleId);
            n.setCreateBy(operator);
            n.setStatus("unread");
            n.setPriority("normal");
            String todoKind = TODO_KIND_BY_ROLE.get(roleKey);
            if (todoKind != null)
            {
                n.setNotifyType("todo");
                n.setTodoKind(todoKind);
                n.setDueTime(dueTime);
                if ("doc".equals(todoKind))
                {
                    n.setTitle("登记项目资料");
                    n.setContent("项目「" + name + "」（" + code + "）已办结，请在资料管理页登记项目资料并跟进归档。");
                    n.setRoutePath("/material");
                }
                else
                {
                    n.setTitle("录入费用结算");
                    n.setContent("项目「" + name + "」（" + code + "）已办结，请在费用结算页录入工作量与产值。");
                    n.setRoutePath("/settlement");
                }
            }
            else
            {
                n.setNotifyType("message");
                n.setTitle("项目已办结");
                n.setContent("项目「" + name + "」（" + code + "）已办结。");
                n.setRoutePath("/project/list");
            }
            n.setRouteQuery(routeQuery);
            sent += notifyMapper.insertNotify(n);
        }
        if (sent > 0)
        {
            log.info("[notify] 项目办结通知已派发 {} 条：{}（{}）", sent, name, code);
        }
    }

    /**
     * 合同新增事件派发（事务提交后调用）。
     * 非档案管理员创建合同 → 给档案管理员（role-doc）派发「合同待签署」待办，落地 /contract/list。
     * 操作人本人即档案管理员时跳过；合同导入不经过本入口，天然不推送。
     */
    @Override
    public void onContractCreated(Long contractId, String operator, Long operatorUserId)
    {
        if (contractId == null)
        {
            return;
        }
        // 操作人自身含 role-doc ⇒ 本人就是档案管理员，无需提醒自己
        if (operatorUserId != null)
        {
            List<String> opRoles = notifyMapper.selectRoleKeysByUserId(operatorUserId);
            if (opRoles != null && opRoles.contains(ROLE_DOC))
            {
                return;
            }
        }
        ProjContract contract = contractMapper.selectContractById(contractId);
        if (contract == null)
        {
            log.warn("[notify] 合同新增派发跳过：合同不存在 id={}", contractId);
            return;
        }
        String roleCfg = configService.selectConfigByKey(CONFIG_RECEIVERS_CONTRACT);
        if (StringUtils.isEmpty(roleCfg))
        {
            roleCfg = ROLE_DOC;
        }
        List<String> roleKeys = Arrays.stream(roleCfg.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
        if (roleKeys.isEmpty())
        {
            return;
        }
        List<Map<String, Object>> receivers = notifyMapper.selectReceiversByRoleKeys(roleKeys);
        if (receivers == null || receivers.isEmpty())
        {
            log.info("[notify] 合同新增派发跳过：角色 {} 下无有效用户", roleKeys);
            return;
        }
        String no = StringUtils.defaultString(contract.getContractNo(), "");
        String name = StringUtils.defaultString(contract.getContractName(),
                no.isEmpty() ? ("合同#" + contractId) : no);
        String creator = StringUtils.defaultString(operator, "系统");
        // 冗余列复用：project_code=合同编号、project_name=合同名称 ⇒ 通知卡片副标题自动显示「合同名称（编号）」
        String routeQuery = (no.isEmpty() ? ("contractId=" + contractId) : ("contractNo=" + no)) + "&from=notify";
        Date dueTime = buildDueTime();
        int sent = 0;
        for (Map<String, Object> rc : receivers)
        {
            Long userId = toLong(rc.get("userId"));
            Long roleId = toLong(rc.get("roleId"));
            if (userId == null)
            {
                continue;
            }
            ProjNotify n = new ProjNotify();
            n.setNotifyType("todo");
            n.setTodoKind("contract");
            n.setDueTime(dueTime);
            n.setBizType("contract_created");
            n.setBizId(contractId);
            n.setProjectCode(no.isEmpty() ? null : no);
            n.setProjectName(name);
            n.setTitle("合同待签署");
            n.setContent("「" + creator + "」新增了合同「" + name + "」"
                    + (no.isEmpty() ? "" : "（" + no + "）") + "，请进行签署并上传签署件。");
            n.setReceiverId(userId);
            n.setReceiverRoleId(roleId);
            n.setRoutePath("/contract/list");
            n.setRouteQuery(routeQuery);
            n.setPriority("normal");
            n.setStatus("unread");
            n.setCreateBy(creator);
            sent += notifyMapper.insertNotify(n);
        }
        if (sent > 0)
        {
            log.info("[notify] 合同新增通知已派发 {} 条：{}（{}）", sent, name, no);
        }
    }

    /**
     * 角标摘要。
     *
     * ⚠️ 此处<b>不做</b> sweep：前端每 30s 轮询一次，GET 里跑 4 条 UPDATE 既是语义错误
     * （只读接口带写副作用），也会在多人在线/多标签页时放大为无谓的行锁竞争。
     * 待办的「做完立刻消」由两处保障：① 用户主动打开面板时 list() 即时 sweep；
     * ② NotifyScheduleConfig 的定时任务（每 5 分钟）全量 sweep。
     */
    @Override
    public NotifySummaryVo summary(Long userId)
    {
        NotifySummaryVo vo = notifyMapper.selectNotifySummary(userId);
        if (vo == null)
        {
            vo = new NotifySummaryVo();
        }
        Long maxId = notifyMapper.selectMaxId(userId);
        vo.setMaxId(maxId == null ? 0L : maxId);
        fillPopupPolicy(vo);
        return vo;
    }

    @Override
    public List<ProjNotify> selectNotifyList(Long userId, String notifyType, List<String> statusList)
    {
        // 保留即时 sweep：本接口仅在用户主动打开通知面板时调用（低频），
        // 且「打开就看到最新状态」是用户可感知的体验关键。
        sweepUser(userId);
        return notifyMapper.selectNotifyList(userId, notifyType, statusList);
    }

    /**
     * 自动弹出增量。
     *
     * ⚠️ 不做 sweep：本接口只查「水位线之后新增的待办与预警」，新增项不存在"已完成"的可能；
     * 且它在弹出链路中被高频调用，写操作无收益。
     */
    @Override
    public List<ProjNotify> selectIncrement(Long userId, Long afterId)
    {
        return notifyMapper.selectIncrementTodos(userId, afterId == null ? 0L : afterId);
    }

    /**
     * 登录首屏「今日待处理摘要」。
     *
     * ⚠️ 这里做一次 sweep（与打开面板同口径）：否则早上登录时，昨天已经登记完资料、
     * 但 status 还停在 read 的待办会被当成"今天还要办"弹给用户。
     * 只在登录首屏调用一次，写操作次数可控。
     */
    @Override
    public List<ProjNotify> selectOpenItems(Long userId)
    {
        sweepUser(userId);
        return notifyMapper.selectOpenItems(userId, 20);
    }

    @Override
    public int unignoreTodo(Long id, Long userId)
    {
        return notifyMapper.unignoreTodo(id, userId);
    }

    @Override
    public int markRead(Long id, Long userId)
    {
        return notifyMapper.markRead(id, userId);
    }

    @Override
    public int markAllRead(Long userId, String notifyType)
    {
        return notifyMapper.markAllRead(userId, notifyType);
    }

    @Override
    public int ignoreTodo(Long id, Long userId, String reason)
    {
        if (StringUtils.isEmpty(reason))
        {
            throw new ServiceException("忽略待办必须填写原因");
        }
        return notifyMapper.ignoreTodo(id, userId, reason);
    }

    @Override
    public int sweepAll()
    {
        int dueDays = getIntConfig(CONFIG_TODO_DUE, 3);
        int docDone = notifyMapper.autoCompleteDocTodos(null);
        int settleDone = notifyMapper.autoCompleteSettleTodos(null);
        int contractDone = notifyMapper.autoCompleteContractTodos(null);
        int escalated = notifyMapper.escalateOverdueTodos(null, dueDays);
        int deEscalated = notifyMapper.deEscalateTodos(null);
        log.info("[notify] 全量sweep：资料完成 {}，结算完成 {}，合同完成 {}，超期升级 {}，到期降级 {}",
                docDone, settleDone, contractDone, escalated, deEscalated);
        return docDone + settleDone + contractDone + escalated + deEscalated;
    }

    /**
     * 当前用户即时 sweep（仅 selectNotifyList 调用，保证"打开面板就看到最新状态"）。
     * 顺序固定：先自动完成 → 再升级 → 最后降级（避免同一行被升级后又被降级来回抖动）。
     */
    private void sweepUser(Long userId)
    {
        int dueDays = getIntConfig(CONFIG_TODO_DUE, 3);
        notifyMapper.autoCompleteDocTodos(userId);
        notifyMapper.autoCompleteSettleTodos(userId);
        notifyMapper.autoCompleteContractTodos(userId);
        notifyMapper.escalateOverdueTodos(userId, dueDays);
        notifyMapper.deEscalateTodos(userId);
    }

    /** 生成待办到期时间：now + notify.todo.due.days 天 */
    private Date buildDueTime()
    {
        int dueDays = getIntConfig(CONFIG_TODO_DUE, 3);
        return new Date(System.currentTimeMillis() + (long) Math.max(1, dueDays) * 24 * 60 * 60 * 1000L);
    }

    /** 填充自动弹出策略（服务端判定免打扰，前端无需重复实现） */
    private void fillPopupPolicy(NotifySummaryVo vo)
    {
        boolean enabled = "true".equalsIgnoreCase(configService.selectConfigByKey(CONFIG_POPUP_ENABLED));
        String start = StringUtils.defaultString(configService.selectConfigByKey(CONFIG_POPUP_START), "08:00");
        String end = StringUtils.defaultString(configService.selectConfigByKey(CONFIG_POPUP_END), "18:00");
        boolean workdayOnly = !"false".equalsIgnoreCase(configService.selectConfigByKey(CONFIG_POPUP_WORKDAY));
        int duration = getIntConfig(CONFIG_POPUP_DURATION, 8);
        int throttle = getIntConfig(CONFIG_POPUP_THROTTLE, 5);

        vo.setPopupEnabled(enabled);
        vo.setWorkStart(start);
        vo.setWorkEnd(end);
        vo.setPopupDuration(duration);
        vo.setThrottleMinutes(throttle);

        boolean allow = enabled;
        if (allow)
        {
            java.time.LocalDate today = java.time.LocalDate.now();
            if (workdayOnly && (today.getDayOfWeek() == DayOfWeek.SATURDAY || today.getDayOfWeek() == DayOfWeek.SUNDAY))
            {
                allow = false;
            }
        }
        if (allow)
        {
            LocalTime now = LocalTime.now();
            LocalTime s = parseHm(start, LocalTime.of(8, 0));
            LocalTime e = parseHm(end, LocalTime.of(18, 0));
            allow = !now.isBefore(s) && now.isBefore(e);
        }
        vo.setAllowPopup(allow);
    }

    private LocalTime parseHm(String hm, LocalTime dft)
    {
        try
        {
            String[] p = hm.split(":");
            return LocalTime.of(Integer.parseInt(p[0].trim()), p.length > 1 ? Integer.parseInt(p[1].trim()) : 0);
        }
        catch (Exception ex)
        {
            return dft;
        }
    }

    private int getIntConfig(String key, int dft)
    {
        try
        {
            String v = configService.selectConfigByKey(key);
            return StringUtils.isEmpty(v) ? dft : Integer.parseInt(v.trim());
        }
        catch (Exception ex)
        {
            return dft;
        }
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
}
