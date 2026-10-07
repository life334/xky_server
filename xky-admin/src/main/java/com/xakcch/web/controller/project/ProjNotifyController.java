package com.xakcch.web.controller.project;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
     * @param type     todo | message | alert（alert 为 P1 占位）
     * @param onlyOpen true=仅未完成/未读
     */
    @GetMapping("/list")
    public TableDataInfo list(@RequestParam(required = false) String type,
                              @RequestParam(required = false, defaultValue = "true") boolean onlyOpen)
    {
        if ("alert".equals(type))
        {
            // P0 未接预警规则，返回空
            return getDataTable(new java.util.ArrayList<ProjNotify>());
        }
        startPage();
        List<ProjNotify> list = notifyService.selectNotifyList(SecurityUtils.getUserId(), type, onlyOpen);
        return getDataTable(list);
    }

    /**
     * 自动弹出增量（水位线之后的未完成待办）
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
        return toAjax(notifyService.markAllRead(SecurityUtils.getUserId(), type));
    }

    /**
     * 忽略待办（必须带原因）
     */
    @PutMapping("/ignore/{id}")
    public AjaxResult ignore(@PathVariable Long id, @RequestBody(required = false) IgnoreBody body)
    {
        String reason = body == null ? null : body.getReason();
        return toAjax(notifyService.ignoreTodo(id, SecurityUtils.getUserId(), reason));
    }

    public static class IgnoreBody
    {
        private String reason;

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}
