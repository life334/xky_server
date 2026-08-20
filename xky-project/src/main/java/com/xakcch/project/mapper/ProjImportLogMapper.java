package com.xakcch.project.mapper;

import com.xakcch.project.domain.ProjImportLog;
import java.util.List;

public interface ProjImportLogMapper
{
    int insertImportLog(ProjImportLog log);
    int updateImportLog(ProjImportLog log);
    ProjImportLog selectImportLogById(Long id);
    List<ProjImportLog> selectImportLogList(ProjImportLog log);
}
