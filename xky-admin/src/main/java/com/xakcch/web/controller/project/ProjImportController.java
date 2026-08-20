package com.xakcch.web.controller.project;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.xakcch.common.core.controller.BaseController;
import com.xakcch.common.core.domain.AjaxResult;
import com.xakcch.common.utils.poi.ExcelUtil;
import com.xakcch.project.domain.ProjImportLog;
import com.xakcch.project.domain.vo.*;
import com.xakcch.project.mapper.ProjImportLogMapper;
import com.xakcch.project.service.IProjImportService;
import com.xakcch.project.service.IProjContractImportService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/project/import")
public class ProjImportController extends BaseController
{
    private static final Logger log = LoggerFactory.getLogger(ProjImportController.class);

    @Autowired private IProjImportService importService;
    @Autowired private IProjContractImportService contractImportService;
    @Autowired private ProjImportLogMapper importLogMapper;
    private final ObjectMapper om = new ObjectMapper();

    /** 步骤1：上传Excel解析预览（不落库），仅返回可导入行 */
    @PostMapping("/preview")
    public AjaxResult preview(@RequestParam("file") MultipartFile file) throws Exception {
        ImportPreviewResponse resp = importService.preview(file);
        return success(resp);
    }

    /** 步骤2：提交确认后的数据落库 */
    @PostMapping("/commit")
    public AjaxResult commit(@RequestBody ImportCommitRequest req) {
        ImportCommitResult r = importService.commit(req);
        log.info("[proj-import] commit finished logId={} succ={} skip={} fail={} failedDetailsSize={} skippedDetailsSize={}",
            r.getLogId(), r.getSuccessCount(), r.getSkippedCount(), r.getFailedCount(),
            r.getFailedDetails() == null ? 0 : r.getFailedDetails().size(),
            r.getSkippedDetails() == null ? 0 : r.getSkippedDetails().size());
        return success(r);
    }

    /** 下载预览问题行明细（type=warning|duplicate|error） */
    @GetMapping("/downloadProblems")
    public void downloadProblems(@RequestParam String token, @RequestParam String type, HttpServletResponse resp) throws Exception {
        List<ProblemRowDetail> rows = importService.getProblems(token, type);
        if (rows == null) rows = Collections.emptyList();
        ExcelUtil<ProblemRowDetail> util = new ExcelUtil<>(ProblemRowDetail.class);
        String sheetName = "warning".equals(type) ? "待修正明细" : "duplicate".equals(type) ? "已存在明细" : "无法导入明细";
        util.exportExcel(resp, rows, sheetName);
    }

    /** 下载失败明细.xlsx */
    @GetMapping("/downloadFailures")
    public void downloadFailures(@RequestParam Long logId, HttpServletResponse resp) throws Exception {
        ProjImportLog lg = importLogMapper.selectImportLogById(logId);
        List<ImportCommitResult.RowDetail> rows = new ArrayList<>();
        if (lg != null && lg.getFailDetails() != null && !lg.getFailDetails().trim().isEmpty()
            && !"[]".equals(lg.getFailDetails().trim()) && !"null".equalsIgnoreCase(lg.getFailDetails().trim())) {
            try {
                rows = om.readValue(lg.getFailDetails(), new TypeReference<List<ImportCommitResult.RowDetail>>() {});
                if (rows == null) rows = new ArrayList<>();
            } catch (Exception ex) {
                log.warn("[proj-import] parse fail_details failed logId={} msg={}", logId, ex.getMessage());
                rows = new ArrayList<>();
            }
        }
        log.info("[proj-import] downloadFailures logId={} rowsInDB={} parsedRows={}", logId,
            (lg == null ? "no_log" : (lg.getFailDetails() == null ? "null" : lg.getFailDetails().length() + "chars")),
            rows.size());
        ExcelUtil<ImportCommitResult.RowDetail> util = new ExcelUtil<>(ImportCommitResult.RowDetail.class);
        util.exportExcel(resp, rows, "失败明细");
    }

    /** 下载跳过明细.xlsx */
    @GetMapping("/downloadSkipped")
    public void downloadSkipped(@RequestParam Long logId, HttpServletResponse resp) throws Exception {
        ProjImportLog lg = importLogMapper.selectImportLogById(logId);
        List<ImportCommitResult.RowDetail> rows = new ArrayList<>();
        if (lg != null && lg.getSkipDetails() != null && !lg.getSkipDetails().trim().isEmpty()
            && !"[]".equals(lg.getSkipDetails().trim()) && !"null".equalsIgnoreCase(lg.getSkipDetails().trim())) {
            try {
                rows = om.readValue(lg.getSkipDetails(), new TypeReference<List<ImportCommitResult.RowDetail>>() {});
                if (rows == null) rows = new ArrayList<>();
            } catch (Exception ex) {
                log.warn("[proj-import] parse skip_details failed logId={} msg={}", logId, ex.getMessage());
                rows = new ArrayList<>();
            }
        }
        log.info("[proj-import] downloadSkipped logId={} rowsInDB={} parsedRows={}", logId,
            (lg == null ? "no_log" : (lg.getSkipDetails() == null ? "null" : lg.getSkipDetails().length() + "chars")),
            rows.size());
        ExcelUtil<ImportCommitResult.RowDetail> util = new ExcelUtil<>(ImportCommitResult.RowDetail.class);
        util.exportExcel(resp, rows, "跳过明细");
    }

    // ====================== 合同数据导入 ======================

    /** 合同导入-步骤1：上传Excel解析预览（不落库） */
    @PostMapping("/contract/preview")
    public AjaxResult contractPreview(@RequestParam("file") MultipartFile file) throws Exception {
        ContractImportPreviewResponse resp = contractImportService.previewContract(file);
        return success(resp);
    }

    /** 合同导入-步骤2：提交确认后的数据落库 */
    @PostMapping("/contract/commit")
    public AjaxResult contractCommit(@RequestBody ImportCommitRequest req) {
        ImportCommitResult r = contractImportService.commitContract(req);
        log.info("[proj-contract-import] commit finished logId={} succ={} skip={} fail={} failedDetailsSize={} skippedDetailsSize={}",
            r.getLogId(), r.getSuccessCount(), r.getSkippedCount(), r.getFailedCount(),
            r.getFailedDetails() == null ? 0 : r.getFailedDetails().size(),
            r.getSkippedDetails() == null ? 0 : r.getSkippedDetails().size());
        return success(r);
    }

    /** 合同导入-下载预览问题行明细（type=duplicate|error） */
    @GetMapping("/contract/downloadProblems")
    public void contractDownloadProblems(@RequestParam String token, @RequestParam String type, HttpServletResponse resp) throws Exception {
        List<ProblemRowDetail> rows = contractImportService.getContractProblems(token, type);
        if (rows == null) rows = Collections.emptyList();
        ExcelUtil<ProblemRowDetail> util = new ExcelUtil<>(ProblemRowDetail.class);
        String sheetName = "duplicate".equals(type) ? "已存在明细" : "无法导入明细";
        util.exportExcel(resp, rows, sheetName);
    }
}
