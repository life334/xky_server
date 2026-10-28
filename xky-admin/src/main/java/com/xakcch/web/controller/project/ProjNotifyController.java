package com.xakcch.web.controller.project;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.xakcch.common.core.controller.BaseController;
import com.xakcch.common.core.domain.AjaxResult;
import com.xakcch.common.core.page.TableDataInfo;
import com.xakcch.common.utils.SecurityUtils;
import com.xakcch.project.domain.ProjNotify;
import com.xakcch.project.service.IProjAlertService;
import com.xakcch.project.service.IProjNotifyService;

/**
 * 站内待办与消息（右上角通知中心）
 *
 * 所有端点均以当前登录用户为接收人，不支持查他人通知（防越权）。
 *
 * @author liuyonghui
 */
@RestController
@RequestMapping("/project/notify")
public class ProjNotifyController extends BaseController
{
    @Autowired
    private IProjNotifyService notifyService;

    @Autowired
    private IProjAlertService alertService;

    /**
     * 角标摘要（含自动弹出策略）
     */
    @GetMapping("/summary")
    public AjaxResult summary()
    {
        return success(notifyService.summary(SecurityUtils.getUserId()));
    }

    /**
     * 通知列表（分页）
     *
     * @param type  todo | message（null=全部）
     * @param status 状态筛选：open=未完成(unread/read)【默认】| done=已完成 |
     *               ignored=已忽略 | closed=历史遗留（认领能力已下线）| all=全部
     */
    @GetMapping("/list")
    public TableDataInfo list(@RequestParam(required = false) String type,
                              @RequestParam(required = false, defaultValue = "open") String status)
    {
        startPage();
        List<ProjNotify> list = notifyService.selectNotifyList(SecurityUtils.getUserId(), type, resolveStatus(status));
        return getDataTable(list);
    }

    /**
     * 状态参数 → 状态集合映射。
     * 白名单解析：未识别的值一律按 open 处理（防止前端拼错参数导致越权读取已关闭数据）。
     */
    private List<String> resolveStatus(String status)
    {
        if (status == null || status.isEmpty())
        {
            return Arrays.asList("unread", "read");
        }
        switch (status)
        {
            case "done":    return Collections.singletonList("done");
            case "ignored": return Collections.singletonList("ignored");
            case "closed":  return Collections.singletonList("closed");
            case "all":     return null;
            default:        return Arrays.asList("unread", "read");
        }
    }

    /**
     * 自动弹出增量（水位线之后新到达的未完成待办与预警）
     */
    @GetMapping("/increment")
    public AjaxResult increment(@RequestParam(required = false, defaultValue = "0") Long afterId)
    {
        List<ProjNotify> items = notifyService.selectIncrement(SecurityUtils.getUserId(), afterId);
        AjaxResult r = success(items);
        r.put("count", items.size());
        return r;
    }

    /**
     * 登录首屏「今日待处理摘要」：当前全部未完成待办 + 未处理预警（不含消息）。
     *
     * 与 /increment 的区别：不按水位线取增量，而是取全量未完成项 ——
     * 这样早上登录时，昨天派发还没处理的待办与未处理预警也能被弹出提醒，而不必手点铃铛。
     * 前端按「每天最多一次 + 每个会话一次」自行节流。
     */
    @GetMapping("/open")
    public AjaxResult open()
    {
        List<ProjNotify> items = notifyService.selectOpenItems(SecurityUtils.getUserId());
        AjaxResult r = success(items);
        r.put("count", items.size());
        return r;
    }

    /**
     * 单条已读（仅接收人本人）
     */
    @PutMapping("/read/{id}")
    public AjaxResult read(@PathVariable Long id)
    {
        return toAjax(notifyService.markRead(id, SecurityUtils.getUserId()));
    }

    /**
     * 全部已读（可限定类型 todo/message）
     */
    @PutMapping("/readAll")
    public AjaxResult readAll(@RequestParam(required = false) String type)
    {
        int rows = notifyService.markAllRead(SecurityUtils.getUserId(), type);
        // ⚠️ 不能用 toAjax(rows)："没有未读内容"是正常结果而非失败，
        // toAjax(0) 会返回"操作失败"，用户点了按钮却看到报错，以为是坏了。
        AjaxResult r = AjaxResult.success();
        r.put("rows", rows);
        return r;
    }

    /**
     * 手动触发预警扫描（仅管理员）。
     *
     * <p>预警由定时任务每日 08:30 自动扫描；本端点用于<b>上线验收</b>与运维补跑。
     * 扫描本身幂等（同一项目同一规则对同一接收人只存在一行），重复触发安全。</p>
     */
    @PostMapping("/alert/scan")
    public AjaxResult scanAlerts()
    {
        if (!SecurityUtils.isAdmin(SecurityUtils.getUserId()))
        {
            return AjaxResult.error("仅管理员可手动触发预警扫描");
        }
        int n = alertService.scanAllAlerts();
        return AjaxResult.success("扫描完成，写入/刷新 " + n + " 条预警");
    }

    /**
     * 忽略待办/预警（<b>不要求填写原因</b>，点击即生效）。
     *
     * @param body 可选的忽略原因（兼容旧前端；新前端不再传）
     */
    @PutMapping("/ignore/{id}")
    public AjaxResult ignore(@PathVariable Long id, @RequestBody(required = false) IgnoreBody body)
    {
        String reason = body == null ? null : body.getReason();
        return toAjax(notifyService.ignoreTodo(id, SecurityUtils.getUserId(), reason));
    }

    /**
     * 批量忽略：勾选若干条（ids）、整类忽略某条预警规则（bizType）、或忽略当前页签全部（all）。
     *
     * <p>背景：预警一次扫描能给一个人堆出上百条，逐条忽略不现实。
     * ids / bizType / all 至少给一个，Service 层对"全空"直接拒绝（否则等于清空全部未完成项）；
     * all=true 时必须带 notifyType，不允许跨待办/预警一把清。</p>
     */
    @PutMapping("/ignoreBatch")
    public AjaxResult ignoreBatch(@RequestBody(required = false) IgnoreBatchBody body)
    {
        if (body == null)
        {
            return AjaxResult.error("参数不能为空");
        }
        int rows = notifyService.ignoreBatch(body.getIds(), body.getNotifyType(), body.getBizType(),
                Boolean.TRUE.equals(body.getAll()), SecurityUtils.getUserId(), body.getReason());
        // 0 行是正常结果（如刚好被别人处理/已被忽略），不能走 toAjax(0) 报"操作失败"
        AjaxResult r = AjaxResult.success(rows > 0 ? ("已忽略 " + rows + " 条") : "没有可忽略的内容");
        r.put("rows", rows);
        return r;
    }

    /**
     * 未处理预警按规则分组计数（前端「按规则一键忽略」入口的数据源）
     */
    @GetMapping("/alertGroups")
    public AjaxResult alertGroups()
    {
        return success(notifyService.selectOpenAlertGroups(SecurityUtils.getUserId()));
    }

    /**
     * 撤销忽略（回到未完成）
     */
    @PutMapping("/unignore/{id}")
    public AjaxResult unignore(@PathVariable Long id)
    {
        return toAjax(notifyService.unignoreTodo(id, SecurityUtils.getUserId()));
    }

    public static class IgnoreBody
    {
        private String reason;

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class IgnoreBatchBody
    {
        /** 待忽略的通知 id 集合（三选一） */
        private List<Long> ids;
        /** 限定类型：todo | alert（忽略全部时必填） */
        private String notifyType;
        /** 限定规则：如 alert_overdue（整类忽略，三选一） */
        private String bizType;
        /** 是否忽略该类型下全部未完成项（三选一） */
        private Boolean all;
        private String reason;

        public List<Long> getIds() { return ids; }
        public void setIds(List<Long> ids) { this.ids = ids; }
        public String getNotifyType() { return notifyType; }
        public void setNotifyType(String notifyType) { this.notifyType = notifyType; }
        public String getBizType() { return bizType; }
        public void setBizType(String bizType) { this.bizType = bizType; }
        public Boolean getAll() { return all; }
        public void setAll(Boolean all) { this.all = all; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}
