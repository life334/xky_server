package com.xakcch.web.controller.project;

import java.io.File;
import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.xakcch.common.core.controller.BaseController;
import com.xakcch.common.core.domain.AjaxResult;
import com.xakcch.common.utils.ServletUtils;
import com.xakcch.common.utils.file.FileUtils;
import com.alibaba.fastjson2.JSON;
import com.xakcch.project.domain.ProjContractAttachment;
import com.xakcch.project.domain.ProjContractAttachmentLog;
import com.xakcch.project.service.IProjContractAttachmentService;
import com.xakcch.project.service.impl.ProjContractAttachmentServiceImpl;

/**
 * 合同附件 信息操作处理
 *
 * @author liuyonghui
 */
@RestController
@RequestMapping("/project/contract/attachment")
public class ProjContractAttachmentController extends BaseController
{
    @Autowired
    private IProjContractAttachmentService attachmentService;

    /**
     * 查询指定合同的所有活跃附件
     */
    @GetMapping("/list/{contractId}")
    public AjaxResult list(@PathVariable Long contractId)
    {
        List<ProjContractAttachment> list = attachmentService.listAttachments(contractId);
        return success(list);
    }

    /**
     * 上传附件
     *
     * @param contractId   合同ID
     * @param file         文件
     * @param fileCategory 文件分类（contract/supplement/acceptance/invoice/other）
     * @param isFinal      是否盖章版（1/0）
     */
    @PostMapping("/{contractId}")
    public AjaxResult upload(@PathVariable Long contractId,
                             @RequestParam("file") MultipartFile file,
                             @RequestParam(value = "fileCategory", defaultValue = "other") String fileCategory,
                             @RequestParam(value = "isFinal", defaultValue = "0") String isFinal)
    {
        ProjContractAttachment att = attachmentService.uploadAttachment(contractId, file, fileCategory, isFinal);
        return success(att);
    }

    /**
     * 删除附件（软删除）
     */
    @DeleteMapping("/{attachmentId}")
    public AjaxResult delete(@PathVariable Long attachmentId)
    {
        attachmentService.deleteAttachment(attachmentId);
        return success();
    }

    /**
     * 查询附件版本历史
     */
    @GetMapping("/{attachmentId}/history")
    public AjaxResult history(@PathVariable Long attachmentId)
    {
        List<ProjContractAttachmentLog> logs = attachmentService.getHistory(attachmentId);
        return success(logs);
    }

    /**
     * 恢复指定历史版本为当前版本
     */
    @PutMapping("/{attachmentId}/restore/{logId}")
    public AjaxResult restore(@PathVariable Long attachmentId, @PathVariable Long logId)
    {
        // 从历史列表中查找对应的日志记录
        List<ProjContractAttachmentLog> logs = attachmentService.getHistory(attachmentId);
        ProjContractAttachmentLog targetLog = null;
        for (ProjContractAttachmentLog l : logs)
        {
            if (l.getId().equals(logId))
            {
                targetLog = l;
                break;
            }
        }
        if (targetLog == null)
        {
            return error("历史版本记录不存在");
        }

        ProjContractAttachmentServiceImpl impl = (ProjContractAttachmentServiceImpl) attachmentService;
        ProjContractAttachment restored = impl.restoreVersion(targetLog);
        return success(restored);
    }

    /**
     * 预览/下载附件
     * 支持 ?version=N 参数查看历史版本
     */
    @GetMapping("/{attachmentId}/preview")
    public void preview(@PathVariable Long attachmentId,
                        @RequestParam(value = "version", required = false) Integer version,
                        HttpServletResponse response) throws Exception
    {
        String filePath = null;
        String fileName = "file";

        if (version != null && version > 0)
        {
            // 查历史版本
            List<ProjContractAttachmentLog> logs = attachmentService.getHistory(attachmentId);
            for (ProjContractAttachmentLog l : logs)
            {
                if (l.getVersion().equals(version))
                {
                    filePath = l.getFilePath();
                    fileName = l.getFileName();
                    break;
                }
            }
        }
        else
        {
            // 查活跃附件或已软删的附件
            ProjContractAttachment att = attachmentService.getAttachmentById(attachmentId);
            if (att != null)
            {
                filePath = att.getFilePath();
                fileName = att.getFileName();
            }
            else
            {
                // 兜底：从历史记录取最新版本
                List<ProjContractAttachmentLog> logs = attachmentService.getHistory(attachmentId);
                if (!logs.isEmpty())
                {
                    filePath = logs.get(0).getFilePath();
                    fileName = logs.get(0).getFileName();
                }
            }
        }

        if (filePath == null)
        {
            ServletUtils.renderString(response, JSON.toJSONString(AjaxResult.error(404, "附件文件不存在")));
            return;
        }

        // 通过 Service 统一解析物理路径（兼容 /profile 前缀和简单文件名两种格式）
        String fullPath = attachmentService.resolvePhysicalPath(filePath);

        java.io.File f = new java.io.File(fullPath);
        if (!f.exists())
        {
            ServletUtils.renderString(response, JSON.toJSONString(AjaxResult.error(404, "物理文件不存在")));
            return;
        }

        String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase() : "";
        String contentType = getContentType(ext);
        response.setContentType(contentType);
        response.setHeader("Content-Disposition",
            "inline; filename=\"" + new String(fileName.getBytes("UTF-8"), "ISO-8859-1") + "\"");
        FileUtils.writeBytes(fullPath, response.getOutputStream());
    }

    private String getContentType(String ext)
    {
        switch (ext)
        {
            case "pdf":  return "application/pdf";
            case "jpg":
            case "jpeg": return "image/jpeg";
            case "png":  return "image/png";
            case "gif":  return "image/gif";
            case "svg":  return "image/svg+xml";
            case "doc":  return "application/msword";
            case "docx": return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls":  return "application/vnd.ms-excel";
            case "xlsx": return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default:     return "application/octet-stream";
        }
    }
}
