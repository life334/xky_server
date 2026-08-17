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

    /** 统计时间区间内的上报记录数（按月上报控制：当月已上报则不允许再报） */
    int countLogsBetween(@Param("begin") java.util.Date begin, @Param("end") java.util.Date end);

    /**
     * 新增上报记录（UNIQUE(project_code) 冲突时跳过，不更新）
     * 返回 1 表示新写入，0 表示已上报过被跳过
     */
    int insertLogIgnore(ProjReportSubmitLog log);

    /** 逻辑删除单条上报记录（仅超管，删除后该工程可重新上报） */
    int deleteLogById(Long id);
}
