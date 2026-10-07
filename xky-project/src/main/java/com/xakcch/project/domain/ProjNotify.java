package com.xakcch.project.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.xakcch.common.core.domain.BaseEntity;

/**
 * 站内待办与消息对象 proj_notify（按接收人一行）
 *
 * notify_type: todo 待办（需完成，业务状态反推自动完成）| message 消息（已读即消）
 * todo_kind:   doc 档案管理登记资料 | settle 生产管理录入结算（仅 todo 有值）
 * status:      unread 未读 | read 已读 | done 已完成（自动）| ignored 已忽略
 * priority:     normal | high（待办超期升级）
 *
 * @author liuyonghui
 */
public class ProjNotify extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** todo | message */
    private String notifyType;

    /** doc | settle（仅 todo） */
    private String todoKind;

    /** 业务事件类型：project_closed */
    private String bizType;

    /** 业务对象 id（项目 id） */
    private Long bizId;

    /** 项目 id */
    private Long projectId;

    /** 工程编号（冗余，列表直出） */
    private String projectCode;

    /** 工程名称（冗余，卡片副标题） */
    private String projectName;

    /** 标题 */
    private String title;

    /** 内容 */
    private String content;

    /** 接收用户 id */
    private Long receiverId;

    /** 接收角色 id */
    private Long receiverRoleId;

    /** 落地路由（如 /material） */
    private String routePath;

    /** 定位参数（projectCode=xxx&from=notify） */
    private String routeQuery;

    /** normal | high */
    private String priority;

    /** unread | read | done | ignored */
    private String status;

    /** 期望完成时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date dueTime;

    /** 已读时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date readTime;

    /** 完成时间（自动） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date doneTime;

    /** 忽略原因 */
    private String ignoreReason;

    /** 删除标志（0正常 2删除） */
    private String delFlag;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNotifyType() { return notifyType; }
    public void setNotifyType(String notifyType) { this.notifyType = notifyType; }

    public String getTodoKind() { return todoKind; }
    public void setTodoKind(String todoKind) { this.todoKind = todoKind; }

    public String getBizType() { return bizType; }
    public void setBizType(String bizType) { this.bizType = bizType; }

    public Long getBizId() { return bizId; }
    public void setBizId(Long bizId) { this.bizId = bizId; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public String getProjectCode() { return projectCode; }
    public void setProjectCode(String projectCode) { this.projectCode = projectCode; }

    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Long getReceiverId() { return receiverId; }
    public void setReceiverId(Long receiverId) { this.receiverId = receiverId; }

    public Long getReceiverRoleId() { return receiverRoleId; }
    public void setReceiverRoleId(Long receiverRoleId) { this.receiverRoleId = receiverRoleId; }

    public String getRoutePath() { return routePath; }
    public void setRoutePath(String routePath) { this.routePath = routePath; }

    public String getRouteQuery() { return routeQuery; }
    public void setRouteQuery(String routeQuery) { this.routeQuery = routeQuery; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Date getDueTime() { return dueTime; }
    public void setDueTime(Date dueTime) { this.dueTime = dueTime; }

    public Date getReadTime() { return readTime; }
    public void setReadTime(Date readTime) { this.readTime = readTime; }

    public Date getDoneTime() { return doneTime; }
    public void setDoneTime(Date doneTime) { this.doneTime = doneTime; }

    public String getIgnoreReason() { return ignoreReason; }
    public void setIgnoreReason(String ignoreReason) { this.ignoreReason = ignoreReason; }

    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }
}
