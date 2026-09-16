package com.xakcch.project.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.xakcch.project.domain.ProjReportSubmitBatch;
import com.xakcch.project.domain.ProjReportSubmitLog;

/**
 * 报表上报（批次 + 记录）Mapper 接口
 *
 * @author liuyonghui
 */
public interface ProjReportSubmitMapper
{
    // ==================== 批次 ====================

    /** 批次列表（按上报时间倒序） */
    List<ProjReportSubmitBatch> selectBatchList(ProjReportSubmitBatch query);

    /** 按ID查询批次 */
    ProjReportSubmitBatch selectBatchById(Long id);

    /** 按批次号前缀查询当天最大批次号（生成序号用，如 SB20260817% → SB20260817005） */
    String selectMaxBatchNoByPrefix(String prefix);

    /** 新增批次 */
    int insertBatch(ProjReportSubmitBatch batch);

    /** 逻辑删除批次 */
    int deleteBatchById(Long id);

    /** 逻辑删除某批次下的全部记录 */
    int deleteLogsByBatchId(Long batchId);

    // ==================== 记录 ====================

    /** 上报记录列表（可按工程编号/操作人/批次号过滤，按上报时间倒序） */
    List<ProjReportSubmitLog> selectLogList(ProjReportSubmitLog query);

    /** 某批次下的上报记录 */
    List<ProjReportSubmitLog> selectLogsByBatchId(Long batchId);

    /** 按工程编号查询上报记录 */
    ProjReportSubmitLog selectLogByProjectCode(String projectCode);

    /** 批量查询已上报工程编号（返回记录，供前端标记已上报状态） */
    List<ProjReportSubmitLog> selectLogsByCodes(@Param("codes") List<String> codes);

    /**
     * 统计时间区间内的「真实上报」记录数（按月上报控制：当月已上报则不允许再报）
     * 只统计 batch_id 非空的真实上报行，历史导入补录行（batch_id 为空）不计入，
     * 避免补录时间恰好落在当月时把「本月已上报」置灰、挡住正常上报。
     */
    int countLogsBetween(@Param("begin") java.util.Date begin, @Param("end") java.util.Date end);

    /**
     * 新增上报记录（UNIQUE(project_code) 冲突时跳过，不更新）
     * 返回 1 表示新写入，0 表示已上报过被跳过
     */
    int insertLogIgnore(ProjReportSubmitLog log);

    /**
     * 新增历史补录上报记录（可指定 submit_time；UNIQUE(project_code) 冲突时跳过）
     *
     * <p>专供历史数据导入补录：导入的定线项目在线下已上报过，导入时按规则
     * （有尾款取尾款到账时间、无尾款取预付款到账时间）把上报时间固化进本表，
     * 作为追溯依据与统计来源，故需要写入指定的历史时间而非 now()。</p>
     */
    int insertLogIgnoreWithTime(ProjReportSubmitLog log);

    /**
     * 修改历史补录记录的上报时间（只允许 batch_id 为空的历史行）
     *
     * <p>SQL 内用 {@code batch_id is null} 守卫：真实上报行（batch_id 非空）恒不被命中，
     * 即「真实上报时间锁定不可改」的约束不靠调用方自觉。</p>
     *
     * @return 1 表示已修改，0 表示该记录不存在／不是历史补录行
     */
    int updateLogSubmitTime(@Param("id") Long id, @Param("submitTime") java.util.Date submitTime);

    /**
     * 逻辑删除单条上报记录（仅超管）
     *
     * <p>注意：唯一索引 uk_submit_log_code 为整表唯一（不含 del_flag 谓词），
     * 软删后该工程编号仍然占位，重新上报会被 insertLogIgnore 静默跳过。
     * 即业务规则「一个定线项目只允许上报一次」在库层被执行，删除不等于可重报。</p>
     */
    int deleteLogById(Long id);
}
