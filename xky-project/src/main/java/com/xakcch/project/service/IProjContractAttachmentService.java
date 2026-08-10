package com.xakcch.project.service;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;
import com.xakcch.project.domain.ProjContractAttachment;
import com.xakcch.project.domain.ProjContractAttachmentLog;

/**
 * 合同附件 Service 接口
 *
 * @author liuyonghui
 */
public interface IProjContractAttachmentService
{
    /**
     * 查询指定合同的所有活跃附件
     *
     * @param contractId 合同ID
     * @return 附件列表
     */
    public List<ProjContractAttachment> listAttachments(Long contractId);

    /**
     * 上传附件（自动检测：同槽位存在则覆盖为新版本，否则首次上传）
     *
     * @param contractId   合同ID
     * @param file         上传文件
     * @param fileCategory 文件归类
     * @param isFinal      是否盖章版
     * @return 附件记录
     */
    public ProjContractAttachment uploadAttachment(Long contractId, MultipartFile file,
                                                   String fileCategory, String isFinal);

    /**
     * 删除附件（软删除 + 记录日志）
     *
     * @param attachmentId 附件ID
     */
    public void deleteAttachment(Long attachmentId);

    /**
     * 查询某附件槽位的版本历史
     *
     * @param attachmentId 附件ID
     * @return 版本历史列表
     */
    public List<ProjContractAttachmentLog> getHistory(Long attachmentId);

    /**
     * 将指定历史版本恢复为当前版本
     *
     * @param logId 历史记录ID
     * @return 恢复后的附件记录
     */
    public ProjContractAttachment restoreVersion(Long logId);

    /**
     * 查询版本历史记录
     *
     * @param logId 历史记录ID
     * @return 版本历史
     */
    public ProjContractAttachmentLog getLogById(Long logId);

    /**
     * 查询附件详情（含软删除的，用于预览）
     *
     * @param attachmentId 附件ID
     * @return 附件
     */
    public ProjContractAttachment getAttachmentById(Long attachmentId);

    /**
     * 根据文件路径构建完整物理路径
     *
     * @param relativePath 相对路径
     * @return 完整物理路径
     */
    public String resolvePhysicalPath(String relativePath);
}
