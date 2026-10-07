package com.xakcch.project.domain.vo;

/**
 * 通知角标摘要 VO（/project/notify/summary 返回体）
 *
 * todoCount     未完成待办数（红点数字）
 * messageCount  未读消息数
 * highTodoCount 其中超期升级为 high 的待办数（琥珀点）
 * alertCount    预警数（P0 无预警规则，恒 0）
 * popup*        自动弹出策略（服务端判定，前端无需重复实现）
 *
 * @author liuyonghui
 */
public class NotifySummaryVo
{
    /** 未完成待办数（unread/read 且 notify_type=todo） */
    private Integer todoCount = 0;

    /** 未读消息数 */
    private Integer messageCount = 0;

    /** 高优先级待办数（priority=high） */
    private Integer highTodoCount = 0;

    /** 预警数（P0 恒 0） */
    private Integer alertCount = 0;

    /** 当前用户最新通知 id（前端水位线） */
    private Long maxId = 0L;

    /** 自动弹出总开关 */
    private Boolean popupEnabled = true;

    /** 当前时刻是否允许自动弹出（免打扰窗口 + 工作日判定，服务端算好） */
    private Boolean allowPopup = false;

    /** 卡片停留秒数（normal） */
    private Integer popupDuration = 8;

    /** 两次弹出最小间隔（分钟，前端节流用） */
    private Integer throttleMinutes = 5;

    /** 工作时段起点 HH:mm（展示用） */
    private String workStart = "08:00";

    /** 工作时段终点 HH:mm（展示用） */
    private String workEnd = "18:00";

    public Integer getTodoCount() { return todoCount; }
    public void setTodoCount(Integer todoCount) { this.todoCount = todoCount; }

    public Integer getMessageCount() { return messageCount; }
    public void setMessageCount(Integer messageCount) { this.messageCount = messageCount; }

    public Integer getHighTodoCount() { return highTodoCount; }
    public void setHighTodoCount(Integer highTodoCount) { this.highTodoCount = highTodoCount; }

    public Integer getAlertCount() { return alertCount; }
    public void setAlertCount(Integer alertCount) { this.alertCount = alertCount; }

    public Long getMaxId() { return maxId; }
    public void setMaxId(Long maxId) { this.maxId = maxId; }

    public Boolean getPopupEnabled() { return popupEnabled; }
    public void setPopupEnabled(Boolean popupEnabled) { this.popupEnabled = popupEnabled; }

    public Boolean getAllowPopup() { return allowPopup; }
    public void setAllowPopup(Boolean allowPopup) { this.allowPopup = allowPopup; }

    public Integer getPopupDuration() { return popupDuration; }
    public void setPopupDuration(Integer popupDuration) { this.popupDuration = popupDuration; }

    public Integer getThrottleMinutes() { return throttleMinutes; }
    public void setThrottleMinutes(Integer throttleMinutes) { this.throttleMinutes = throttleMinutes; }

    public String getWorkStart() { return workStart; }
    public void setWorkStart(String workStart) { this.workStart = workStart; }

    public String getWorkEnd() { return workEnd; }
    public void setWorkEnd(String workEnd) { this.workEnd = workEnd; }
}
