package com.xakcch.project.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import com.xakcch.common.utils.SecurityUtils;
import com.xakcch.project.domain.*;
import com.xakcch.project.domain.vo.*;
import com.xakcch.project.mapper.*;
import com.xakcch.project.service.IProjImportService;
import com.xakcch.project.service.IProjMandateService;

@Service
public class ProjImportServiceImpl implements IProjImportService
{
    static class SessionEntry {
        final ImportPreviewResponse resp;
        final long createAt;
        final AtomicBoolean committing = new AtomicBoolean(false);
        volatile Long logId;            // 最近一次提交的导入日志ID
        volatile ImportCommitResult commitResult; // 已提交完成时缓存结果
        SessionEntry(ImportPreviewResponse r) { this.resp = r; this.createAt = System.currentTimeMillis(); }
        boolean expired() { return System.currentTimeMillis() - createAt > 2 * 3600 * 1000L; }
    }
    private static final Map<String, SessionEntry> SESSION = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService CLEANER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "proj-import-session-cleaner"); t.setDaemon(true); return t;
    });
    static {
        CLEANER.scheduleAtFixedRate(() -> {
            try { SESSION.entrySet().removeIf(e -> e.getValue().expired()); }
            catch (Throwable ignore) { /* ignore */ }
        }, 10, 10, TimeUnit.MINUTES);
    }

    /** 导入落库后台线程池：单线程串行执行，避免同库并发写连接争用 */
    private static final ExecutorService IMPORT_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "proj-import-worker"); t.setDaemon(true); return t;
    });
    /** 事务批大小：每批 TX_BATCH_SIZE 个工程编号组共用一次事务提交（减少 WAL fsync） */
    private static final int TX_BATCH_SIZE = 20;

    /**
     * 历史上报补录行的 {@code submit_by} 标识。
     * <p>约定：库里的码值/标识一律用英文，中文只在前端做展示映射
     * （前端 {@code utils/projStatus.js#submitByText} 把 import →「历史导入」）。</p>
     */
    private static final String SUBMIT_BY_IMPORT = "import";

    @Autowired private ProjProjectMapper projectMapper;
    @Autowired private ProjCategoryMapper categoryMapper;
    @Autowired private ProjCategoryBillingMapper billingMapper;
    @Autowired private ProjLeaderMapper leaderMapper;
    @Autowired private ProjTaskMapper taskMapper;
    @Autowired private ProjWorkloadMapper workloadMapper;
    @Autowired private ProjPaymentMapper paymentMapper;
    @Autowired private ProjReportSubmitMapper reportSubmitMapper;
    @Autowired private ProjMaterialMapper materialMapper;
    @Autowired private ProjMaterialFlowMapper materialFlowMapper;
    @Autowired private ProjImportLogMapper importLogMapper;
    @Autowired private IProjMandateService mandateService;
    @Autowired private PlatformTransactionManager txManager;
    private transient TransactionTemplate _txRequiresNew;
    private TransactionTemplate txRequiresNew() {
        if (_txRequiresNew == null) {
            DefaultTransactionDefinition def = new DefaultTransactionDefinition();
            def.setName("proj-import-row");
            def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            _txRequiresNew = new TransactionTemplate(txManager, def);
        }
        return _txRequiresNew;
    }
    @Autowired private com.xakcch.project.service.IProjProjectService projectService;

    private ObjectMapper om = new ObjectMapper();

    // ====================== 预览 ======================
    @Override
    public ImportPreviewResponse preview(MultipartFile file) throws Exception {
        ImportPreviewResponse fullResp = new ImportPreviewResponse();
        Map<String, List<List<Object>>> book = readAllSheets(file);
        loadMetaOptions(fullResp);
        parseSheet1(book, fullResp);
        parseSheet2AndMerge(book, fullResp);
        recalcPrices(fullResp);
        // 分类
        List<ImportPreviewRow> readyRows = new ArrayList<>();
        List<ImportPreviewRow> problemRows = new ArrayList<>();
        // 已存在工程编号预取：命中者导入时整组跳过（预览阶段即提示用户）
        Map<String, Long> existingIdByCode = new HashMap<>();
        List<String> allCodes = fullResp.getRows().stream()
            .map(ImportPreviewRow::getProjectCode)
            .filter(StringUtils::isNotBlank)
            .map(String::trim)
            .distinct()
            .collect(Collectors.toList());
        if (!allCodes.isEmpty()) {
            List<Map<String, Object>> hits = projectMapper.selectProjectIdsByCodes(allCodes);
            for (Map<String, Object> m : hits) {
                Object codeObj = m.get("project_code");
                Object idObj = m.get("id");
                if (codeObj != null && idObj != null) {
                    existingIdByCode.put(String.valueOf(codeObj), ((Number) idObj).longValue());
                }
            }
        }
        int existsCnt = 0;
        int payOnlyCnt = 0;
        List<String> existsCodes = new ArrayList<>();
        for (ImportPreviewRow r : fullResp.getRows()) {
            // 「仅补充到账」行：只覆盖写付款、不建项目，跳过类别/计费/委托任务等常规校验，单独计数
            if (Boolean.TRUE.equals(r.getPayOnly())) { payOnlyCnt++; continue; }
            if (r.getErrors() != null && !r.getErrors().isEmpty()) { problemRows.add(r); continue; }
            if (StringUtils.isBlank(r.getProjectCode())) {
                r.getErrors().add("工程编号为空");
                problemRows.add(r); continue;
            }
            if (StringUtils.isBlank(r.getEngineeringProject())) {
                r.getWarnings().add("委托任务为空，无法匹配项目类别");
                problemRows.add(r); continue;
            }
            boolean missCat = r.getProjectCategoryId() == null;
            // 负责人未匹配不再阻断：落库时自动创建负责人档案（影子用户）
            boolean missBill = r.getWorkloads().stream().anyMatch(w -> w.getBillingId() == null);
            boolean hasWarn = (r.getWarnings() != null && !r.getWarnings().isEmpty())
                || r.getWorkloads().stream().anyMatch(w -> w.getWarning() != null && !w.getWarning().isEmpty());
            if (missCat || missBill || hasWarn) {
                problemRows.add(r); continue;
            }
            readyRows.add(r);
            // 工程编号已存在 → 标记（导入时整组跳过，不计入可写入行数）
            Long existedId = existingIdByCode.get(r.getProjectCode().trim());
            if (existedId != null) {
                r.setExistsInDb(true);
                r.setExistingProjectId(existedId);
                existsCnt++;
                String code = r.getProjectCode().trim();
                if (!existsCodes.contains(code)) existsCodes.add(code);
            }
        }
        // totalRows 只统计「产值计算」页解析出的行，不含仅补充到账的行
        fullResp.setTotalRows(fullResp.getRows().size() - payOnlyCnt);
        // 统计问题行分类
        int errCnt = 0, warnCnt = 0;
        for (ImportPreviewRow r : problemRows) {
            if (r.getErrors() != null && !r.getErrors().isEmpty()) errCnt++;
            else warnCnt++;
        }
        fullResp.setErrorCount(errCnt);
        fullResp.setWarningCount(warnCnt);
        // readyCount = 实际将写入的行数（已存在的编号不写入）
        fullResp.setReadyCount(readyRows.size() - existsCnt);
        fullResp.setExistsCount(existsCnt);
        fullResp.setExistsCodes(existsCodes);
        fullResp.setPayOnlyCount(payOnlyCnt);
        // 「待补到账」已存在项目数（去重编号）：只统计「本次到账与库内现值确有差异」的编号。
        //   · 差异判定 = 同类型比对 金额 + 到账时间（库中无该类型 / 金额不同 / 时间不同 ⇒ 有差异）
        //   · 台账是累计口径，重复导入或跨文件交叉时会带上与库里完全相同的到账行 —— 这类属于幂等
        //     重复，不该提示「补到账」，否则预览显示与真实变化不符（2025管定1115 就是这么被误标的）
        // 口径与 runCommitAsync 的跳过分支严格一致，避免前端数字和真实写入条数对不上。
        int payWriteCnt = 0;
        Map<String, List<ImportPreviewRow>> rowsByCode = new LinkedHashMap<>();
        for (ImportPreviewRow r : fullResp.getRows()) {
            if (StringUtils.isBlank(r.getProjectCode())) continue;
            rowsByCode.computeIfAbsent(r.getProjectCode().trim(), k -> new ArrayList<>()).add(r);
        }
        // 批量取「已存在编号」的库内付款现值（一次查询带出，不做 N+1）
        Map<Long, Map<String, ProjPayment>> dbPaysByProject =
            loadExistingPayments(new LinkedHashSet<>(existingIdByCode.values()));
        for (Map.Entry<String, List<ImportPreviewRow>> e : rowsByCode.entrySet()) {
            boolean existsGroup = e.getValue().stream().anyMatch(x -> Boolean.TRUE.equals(x.getExistsInDb()));
            if (!existsGroup) continue; // 新项目：到账随项目一起写，不属于「补录」
            Long gid = null;
            for (ImportPreviewRow r : e.getValue()) {
                if (r.getExistingProjectId() != null) { gid = r.getExistingProjectId(); break; }
            }
            List<ImportPreviewPayment> mergedPays = mergeGroupPayments(e.getValue());
            if (!payDiffers(dbPaysByProject.get(gid), mergedPays)) continue; // 与库内一致 → 不算待补
            payWriteCnt++;
            for (ImportPreviewRow r : e.getValue()) r.setPayChanged(true);
        }
        fullResp.setPayWriteCount(payWriteCnt);
        // 问题摘要
        ImportPreviewResponse.ProblemSummary ps = new ImportPreviewResponse.ProblemSummary();
        if (warnCnt > 0) ps.setWarningDesc(warnCnt + " 行数据存在未匹配字段（项目类别/负责人/计费类别等），请查看下方明细，修正Excel后重新上传");
        if (errCnt > 0) ps.setErrorDesc(errCnt + " 行缺少关键字段，无法导入");
        fullResp.setProblemSummary(ps);
        // 生成token，缓存全量数据
        String token = UUID.randomUUID().toString().replace("-", "");
        fullResp.setToken(token);
        SESSION.put(token, new SessionEntry(fullResp));
        // 构建轻量响应
        ImportPreviewResponse lightResp = new ImportPreviewResponse();
        lightResp.setToken(token);
        lightResp.setTotalRows(fullResp.getTotalRows());
        lightResp.setReadyCount(fullResp.getReadyCount());
        lightResp.setWarningCount(warnCnt);
        lightResp.setErrorCount(errCnt);
        lightResp.setExistsCount(existsCnt);
        lightResp.setExistsCodes(existsCodes);
        lightResp.setPayOnlyCount(payOnlyCnt);
        lightResp.setPayWriteCount(payWriteCnt);
        lightResp.setUnmatchedPayCodes(fullResp.getUnmatchedPayCodes());
        lightResp.setProblemSummary(ps);
        lightResp.getCategoryOptions().addAll(fullResp.getCategoryOptions());
        lightResp.getBillingOptions().addAll(fullResp.getBillingOptions());
        lightResp.getRows().addAll(readyRows);
        // 「仅补充到账」行必须一并回传：①预览表格要能看到；②提交时前端是带着 rows 回传的
        // （commit 取 req.getRows()），漏传 = 到账永远补不上。
        for (ImportPreviewRow r : fullResp.getRows()) {
            if (Boolean.TRUE.equals(r.getPayOnly())) lightResp.getRows().add(r);
        }
        // 问题行明细（供前端页面展示）
        List<ProblemRowDetail> pDetails = new ArrayList<>();
        for (ImportPreviewRow r : problemRows) pDetails.add(buildProblemDetail(r));
        lightResp.setProblemRows(pDetails);
        return lightResp;
    }

    // ====================== 构建单行问题明细 ======================
    private ProblemRowDetail buildProblemDetail(ImportPreviewRow r) {
        List<String> problems = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        String probType;
        if (r.getErrors() != null && !r.getErrors().isEmpty()) {
            probType = "无法导入";
            problems.addAll(r.getErrors());
            suggestions.add("请补全Excel中缺失的关键字段");
        } else {
            probType = "待修正";
            if (r.getProjectCategoryId() == null) {
                problems.add(StringUtils.isNotBlank(r.getEngineeringProject())
                    ? "委托任务「" + r.getEngineeringProject() + "」无法匹配到项目类别"
                    : "委托任务为空，无法匹配项目类别");
                suggestions.add("请在系统中确认项目类别名称，或修改Excel中的委托任务名称");
            }
            for (ImportPreviewWorkload w : r.getWorkloads()) {
                if (w.getBillingId() == null) {
                    String disp = (w.getBillingCategoryRaw() == null ? "" : w.getBillingCategoryRaw())
                        .replaceAll("[（(](内部|外部)[）)]", "").trim();
                    if (disp.isEmpty()) disp = w.getBillingCategoryRaw();
                    problems.add("计费类别「" + disp + "」无法匹配");
                    suggestions.add("请在系统中确认计费类别配置");
                }
            }
            if (r.getWarnings() != null) {
                for (String warn : r.getWarnings()) {
                    if (!problems.contains(warn)) problems.add(warn);
                }
            }
        }
        ProblemRowDetail d = new ProblemRowDetail();
        d.setExcelRow(r.getExcelRow());
        d.setProjectCode(r.getProjectCode());
        d.setClientUnit(r.getClientUnit());
        d.setEngineeringProject(r.getEngineeringProject());
        d.setLeaderName(r.getLeaderName());
        d.setProblemType(probType);
        d.setProblemDetail(String.join("；", problems));
        d.setSuggestion(String.join("；", suggestions.isEmpty() ? Collections.singletonList("-") : suggestions));
        return d;
    }

    // ====================== 获取问题行明细（下载Excel用） ======================
    @Override
    public List<ProblemRowDetail> getProblems(String token, String type) {
        SessionEntry entry = SESSION.get(token);
        if (entry == null || entry.expired()) throw new RuntimeException("会话已过期，请重新解析");
        ImportPreviewResponse cached = entry.resp;
        List<ProblemRowDetail> all = new ArrayList<>();
        for (ImportPreviewRow r : cached.getRows()) {
            boolean isTarget = false;
            if ("error".equals(type) && r.getErrors() != null && !r.getErrors().isEmpty()) isTarget = true;
            else if ("warning".equals(type)
                && (r.getErrors() == null || r.getErrors().isEmpty())
                && (r.getProjectCategoryId() == null
                    || (StringUtils.isNotBlank(r.getLeaderName()) && r.getLeaderId() == null)
                    || r.getWorkloads().stream().anyMatch(w -> w.getBillingId() == null)
                    || (r.getWarnings() != null && !r.getWarnings().isEmpty())
                    || r.getWorkloads().stream().anyMatch(w -> w.getWarning() != null && !w.getWarning().isEmpty())
                    || StringUtils.isBlank(r.getEngineeringProject()))) isTarget = true;
            if (!isTarget) continue;
            all.add(buildProblemDetail(r));
        }
        return all;
    }

    // ====================== 读取 Excel ======================
    private Map<String, List<List<Object>>> readAllSheets(MultipartFile file) throws Exception {
        Map<String, List<List<Object>>> result = new LinkedHashMap<>();
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            DataFormatter fmt = new DataFormatter();
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet sheet = wb.getSheetAt(s);
                String name = sheet.getSheetName();
                List<List<Object>> rows = new ArrayList<>();
                int lastRow = sheet.getLastRowNum();
                for (int i = 0; i <= lastRow; i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) { rows.add(new ArrayList<>()); continue; }
                    int lastCol = row.getLastCellNum();
                    List<Object> cells = new ArrayList<>(lastCol < 0 ? 0 : lastCol);
                    for (int c = 0; c < lastCol; c++) {
                        Cell cell = row.getCell(c);
                        Object v;
                        if (cell == null) v = null;
                        else if (cell.getCellType() == CellType.NUMERIC) {
                            if (DateUtil.isCellDateFormatted(cell)) v = cell.getDateCellValue();
                            else v = cell.getNumericCellValue();
                        } else if (cell.getCellType() == CellType.BOOLEAN) v = cell.getBooleanCellValue();
                        else if (cell.getCellType() == CellType.FORMULA) {
                            try { v = cell.getNumericCellValue(); }
                            catch (Exception e) { v = fmt.formatCellValue(cell); }
                        } else {
                            v = fmt.formatCellValue(cell).trim();
                            if ("".equals(v)) v = null;
                        }
                        cells.add(v);
                    }
                    rows.add(cells);
                }
                expandMerge(sheet, rows);
                result.put(name, rows);
            }
        }
        return result;
    }

    private void expandMerge(Sheet sheet, List<List<Object>> rows) {
        for (var mr : sheet.getMergedRegions()) {
            Object topVal = getCell(rows, mr.getFirstRow(), mr.getFirstColumn());
            if (topVal == null) continue;
            for (int r = mr.getFirstRow(); r <= mr.getLastRow(); r++) {
                for (int c = mr.getFirstColumn(); c <= mr.getLastColumn(); c++) {
                    if (r == mr.getFirstRow() && c == mr.getFirstColumn()) continue;
                    setCell(rows, r, c, topVal);
                }
            }
        }
    }
    private Object getCell(List<List<Object>> rows, int r, int c) {
        if (r >= rows.size()) return null;
        List<Object> row = rows.get(r);
        if (row == null || c >= row.size()) return null;
        return row.get(c);
    }
    private void setCell(List<List<Object>> rows, int r, int c, Object val) {
        while (rows.size() <= r) rows.add(new ArrayList<>());
        List<Object> row = rows.get(r);
        while (row.size() <= c) row.add(null);
        if (row.get(c) == null || "".equals(row.get(c))) row.set(c, val);
    }

    // ====================== 加载下拉选项 ======================
    private void loadMetaOptions(ImportPreviewResponse resp) {
        ProjCategory q = new ProjCategory();
        q.setLevel(2);
        q.setStatus("0");
        List<ProjCategory> cats = categoryMapper.selectCategoryList(q);
        for (ProjCategory c : cats) {
            ImportPreviewResponse.CategoryOption co = new ImportPreviewResponse.CategoryOption();
            co.setId(c.getId()); co.setName(c.getName()); co.setParentId(c.getParentId());
            resp.getCategoryOptions().add(co);
        }
        ProjCategoryBilling bq = new ProjCategoryBilling(); bq.setStatus("0");
        List<ProjCategoryBilling> bills = billingMapper.selectBillingList(bq);
        for (ProjCategoryBilling b : bills) {
            ImportPreviewResponse.BillingOption bo = new ImportPreviewResponse.BillingOption();
            bo.setBillingId(b.getId()); bo.setCategoryId(b.getCategoryId());
            bo.setBillingType(b.getBillingType()); bo.setBillingCategory(b.getBillingCategory());
            bo.setUnitPrice(b.getUnitPrice()); bo.setPriceUnit(b.getPriceUnit()); bo.setMinQuantity(b.getMinQuantity());
            resp.getBillingOptions().add(bo);
        }
    }

    // ====================== 解析 Sheet1 ======================
    private void parseSheet1(Map<String, List<List<Object>>> book, ImportPreviewResponse resp) {
        List<List<Object>> rows = null;
        for (Map.Entry<String,List<List<Object>>> e : book.entrySet()) {
            if (rows == null) rows = e.getValue();
        }
        if (rows == null || rows.isEmpty()) return;
        int headerRowIdx = -1;
        for (int i = 0; i < Math.min(rows.size(), 10); i++) {
            List<Object> r = rows.get(i);
            for (Object o : r) {
                if (o != null && o.toString().contains("工程编号")) { headerRowIdx = i; break; }
            }
            if (headerRowIdx >= 0) break;
        }
        if (headerRowIdx < 0) return;
        List<Object> headerL1 = rows.get(headerRowIdx);
        Map<String, Integer> colMap = new HashMap<>();
        Map<Integer, String> billingCol = new LinkedHashMap<>();
        List<Object> headerL2 = headerRowIdx > 0 ? rows.get(headerRowIdx - 1) : null;
        for (int c = 0; c < headerL1.size(); c++) {
            Object v = headerL1.get(c);
            if (v == null) continue;
            String s = v.toString().trim();
            if (s.isEmpty()) continue;
            colMap.put(s, c);
        }
        // 工作量列：一级表头需属于（管线定验线、实测工作量 / 管线图工作量 / 其它工作量）三类之一
        if (headerL2 != null) {
            for (int c = 0; c < headerL2.size(); c++) {
                if (headerL2.get(c) == null) continue;
                String top = headerL2.get(c).toString().trim();
                if (top.isEmpty() || top.contains("合计")) continue;
                String sub = (c < headerL1.size() && headerL1.get(c) != null) ? headerL1.get(c).toString().trim() : "";
                if (sub.isEmpty()) continue;
                // 历史文件图组内的「秦华外部」列不是工作量项，保持排除
                if (sub.equals("秦华外部")) continue;
                if (isWorkloadTopHeader(top)) {
                    billingCol.put(c, sub);
                }
            }
        }
        int colProjectCode = firstMatch(colMap, "工程编号");
        int colClientUnit  = firstMatch(colMap, "委托单位");
        int colProject     = firstMatch(colMap, "委托任务");
        int colLocation    = firstMatch(colMap, "委托地点|工程地点");
        int colLeader      = firstMatch(colMap, "项目负责人|负责人");
        int colFinish      = firstMatch(colMap, "验收日期|办结日期");
        // 内部产值合计/外部产值合计：产值只读合计列，不再读 J~N、P~S 细分列。
        // 一级表头(headerL2)中「内部产值」「外部产值」组之后的第一个「合计」列即对应合计列（O/T）。
        int colInternalTot = -1;
        int colExternalTot = -1;
        if (headerL2 != null) {
            int internalGroupStart = -1, externalGroupStart = -1;
            for (int c = 0; c < headerL2.size(); c++) {
                String top = headerL2.get(c) == null ? "" : headerL2.get(c).toString().trim();
                if (top.contains("内部产值")) internalGroupStart = c;
                else if (top.contains("外部产值")) externalGroupStart = c;
            }
            if (internalGroupStart >= 0) {
                for (int c = internalGroupStart + 1; c < headerL2.size(); c++) {
                    String top = headerL2.get(c) == null ? "" : headerL2.get(c).toString().trim();
                    if (top.contains("合计")) { colInternalTot = c; break; }
                }
            }
            if (externalGroupStart >= 0) {
                for (int c = externalGroupStart + 1; c < headerL2.size(); c++) {
                    String top = headerL2.get(c) == null ? "" : headerL2.get(c).toString().trim();
                    if (top.contains("合计")) { colExternalTot = c; break; }
                }
            }
        }
        int colPayTime     = firstMatch(colMap, "到账时间|付款时间");
        int colMaterial    = firstMatch(colMap, "资料领取");
        
        for (int r = headerRowIdx + 1; r < rows.size(); r++) {
            List<Object> row = rows.get(r);
            if (row == null || row.isEmpty()) continue;
            String projectCode = strCell(row, colProjectCode);
            if (StringUtils.isBlank(projectCode)) continue;
            ImportPreviewRow pr = new ImportPreviewRow();
            pr.setExcelRow(r + 1);
            pr.setProjectCode(projectCode.trim());
            pr.setClientUnit(strCell(row, colClientUnit));
            pr.setEngineeringProject(strCell(row, colProject));
            pr.setProjectLocation(strCell(row, colLocation));
            pr.setLeaderName(strCell(row, colLeader));
            pr.setFinishDate(dateCell(row, colFinish));
            Date mat = dateCell(row, colMaterial);
            if (mat == null && StringUtils.isNotBlank(strCell(row, colMaterial))) {
                mat = pr.getFinishDate();
            }
            pr.setMaterialSubmitTime(mat);
            if (StringUtils.isNotBlank(pr.getEngineeringProject())) {
                FuzzyResult<ProjCategory> fr = fuzzyMatchCategory(pr.getEngineeringProject(), resp.getCategoryOptions());
                if (fr != null && fr.score >= 0.5) {
                    pr.setProjectCategoryId(fr.item.getId());
                    pr.setProjectCategoryName(fr.item.getName());
                    pr.setProjectCategoryScore(fr.score);
                } else if (fr != null) {
                    pr.getWarnings().add("项目类别匹配度" + String.format("%.2f", fr.score) + "，请手动选择");
                } else {
                    pr.getWarnings().add("未匹配到项目类别，请手动选择");
                }
            } else {
                pr.getWarnings().add("委托任务为空，无法匹配项目类别");
            }
            // 负责人昵称 → userId 的解析不在这里做：逐行查库在 2000 行时就是 2000 次额外网络往返
            // （预览阶段最大的性能损失点）。改为循环结束后一次批量查，见方法末尾。
            // 产值只读合计：内部产值合计(O)、外部产值合计(T)
            // 注意：外部产值合计需在解析工作量之前读取，用于判定「外部产值空/0 → 不导入外部工作量」
            BigDecimal internalTotal = numCell(row, colInternalTot);
            BigDecimal externalTotal = numCell(row, colExternalTot);
            boolean externalOutputEmpty = (externalTotal == null || externalTotal.signum() == 0);

            // 解析工作量（billingCol: 一级表头属于工作量组的列）
            // 历史数据存在跨类型填列（如「管线实测」行填了图组工作量），经与客户确认同样应当导入，
            // 因此不再做「委托任务 × 表头组」匹配校验，只要填了非空非 0 的值即导入。
            // 例外：外部产值合计为空或 0 时，外部工作量不读取、不导入（内部工作量照常）。
            for (Map.Entry<Integer, String> me : billingCol.entrySet()) {
                int col = me.getKey();
                String rawHeader = me.getValue();
                BigDecimal wl = numCell(row, col);
                if (wl == null || wl.signum() == 0) continue;
                // 内/外判定：优先显式(内部)/(外部)；其次"水准"/"管线"特殊规则（包含即触发）
                boolean isInt = isInternalWorkload(rawHeader, pr.getEngineeringProject());
                if (!isInt && externalOutputEmpty) continue;   // 外部产值空/0 → 丢弃外部工作量
                ImportPreviewWorkload w = new ImportPreviewWorkload();
                w.setBillingCategoryRaw(rawHeader);
                w.setWorkload(wl);
                w.setBillingType(isInt ? "internal" : "external");
                // 提取计费类别名：去掉(内部)/(外部)/(内)/(外)及空白
                String billingCatName = rawHeader.replaceAll("[（(](内部|外部|内|外)[）)]", "").trim();
                FuzzyBilling fb = fuzzyMatchBilling(billingCatName, w.getBillingType(), resp.getBillingOptions(), resp.getCategoryOptions(), pr.getProjectCategoryId());
                if (fb != null && fb.score >= 0.5) {
                    w.setBillingId(fb.billingId);
                    w.setBillingCategory(fb.billingCategory);
                    w.setCategoryId(fb.categoryId);
                    w.setScore(fb.score);
                    w.setPriceUnit(fb.priceUnit);
                    w.setMinQuantity(fb.minQuantity);
                    w.setUnitPrice(fb.unitPrice);
                } else if (fb != null) {
                    w.setWarning("计费类别「" + billingCatName + "」匹配度" + String.format("%.2f", fb.score) + "，请手动选择");
                } else {
                    w.setWarning("未匹配到计费类别「" + billingCatName + "」，请手动选择");
                }
                pr.getWorkloads().add(w);
            }
            if (internalTotal != null && internalTotal.signum() > 0) pr.setInternalTotalFromExcel(internalTotal);
            if (externalTotal != null && externalTotal.signum() > 0) pr.setExternalTotalFromExcel(externalTotal);
            resp.getRows().add(pr);
        }

        // 负责人昵称 → userId：循环结束后一次批量查（原实现逐行 selectUserByNickName）。
        // selectUsersByNickNames 按「在职优先 + user_id 升序」返回，putIfAbsent 命中的即为
        // 原 selectUserByNickName 的 limit 1 结果（含离职/影子用户）。匹配不到则预览阶段静默跳过，
        // 落库时由 prefetchLeaders 自动建档（影子用户）。
        LinkedHashSet<String> leaderNames = new LinkedHashSet<>();
        for (ImportPreviewRow pr : resp.getRows()) {
            if (pr.getLeaderId() == null && StringUtils.isNotBlank(pr.getLeaderName())) {
                leaderNames.add(pr.getLeaderName().trim());
            }
        }
        if (!leaderNames.isEmpty()) {
            Map<String, Long> uidByName = new HashMap<>();
            for (com.xakcch.common.core.domain.entity.SysUser u
                    : leaderMapper.selectUsersByNickNames(new ArrayList<>(leaderNames))) {
                if (u.getNickName() != null) uidByName.putIfAbsent(u.getNickName().trim(), u.getUserId());
            }
            for (ImportPreviewRow pr : resp.getRows()) {
                if (pr.getLeaderId() == null && StringUtils.isNotBlank(pr.getLeaderName())) {
                    Long uid = uidByName.get(pr.getLeaderName().trim());
                    if (uid != null) {
                        pr.setLeaderId(uid);
                        pr.setLeaderScore(1.0);
                    }
                }
            }
        }
    }

    private void recalcPrices(ImportPreviewResponse resp) {
        for (ImportPreviewRow row : resp.getRows()) {
            List<ImportPreviewWorkload> internals = row.getWorkloads().stream().filter(w-> "internal".equals(w.getBillingType())).collect(Collectors.toList());
            List<ImportPreviewWorkload> externals = row.getWorkloads().stream().filter(w-> "external".equals(w.getBillingType())).collect(Collectors.toList());
            calcGroup(internals, row.getInternalTotalFromExcel(), true, row);
            calcGroup(externals, row.getExternalTotalFromExcel(), false, row);
        }
    }
    private void calcGroup(List<ImportPreviewWorkload> list, BigDecimal total, boolean isInternal, ImportPreviewRow row) {
        final BigDecimal zero = BigDecimal.ZERO;
        if (isInternal) row.setInternalTotalCalced(zero); else row.setExternalTotalCalced(zero);
        if (list.isEmpty() || total == null || total.signum() == 0) return;

        // 只保留工作量 > 0 的项（工作量为 0 的项产值直接为 0，不参与分摊；不考虑起步量）
        List<ImportPreviewWorkload> items = new ArrayList<>();
        for (ImportPreviewWorkload w : list) {
            if (w.getWorkload() != null && w.getWorkload().signum() > 0) items.add(w);
        }
        if (items.isEmpty()) return;

        // 分组：有配置单价 vs 无配置单价（匹配到小类，unitPrice 为 null）
        List<ImportPreviewWorkload> priced = new ArrayList<>();
        List<ImportPreviewWorkload> unpriced = new ArrayList<>();
        for (ImportPreviewWorkload w : items) {
            if (w.getUnitPrice() != null && w.getUnitPrice().signum() > 0) priced.add(w);
            else unpriced.add(w);
        }

        if (unpriced.isEmpty()) {
            // 全部有单价：等比缩放 k = 合计 ÷ Σ(配置单价×工作量)，单价_i = 配置单价_i × k
            BigDecimal baseSum = sumBase(priced);
            if (baseSum.signum() > 0) {
                final BigDecimal k = total.divide(baseSum, 6, RoundingMode.HALF_UP);
                assignOutputs(priced, total, w -> w.getUnitPrice().multiply(k));
            }
        } else if (priced.isEmpty()) {
            // 全部无单价：按工作量均分，单价 = 合计 ÷ Σ工作量
            BigDecimal wlSum = sumWorkload(items);
            if (wlSum.signum() > 0) {
                final BigDecimal k = total.divide(wlSum, 6, RoundingMode.HALF_UP);
                assignOutputs(items, total, w -> k);
            }
        } else {
            // 混合：有单价项按配置单价原价，无单价项瓜分剩余 = 合计 - Σ(有单价项原价产值)
            BigDecimal baseSum = sumBase(priced);
            BigDecimal remain = total.subtract(baseSum);
            if (remain.signum() > 0) {
                for (ImportPreviewWorkload w : priced) {
                    w.setOutput(w.getUnitPrice().multiply(w.getWorkload()).setScale(2, RoundingMode.HALF_UP));
                }
                BigDecimal wlSum = sumWorkload(unpriced);
                if (wlSum.signum() > 0) {
                    final BigDecimal k = remain.divide(wlSum, 6, RoundingMode.HALF_UP);
                    assignOutputs(unpriced, remain, w -> k);
                }
            } else {
                // 有单价项原价已超总额：整体等比缩放拉回，无单价项记 0
                final BigDecimal k = total.divide(baseSum, 6, RoundingMode.HALF_UP);
                assignOutputs(priced, total, w -> w.getUnitPrice().multiply(k));
                for (ImportPreviewWorkload w : unpriced) { w.setUnitPrice(zero); w.setOutput(zero); }
            }
        }

        BigDecimal sum = zero;
        for (ImportPreviewWorkload w : items) {
            if (w.getOutput() != null) sum = sum.add(w.getOutput());
        }
        if (isInternal) row.setInternalTotalCalced(sum); else row.setExternalTotalCalced(sum);
    }

    /** 末项尾差兜底：按 unitPriceFn 算各单价，产值=单价×工作量，末项产值=合计-前面之和 */
    private void assignOutputs(List<ImportPreviewWorkload> items, BigDecimal total,
                               java.util.function.Function<ImportPreviewWorkload, BigDecimal> unitPriceFn) {
        BigDecimal sumOut = BigDecimal.ZERO;
        for (int i = 0; i < items.size(); i++) {
            ImportPreviewWorkload w = items.get(i);
            w.setUnitPrice(unitPriceFn.apply(w));
            if (i < items.size() - 1) {
                BigDecimal out = w.getUnitPrice().multiply(w.getWorkload()).setScale(2, RoundingMode.HALF_UP);
                w.setOutput(out);
                sumOut = sumOut.add(out);
            }
        }
        ImportPreviewWorkload last = items.get(items.size() - 1);
        BigDecimal tail = total.subtract(sumOut).setScale(2, RoundingMode.HALF_UP);
        last.setOutput(tail);
        if (last.getWorkload().signum() != 0) {
            last.setUnitPrice(tail.divide(last.getWorkload(), 4, RoundingMode.HALF_UP));
        }
    }

    private BigDecimal sumBase(List<ImportPreviewWorkload> list) {
        BigDecimal sum = BigDecimal.ZERO;
        for (ImportPreviewWorkload w : list) {
            if (w.getUnitPrice() != null && w.getWorkload() != null) {
                sum = sum.add(w.getUnitPrice().multiply(w.getWorkload()));
            }
        }
        return sum;
    }

    private BigDecimal sumWorkload(List<ImportPreviewWorkload> list) {
        BigDecimal sum = BigDecimal.ZERO;
        for (ImportPreviewWorkload w : list) {
            if (w.getWorkload() != null) sum = sum.add(w.getWorkload());
        }
        return sum;
    }

    private void parseSheet2AndMerge(Map<String, List<List<Object>>> book, ImportPreviewResponse resp) {
        List<List<Object>> rows = null;
        boolean first = true;
        for (Map.Entry<String,List<List<Object>>> e : book.entrySet()) {
            if (first) { first = false; continue; }
            rows = e.getValue(); break;
        }
        if (rows == null || rows.isEmpty()) return;

        // 找一级表头行（含"工程编号"）
        int l1Idx = -1;
        for (int i = 0; i < Math.min(rows.size(), 5); i++) {
            List<Object> r = rows.get(i);
            for (Object o : r) if (o != null && o.toString().contains("工程编号")) { l1Idx = i; break; }
            if (l1Idx >= 0) break;
        }
        if (l1Idx < 0) return;

        List<Object> l1 = rows.get(l1Idx);                                                   // 一级表头：预付款 / 尾款支付 / 工程编号 / 到账说明 / 备注
        List<Object> l2 = (l1Idx + 1 < rows.size()) ? rows.get(l1Idx + 1) : null;            // 二级表头：金额 / 支付时间及方式

        int colProjectCode = findCol(l1, "工程编号", 0, -1);

        // 一级表头定位「预付款」「尾款支付」分组起始列
        int prepayGroup = findCol(l1, "预付款", 0, -1);
        int finalGroup  = findCol(l1, "尾款", 0, -1);

        int colPrepayAmt = -1, colPrepayTime = -1;
        int colFinalAmt = -1, colFinalTime = -1;
        if (l2 != null) {
            if (prepayGroup >= 0) {
                colPrepayAmt  = findCol(l2, "金额", prepayGroup, finalGroup < 0 ? -1 : finalGroup);
                colPrepayTime = findCol(l2, "时间", prepayGroup, finalGroup < 0 ? -1 : finalGroup);
            }
            if (finalGroup >= 0) {
                colFinalAmt  = findCol(l2, "金额", finalGroup, -1);
                colFinalTime = findCol(l2, "时间", finalGroup, -1);
            }
        }

        // 备注字段 = 到账说明 + 备注 两列拼接
        int colExplain = findCol(l1, "到账说明", 0, -1);
        int colRemark  = findCol(l1, "备注", 0, -1);

        Map<String, ImportPreviewRow> codeMap = resp.getRows().stream()
            .collect(Collectors.toMap(ImportPreviewRow::getProjectCode, x -> x, (a,b) -> a));

        // 本文件「产值计算」页里没有的编号 → 到账项先按编号暂存，收完整批再查库
        // （避免逐行查库；库里已有该编号时生成「仅补充到账」行，支持跨文件导入的到账补录）
        Map<String, List<ImportPreviewPayment>> orphanPayments = new LinkedHashMap<>();

        int dataStart = l2 != null ? l1Idx + 2 : l1Idx + 1;
        for (int r = dataStart; r < rows.size(); r++) {
            List<Object> row = rows.get(r);
            String code = strCell(row, colProjectCode);
            if (StringUtils.isBlank(code)) continue;
            code = code.trim();
            ImportPreviewRow pr = codeMap.get(code);

            BigDecimal preAmt = numCell(row, colPrepayAmt);
            if (preAmt != null && preAmt.signum() > 0) {
                ImportPreviewPayment p = new ImportPreviewPayment();
                p.setPaymentType("advance");
                p.setAmount(preAmt);
                p.setPayTime(dateCell(row, colPrepayTime));
                p.setSource("sheet2预付款");
                p.setRemark(buildRemark(row, colExplain, colRemark));
                if (pr != null) pr.getPayments().add(p);
                else orphanPayments.computeIfAbsent(code, k -> new ArrayList<>()).add(p);
            }
            BigDecimal finalAmt = numCell(row, colFinalAmt);
            if (finalAmt != null && finalAmt.signum() > 0) {
                ImportPreviewPayment p = new ImportPreviewPayment();
                p.setPaymentType("final");
                p.setAmount(finalAmt);
                p.setPayTime(dateCell(row, colFinalTime));
                p.setSource("sheet2尾款");
                p.setRemark(buildRemark(row, colExplain, colRemark));
                if (pr != null) pr.getPayments().add(p);
                else orphanPayments.computeIfAbsent(code, k -> new ArrayList<>()).add(p);
            }
        }

        // 未命中本次产值页的编号：一次性批量查库
        //   库中已有 → 生成「仅补充到账」行（提交时只覆盖写入付款，不建项目/工作量/负责人/任务）
        //   两处都查不到 → 记入未匹配清单，预览页提示（此前是静默丢弃，用户无从察觉）
        if (!orphanPayments.isEmpty()) {
            Map<String, Long> idByCode = new HashMap<>();
            List<Map<String, Object>> hits =
                projectMapper.selectProjectIdsByCodes(new ArrayList<>(orphanPayments.keySet()));
            if (hits != null) {
                for (Map<String, Object> m : hits) {
                    Object codeObj = m.get("project_code");
                    Object idObj = m.get("id");
                    if (codeObj != null && idObj != null) {
                        idByCode.put(String.valueOf(codeObj), ((Number) idObj).longValue());
                    }
                }
            }
            for (Map.Entry<String, List<ImportPreviewPayment>> e : orphanPayments.entrySet()) {
                Long existedId = idByCode.get(e.getKey());
                if (existedId == null) {
                    List<String> unmatched = resp.getUnmatchedPayCodes();
                    if (unmatched != null && !unmatched.contains(e.getKey())) unmatched.add(e.getKey());
                    continue;
                }
                ImportPreviewRow payRow = new ImportPreviewRow();
                payRow.setProjectCode(e.getKey());
                payRow.setPayOnly(true);
                payRow.setExistsInDb(true);
                payRow.setExistingProjectId(existedId);
                payRow.setPayments(e.getValue());
                resp.getRows().add(payRow);
            }
        }
    }

    /** 备注 = 到账说明 + 备注 拼接（空值 / 日期 / 数字一律跳过，分号分隔；判定见 textOf） */
    private String buildRemark(List<Object> row, int colExplain, int colRemark) {
        String explain = textOf(row, colExplain);
        String remark = textOf(row, colRemark);
        if (explain == null && remark == null) return null;
        if (explain == null) return remark;
        if (remark == null) return explain;
        return explain + "；" + remark;
    }

    // ====================== 提交导入 ======================
    @Override
    public ImportCommitResult commit(ImportCommitRequest req) {
        SessionEntry entry = SESSION.get(req.getToken());
        if (entry == null || entry.expired()) throw new RuntimeException("导入会话已过期，请重新解析");
        // 幂等：已导入完成则直接返回缓存结果
        if (entry.commitResult != null) return entry.commitResult;
        // 正在后台导入中：返回 running 占位，由前端轮询 status 直到 done
        if (!entry.committing.compareAndSet(false, true)) {
            ImportCommitResult running = new ImportCommitResult();
            running.setLogId(entry.logId);
            running.setStatus("running");
            return running;
        }
        ImportPreviewResponse cached = entry.resp;
        List<ImportPreviewRow> rows = new ArrayList<>(
            req.getRows() == null || req.getRows().isEmpty() ? cached.getRows() : req.getRows());
        if (rows == null) rows = Collections.emptyList();
        // 兜底：前端因浏览器缓存等原因未回传「仅补充到账」行时，用服务端预览缓存补齐，
        // 否则跨文件到账会被静默丢弃（提交接口拿到的 rows 是前端回传的，不补齐就写不进去）。
        List<String> sentPayOnlyCodes = new ArrayList<>();
        for (ImportPreviewRow r : rows) {
            if (Boolean.TRUE.equals(r.getPayOnly()) && r.getProjectCode() != null) {
                sentPayOnlyCodes.add(r.getProjectCode().trim());
            }
        }
        for (ImportPreviewRow cr : cached.getRows()) {
            if (!Boolean.TRUE.equals(cr.getPayOnly())) continue;
            String code = cr.getProjectCode() == null ? "" : cr.getProjectCode().trim();
            if (!sentPayOnlyCodes.contains(code)) rows.add(cr);
        }
        final String user = SecurityUtils.getUsername();
        final ProjImportLog log = new ProjImportLog();
        log.setFileName("upload.xlsx"); log.setTotalRows(rows.size());
        log.setStatus("running"); log.setCreateBy(user);
        // 1. 建导入日志（running）独立事务，成功后即拿到 logId 供轮询；失败复位
        try {
            runInNewTx(() -> importLogMapper.insertImportLog(log));
        } catch (Exception ex) {
            entry.committing.set(false);
            throw new RuntimeException("导入日志初始化失败：" + ex.getMessage(), ex);
        }
        entry.logId = log.getId();
        // 2. 异步落库：立即返回 running，线程池完成后写 entry.commitResult 供 status 轮询
        final List<ImportPreviewRow> rowsRef = rows;
        IMPORT_EXECUTOR.execute(() -> runCommitAsync(entry, rowsRef, user, log));
        ImportCommitResult running = new ImportCommitResult();
        running.setLogId(log.getId());
        running.setStatus("running");
        return running;
    }

    /** 后台线程执行主体：分组 → 预解析 → 按批事务写库 → 收尾。异常不外抛，全部落到 log 与缓存结果。 */
    private void runCommitAsync(SessionEntry entry, List<ImportPreviewRow> rows, String user, ProjImportLog log) {
        long t0 = System.currentTimeMillis();
        ImportCommitResult result = new ImportCommitResult();
        final int[] counter = {0, 0, 0}; // succ, skip, fail
        final int[] payWriteCnt = {0};   // 其中「已存在项目仅补写到账」的项目数（successCount 的子集）
        try {
            // 按工程编号分组（保持 Excel 顺序）：同编号多条记录 = 同一父项目的多个子项
            LinkedHashMap<String, List<ImportPreviewRow>> groups = new LinkedHashMap<>();
            for (ImportPreviewRow row : rows) {
                if (row.getErrors() != null && !row.getErrors().isEmpty()) {
                    counter[2]++;
                    ImportCommitResult.RowDetail d = new ImportCommitResult.RowDetail();
                    d.setExcelRow(row.getExcelRow()); d.setProjectCode(row.getProjectCode());
                    d.setReason(String.join("；", row.getErrors()));
                    result.getFailedDetails().add(d);
                    continue;
                }
                if (StringUtils.isBlank(row.getProjectCode())) {
                    counter[2]++;
                    ImportCommitResult.RowDetail d = new ImportCommitResult.RowDetail();
                    d.setExcelRow(row.getExcelRow()); d.setProjectCode(row.getProjectCode());
                    d.setReason("工程编号为空");
                    result.getFailedDetails().add(d);
                    continue;
                }
                groups.computeIfAbsent(row.getProjectCode().trim(), k -> new ArrayList<>()).add(row);
            }
            // 档1：写库前一次性预解析（负责人建档 + 已存在编号预取），把组内逐行/逐组查询降为批前各一次
            Map<String, com.xakcch.common.core.domain.entity.SysUser> leaderByName = prefetchLeaders(rows, user);
            Map<String, Long> existingIdByCode = new HashMap<>();
            if (!groups.isEmpty()) {
                List<Map<String, Object>> hits = projectMapper.selectProjectIdsByCodes(new ArrayList<>(groups.keySet()));
                for (Map<String, Object> m : hits) {
                    Object codeObj = m.get("project_code");
                    Object idObj = m.get("id");
                    if (codeObj != null && idObj != null) {
                        existingIdByCode.put(String.valueOf(codeObj), ((Number) idObj).longValue());
                    }
                }
            }
            // 已存在编号的库内付款现值（一次带出）：判断「本次到账与系统是否真有差异」，
            // 无差异的编号直接跳过不写 —— 避免台账累计口径带来的重复行白写 update_time、并把开票状态刷回未开。
            Map<Long, Map<String, ProjPayment>> dbPaysByProject =
                loadExistingPayments(new LinkedHashSet<>(existingIdByCode.values()));
            // 策略：工程编号已存在 → 项目/工作量/负责人/任务整组跳过（不写入、不合并、不覆盖），
            //      避免重复导入产生重复子项/任务；
            //      但到账信息（付款）仍要补写 —— 支持「项目在 A 文件、到账在 B 文件」的跨文件历史数据导入。
            //      同类型付款按 (project_id, payment_type) 覆盖写入，重复导入幂等、不翻倍。
            List<Map.Entry<String, List<ImportPreviewRow>>> groupList = new ArrayList<>();
            for (Map.Entry<String, List<ImportPreviewRow>> g : groups.entrySet()) {
                Long existedId = existingIdByCode.get(g.getKey());
                if (existedId != null) {
                    List<ImportPreviewPayment> mergedPays = mergeGroupPayments(g.getValue());
                    boolean isPayOnlyGroup = g.getValue().stream()
                        .allMatch(x -> Boolean.TRUE.equals(x.getPayOnly()));
                    String baseReason = isPayOnlyGroup
                        ? "工程编号已存在（项目ID=" + existedId + "），仅补充到账信息"
                        : "工程编号已存在（项目ID=" + existedId + "），项目/工作量/负责人/任务整组跳过";
                    boolean payChanged = payDiffers(dbPaysByProject.get(existedId), mergedPays);
                    String payErr = null;
                    if (payChanged) {
                        try {
                            runInNewTx(() -> writePaymentsOnly(existedId, mergedPays, user));
                        } catch (Exception ex) {
                            String m = ex.getCause() != null && ex.getCause().getMessage() != null
                                ? ex.getCause().getMessage() : ex.getMessage();
                            payErr = m != null && m.contains("\n") ? m.split("\n")[0] : m;
                        }
                    }
                    // 三态归类（口径与预览 payWriteCount 严格一致）：
                    //   到账有差异且写入成功 → 计入「导入成功」——本次确实改动了系统数据
                    //   到账无差异 / 本文件无到账 → 计入「跳过」——不需要任何写入
                    //   到账写入失败 → 计入「失败」
                    if (payErr != null) {
                        counter[2] += g.getValue().size();
                        for (ImportPreviewRow row : g.getValue()) {
                            ImportCommitResult.RowDetail d = new ImportCommitResult.RowDetail();
                            d.setExcelRow(row.getExcelRow());
                            d.setProjectCode(row.getProjectCode());
                            d.setReason(baseReason + "，到账信息写入失败：" + payErr);
                            result.getFailedDetails().add(d);
                        }
                    } else if (payChanged) {
                        counter[0] += g.getValue().size();
                        payWriteCnt[0]++;
                        for (ImportPreviewRow row : g.getValue()) {
                            ImportCommitResult.RowDetail d = new ImportCommitResult.RowDetail();
                            d.setExcelRow(row.getExcelRow());
                            d.setProjectCode(row.getProjectCode());
                            d.setReason(baseReason + "，已覆盖写入到账 " + mergedPays.size() + " 项");
                            result.getPayWriteDetails().add(d);
                        }
                    } else {
                        counter[1] += g.getValue().size();
                        String tail = mergedPays.isEmpty()
                            ? "，无到账信息需要补充"
                            : "，到账信息与系统一致，无需更新";
                        for (ImportPreviewRow row : g.getValue()) {
                            ImportCommitResult.RowDetail d = new ImportCommitResult.RowDetail();
                            d.setExcelRow(row.getExcelRow());
                            d.setProjectCode(row.getProjectCode());
                            d.setReason(baseReason + tail);
                            result.getSkippedDetails().add(d);
                        }
                    }
                    continue;
                }
                groupList.add(g);
            }
            // 档3a：按批事务写库（TX_BATCH_SIZE 组一批）。
            // 核心提速点：批内用「10 条多值 SQL 覆盖全批」替代原「每项目 10 条独立 SQL」——
            // 2000 行 ≈ 1900 组 ⇒ 19000 次往返 降到 95 批 × 10 = 950 次（生产实测每条 SQL 端到端 68ms，
            // 耗时 ∝ SQL 条数 × 往返延迟，与数据内容无关）。
            // 容错分两层：①内存可判定的错误（类别/负责人/计费类别缺失）先按组摘出，不拖垮同批其它组；
            //            ②DB 级错误 → 整批回滚后逐组重试，把真正失败的那组隔离出来，其余照常入库。
            for (int i = 0; i < groupList.size(); i += TX_BATCH_SIZE) {
                int end = Math.min(i + TX_BATCH_SIZE, groupList.size());
                List<Map.Entry<String, List<ImportPreviewRow>>> batch =
                    new ArrayList<>(groupList.subList(i, end));
                List<Map.Entry<String, List<ImportPreviewRow>>> validBatch = new ArrayList<>(batch.size());
                for (Map.Entry<String, List<ImportPreviewRow>> g : batch) {
                    String err = validateGroup(g.getValue());
                    if (err != null) {
                        markGroupFailed(result, counter, g, err, false);
                    } else {
                        validBatch.add(g);
                    }
                }
                if (validBatch.isEmpty()) continue;
                try {
                    runInNewTx(() -> writeBatchGroups(validBatch, user, leaderByName));
                    for (Map.Entry<String, List<ImportPreviewRow>> g : validBatch) counter[0] += g.getValue().size();
                } catch (Exception ex) {
                    String msg = rootMsg(ex);
                    System.err.println("[proj-import] 批写失败(" + validBatch.size() + " 组)，转逐组重试以隔离坏组: " + msg);
                    for (Map.Entry<String, List<ImportPreviewRow>> g : validBatch) {
                        try {
                            runInNewTx(() -> writeBatchGroups(Collections.singletonList(g), user, leaderByName));
                            counter[0] += g.getValue().size();
                        } catch (Exception ex2) {
                            markGroupFailed(result, counter, g, rootMsg(ex2), true);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            // 结构性异常（理论上只在预解析阶段）→ 全部行记失败，避免静默
            String msg = t.getMessage();
            if (msg != null && msg.contains("\n")) msg = msg.split("\n")[0];
            ImportCommitResult.RowDetail d = new ImportCommitResult.RowDetail();
            d.setReason("导入流程异常：" + (msg == null ? t.getClass().getSimpleName() : msg));
            result.getFailedDetails().add(d);
            counter[2] = Math.max(counter[2], rows.size() - counter[0] - counter[1]);
            System.err.println("[proj-import] async commit FATAL: " + t.getMessage());
            t.printStackTrace();
        } finally {
            long cost = System.currentTimeMillis() - t0;
            log.setCostMs(cost);
            log.setSuccessCount(counter[0]);
            log.setSkippedCount(counter[1]);
            log.setFailedCount(counter[2]);
            try {
                log.setFailDetails(om.writeValueAsString(result.getFailedDetails()));
                log.setSkipDetails(om.writeValueAsString(result.getSkippedDetails()));
            } catch (Exception ignore) {}
            log.setStatus("done");
            log.setUpdateBy(user);
            try {
                runInNewTx(() -> importLogMapper.updateImportLog(log));
                System.out.println("[proj-import] log updated id=" + log.getId()
                    + " succ=" + counter[0] + " skip=" + counter[1] + " fail=" + counter[2]);
            } catch (Exception ignoreLog) {
                System.err.println("[proj-import] log update FAILED id=" + log.getId() + " -> " + ignoreLog.getMessage());
            }
            // 缓存结果供 status 轮询 / 幂等重入读取（与落库 JSON 同源）
            result.setLogId(log.getId());
            result.setStatus("done");
            result.setCostMs(cost);
            result.setSuccessCount(counter[0]);
            result.setSkippedCount(counter[1]);
            result.setFailedCount(counter[2]);
            result.setPayWriteCount(payWriteCnt[0]);
            if (result.getFailedDetails() == null) result.setFailedDetails(new ArrayList<>());
            if (result.getSkippedDetails() == null) result.setSkippedDetails(new ArrayList<>());
            if (result.getPayWriteDetails() == null) result.setPayWriteDetails(new ArrayList<>());
            entry.commitResult = result;
            entry.committing.set(false);
        }
    }

    /** 查询导入状态（轮询用）：done 携带完整结果；running 仅有 logId；expired 表示会话已失效 */
    @Override
    public ImportCommitResult getCommitStatus(String token) {
        SessionEntry entry = SESSION.get(token);
        if (entry == null || entry.expired()) {
            ImportCommitResult r = new ImportCommitResult();
            r.setStatus("expired");
            return r;
        }
        if (entry.commitResult != null) return entry.commitResult;
        ImportCommitResult running = new ImportCommitResult();
        running.setLogId(entry.logId);
        running.setStatus("running");
        return running;
    }

    /**
     * 档1·预解析负责人：对全量行中「无 leaderId 但有姓名」的行统一处理——
     * 先一次批量查库命中已有用户，缺失的才建档（影子用户），随后回填 leaderId。
     * 消除写库阶段组内对每行重复 selectUserByNickName 的 N+1。
     */
    private Map<String, com.xakcch.common.core.domain.entity.SysUser> prefetchLeaders(List<ImportPreviewRow> rows, String user) {
        Map<String, com.xakcch.common.core.domain.entity.SysUser> result = new HashMap<>();
        Set<String> names = new LinkedHashSet<>();
        for (ImportPreviewRow row : rows) {
            if (row.getLeaderId() == null && StringUtils.isNotBlank(row.getLeaderName())) {
                names.add(row.getLeaderName().trim());
            }
        }
        if (names.isEmpty()) return result;
        Map<String, com.xakcch.common.core.domain.entity.SysUser> existByName = new HashMap<>();
        for (com.xakcch.common.core.domain.entity.SysUser u : leaderMapper.selectUsersByNickNames(new ArrayList<>(names))) {
            if (u.getNickName() != null) existByName.putIfAbsent(u.getNickName().trim(), u);
        }
        for (String name : names) {
            com.xakcch.common.core.domain.entity.SysUser u = existByName.get(name);
            if (u == null) u = projectService.ensureLeaderByName(name, user);
            if (u != null) result.put(name, u);
        }
        // 回填 leaderId：后续批量写库不再触发 DB 建档
        for (ImportPreviewRow row : rows) {
            if (row.getLeaderId() == null && StringUtils.isNotBlank(row.getLeaderName())) {
                com.xakcch.common.core.domain.entity.SysUser u = result.get(row.getLeaderName().trim());
                if (u != null) row.setLeaderId(u.getUserId());
            }
        }
        return result;
    }

    private void runInNewTx(Runnable r) {
        txRequiresNew().execute(new TransactionCallbackWithoutResult() {
            @Override protected void doInTransactionWithoutResult(TransactionStatus status) {
                r.run();
            }
        });
    }

    /**
     * 单组（同一工程编号的所有 Excel 行）父项目字段的合并结果。
     * 批量写库前先把全批各组的字段在内存里合并好，再一次性下发多值 SQL。
     */
    private static class GroupAgg {
        String code;                 // 工程编号（同组唯一）
        String projectName;
        String engineeringProject;
        String clientUnit;
        String projectLocation;
        Long categoryId;
        Date closeTime;              // 办结时间 = 组内验收日期最晚值
        Date materialTime;           // 资料领取时间 = 组内第一条非空
        final LinkedHashSet<Long> leaderIds = new LinkedHashSet<>();
    }

    /**
     * 组内校验（纯内存判定，不碰库）：把「项目类别 / 负责人 / 计费类别缺失」这类错误在开启事务前
     * 按组摘出，避免一个坏组把同批其它组一起回滚掉（原实现是整批回滚）。
     * 校验顺序与错误文案沿用原 writeOneGroup，保证前端提示口径不变。
     *
     * @param group 同一工程编号的所有行
     * @return null 表示通过；否则为失败原因（写入该组全部行的失败明细）
     */
    private String validateGroup(List<ImportPreviewRow> group) {
        if (group == null || group.isEmpty()) return "工程编号为空";
        Long categoryId = null;
        LinkedHashSet<Long> leaderIds = new LinkedHashSet<>();
        for (ImportPreviewRow row : group) {
            if (row.getLeaderId() != null) leaderIds.add(row.getLeaderId());
            if (categoryId == null && row.getProjectCategoryId() != null) categoryId = row.getProjectCategoryId();
        }
        if (categoryId == null) return "项目类别未选择";
        if (leaderIds.isEmpty()) return "负责人为空且姓名缺失";
        for (ImportPreviewRow row : group) {
            if (row.getWorkloads() == null) continue;
            for (ImportPreviewWorkload w : row.getWorkloads()) {
                if (w.getBillingId() == null) {
                    String disp = w.getBillingCategoryRaw() == null ? "" : w.getBillingCategoryRaw()
                        .replaceAll("[（(](内部|外部)[）)]", "").trim();
                    return "工作项未匹配计费类别：" + disp;
                }
            }
        }
        return null;
    }

    /**
     * 合并一组（同一工程编号）的父项目字段 + 负责人锚定。
     * 合并口径与原 writeOneGroup 完全一致：close_time 取最晚、负责人取并集、其余文本取第一条非空。
     *
     * @param leaderByName 档1预解析的 负责人姓名→SysUser 映射（可能为 null）
     */
    private GroupAgg aggregateGroup(String code, List<ImportPreviewRow> group, String user,
                                    Map<String, com.xakcch.common.core.domain.entity.SysUser> leaderByName) {
        GroupAgg a = new GroupAgg();
        a.code = code;
        for (ImportPreviewRow row : group) {
            // 负责人锚定（档1预解析已回填 leaderId；此处仅兜底）
            if (row.getLeaderId() == null && StringUtils.isNotBlank(row.getLeaderName())) {
                com.xakcch.common.core.domain.entity.SysUser shadow =
                        leaderByName == null ? null : leaderByName.get(row.getLeaderName().trim());
                if (shadow == null) shadow = projectService.ensureLeaderByName(row.getLeaderName(), user);
                if (shadow != null) row.setLeaderId(shadow.getUserId());
            }
            if (row.getLeaderId() != null) a.leaderIds.add(row.getLeaderId());
            if (a.projectName == null && StringUtils.isNotBlank(row.getEngineeringProject())) {
                a.projectName = row.getEngineeringProject();
            }
            if (a.engineeringProject == null && StringUtils.isNotBlank(row.getEngineeringProject())) {
                a.engineeringProject = row.getEngineeringProject();
            }
            if (a.clientUnit == null && StringUtils.isNotBlank(row.getClientUnit())) {
                a.clientUnit = row.getClientUnit();
            }
            if (a.projectLocation == null && StringUtils.isNotBlank(row.getProjectLocation())) {
                a.projectLocation = row.getProjectLocation();
            }
            if (a.categoryId == null && row.getProjectCategoryId() != null) {
                a.categoryId = row.getProjectCategoryId();
            }
            if (row.getFinishDate() != null && (a.closeTime == null || row.getFinishDate().after(a.closeTime))) {
                a.closeTime = row.getFinishDate();
            }
            if (a.materialTime == null && row.getMaterialSubmitTime() != null) {
                a.materialTime = row.getMaterialSubmitTime();
            }
        }
        if (StringUtils.isBlank(a.projectName)) a.projectName = code;
        return a;
    }

    /**
     * 批量写一批「新建」项目组：全批共用 10 条多值 SQL（原实现每个项目 10 条独立 SQL）。
     *
     * <p>语句顺序：
     * ① {@code insertProjectBatch} 父项目
     * ② {@code selectProjectIdsByCodes} 同事务内按工程编号回查主键（不依赖驱动的 key 回填顺序）
     * ③ {@code insertProjectLeadersBatch} 负责人
     * ④ {@code insertTaskBatch} 任务
     * ⑤ {@code insertWorkloadBatch} 子项工作量
     * ⑥ {@code batchUpsertPayment} 到账（覆盖语义 ⇒ 重复导入幂等、金额不翻倍）
     * ⑦ {@code insertLogIgnoreWithTimeBatch} 历史上报补录（on conflict do nothing ⇒ 不重报）
     * ⑧ {@code insertMaterialBatch} 资料记录
     * ⑨ {@code selectMaterialIdsByProjectIds} 回查资料主键（仅当批内确有领取日期时才发）
     * ⑩ {@code insertFlowBatch} 资料领取流转（同上，按需）</p>
     *
     * <p>相较原逐项目实现，另去掉两条纯冗余查询：
     * {@code selectMaxSubItemNo}（本路径只处理新建项目，子项号恒从 1 开始）与
     * {@code selectPaymentsByProjectId}（补录时间直接由本批内存中的到账项推导）。</p>
     *
     * <p>运行在调用方的事务中（整批原子提交）；任一步失败由调用方回滚后逐组重试。</p>
     */
    private void writeBatchGroups(List<Map.Entry<String, List<ImportPreviewRow>>> groups, String user,
                                  Map<String, com.xakcch.common.core.domain.entity.SysUser> leaderByName) {
        final int n = groups.size();
        List<GroupAgg> aggs = new ArrayList<>(n);
        List<ProjProject> pjs = new ArrayList<>(n);
        for (Map.Entry<String, List<ImportPreviewRow>> g : groups) {
            GroupAgg a = aggregateGroup(g.getKey(), g.getValue(), user, leaderByName);
            aggs.add(a);
            ProjProject pj = new ProjProject();
            pj.setProjectCode(a.code);
            pj.setProjectName(a.projectName);
            pj.setEngineeringProject(a.engineeringProject);
            pj.setProjectCategoryId(a.categoryId);
            pj.setClientUnit(a.clientUnit);
            // 指令性任务自动打标（委托单位命中规则关键词 ⇒ mandate；否则 normal）
            pj.setProjectNature(mandateService.resolveNature(a.clientUnit));
            pj.setProjectLocation(a.projectLocation);
            pj.setDataSource("import");
            pj.setStatus("closed");
            pj.setCloseTime(a.closeTime);
            pj.setAssignDate(a.closeTime);
            pj.setCreateBy(user);
            pjs.add(pj);
        }

        // ① 父项目：一次多值插入
        projectMapper.insertProjectBatch(pjs);
        // ② 主键回查（同一事务内可见刚插入的行；工程编号在本批内唯一且入库前已确认库中不存在）
        List<String> codes = new ArrayList<>(n);
        for (GroupAgg a : aggs) codes.add(a.code);
        Map<String, Long> idByCode = new HashMap<>(n * 2);
        for (Map<String, Object> m : projectMapper.selectProjectIdsByCodes(codes)) {
            Object codeObj = m.get("project_code");
            Object idObj = m.get("id");
            if (codeObj != null && idObj != null) idByCode.put(String.valueOf(codeObj), ((Number) idObj).longValue());
        }
        List<Long> projectIds = new ArrayList<>(n);
        for (GroupAgg a : aggs) {
            Long pid = idByCode.get(a.code);
            if (pid == null) throw new RuntimeException("批量插入项目后未按编号回查到主键：" + a.code);
            projectIds.add(pid);
        }

        // ③ 负责人：跨全批合并成一条多值 SQL（新建项目不存在唯一键冲突，无需查旧负责人）
        List<Map<String, Object>> leaders = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (Long lid : aggs.get(i).leaderIds) {
                Map<String, Object> m = new HashMap<>(4);
                m.put("projectId", projectIds.get(i));
                m.put("userId", lid);
                m.put("createBy", user);
                leaders.add(m);
            }
        }
        if (!leaders.isEmpty()) leaderMapper.insertProjectLeadersBatch(leaders);

        // ④ 任务：每个负责人一条（合并）
        List<ProjTask> tasks = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            GroupAgg a = aggs.get(i);
            for (Long lid : a.leaderIds) {
                ProjTask task = new ProjTask();
                task.setProjectId(projectIds.get(i));
                task.setUserId(lid);
                task.setTaskName(a.projectName);
                task.setStatus("completed");
                task.setActualFinishDate(a.closeTime);
                task.setRequiredFinishDate(a.closeTime);
                task.setAssignDate(a.closeTime);
                task.setCreateBy(user);
                tasks.add(task);
            }
        }
        if (!tasks.isEmpty()) taskMapper.insertTaskBatch(tasks);

        // ⑤ 子项工作量：每个 Excel 行 = 一个子项，sub_item_no 组内自增。
        //    本路径只处理新建项目（已存在编号在上游整组跳过）⇒ 子项号恒从 1 开始，无需查库续号。
        List<ProjWorkload> workloads = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ProjProject pj = pjs.get(i);
            pj.setId(projectIds.get(i));
            int seq = 0;
            for (ImportPreviewRow row : groups.get(i).getValue()) {
                seq++;
                workloads.addAll(buildSubItemWorkloads(pj, row, seq, user));
            }
        }
        if (!workloads.isEmpty()) workloadMapper.insertWorkloadBatch(workloads);

        // ⑥⑦ 到账合并项只算一次，供「付款 upsert」与「上报补录时间」共用
        List<List<ImportPreviewPayment>> mergedPays = new ArrayList<>(n);
        for (int i = 0; i < n; i++) mergedPays.add(mergeGroupPayments(groups.get(i).getValue()));

        // ⑥ 付款：同类型合并成一条后批量 upsert（覆盖语义，重复导入幂等、不累加）
        List<ProjPayment> pays = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            pays.addAll(buildPaymentEntities(projectIds.get(i), mergedPays.get(i), user));
        }
        if (!pays.isEmpty()) paymentMapper.batchUpsertPayment(pays);

        // ⑦ 历史上报补录：导入的是历史数据，这些定线项目在线下就已上报过，故按
        //    「有尾款取尾款到账时间、无尾款取预付款到账时间」把上报时间固化进上报记录表。
        //    业务规则：一个定线项目只允许上报一次 ⇒ 只写、不覆盖、不重报（on conflict do nothing）。
        //    时间直接取本批内存中的到账项，不再回查数据库。两者都没有则不写（不编造上报时间）。
        List<ProjReportSubmitLog> submitLogs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Date t = maxMergedPayTime(mergedPays.get(i), "final");
            if (t == null) t = maxMergedPayTime(mergedPays.get(i), "advance");
            if (t == null) continue;
            GroupAgg a = aggs.get(i);
            ProjReportSubmitLog rl = new ProjReportSubmitLog();
            rl.setProjectCode(a.code);
            rl.setProjectName(a.projectName);
            rl.setUnitName(a.clientUnit);
            rl.setSubmitTime(t);
            // 写入者标识用英文（库里统一存码值，不写中文）；前端 utils/projStatus.js#submitByText
            // 把 import 映射为「历史导入」。真实上报行存的是操作人账号。
            rl.setSubmitBy(SUBMIT_BY_IMPORT);
            rl.setBatchId(null);
            submitLogs.add(rl);
        }
        if (!submitLogs.isEmpty()) reportSubmitMapper.insertLogIgnoreWithTimeBatch(submitLogs);

        // ⑧ 资料：每个导入项目一条（保证已办结项目在资料管理可见）。
        //    有「资料领取」日期 → 已领取 + 领取流转；无日期/无该列 → 待领取、待提交
        //    （与手工办结 completeProject 的兜底口径一致），领取时间/联系人由用户后续在资料管理补录。
        List<ProjMaterial> mats = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ProjMaterial mat = new ProjMaterial();
            mat.setProjectId(projectIds.get(i));
            mat.setGuarantorFlag("N");
            mat.setArchiveFlag("N");
            mat.setCreateBy(user);
            Date mt = aggs.get(i).materialTime;
            if (mt != null) {
                mat.setSubmitTime(mt);
                // 状态写字典值（proj_material_status: received=已领取），不要写中文标签
                mat.setStatus("received");
                mat.setSubmitStatus("submitted");
            } else {
                mat.setStatus("pending");
                mat.setSubmitStatus("pending");
            }
            mats.add(mat);
        }
        if (!mats.isEmpty()) materialMapper.insertMaterialBatch(mats);

        // ⑨⑩ 资料领取流转：仅有「资料领取日期」的项目需要，且需要 material_id，
        //      故只在确有需要时回查一次资料主键（同样是同事务内可见）。
        List<Long> flowPids = new ArrayList<>();
        List<Date> flowTimes = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Date mt = aggs.get(i).materialTime;
            if (mt == null) continue;
            flowPids.add(projectIds.get(i));
            flowTimes.add(mt);
        }
        if (!flowPids.isEmpty()) {
            Map<Long, Long> midByProject = new HashMap<>();
            for (Map<String, Object> m : materialMapper.selectMaterialIdsByProjectIds(flowPids)) {
                Object pidObj = m.get("project_id");
                Object midObj = m.get("id");
                if (pidObj != null && midObj != null) {
                    midByProject.put(((Number) pidObj).longValue(), ((Number) midObj).longValue());
                }
            }
            List<ProjMaterialFlow> flows = new ArrayList<>(flowPids.size());
            for (int i = 0; i < flowPids.size(); i++) {
                Long mid = midByProject.get(flowPids.get(i));
                if (mid == null) throw new RuntimeException("资料记录写入后未回查到主键，projectId=" + flowPids.get(i));
                ProjMaterialFlow flow = new ProjMaterialFlow();
                flow.setMaterialId(mid);
                flow.setFlowType("领取");
                flow.setOperateTime(flowTimes.get(i));
                flow.setCreateBy(user);
                flows.add(flow);
            }
            materialFlowMapper.insertFlowBatch(flows);
        }
    }

    /** 取「合并后的到账项」中某付款类型的最大到账时间（无该类型或无时间则返回 null） */
    private Date maxMergedPayTime(List<ImportPreviewPayment> merged, String paymentType) {
        if (merged == null) return null;
        Date max = null;
        for (ImportPreviewPayment p : merged) {
            if (!paymentType.equals(p.getPaymentType())) continue;
            Date t = p.getPayTime();
            if (t != null && (max == null || t.after(max))) max = t;
        }
        return max;
    }

    /** 把一组全部行记为失败并计数（批前校验失败 / 逐组重试失败 两条路径共用，口径一致） */
    private void markGroupFailed(ImportCommitResult result, int[] counter,
                                 Map.Entry<String, List<ImportPreviewRow>> group, String reason,
                                 boolean rolledBack) {
        counter[2] += group.getValue().size();
        String tail = rolledBack ? "（该行所在分组写入失败，未写入数据）" : "";
        String msg = reason == null ? "未知错误" : reason;
        for (ImportPreviewRow row : group.getValue()) {
            ImportCommitResult.RowDetail d = new ImportCommitResult.RowDetail();
            d.setExcelRow(row.getExcelRow());
            d.setProjectCode(row.getProjectCode());
            d.setReason(msg + tail);
            result.getFailedDetails().add(d);
        }
    }

    /** 取异常最内层原因并压成单行 —— 批量写库失败时用于定位真正原因（唯一键/长度/NOT NULL 等） */
    private String rootMsg(Throwable t) {
        Throwable c = t;
        for (int i = 0; i < 8 && c.getCause() != null && c.getCause() != c; i++) c = c.getCause();
        String m = c.getMessage() != null ? c.getMessage() : t.getMessage();
        if (m == null) m = t.getClass().getSimpleName();
        return m.contains("\n") ? m.split("\n")[0] : m;
    }



    /** 构造单个子项的工作量列表（按组合键聚合同类项，打上 sub_item_no / sub_item_name），由调用方统一批量插入 */
    private List<ProjWorkload> buildSubItemWorkloads(ProjProject pj, ImportPreviewRow row, int subItemNo, String user) {
        List<ProjWorkload> result = new ArrayList<>();
        // 按组合键(categoryId, billingType, billingCategory)聚合，避免唯一键冲突
        // 原因：Excel 中 3 个工作量组(定验线/管线图/其它)可能有同名二级表头，匹配后组合键完全相同
        Map<String, ImportPreviewWorkload> mergedWl = new LinkedHashMap<>();
        for (ImportPreviewWorkload w : row.getWorkloads()) {
            // 匹配到小类（billingId 为 null）也要落库，故只校验 categoryId
            if (w.getCategoryId() == null) continue;
            String key = w.getCategoryId() + "|" + w.getBillingType() + "|"
                + (w.getBillingCategory() == null ? "" : w.getBillingCategory());
            ImportPreviewWorkload exist = mergedWl.get(key);
            if (exist == null) {
                mergedWl.put(key, w);
            } else {
                // 聚合同类项：工作量和产值累加
                if (w.getWorkload() != null) {
                    exist.setWorkload(exist.getWorkload() == null ? w.getWorkload()
                        : exist.getWorkload().add(w.getWorkload()));
                }
                if (w.getOutput() != null) {
                    exist.setOutput(exist.getOutput() == null ? w.getOutput()
                        : exist.getOutput().add(w.getOutput()));
                }
                // 单价重算：产值/工作量（保持反推逻辑一致）
                if (exist.getOutput() != null && exist.getWorkload() != null
                    && exist.getWorkload().signum() != 0) {
                    exist.setUnitPrice(exist.getOutput().divide(exist.getWorkload(), 4, RoundingMode.HALF_UP));
                }
            }
        }
        for (ImportPreviewWorkload w : mergedWl.values()) {
            ProjWorkload wl = new ProjWorkload();
            wl.setProjectId(pj.getId());
            wl.setUserId(row.getLeaderId() != null ? row.getLeaderId() : 0L);
            wl.setCategoryId(w.getCategoryId());
            wl.setWorkload(w.getWorkload());
            wl.setBillingType(w.getBillingType());
            wl.setBillingCategory(w.getBillingCategory());
            wl.setSubItemNo(subItemNo);
            wl.setSubItemName(row.getEngineeringProject());
            wl.setPriceUnit(w.getPriceUnit());
            wl.setMinQuantity(w.getMinQuantity());
            wl.setUnitPrice(w.getUnitPrice());
            // 导入项目的工作量单价为「导入推导价」，来源标记 imported；
            // 同时把推导价备份到 extra_data.origin_price，便于取消合同关联时精确还原
            wl.setPriceSource("imported");
            if (w.getUnitPrice() != null) {
                java.util.Map<String, Object> extra = new java.util.LinkedHashMap<>();
                extra.put("origin_price", w.getUnitPrice());
                wl.setExtraData(com.alibaba.fastjson2.JSON.toJSONString(extra));
            }
            if ("internal".equals(w.getBillingType())) {
                wl.setInternalPrice(w.getUnitPrice());
                wl.setInternalOutput(w.getOutput());
            } else {
                wl.setExternalPrice(w.getUnitPrice());
                wl.setExternalOutput(w.getOutput());
            }
            wl.setCreateBy(user);
            result.add(wl);
        }
        return result;
    }

    /** 付款合并：同类型金额累加成一条；复用项目时与库内已有付款累加；最终一次批量 upsert */
    /** 合并组内所有行的到账项：同类型金额累加成一条（仅组内合并，不涉及库内已有数据） */
    private List<ImportPreviewPayment> mergeGroupPayments(List<ImportPreviewRow> group) {
        Map<String, ImportPreviewPayment> merged = new LinkedHashMap<>();
        for (ImportPreviewRow row : group) {
            if (row.getPayments() == null) continue;
            for (ImportPreviewPayment pm : row.getPayments()) {
                if (pm.getAmount() == null || pm.getAmount().signum() == 0) continue;
                String key = pm.getPaymentType() == null ? "" : pm.getPaymentType();
                ImportPreviewPayment exist = merged.get(key);
                if (exist == null) {
                    ImportPreviewPayment copy = new ImportPreviewPayment();
                    copy.setPaymentType(pm.getPaymentType());
                    copy.setAmount(pm.getAmount());
                    copy.setPayTime(pm.getPayTime());
                    copy.setPayUnit(pm.getPayUnit());
                    copy.setPayMethod(pm.getPayMethod());
                    copy.setSource(pm.getSource());
                    copy.setRemark(pm.getRemark());
                    merged.put(key, copy);
                } else {
                    exist.setAmount(exist.getAmount().add(pm.getAmount()));
                    if (exist.getPayTime() == null) exist.setPayTime(pm.getPayTime());
                    if (exist.getPayMethod() == null) exist.setPayMethod(pm.getPayMethod());
                    if (exist.getRemark() == null) exist.setRemark(pm.getRemark());
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    /** 批量加载项目的库内付款现值：projectId → (paymentType → 付款)。一次查询带出，避免逐个项目查库 */
    private Map<Long, Map<String, ProjPayment>> loadExistingPayments(Collection<Long> projectIds) {
        Map<Long, Map<String, ProjPayment>> out = new HashMap<>();
        if (projectIds == null || projectIds.isEmpty()) return out;
        List<ProjPayment> pays = paymentMapper.selectPaymentsByProjectIds(projectIds.toArray(new Long[0]));
        if (pays == null) return out;
        for (ProjPayment p : pays) {
            if (p.getProjectId() == null) continue;
            String key = p.getPaymentType() == null ? "" : p.getPaymentType();
            out.computeIfAbsent(p.getProjectId(), k -> new HashMap<>()).put(key, p);
        }
        return out;
    }

    /**
     * 本次合并后的到账与库内现值是否存在差异 —— 决定是否真的需要写库。
     * 判定字段：付款类型（作为键）+ 金额 + 到账时间（按天比较，避免 Date 时分秒差异误判）。
     * 台账是累计口径，跨文件/重复导入常带上与库里完全相同的到账行，此类属幂等重复，应视为「无差异」。
     */
    private boolean payDiffers(Map<String, ProjPayment> dbPays, List<ImportPreviewPayment> merged) {
        if (merged == null || merged.isEmpty()) return false;
        SimpleDateFormat dayFmt = new SimpleDateFormat("yyyy-MM-dd");
        for (ImportPreviewPayment pm : merged) {
            String key = pm.getPaymentType() == null ? "" : pm.getPaymentType();
            ProjPayment db = dbPays == null ? null : dbPays.get(key);
            if (db == null) return true;                    // 库里没有该类型的付款 → 新增
            if (db.getAmount() == null || pm.getAmount() == null
                || db.getAmount().compareTo(pm.getAmount()) != 0) return true;   // 金额不同
            if (db.getPayTime() == null || pm.getPayTime() == null) {
                if (db.getPayTime() != pm.getPayTime()) return true;            // 一有一无 → 有差异
            } else if (!dayFmt.format(db.getPayTime()).equals(dayFmt.format(pm.getPayTime()))) {
                return true;                                                    // 到账时间不同
            }
        }
        return false;
    }

    /**
     * 构造付款实体（不落库）：供「批量写库」与「已存在编号只补到账」两条路径共用。
     * ⚠️ 不与库内已有金额累加：batchUpsertPayment 是
     * {@code on conflict (project_id, payment_type) ... do update set amount = EXCLUDED.amount}
     * 的覆盖语义，先累加再覆盖会让重复导入的金额翻倍。
     */
    private List<ProjPayment> buildPaymentEntities(Long projectId, List<ImportPreviewPayment> merged, String user) {
        List<ProjPayment> toSave = new ArrayList<>();
        if (projectId == null || merged == null || merged.isEmpty()) return toSave;
        for (ImportPreviewPayment pm : merged) {
            ProjPayment pp = new ProjPayment();
            pp.setProjectId(projectId);
            pp.setPaymentType(pm.getPaymentType());
            pp.setAmount(pm.getAmount());
            pp.setPayTime(pm.getPayTime());
            pp.setPayUnit(pm.getPayUnit());
            pp.setPayMethod(pm.getPayMethod());
            // received_status 已废弃（无界面维护、只会落到默认值），到账判定统一看 payTime，此处不再赋值
            // 开票状态写英文码值 pending（未开）—— 库里统一存码值，中文由前端
            // utils/projStatus.js#invoiceStatusText 映射（pending→未开 / invoiced→已开 / voided→已作废）。
            // ⚠️ 不要在这里写中文标签，否则库内会出现中英两套取值。
            pp.setInvoiceStatus("pending");
            pp.setRemark(pm.getRemark());
            pp.setCreateBy(user);
            toSave.add(pp);
        }
        return toSave;
    }

    /** 仅写付款、不动项目：用于「已存在编号只补到账」场景（同类型覆盖） */
    private void writePaymentsOnly(Long projectId, List<ImportPreviewPayment> merged, String user) {
        List<ProjPayment> toSave = buildPaymentEntities(projectId, merged, user);
        if (!toSave.isEmpty()) paymentMapper.batchUpsertPayment(toSave);
    }

    // ================ 工具函数 ================
    private int firstMatch(Map<String,Integer> map, String regex) {
        Pattern p = Pattern.compile(regex);
        for (Map.Entry<String,Integer> e : map.entrySet()) {
            if (p.matcher(e.getKey()).find()) return e.getValue();
        }
        return -1;
    }
    /** 在一行中找首个 cell 文本含 keyword 的列索引；endExcl<0 表示到行尾 */
    private int findCol(List<Object> row, String keyword, int startIncl, int endExcl) {
        if (row == null || startIncl < 0) return -1;
        int end = (endExcl < 0 || endExcl > row.size()) ? row.size() : endExcl;
        for (int c = startIncl; c < end; c++) {
            Object v = row.get(c);
            if (v != null && v.toString().contains(keyword)) return c;
        }
        return -1;
    }
    private String strCell(List<Object> row, int c) {
        if (row == null || c < 0 || c >= row.size()) return null;
        Object v = row.get(c); if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }
    /**
     * 取单元格文本（用于「到账说明 / 备注」这类**纯文本列**）。
     *
     * 空值 → null；日期 → null（日期直接 toString 会写出 "Mon Jan 01 00:00:00 CST 2026" 这种垃圾）；
     * 数字 → null（09-16 修复，见下）。
     *
     * 为什么要连数字一起忽略：Sheet2 的「到账说明 / 备注」列极易被设成日期格式或被填成
     * 「Excel 日期序列号」（2026-07-01 的序列号就是 46204）。POI 对这种单元格可能直接给出
     * Number，`v.toString()` 就得到 "46204.0"，再被 buildRemark 拼进备注，用户看到的现象就是
     * 「有正常备注时末尾永远跟着一个 46204.0」。文本列里的裸数字没有任何业务含义，
     * 一律忽略，避免污染备注。
     */
    private String textOf(List<Object> row, int c) {
        if (row == null || c < 0 || c >= row.size()) return null;
        Object v = row.get(c); if (v == null) return null;
        if (v instanceof Date) return null;
        if (v instanceof Number) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }
    private BigDecimal numCell(List<Object> row, int c) {
        if (row == null || c < 0 || c >= row.size()) return null;
        Object v = row.get(c); if (v == null) return null;
        if (v instanceof Number) return new BigDecimal(v.toString());
        try {
            String s = v.toString().trim().replace(",", "");
            if (s.isEmpty()) return null;
            return new BigDecimal(s);
        } catch (Exception e) { return null; }
    }
    /** 日期文本：yyyy-M-d，分隔符允许 - / . 年 月 混用（2026.1.15 / 2026-01-14 / 2026年1月15日 都认） */
    private static final Pattern DATE_TEXT = Pattern.compile(
            "(\\d{4})\\s*[-/.年]\\s*(\\d{1,2})\\s*[-/.月]\\s*(\\d{1,2})\\s*日?");

    /**
     * 解析单元格日期。历史台账里同一个「日期」列的存法五花八门，逐一兼容：
     * 1) POI 已按日期格式读出 → Date，直接用；
     * 2) 数值型：Excel 日期序列号（45800 → 2025-05-10）转 Date；8 位 yyyymmdd 直接拆解；
     * 3) 文本型：yyyy-M-d（- / . 年 月 混用）取第一个匹配；纯 8 位数字按 yyyymmdd 拆解。
     * 为什么必须这么宽：旧实现只认 Date 和「- / 年」分隔，于是「验收日期」列写成 2026.1.15
     * 这类文本、或单元格被清成「常规」格式（POI 读出数字序列号）时会**静默丢日期**，
     * 表现为导入项目「状态已办结但办结时间为空」。
     */
    private Date dateCell(List<Object> row, int c) {
        if (row == null || c < 0 || c >= row.size()) return null;
        Object v = row.get(c); if (v == null) return null;
        if (v instanceof Date) return (Date) v;
        try {
            if (v instanceof Number) {
                double d = ((Number) v).doubleValue();
                // Excel 日期序列号区间：1900-01-01(=1) ~ 9999-12-31(≈2958465)
                if (d >= 1 && d <= 2958465) return DateUtil.getJavaDate(d, false);
                long n = (long) d;
                if (n >= 19000101L && n <= 99991231L) {
                    return ymd((int) (n / 10000), (int) (n / 100 % 100), (int) (n % 100));
                }
                return null;
            }
            String s = v.toString().trim();
            if (s.isEmpty()) return null;
            if (s.matches("\\d{8}")) {
                long n = Long.parseLong(s);
                Date r = ymd((int) (n / 10000), (int) (n / 100 % 100), (int) (n % 100));
                if (r != null) return r;
            }
            Matcher m = DATE_TEXT.matcher(s);
            if (m.find()) {
                return ymd(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
            }
            return null;
        } catch (Exception e) { return null; }
    }

    /** 组装 y-M-d；越界返回 null（避免 Calendar 静默滚动到相邻月份） */
    private Date ymd(int y, int m, int d) {
        if (y < 1900 || y > 9999 || m < 1 || m > 12 || d < 1 || d > 31) return null;
        Calendar cal = Calendar.getInstance(); cal.clear();
        cal.set(y, m - 1, d);
        return cal.getTime();
    }

    /**
     * 判断某工作量二级表头是否为「内部工作量」。
     * 规则（包含即触发）：
     * 1. 表头含(内部)/（内部）→ 内部；含(外部)/（外部）→ 外部。
     * 2. 表头含"水准" → 委托任务含"定线"或"实测"为外部，否则内部。
     * 3. 表头含"管线" → 委托任务含"验线"为内部，否则外部。
     * 4. 其余默认外部。
     */
    private boolean isInternalWorkload(String rawHeader, String engineeringProject) {
        if (rawHeader == null) return false;
        if (rawHeader.contains("(内部)") || rawHeader.contains("（内部）")) return true;
        if (rawHeader.contains("(外部)") || rawHeader.contains("（外部）")) return false;
        String eng = engineeringProject == null ? "" : engineeringProject;
        if (rawHeader.contains("水准")) {
            return !(eng.contains("定线") || eng.contains("实测"));
        }
        if (rawHeader.contains("管线")) {
            return eng.contains("验线");
        }
        return false;
    }

    // ====================== 工作量列识别 ======================

    /**
     * 一级表头是否为工作量列（含「工作量」字样，且属于管线定验线/实测、管线图、其它三类语义组）。
     * 注意：二级表头逐年在变（2024 图组=管线探测/管线核查/秦华外部/图格，2026 图组=管线新测/…/图格(内部)），
     * 因此只能按一级表头语义识别，绝不能依赖二级表头名。
     *
     * 历史口径说明：曾按「一级表头组 × 委托任务」白名单过滤「外部」工作量，后经与客户确认为历史数据遗留问题——
     * 跨类型填列的数据同样应当导入，故已取消该过滤，现在一律按实际填写的值导入。
     */
    private static boolean isWorkloadTopHeader(String topHeader) {
        if (topHeader == null) return false;
        String t = topHeader.replace(" ", "").replace("\u3000", "");
        if (!t.contains("工作量")) return false;
        return t.contains("管线图") || t.contains("定验线") || t.contains("实测")
                || t.contains("其它") || t.contains("其他");
    }

    static class FuzzyResult<T> { T item; double score; }
    static class FuzzyBilling { Long billingId; Long categoryId; String billingCategory; String priceUnit; BigDecimal minQuantity; BigDecimal unitPrice; double score; }

    private FuzzyResult<ProjCategory> fuzzyMatchCategory(String query, List<ImportPreviewResponse.CategoryOption> options) {
        if (options == null || options.isEmpty()) return null;
        FuzzyResult<ProjCategory> best = null;
        for (ImportPreviewResponse.CategoryOption o : options) {
            double s = similarity(query, o.getName());
            ProjCategory pc = new ProjCategory(); pc.setId(o.getId()); pc.setName(o.getName());
            if (best == null || s > best.score) {
                FuzzyResult<ProjCategory> r = new FuzzyResult<>(); r.item = pc; r.score = s; best = r;
            }
        }
        return best;
    }
    private FuzzyBilling fuzzyMatchBilling(String rawHeader, String billingType,
                                           List<ImportPreviewResponse.BillingOption> opts,
                                           List<ImportPreviewResponse.CategoryOption> cats,
                                           Long projectCategoryId) {
        if (opts == null || opts.isEmpty()) return null;
        String clean = normalizeBilling(rawHeader);
        if (clean.endsWith("工作量")) clean = clean.substring(0, clean.length() - 3).trim();
        boolean wantInternal = "internal".equals(billingType);
        // 三轮匹配计费类别（billing_category）
        FuzzyBilling best = findBestBilling(clean, opts, wantInternal, projectCategoryId, true, true);
        if (best != null && best.score >= 1.0) return best;
        FuzzyBilling r2 = findBestBilling(clean, opts, wantInternal, projectCategoryId, true, false);
        if (r2 != null && r2.score > (best == null ? 0 : best.score) + 0.001) best = r2;
        if (best != null && best.score >= 1.0) return best;
        FuzzyBilling r3 = findBestBilling(clean, opts, wantInternal, null, false, false);
        if (r3 != null && r3.score > (best == null ? 0 : best.score) + 0.001) best = r3;
        // 计费类别匹配度达标(>=0.5)则直接返回
        if (best != null && best.score >= 0.5) return best;
        // 匹配不到计费类别，退而匹配小类（proj_category.name，level=2），billingId 置 null
        if (cats != null && !cats.isEmpty()) {
            FuzzyBilling catBest = findBestCategory(clean, cats);
            if (catBest != null && catBest.score >= 0.5) return catBest;
        }
        return best;
    }
    private boolean billingTypeMatch(boolean wantInternal, String dbBillingType, boolean strict) {
        if (dbBillingType == null) return !strict;
        String t = dbBillingType.trim();
        boolean dbIsInt = "internal".equalsIgnoreCase(t) || "内部".equals(t);
        boolean dbIsExt = "external".equalsIgnoreCase(t) || "外部".equals(t);
        if (!dbIsInt && !dbIsExt) return !strict; // 配置未知值时严格模式就跳过，宽松就通过
        return wantInternal ? dbIsInt : dbIsExt;
    }
    private FuzzyBilling findBestBilling(String clean, List<ImportPreviewResponse.BillingOption> opts,
                                          boolean wantInternal, Long projectCategoryId,
                                          boolean strictCategory, boolean strictType) {
        FuzzyBilling best = null;
        for (ImportPreviewResponse.BillingOption o : opts) {
            if (strictCategory && projectCategoryId != null
                && !projectCategoryId.equals(o.getCategoryId())) continue;
            String dbBillingType = o.getBillingType() == null ? null : o.getBillingType().trim();
            if (!billingTypeMatch(wantInternal, dbBillingType, strictType)) continue;
            String dbCatRaw = o.getBillingCategory() == null ? "" : o.getBillingCategory().trim();
            double s = similarity(clean, normalizeBilling(dbCatRaw));
            if (best == null || s > best.score) {
                FuzzyBilling b = new FuzzyBilling();
                b.billingId = o.getBillingId(); b.categoryId = o.getCategoryId();
                b.billingCategory = dbCatRaw;
                b.priceUnit = o.getPriceUnit(); b.minQuantity = o.getMinQuantity();
                b.unitPrice = o.getUnitPrice();
                b.score = s; best = b;
            }
        }
        return best;
    }

    /** 匹配小类（proj_category.name，level=2）：billingId 置 null，单价 null（由 calcGroup 剩余项兜底推算） */
    private FuzzyBilling findBestCategory(String clean, List<ImportPreviewResponse.CategoryOption> cats) {
        FuzzyBilling best = null;
        for (ImportPreviewResponse.CategoryOption c : cats) {
            String catName = c.getName() == null ? "" : c.getName().trim();
            double s = similarity(clean, normalizeBilling(catName));
            if (best == null || s > best.score) {
                FuzzyBilling b = new FuzzyBilling();
                b.billingId = null;
                b.categoryId = c.getId();
                b.billingCategory = catName;
                b.priceUnit = null;
                b.minQuantity = null;
                b.unitPrice = null;
                b.score = s;
                best = b;
            }
        }
        return best;
    }

    /** 归一化计费类别/小类名：剥离(内部/外部/内/外)标记、全角括号转半角、去空白 */
    private String normalizeBilling(String s) {
        if (s == null) return "";
        s = s.trim();
        s = s.replaceAll("[（(](内部|外部|内|外)[）)]", "");
        s = s.replaceAll("（", "(").replaceAll("）", ")");
        s = s.replaceAll("[\\s　]", "");
        return s;
    }

    private double similarity(String a, String b) {
        if (a == null) a = ""; if (b == null) b = "";
        if (a.equals(b)) return 1.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        Set<Character> sa = new HashSet<>(); for (char c : a.toCharArray()) sa.add(c);
        Set<Character> sb = new HashSet<>(); for (char c : b.toCharArray()) sb.add(c);
        Set<Character> inter = new HashSet<>(sa); inter.retainAll(sb);
        Set<Character> uni = new HashSet<>(sa); uni.addAll(sb);
        double jaccard = uni.isEmpty() ? 0 : (double) inter.size() / uni.size();
        double contain = (a.contains(b) || b.contains(a)) ? 0.8 : 0.0;
        return Math.max(jaccard, contain);
    }
}
