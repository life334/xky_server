package com.xakcch.project.mapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.xakcch.project.domain.ProjNotify;

/**
 * 项目预警（走铃铛，每日扫描一次）
 *
 * <p>三类规则共用 proj_notify 表，notify_type='alert'，biz_type 区分规则，biz_id=项目 id。
 * 幂等由唯一索引 uk_proj_notify_biz(biz_type, biz_id, receiver_id) where del_flag='0' 保障。</p>
 *
 * @author liuyonghui
 */
public interface ProjAlertMapper
{
    /**
     * 规则一「欠款逾期」候选：办结且欠款 ≥ 阈值、办结天数 ≥ 阈值。
     * 全量口径（不带驾驶舱的时间/负责人/类别筛选）。
     */
    List<Map<String, Object>> selectDebtCandidates(@Param("minAmount") BigDecimal minAmount,
                                                   @Param("minDays") int minDays);

    /**
     * 规则二「工期超期」候选：手动录入、已安排日期且维护了工期要求、<b>尚未办结</b>。
     * 是否真超期由 Java 层按工作日历判定（WorkdayUtils）。
     */
    List<Map<String, Object>> selectOverdueCandidates();

    /**
     * 规则三「未关联合同」候选：手动录入、无关联合同、创建于今天之前。
     * 是否已越过阈值由 Java 层按工作日历判定。
     */
    List<Map<String, Object>> selectContractMissingCandidates();

    /**
     * 写入/刷新一条预警。
     *
     * <p>冲突时（同一项目+同一规则+同一接收人已存在）更新文案与严重度，
     * 并把「已自动完成(done)」的行复活为 unread —— 风险可能反复出现（如回款后又欠款）。
     * <b>已忽略(ignored)的行不复活</b>：用户明确表达过"不用管"就尊重其意愿。</p>
     */
    int insertAlert(ProjNotify n);

    /**
     * 自动消解：不在命中列表里的未处理预警置为 done（风险已解除）。
     *
     * @param keepIds 今日仍命中的项目 id；为空表示今日无人命中 ⇒ 现存全部置 done
     */
    int resolveAlerts(@Param("bizType") String bizType, @Param("keepIds") List<Long> keepIds);

    /** 按角色 key 查接收人（复用通知模块口径：仅正常状态的用户与角色） */
    List<Map<String, Object>> selectReceiversByRoleKeys(@Param("roleKeys") List<String> roleKeys);

    /** 按用户 id 查接收人（配置具体人时使用；roleId 取其首个有效角色） */
    List<Map<String, Object>> selectReceiversByUserIds(@Param("userIds") List<Long> userIds);
}
