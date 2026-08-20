package com.xakcch.project.service;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;
import com.xakcch.project.domain.vo.ContractImportPreviewResponse;
import com.xakcch.project.domain.vo.ImportCommitRequest;
import com.xakcch.project.domain.vo.ImportCommitResult;
import com.xakcch.project.domain.vo.ProblemRowDetail;

/**
 * 合同数据导入 服务层
 *
 * @author liuyonghui
 */
public interface IProjContractImportService
{
    /**
     * 步骤1：上传Excel解析预览（不落库），仅返回可导入行 + 问题行明细
     */
    ContractImportPreviewResponse previewContract(MultipartFile file) throws Exception;

    /**
     * 步骤2：提交确认后的数据落库
     */
    ImportCommitResult commitContract(ImportCommitRequest req);

    /**
     * 获取预览中问题行明细（用于下载Excel）
     *
     * @param token 会话 token
     * @param type  duplicate / error
     */
    List<ProblemRowDetail> getContractProblems(String token, String type);
}
