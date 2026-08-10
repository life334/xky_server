package com.xakcch.project.service.impl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.xakcch.common.config.LandPatchConfig;
import com.xakcch.common.constant.Constants;
import com.xakcch.common.exception.ServiceException;
import com.xakcch.common.utils.SecurityUtils;
import com.xakcch.common.utils.file.FileUploadUtils;
import com.xakcch.project.domain.ProjContractAttachment;
import com.xakcch.project.domain.ProjContractAttachmentLog;
import com.xakcch.project.mapper.ProjContractAttachmentMapper;
import com.xakcch.project.service.IProjContractAttachmentService;

/**
 * 合同附件 Service 实现
 *
 * @author liuyonghui
 */
@Service
public class ProjContractAttachmentServiceImpl implements IProjContractAttachmentService
{
    /** 附件存储子目录 */
    private static final String ATTACHMENT_SUB_DIR = "contract";

    @Autowired
    private ProjContractAttachmentMapper attachmentMapper;

    @Override
    public List<ProjContractAttachment> listAttachments(Long contractId)
    {
        return attachmentMapper.selectAttachmentsByContractId(contractId);
    }

    /**
     * 上传附件。
     * 逻辑：
     * 1. 物理存储到 upload/contract/{contractId}/ 目录
     * 2. 检查该 fileCategory 是否已有活跃附件：
     *    - 有 → 归档旧版本到 log 表，更新主表（version +1）
     *    - 无 → 插入新记录（version = 1）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjContractAttachment uploadAttachment(Long contractId, MultipartFile file,
                                                   String fileCategory, String isFinal)
    {
        if (file == null || file.isEmpty())
        {
            throw new ServiceException("上传文件不能为空");
        }
        if (fileCategory == null || fileCategory.isEmpty())
        {
            fileCategory = "other";
        }
        if (isFinal == null)
        {
            isFinal = "0";
        }

        String username = SecurityUtils.getUsername();

        // 1. 物理存储
        String basePath = LandPatchConfig.getUploadPath() + File.separator
                        + ATTACHMENT_SUB_DIR + File.separator + contractId;
        try
        {
            String fileName = FileUploadUtils.upload(basePath, file);
            String originalName = file.getOriginalFilename();
            String ext = FilenameUtils.getExtension(fileName);

            // 2. 判断是否是覆盖（同槽位已存在）
            List<ProjContractAttachment> existingList = attachmentMapper.selectAttachmentsByContractId(contractId);
            ProjContractAttachment existing = null;
            for (ProjContractAttachment a : existingList)
            {
                if (fileCategory.equals(a.getFileCategory()))
                {
                    existing = a;
                    break;
                }
            }

            if (existing != null)
            {
                // 归档旧版本到 log
                ProjContractAttachmentLog log = new ProjContractAttachmentLog();
                log.setAttachmentId(existing.getId());
                log.setContractId(contractId);
                log.setFileName(existing.getFileName());
                log.setFilePath(existing.getFilePath());
                log.setFileSize(existing.getFileSize());
                log.setFileType(existing.getFileType());
                log.setFileCategory(existing.getFileCategory());
                log.setIsFinal(existing.getIsFinal());
                log.setVersion(existing.getVersion());
                log.setAction("replace");
                log.setOperator(username);
                log.setRemark("上传新版本替换");
                attachmentMapper.insertAttachmentLog(log);

                // 更新主表
                int newVersion = existing.getVersion() + 1;
                existing.setFileName(originalName);
                existing.setFilePath(fileName);
                existing.setFileSize(file.getSize());
                existing.setFileType(getExtLower(ext));
                existing.setIsFinal(isFinal);
                existing.setVersion(newVersion);
                existing.setUpdateBy(username);
                attachmentMapper.updateAttachment(existing);

                return existing;
            }
            else
            {
                // 首次上传
                ProjContractAttachment att = new ProjContractAttachment();
                att.setContractId(contractId);
                att.setFileName(originalName);
                att.setFilePath(fileName);
                att.setFileSize(file.getSize());
                att.setFileType(getExtLower(ext));
                att.setFileCategory(fileCategory);
                att.setIsFinal(isFinal);
                att.setVersion(1);
                att.setSortOrder(0);
                att.setCreateBy(username);
                attachmentMapper.insertAttachment(att);

                // 记录上传日志
                ProjContractAttachmentLog log = new ProjContractAttachmentLog();
                log.setAttachmentId(att.getId());
                log.setContractId(contractId);
                log.setFileName(originalName);
                log.setFilePath(fileName);
                log.setFileSize(file.getSize());
                log.setFileType(getExtLower(ext));
                log.setFileCategory(fileCategory);
                log.setIsFinal(isFinal);
                log.setVersion(1);
                log.setAction("upload");
                log.setOperator(username);
                log.setRemark("首次上传");
                attachmentMapper.insertAttachmentLog(log);

                return att;
            }
        }
        catch (IOException e)
        {
            throw new ServiceException("文件上传失败：" + e.getMessage());
        }
    }

    /**
     * 删除附件（软删除 + 日志记录）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAttachment(Long attachmentId)
    {
        ProjContractAttachment att = attachmentMapper.selectAttachmentById(attachmentId);
        if (att == null)
        {
            throw new ServiceException("附件不存在");
        }

        String username = SecurityUtils.getUsername();

        // 记录删除日志
        ProjContractAttachmentLog log = new ProjContractAttachmentLog();
        log.setAttachmentId(att.getId());
        log.setContractId(att.getContractId());
        log.setFileName(att.getFileName());
        log.setFilePath(att.getFilePath());
        log.setFileSize(att.getFileSize());
        log.setFileType(att.getFileType());
        log.setFileCategory(att.getFileCategory());
        log.setIsFinal(att.getIsFinal());
        log.setVersion(att.getVersion());
        log.setAction("delete");
        log.setOperator(username);
        log.setRemark("删除附件");
        attachmentMapper.insertAttachmentLog(log);

        // 软删除主表
        attachmentMapper.deleteAttachmentById(attachmentId);
    }

    @Override
    public List<ProjContractAttachmentLog> getHistory(Long attachmentId)
    {
        return attachmentMapper.selectHistoryByAttachmentId(attachmentId);
    }

    @Override
    public ProjContractAttachmentLog getLogById(Long logId)
    {
        return null; // 由 Controller 通过 history 列表查找
    }

    @Override
    public ProjContractAttachment getAttachmentById(Long attachmentId)
    {
        return attachmentMapper.selectAttachmentById(attachmentId);
    }

    @Override
    public String resolvePhysicalPath(String relativePath)
    {
        if (relativePath == null) return null;
        // /profile/xxx → 替换为实际的项目根路径
        if (relativePath.startsWith(Constants.RESOURCE_PREFIX))
        {
            return LandPatchConfig.getProfile() + relativePath.substring(Constants.RESOURCE_PREFIX.length());
        }
        if (relativePath.startsWith("/"))
        {
            return LandPatchConfig.getProfile() + relativePath;
        }
        return LandPatchConfig.getUploadPath() + File.separator + ATTACHMENT_SUB_DIR + File.separator + relativePath;
    }

    /**
     * 从历史版本恢复为当前版本
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjContractAttachment restoreVersion(Long logId)
    {
        String username = SecurityUtils.getUsername();

        // 找到对应的历史记录
        // 实际中我们遍历所有附件的历史来找，这里简化：先查所有可能的 attachment
        // 更简单的方式：直接在 Controller 层处理查找逻辑
        // 这里 Service 提供一个基于 log 对象恢复的方法
        
        // 由于 MyBatis 的局限，我们这里用另外一种方式实现：
        // Controller 会先根据 logId 获取 log 对象（从 attachment 列表循环找），
        // 然后传递 attachmentId + log 信息给这里的恢复逻辑。
        // 为了简单，我改为在 Service 层实现基于完整 log 信息的恢复。

        // 这个方法的调用方需要先通过 log history 找到对应的 log 对象
        throw new ServiceException("请使用 restoreVersion(ProjContractAttachmentLog log) 方法");
    }

    /**
     * 基于完整 log 信息恢复版本（由 Controller 调用）
     */
    @Transactional(rollbackFor = Exception.class)
    public ProjContractAttachment restoreVersion(ProjContractAttachmentLog logRecord)
    {
        if (logRecord == null)
        {
            throw new ServiceException("历史版本记录不存在");
        }

        String username = SecurityUtils.getUsername();
        ProjContractAttachment att = attachmentMapper.selectAttachmentById(logRecord.getAttachmentId());
        if (att == null)
        {
            throw new ServiceException("附件槽位不存在，无法恢复");
        }

        // 归档当前版本到 log
        ProjContractAttachmentLog currentLog = new ProjContractAttachmentLog();
        currentLog.setAttachmentId(att.getId());
        currentLog.setContractId(att.getContractId());
        currentLog.setFileName(att.getFileName());
        currentLog.setFilePath(att.getFilePath());
        currentLog.setFileSize(att.getFileSize());
        currentLog.setFileType(att.getFileType());
        currentLog.setFileCategory(att.getFileCategory());
        currentLog.setIsFinal(att.getIsFinal());
        currentLog.setVersion(att.getVersion());
        currentLog.setAction("replace");
        currentLog.setOperator(username);
        currentLog.setRemark("恢复历史版本前归档");
        attachmentMapper.insertAttachmentLog(currentLog);

        // 更新主表记录
        int newVersion = att.getVersion() + 1;
        att.setFileName(logRecord.getFileName());
        att.setFilePath(logRecord.getFilePath());
        att.setFileSize(logRecord.getFileSize());
        att.setFileType(logRecord.getFileType());
        att.setIsFinal(logRecord.getIsFinal());
        att.setVersion(newVersion);
        att.setUpdateBy(username);
        attachmentMapper.updateAttachment(att);

        // 记录恢复日志
        ProjContractAttachmentLog restoreLog = new ProjContractAttachmentLog();
        restoreLog.setAttachmentId(att.getId());
        restoreLog.setContractId(att.getContractId());
        restoreLog.setFileName(logRecord.getFileName());
        restoreLog.setFilePath(logRecord.getFilePath());
        restoreLog.setFileSize(logRecord.getFileSize());
        restoreLog.setFileType(logRecord.getFileType());
        restoreLog.setFileCategory(logRecord.getFileCategory());
        restoreLog.setIsFinal(logRecord.getIsFinal());
        restoreLog.setVersion(newVersion);
        restoreLog.setAction("restore");
        restoreLog.setOperator(username);
        restoreLog.setRemark("从 v" + logRecord.getVersion() + " 恢复");
        attachmentMapper.insertAttachmentLog(restoreLog);

        return att;
    }

    // ===== 私有方法 =====

    private String getExtLower(String ext)
    {
        if (ext == null) return "";
        return ext.toLowerCase().replace(".", "");
    }
}
