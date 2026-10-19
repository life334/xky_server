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
     * 角标摘要（含自动弹出策略）。
     *
     * ⚠️ 只读：不做 sweep。本接口被前端 30s 轮询，GET 里跑 UPDATE 既语义错误又放大锁竞争。
     */
    public NotifySummaryVo summary(Long userId);

    /**
     * 通知列表（查询前做即时 sweep，保证"打开面板就看到最新状态"）。
     *
     * @param notifyType todo | message（null=全部）
     * @param statusList 状态集合（null=全部）：unread/read=未完成；done=已完成；
     *                   ignored=已忽略；closed=历史遗留（认领能力已下线，仅历史数据存在）
     */
    public List<ProjNotify> selectNotifyList(Long userId, String notifyType, List<String> statusList);

    /**
     * 自动弹出增量：水位线之后新到达的未完成待办与预警（只读，不做 sweep）
     */
    public List<ProjNotify> selectIncrement(Long userId, Long afterId);

    /**
     * 登录首屏「今日待处理摘要」：当前全部未完成待办 + 未处理预警（不含消息）。
     *
     * ⚠️ 会先对该用户跑一次 sweep：业务上已完成的待办不应出现在"今天还要办"的清单里。
     * 只在登录首屏触发一次，写操作次数可控。
     */
    public List<ProjNotify> selectOpenItems(Long userId);

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
     * 撤销忽略（status 回退为 read）。解决"忽略后不可恢复"的问题。
     */
    public int unignoreTodo(Long id, Long userId);

    /**
     * 全量 sweep（定时任务）：自动完成三类待办 + 超期升级 + 到期降级
     */
    public int sweepAll();
}
