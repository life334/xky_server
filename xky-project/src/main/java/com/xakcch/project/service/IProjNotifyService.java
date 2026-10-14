package com.xakcch.project.service;

import java.util.List;
import com.xakcch.project.domain.ProjNotify;
import com.xakcch.project.domain.vo.NotifySummaryVo;

/**
 * 站内待办与消息 服务接口
 *
 * @author liuyonghui
 */
public interface IProjNotifyService
{
    /**
     * 项目办结事件：按配置角色派发通知
     * （role-doc → 待办·登记资料；role-product → 待办·录入结算；其余角色 → 消息）
     * 幂等：重复调用不产生重复记录（唯一索引 + on conflict do nothing）
     *
     * @param projectId 项目 id
     * @param operator  操作人（办结人，记 create_by）
     */
    public void onProjectClosed(Long projectId, String operator);

    /**
     * 合同新增事件：非档案管理员创建合同时，给档案管理员（role-doc）派发「合同待签署」待办。
     * <p>跳过规则：操作人自身已含 role-doc（本人即档案管理员，无需提醒自己）。
     * 导入路径不经过 service 层 insertContract，故天然不推送。
     * 幂等：biz_type='contract_created' + biz_id=合同id + receiver_id 唯一索引去重。
     *
     * @param contractId     合同 id
     * @param operator       操作人（创建人，记 create_by）
     * @param operatorUserId 操作人用户 id（可为空；用于判定是否档案管理员）
     */
    public void onContractCreated(Long contractId, String operator, Long operatorUserId);

    /**
     * 角标摘要（含自动弹出策略；查询前先做即时 sweep：自动完成 + 超期升级）
     */
    public NotifySummaryVo summary(Long userId);

    /**
     * 通知列表（查询前先做即时 sweep）
     *
     * @param notifyType todo | message（null=全部）
     * @param onlyOpen   true=仅未完成（unread/read）；false=全部状态
     */
    public List<ProjNotify> selectNotifyList(Long userId, String notifyType, boolean onlyOpen);

    /**
     * 自动弹出增量：水位线之后的未完成待办
     */
    public List<ProjNotify> selectIncrement(Long userId, Long afterId);

    /**
     * 单条已读（防越权：仅接收人本人）
     */
    public int markRead(Long id, Long userId);

    /**
     * 全部已读（可限定类型）
     */
    public int markAllRead(Long userId, String notifyType);

    /**
     * 忽略待办（带原因）
     */
    public int ignoreTodo(Long id, Long userId, String reason);

    /**
     * 全量 sweep（定时任务每日执行）：自动完成两类待办 + 超期升级
     */
    public int sweepAll();
}
