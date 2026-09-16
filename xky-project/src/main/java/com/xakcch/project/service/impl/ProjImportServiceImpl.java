package com.xakcch.project.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    @Autowired private ProjProjectMapper projectMapper;
    @Autowired private ProjCategoryMapper categoryMapper;
    @Autowired private ProjCategoryBillingMapper billingMapper;
    @Autowired private ProjLeaderMapper leaderMapper;
    @Autowired private ProjTaskMapper taskMapper;
    @Autowired private ProjWorkloadMapper workloadMapper;
    @Autowired private ProjPaymentMapper paymentMapper;
    @Autowired private ProjMaterialMapper materialMapper;
    @Autowired private ProjMaterialFlowMapper materialFlowMapper;
    @Autowired private ProjImportLogMapper importLogMapper;
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
        List<String> existsCodes = new ArrayList<>();
        for (ImportPreviewRow r : fullResp.getRows()) {
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
        fullResp.setTotalRows(fullResp.getRows().size());
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
        lightResp.setProblemSummary(ps);
        lightResp.getCategoryOptions().addAll(fullResp.getCategoryOptions());
        lightResp.getBillingOptions().addAll(fullResp.getBillingOptions());
        lightResp.getRows().addAll(readyRows);
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
        ProjCategory q = new ProjCategory(); q.setLevel(2); q.setStatus("0");
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
            if (StringUtils.isNotBlank(pr.getLeaderName())) {
                // 昵称精确匹配（含离职/影子用户）；匹配不到则预览阶段静默跳过，落库时自动建档
                com.xakcch.common.core.domain.entity.SysUser exact =
                        leaderMapper.selectUserByNickName(pr.getLeaderName().trim());
                if (exact != null) {
                    pr.setLeaderId(exact.getUserId());
                    pr.setLeaderScore(1.0);
                }
            }
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

        int dataStart = l2 != null ? l1Idx + 2 : l1Idx + 1;
        for (int r = dataStart; r < rows.size(); r++) {
            List<Object> row = rows.get(r);
            String code = strCell(row, colProjectCode);
            if (StringUtils.isBlank(code)) continue;
            ImportPreviewRow pr = codeMap.get(code.trim());
            if (pr == null) continue;

            BigDecimal preAmt = numCell(row, colPrepayAmt);
            if (preAmt != null && preAmt.signum() > 0) {
                ImportPreviewPayment p = new ImportPreviewPayment();
                p.setPaymentType("advance");
                p.setAmount(preAmt);
                p.setPayTime(dateCell(row, colPrepayTime));
                p.setSource("sheet2预付款");
                p.setRemark(buildRemark(row, colExplain, colRemark));
                pr.getPayments().add(p);
            }
            BigDecimal finalAmt = numCell(row, colFinalAmt);
            if (finalAmt != null && finalAmt.signum() > 0) {
                ImportPreviewPayment p = new ImportPreviewPayment();
                p.setPaymentType("final");
                p.setAmount(finalAmt);
                p.setPayTime(dateCell(row, colFinalTime));
                p.setSource("sheet2尾款");
                p.setRemark(buildRemark(row, colExplain, colRemark));
                pr.getPayments().add(p);
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
        List<ImportPreviewRow> rows = req.getRows() == null ? cached.getRows() : req.getRows();
        if (rows == null) rows = Collections.emptyList();
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
            // 策略：工程编号已存在 → 整组跳过（不写入、不合并、不覆盖），计入跳过明细，避免重复导入产生重复子项/任务与付款金额翻倍
            List<Map.Entry<String, List<ImportPreviewRow>>> groupList = new ArrayList<>();
            for (Map.Entry<String, List<ImportPreviewRow>> g : groups.entrySet()) {
                Long existedId = existingIdByCode.get(g.getKey());
                if (existedId != null) {
                    counter[1] += g.getValue().size();
                    for (ImportPreviewRow row : g.getValue()) {
                        ImportCommitResult.RowDetail d = new ImportCommitResult.RowDetail();
                        d.setExcelRow(row.getExcelRow());
                        d.setProjectCode(row.getProjectCode());
                        d.setReason("工程编号已存在（项目ID=" + existedId + "），整组跳过未写入");
                        result.getSkippedDetails().add(d);
                    }
                    continue;
                }
                groupList.add(g);
            }
            // 档3a：按批事务写库（TX_BATCH_SIZE 组一批），批内任何 DB 错误 → 整批回滚并标记批内全部组
            for (int i = 0; i < groupList.size(); i += TX_BATCH_SIZE) {
                int end = Math.min(i + TX_BATCH_SIZE, groupList.size());
                List<Map.Entry<String, List<ImportPreviewRow>>> batch = groupList.subList(i, end);
                try {
                    runInNewTx(() -> {
                        for (Map.Entry<String, List<ImportPreviewRow>> g : batch) {
                            writeOneGroup(g.getValue(), user, null, leaderByName);
                        }
                    });
                    for (Map.Entry<String, List<ImportPreviewRow>> g : batch) counter[0] += g.getValue().size();
                } catch (Exception ex) {
                    String msg = ex.getCause() != null && ex.getCause().getMessage() != null
                        ? ex.getCause().getMessage() : ex.getMessage();
                    if (msg != null && msg.contains("\n")) msg = msg.split("\n")[0];
                    for (Map.Entry<String, List<ImportPreviewRow>> g : batch) {
                        counter[2] += g.getValue().size();
                        for (ImportPreviewRow row : g.getValue()) {
                            ImportCommitResult.RowDetail d = new ImportCommitResult.RowDetail();
                            d.setExcelRow(row.getExcelRow()); d.setProjectCode(row.getProjectCode());
                            d.setReason(msg + "（该行所在批次已整体回滚，未写入数据）");
                            result.getFailedDetails().add(d);
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
            if (result.getFailedDetails() == null) result.setFailedDetails(new ArrayList<>());
            if (result.getSkippedDetails() == null) result.setSkippedDetails(new ArrayList<>());
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
     * 消除 writeOneGroup 组内对每行重复 selectUserByNickName 的 N+1。
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
        // 回填 leaderId：后续 writeOneGroup 不再触发 DB 建档
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
     * 一组写入（同一工程编号的所有行 = 一个父项目 + 若干子项；运行在批事务中）。
     * 父项目字段合并：close_time 取最晚、负责人取并集、其余文本取第一条非空；
     * 子项体现在 proj_workload.sub_item_no / sub_item_name 上。
     * 档1优化：负责人由批前预解析回填；新建项目跳过「查旧负责人/查最大子项号/查旧付款」三个冗余 select。
     *
     * @param group              同工程编号的行
     * @param user               操作人
     * @param existingProjectId  已存在项目ID；当前策略为「已存在编号整组跳过」，调用处恒传 null（即全部走新建）。
     *                           复用分支（合并追加）保留代码，以备策略切换时启用。
     * @param leaderByName       批前预解析的 负责人姓名→SysUser 映射（可能为 null 表示未预解析）
     */
    private void writeOneGroup(List<ImportPreviewRow> group, String user,
                               Long existingProjectId,
                               Map<String, com.xakcch.common.core.domain.entity.SysUser> leaderByName) {
        if (group == null || group.isEmpty()) return;
        String code = group.get(0).getProjectCode() == null ? "" : group.get(0).getProjectCode().trim();
        if (code.isEmpty()) throw new RuntimeException("工程编号为空");

        // 1. 负责人锚定（批前预解析已回填 leaderId；此处仅兜底）并合并父项目字段
        String projectName = null, engineeringProject = null, clientUnit = null, projectLocation = null;
        Long categoryId = null;
        Date closeTime = null;
        Date materialTime = null;
        LinkedHashSet<Long> leaderIds = new LinkedHashSet<>();
        for (ImportPreviewRow row : group) {
            if (row.getLeaderId() == null && StringUtils.isNotBlank(row.getLeaderName())) {
                com.xakcch.common.core.domain.entity.SysUser shadow =
                        leaderByName == null ? null : leaderByName.get(row.getLeaderName().trim());
                if (shadow == null) shadow = projectService.ensureLeaderByName(row.getLeaderName(), user);
                if (shadow != null) row.setLeaderId(shadow.getUserId());
            }
            if (row.getLeaderId() != null) leaderIds.add(row.getLeaderId());
            if (projectName == null && StringUtils.isNotBlank(row.getEngineeringProject())) {
                projectName = row.getEngineeringProject();
            }
            if (engineeringProject == null && StringUtils.isNotBlank(row.getEngineeringProject())) {
                engineeringProject = row.getEngineeringProject();
            }
            if (clientUnit == null && StringUtils.isNotBlank(row.getClientUnit())) {
                clientUnit = row.getClientUnit();
            }
            if (projectLocation == null && StringUtils.isNotBlank(row.getProjectLocation())) {
                projectLocation = row.getProjectLocation();
            }
            if (categoryId == null && row.getProjectCategoryId() != null) {
                categoryId = row.getProjectCategoryId();
            }
            if (row.getFinishDate() != null && (closeTime == null || row.getFinishDate().after(closeTime))) {
                closeTime = row.getFinishDate();
            }
            if (materialTime == null && row.getMaterialSubmitTime() != null) {
                materialTime = row.getMaterialSubmitTime();
            }
        }
        if (categoryId == null) throw new RuntimeException("项目类别未选择");
        if (leaderIds.isEmpty()) throw new RuntimeException("负责人为空且姓名缺失");
        if (StringUtils.isBlank(projectName)) projectName = code;

        // 2. 校验所有子项的工作量计费类别均已匹配
        for (ImportPreviewRow row : group) {
            for (ImportPreviewWorkload w : row.getWorkloads()) {
                if (w.getBillingId() == null) {
                    String disp = w.getBillingCategoryRaw() == null ? "" : w.getBillingCategoryRaw()
                        .replaceAll("[（(](内部|外部)[）)]", "").trim();
                    throw new RuntimeException("工作项未匹配计费类别：" + disp);
                }
            }
        }

        // 3. 确定父项目：批前预取判定新建/复用（复用则合并办结时间，取更晚）
        boolean isNew = (existingProjectId == null);
        ProjProject pj = new ProjProject();
        if (isNew) {
            pj.setProjectCode(code);
            pj.setProjectName(projectName);
            pj.setEngineeringProject(engineeringProject);
            pj.setProjectCategoryId(categoryId);
            pj.setClientUnit(clientUnit);
            pj.setProjectLocation(projectLocation);
            pj.setDataSource("import");
            pj.setStatus("closed");
            pj.setCloseTime(closeTime);
            pj.setAssignDate(closeTime);
            pj.setCreateBy(user);
            projectMapper.insertProject(pj);
        } else {
            pj.setId(existingProjectId);
            if (closeTime != null) {
                projectMapper.updateProjectCloseTime(existingProjectId, closeTime);
            }
        }

        // 4. 负责人并集（新建项目无需查旧负责人；复用项目保留原负责人，仅插新增，避免唯一索引冲突）
        List<Long> toInsert = new ArrayList<>(leaderIds);
        if (!isNew) {
            Long[] existingLeaderIds = leaderMapper.selectLeaderIdsByProjectId(pj.getId());
            Set<Long> existingSet = new HashSet<>();
            if (existingLeaderIds != null) existingSet.addAll(Arrays.asList(existingLeaderIds));
            toInsert.removeIf(existingSet::contains);
        }
        if (!toInsert.isEmpty()) {
            leaderMapper.insertProjectLeaders(pj.getId(), toInsert.toArray(new Long[0]), user);
        }

        // 5. 任务：每个负责人一条（合并），批量插入
        List<ProjTask> tasks = new ArrayList<>();
        for (Long lid : leaderIds) {
            ProjTask task = new ProjTask();
            task.setProjectId(pj.getId());
            task.setUserId(lid);
            task.setTaskName(projectName);
            task.setStatus("finished");
            task.setActualFinishDate(closeTime);
            task.setRequiredFinishDate(closeTime);
            task.setAssignDate(closeTime);
            task.setCreateBy(user);
            tasks.add(task);
        }
        if (!tasks.isEmpty()) taskMapper.insertTaskBatch(tasks);

        // 6. 子项写入：每个 Excel 行 = 一个子项，sub_item_no 项目内自增（续接已有最大序号）
        Integer maxNo = workloadMapper.selectMaxSubItemNo(pj.getId());
        int seq = maxNo == null ? 0 : maxNo;
        List<ProjWorkload> allWorkloads = new ArrayList<>();
        for (ImportPreviewRow row : group) {
            seq++;
            allWorkloads.addAll(buildSubItemWorkloads(pj, row, seq, user));
        }
        if (!allWorkloads.isEmpty()) workloadMapper.insertWorkloadBatch(allWorkloads);

        // 7. 付款合并（同类型金额累加成一条；新建项目免查旧付款）
        writeMergedPayments(pj, group, user, isNew);

        // 8. 资料提交（每个导入项目必须生成一条资料记录，保证已办结项目在资料管理可见）：
        //    有「资料领取」日期 → 已领取 + 领取流转；无日期/无该列 → 待领取、待提交（与手工办结 completeProject 的兜底口径一致），
        //    领取时间、联系人等由用户后续在资料管理页面补录
        ProjMaterial mat = new ProjMaterial();
        mat.setProjectId(pj.getId());
        mat.setGuarantorFlag("N");
        mat.setArchiveFlag("N");
        mat.setCreateBy(user);
        if (materialTime != null) {
            mat.setSubmitTime(materialTime);
            // 状态写字典值（proj_material_status: received=已领取），不要写中文标签
            mat.setStatus("received");
            mat.setSubmitStatus("submitted");
            materialMapper.insertMaterial(mat);
            ProjMaterialFlow flow = new ProjMaterialFlow();
            flow.setMaterialId(mat.getId());
            flow.setFlowType("领取");
            flow.setOperateTime(materialTime);
            flow.setCreateBy(user);
            materialFlowMapper.insertFlow(flow);
        } else {
            mat.setStatus("pending");
            mat.setSubmitStatus("pending");
            materialMapper.insertMaterial(mat);
        }
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
    private void writeMergedPayments(ProjProject pj, List<ImportPreviewRow> group, String user, boolean isNew) {
        Map<String, ImportPreviewPayment> merged = new LinkedHashMap<>();
        for (ImportPreviewRow row : group) {
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
        if (merged.isEmpty()) return;
        // 复用已有项目时，同类型付款与库内累加（新建项目免查旧付款）
        if (!isNew) {
            List<ProjPayment> existing = paymentMapper.selectPaymentsByProjectId(pj.getId());
            for (ProjPayment ep : existing) {
                String key = ep.getPaymentType() == null ? "" : ep.getPaymentType();
                ImportPreviewPayment m = merged.get(key);
                if (m != null && ep.getAmount() != null) {
                    m.setAmount(m.getAmount().add(ep.getAmount()));
                }
            }
        }
        List<ProjPayment> toSave = new ArrayList<>();
        for (ImportPreviewPayment pm : merged.values()) {
            ProjPayment pp = new ProjPayment();
            pp.setProjectId(pj.getId());
            pp.setPaymentType(pm.getPaymentType());
            pp.setAmount(pm.getAmount());
            pp.setPayTime(pm.getPayTime());
            pp.setPayUnit(pm.getPayUnit());
            pp.setPayMethod(pm.getPayMethod());
            pp.setReceivedStatus("received");
            pp.setInvoiceStatus("pending");
            pp.setRemark(pm.getRemark());
            pp.setCreateBy(user);
            toSave.add(pp);
        }
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
