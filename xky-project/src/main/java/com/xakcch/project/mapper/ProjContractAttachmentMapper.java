package com.xakcch.project.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.xakcch.project.domain.ProjContractAttachment;
import com.xakcch.project.domain.ProjContractAttachmentLog;

/**
 * 合同附件 数据层
 *
 * @author liuyonghui
 */
public interface ProjContractAttachmentMapper
{
    /**
     * 查询指定合同的所有活跃附件
     *
     * @param contractId 合同ID
     * @return 附件列表
     */
    public List<ProjContractAttachment> selectAttachmentsByContractId(Long contractId);

    /**
     * 查询指定附件详情
     *
     * @param id 附件ID
     * @return 附件
     */
    public ProjContractAttachment selectAttachmentById(Long id);

    /**
     * 新增附件
     *
     * @param attachment 附件
     * @return 结果
     */
    public int insertAttachment(ProjContractAttachment attachment);

    /**
     * 更新附件（覆盖新版本时更新 fileName/filePath/fileSize/fileType/isFinal/version）
     *
     * @param attachment 附件
     * @return 结果
     */
    public int updateAttachment(ProjContractAttachment attachment);

    /**
     * 删除附件（逻辑删除）
     *
     * @param id 附件ID
     * @return 结果
     */
    public int deleteAttachmentById(Long id);

    /**
     * 查询某附件槽位的历史版本
     *
     * @param attachmentId 附件槽位ID
     * @return 版本历史列表
     */
    public List<ProjContractAttachmentLog> selectHistoryByAttachmentId(Long attachmentId);

    /**
     * 插入版本历史记录
     *
     * @param log 版本历史
     * @return 结果
     */
    public int insertAttachmentLog(ProjContractAttachmentLog log);
}
