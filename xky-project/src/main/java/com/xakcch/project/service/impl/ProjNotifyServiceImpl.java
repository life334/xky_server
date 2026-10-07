package com.xakcch.project.service.impl;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.xakcch.common.exception.ServiceException;
import com.xakcch.common.utils.StringUtils;
import com.xakcch.project.domain.ProjNotify;
import com.xakcch.project.domain.ProjProject;
import com.xakcch.project.domain.vo.NotifySummaryVo;
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

    @Autowired
    private ProjNotifyMapper notifyMapper;

    @Autowired
    private ProjProjectMapper projectMapper;

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

    @Override
    public NotifySummaryVo summary(Long userId)
    {
        sweepUser(userId);
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
    public List<ProjNotify> selectNotifyList(Long userId, String notifyType, boolean onlyOpen)
    {
        sweepUser(userId);
        List<String> statusList = onlyOpen ? Arrays.asList("unread", "read") : null;
        return notifyMapper.selectNotifyList(userId, notifyType, statusList);
    }

    @Override
    public List<ProjNotify> selectIncrement(Long userId, Long afterId)
    {
        sweepUser(userId);
        return notifyMapper.selectIncrementTodos(userId, afterId == null ? 0L : afterId);
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
        int docDone = notifyMapper.autoCompleteDocTodos(null);
        int settleDone = notifyMapper.autoCompleteSettleTodos(null);
        int escalated = notifyMapper.escalateOverdueTodos(null, getIntConfig(CONFIG_TODO_DUE, 3));
        log.info("[notify] 每日sweep：资料待办完成 {}，结算待办完成 {}，超期升级 {}", docDone, settleDone, escalated);
        return docDone + settleDone + escalated;
    }

    /** 当前用户即时 sweep（角标/列表查询前执行，保证"做完立刻消"） */
    private void sweepUser(Long userId)
    {
        notifyMapper.autoCompleteDocTodos(userId);
        notifyMapper.autoCompleteSettleTodos(userId);
        notifyMapper.escalateOverdueTodos(userId, getIntConfig(CONFIG_TODO_DUE, 3));
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
