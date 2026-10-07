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
     * @param status     unread | read | done | ignored（可选；查"未完成待办"传 unread,read 由 XML in 处理）
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
     * 水位线之后的未完成待办（自动弹出的增量数据）
     */
    public List<ProjNotify> selectIncrementTodos(@Param("receiverId") Long receiverId,
                                                 @Param("afterId") Long afterId);

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
     * 忽略待办（带原因）
     */
    public int ignoreTodo(@Param("id") Long id, @Param("receiverId") Long receiverId,
                          @Param("reason") String reason);

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
     * 待办超期升级为 high
     */
    public int escalateOverdueTodos(@Param("receiverId") Long receiverId,
                                    @Param("dueDays") int dueDays);

    /**
     * 按角色 key 解析接收用户（sys_user_role × sys_role）
     *
     * @return 每项含 "userId" / "roleId" / "roleKey"（驼峰键）
     */
    public List<Map<String, Object>> selectReceiversByRoleKeys(@Param("roleKeys") List<String> roleKeys);
}
