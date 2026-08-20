package com.xakcch.project.service;

import com.xakcch.project.domain.vo.ImportCommitRequest;
import com.xakcch.project.domain.vo.ImportCommitResult;
import com.xakcch.project.domain.vo.ImportPreviewResponse;
import com.xakcch.project.domain.vo.ProblemRowDetail;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IProjImportService
{
    ImportPreviewResponse preview(MultipartFile file) throws Exception;

    ImportCommitResult commit(ImportCommitRequest req);

    /** 获取预览中问题行明细（用于下载Excel） */
    List<ProblemRowDetail> getProblems(String token, String type);
}
