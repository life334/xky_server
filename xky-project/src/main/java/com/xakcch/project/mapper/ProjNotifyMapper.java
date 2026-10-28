package com.xakcch.project.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.xakcch.project.domain.ProjNotify;
import com.xakcch.project.domain.vo.NotifySummaryVo;

/**
 * 站内待办与消息 数据层
 *
 * @author liuyonghui
 */
public interface ProjNotifyMapper
{
    /**
     * 插入通知（幂等：biz_type+biz_id+receiver_id 唯一索引，冲突忽略）
     */
    public int insertNotify(ProjNotify notify);

    /**
     * 查询某接收人的通知列表（倒序）
     *
     * @param receiverId 接收用户 id（必填）
     * @param notifyType todo | message（可选）
     * @param statusList 状态集合（可选）：unread/read=未完成；done=已完成；ignored=已忽略；
     *                   closed=历史遗留（认领能力已下线）；null=全部
     */
    public List<ProjNotify> selectNotifyList(@Param("receiverId") Long receiverId,
                                              @Param("notifyType") String notifyType,
                                              @Param("statusList") List<String> statusList);

    /**
     * 角标摘要（计数）
     */
    public NotifySummaryVo selectNotifySummary(@Param("receiverId") Long receiverId);

    /**
     * 当前用户最新通知 id（弹出水位线）
     */
    public Long selectMaxId(@Param("receiverId") Long receiverId);

    /**
     * 水位线之后新到达的未完成待办与预警（自动弹出的增量数据）
     */
    public List<ProjNotify> selectIncrementTodos(@Param("receiverId") Long receiverId,
                                                 @Param("afterId") Long afterId);

    /**
     * 当前全部未完成待办 + 未处理预警（登录首屏「今日待处理摘要」用，不含消息）
     *
     * @param limit 上限（防止一天积压上百条时把弹出卡撑爆）
     */
    public List<ProjNotify> selectOpenItems(@Param("receiverId") Long receiverId, @Param("limit") int limit);

    /**
     * 单条已读（校验接收人，防越权）
     */
    public int markRead(@Param("id") Long id, @Param("receiverId") Long receiverId);

    /**
     * 全部已读（可限定类型）
     */
    public int markAllRead(@Param("receiverId") Long receiverId,
                           @Param("notifyType") String notifyType);

    /**
     * 忽略待办/预警（原因可空：一键忽略不再强制填写）
     */
    public int ignoreTodo(@Param("id") Long id, @Param("receiverId") Long receiverId,
                          @Param("reason") String reason);

    /**
     * 批量忽略：按 id 集合忽略、按 bizType 整类忽略（预警「忽略此规则」），
     * 或忽略某类型下全部未完成项（「忽略全部」，由 Service 侧以 notifyType 限定）。
     *
     * <p>⚠️ 调用方<b>必须</b>保证 ids / bizType / notifyType 至少有一个非空：
     * 全空会命中该接收人全部未完成项，属于危险的全量操作。</p>
     *
     * @param ids        待忽略的通知 id 集合（可空）
     * @param receiverId 接收人（防越权，必填）
     * @param notifyType 限定通知类型 todo | alert（可空）
     * @param bizType    限定业务类型，如 alert_overdue（可空）
     * @param reason     忽略原因（可空）
     */
    public int ignoreTodoBatch(@Param("ids") List<Long> ids,
                               @Param("receiverId") Long receiverId,
                               @Param("notifyType") String notifyType,
                               @Param("bizType") String bizType,
                               @Param("reason") String reason);

    /**
     * 当前用户未处理预警按规则分组计数（供前端「按规则一键忽略」）
     *
     * @return 每项含 "bizType" / "count"
     */
    public List<Map<String, Object>> selectOpenAlertGroups(@Param("receiverId") Long receiverId);

    /**
     * 撤销忽略：status 回退为 read，清空 ignore_reason
     */
    public int unignoreTodo(@Param("id") Long id, @Param("receiverId") Long receiverId);

    /**
     * 单条查询（带 receiver_id 校验，防越权）
     */
    public ProjNotify selectNotifyById(@Param("id") Long id, @Param("receiverId") Long receiverId);

    /**
     * 自动完成「档案管理登记资料」待办：
     * 项目存在 proj_material 且 COALESCE(result_type,'') <> ''（⚠️ 空串坑：全库为 '' 非 NULL）
     *
     * @param receiverId 限定接收人（查询时即时 sweep 用）；null = 全量（定时任务用）
     */
    public int autoCompleteDocTodos(@Param("receiverId") Long receiverId);

    /**
     * 自动完成「生产管理录入结算」待办：
     * 项目存在 proj_workload 且 billing_type='external' AND COALESCE(workload,0)>0
     * （⚠️ 禁用 external_workload 列——全库未写入；数量统一存 workload）
     */
    public int autoCompleteSettleTodos(@Param("receiverId") Long receiverId);

    /**
     * 自动完成「合同待签署」待办：
     * 对应合同 proj_contract.status='completed'，或已上传<b>签署件</b>
     * （proj_contract_attachment 中 file_category='contract' 且 is_final='1'）。
     *
     * ⚠️ 不可放宽为"存在任意附件"：历史数据导入常自带附件，会导致待办从未出现或立刻消失。
     */
    public int autoCompleteContractTodos(@Param("receiverId") Long receiverId);

    /**
     * 待办超期升级为 high（以 due_time 为唯一口径）
     *
     * @param dueDays 仅用于 due_time 为空时的兜底计算（正常情况下 due_time 必有值）
     */
    public int escalateOverdueTodos(@Param("receiverId") Long receiverId,
                                    @Param("dueDays") int dueDays);

    /**
     * 待办降级：due_time 尚未到期 → 从 high 降回 normal。
     * 解决「一旦升 high 永不降级」（如超期天数配置调大后旧行仍显示超期）。
     */
    public int deEscalateTodos(@Param("receiverId") Long receiverId);

    /**
     * 按角色 key 解析接收用户（sys_user_role × sys_role）
     *
     * @return 每项含 "userId" / "roleId" / "roleKey"（驼峰键）
     */
    public List<Map<String, Object>> selectReceiversByRoleKeys(@Param("roleKeys") List<String> roleKeys);

    /**
     * 查询某用户的角色 key 集合（用于判定操作人是否为档案管理员）
     *
     * @param userId 用户 id；null 时返回空集合
     * @return role_key 列表（已过滤停用/删除角色）
     */
    public List<String> selectRoleKeysByUserId(@Param("userId") Long userId);
}
