package com.xakcch.project.mapper;

import java.util.List;
import com.xakcch.project.domain.ProjReportLog;

/**
 * 报表导出历史 Mapper 接口
 *
 * @author liuyonghui
 */
public interface ProjReportLogMapper
{
    /** 查询导出历史列表（按导出时间倒序） */
    List<ProjReportLog> selectLogList(ProjReportLog log);

    /** 按ID查询导出记录 */
    ProjReportLog selectLogById(Long id);

    /** 新增导出记录 */
    int insertLog(ProjReportLog log);

    /** 逻辑删除导出记录 */
    int deleteLogById(Long id);
}
