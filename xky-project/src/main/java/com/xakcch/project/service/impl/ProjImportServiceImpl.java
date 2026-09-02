package com.xakcch.project.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
        fullResp.setReadyCount(readyRows.size());
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
        // 工作量列：只从顶层表头含"工作量"的组中找（管线定验线实测工作量/管线图工作量/其它工作量）
        if (headerL2 != null) {
            for (int c = 0; c < headerL2.size(); c++) {
                if (headerL2.get(c) == null) continue;
                String top = headerL2.get(c).toString().trim();
                if (top.isEmpty() || top.contains("合计")) continue;
                String sub = (c < headerL1.size() && headerL1.get(c) != null) ? headerL1.get(c).toString().trim() : "";
                if (sub.isEmpty()) continue;
                if (top.contains("工作量") && !sub.equals("秦华外部")) {
                    // 工作量列：子表头含(内部)的是内部工作量，否则是外部工作量
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
            // 解析工作量（billingCol: 顶层"工作量"组的列）
            for (Map.Entry<Integer, String> me : billingCol.entrySet()) {
                int col = me.getKey();
                String rawHeader = me.getValue();
                BigDecimal wl = numCell(row, col);
                if (wl == null || wl.signum() == 0) continue;
                ImportPreviewWorkload w = new ImportPreviewWorkload();
                w.setBillingCategoryRaw(rawHeader);
                w.setWorkload(wl);
                // 内/外判定：优先显式(内部)/(外部)；其次"水准"/"管线"特殊规则（包含即触发）
                boolean isInt = isInternalWorkload(rawHeader, pr.getEngineeringProject());
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
            // 产值只读合计：内部产值合计(O)、外部产值合计(T)
            BigDecimal internalTotal = numCell(row, colInternalTot);
            BigDecimal externalTotal = numCell(row, colExternalTot);
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

    /** 备注 = 到账说明 + 备注 拼接（空值/日期跳过，分号分隔） */
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
        // 重复提交保护：同一 token 只允许一个 commit 在跑；若已跑完则直接返回缓存结果
        if (entry.commitResult != null) return entry.commitResult;
        if (!entry.committing.compareAndSet(false, true)) {
            // 有并发/重试请求正在提交中，短自旋等待已有线程完成结果并返回（最多90s）
            long start = System.currentTimeMillis();
            while (entry.commitResult == null && entry.committing.get() && System.currentTimeMillis() - start < 90 * 1000L) {
                try { Thread.sleep(500L); } catch (InterruptedException ignore) { Thread.currentThread().interrupt(); break; }
            }
            if (entry.commitResult != null) return entry.commitResult;
            throw new RuntimeException("正在导入中，请稍后到导入日志查看结果");
        }
        ImportPreviewResponse cached = entry.resp;
        ImportCommitResult result = new ImportCommitResult();
        List<ImportPreviewRow> rows = req.getRows() == null ? cached.getRows() : req.getRows();
        long t0 = System.currentTimeMillis();
        final int[] counter = {0, 0, 0}; // succ, skip, fail
        final String user = SecurityUtils.getUsername();
        final ProjImportLog log = new ProjImportLog();
        log.setFileName("upload.xlsx"); log.setTotalRows(rows.size());
        log.setStatus("running"); log.setCreateBy(user);
        // 1. log insert 独立事务（避免被后续错误牵连）
        runInNewTx(() -> importLogMapper.insertImportLog(log));
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
            for (Map.Entry<String, List<ImportPreviewRow>> g : groups.entrySet()) {
                List<ImportPreviewRow> group = g.getValue();
                // 一组 = 一个父项目 + 若干子项，单事务写入，任何 DB 错误只回滚本组
                try {
                    Throwable[] err = {null};
                    runInNewTx(() -> {
                        try {
                            writeOneGroup(group, user);
                        } catch (Throwable t) { err[0] = t; throw t; }
                    });
                    if (err[0] != null) throw new RuntimeException(err[0].getMessage(), err[0]);
                    counter[0] += group.size();
                } catch (Exception ex) {
                    counter[2] += group.size();
                    String msg = ex.getCause() != null && ex.getCause().getMessage() != null
                        ? ex.getCause().getMessage() : ex.getMessage();
                    // 去掉可能超长的 PSQLException 堆栈前缀(只留第一行)
                    if (msg != null && msg.contains("\n")) msg = msg.split("\n")[0];
                    for (ImportPreviewRow row : group) {
                        ImportCommitResult.RowDetail d = new ImportCommitResult.RowDetail();
                        d.setExcelRow(row.getExcelRow()); d.setProjectCode(row.getProjectCode());
                        d.setReason(msg);
                        result.getFailedDetails().add(d);
                    }
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException("导入失败：" + ex.getMessage(), ex);
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
            // 3. log update 独立事务（无论之前多少行DB报错，这一步不受影响）
            try {
                runInNewTx(() -> importLogMapper.updateImportLog(log));
                System.out.println("[proj-import] log updated id=" + log.getId()
                    + " failChars=" + (log.getFailDetails() == null ? 0 : log.getFailDetails().length())
                    + " skipChars=" + (log.getSkipDetails() == null ? 0 : log.getSkipDetails().length()));
            } catch (Exception ignoreLog) {
                System.err.println("[proj-import] log update FAILED id=" + log.getId() + " -> " + ignoreLog.getMessage());
                ignoreLog.printStackTrace();
            }
            // 4. 缓存结果供重试/重入请求读取（2小时 TTL，不清空 token）
            ImportCommitResult cachedResult = new ImportCommitResult();
            cachedResult.setLogId(log.getId());
            cachedResult.setSuccessCount(counter[0]);
            cachedResult.setSkippedCount(counter[1]);
            cachedResult.setFailedCount(counter[2]);
            cachedResult.setCostMs(System.currentTimeMillis() - t0);
            try {
                cachedResult.setSkippedDetails(om.readValue(om.writeValueAsString(result.getSkippedDetails()),
                    com.fasterxml.jackson.databind.type.TypeFactory.defaultInstance().constructCollectionType(List.class, ImportCommitResult.RowDetail.class)));
                cachedResult.setFailedDetails(om.readValue(om.writeValueAsString(result.getFailedDetails()),
                    com.fasterxml.jackson.databind.type.TypeFactory.defaultInstance().constructCollectionType(List.class, ImportCommitResult.RowDetail.class)));
            } catch (Exception ignore) {}
            entry.commitResult = cachedResult;
            entry.committing.set(false);
        }
        result.setLogId(log.getId());
        result.setSuccessCount(counter[0]);
        result.setSkippedCount(counter[1]);
        result.setFailedCount(counter[2]);
        result.setCostMs(System.currentTimeMillis() - t0);
        // 保证前端收到明细，便于结果页直接展示
        if (result.getFailedDetails() == null) result.setFailedDetails(new ArrayList<>());
        if (result.getSkippedDetails() == null) result.setSkippedDetails(new ArrayList<>());
        // 与DB中写入的日志保持一致：把cachedResult里序列化后的JSON再读回（与落库内容完全一致）
        if (entry.commitResult != null) {
            result.setFailedDetails(entry.commitResult.getFailedDetails() == null ? new ArrayList<>() : entry.commitResult.getFailedDetails());
            result.setSkippedDetails(entry.commitResult.getSkippedDetails() == null ? new ArrayList<>() : entry.commitResult.getSkippedDetails());
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
     * 一组写入（同一工程编号的所有行 = 一个父项目 + 若干子项；必须运行在独立事务中）。
     * 父项目字段合并：close_time 取最晚、负责人取并集、其余文本取第一条非空；
     * 子项体现在 proj_workload.sub_item_no / sub_item_name 上。
     */
    private void writeOneGroup(List<ImportPreviewRow> group, String user) {
        if (group == null || group.isEmpty()) return;
        String code = group.get(0).getProjectCode() == null ? "" : group.get(0).getProjectCode().trim();
        if (code.isEmpty()) throw new RuntimeException("工程编号为空");

        // 1. 解析负责人（未匹配则自动建档影子用户）并合并父项目字段
        String projectName = null, engineeringProject = null, clientUnit = null, projectLocation = null;
        Long categoryId = null;
        Date closeTime = null;
        Date materialTime = null;
        LinkedHashSet<Long> leaderIds = new LinkedHashSet<>();
        for (ImportPreviewRow row : group) {
            if (row.getLeaderId() == null && StringUtils.isNotBlank(row.getLeaderName())) {
                com.xakcch.common.core.domain.entity.SysUser shadow =
                        projectService.ensureLeaderByName(row.getLeaderName(), user);
                row.setLeaderId(shadow.getUserId());
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

        // 3. 确定父项目：不存在则新建，存在则复用并合并办结时间（取更晚）
        ProjProject pj = projectMapper.checkProjectCodeUnique(new ProjProject() {{ setProjectCode(code); }});
        if (pj == null) {
            pj = new ProjProject();
            pj.setProjectCode(code);
            pj.setProjectName(projectName);
            pj.setEngineeringProject(engineeringProject);
            pj.setProjectCategoryId(categoryId);
            pj.setClientUnit(clientUnit);
            pj.setProjectLocation(projectLocation);
            pj.setStatus("closed");
            pj.setCloseTime(closeTime);
            pj.setAssignDate(closeTime);
            pj.setCreateBy(user);
            projectMapper.insertProject(pj);
        } else if (closeTime != null) {
            projectMapper.updateProjectCloseTime(pj.getId(), closeTime);
        }

        // 4. 负责人并集（已有项目保留原负责人，仅插入新增，避免唯一索引冲突）
        Long[] existingLeaderIds = leaderMapper.selectLeaderIdsByProjectId(pj.getId());
        Set<Long> existingSet = new HashSet<>();
        if (existingLeaderIds != null) existingSet.addAll(Arrays.asList(existingLeaderIds));
        List<Long> toInsert = new ArrayList<>();
        for (Long lid : leaderIds) if (!existingSet.contains(lid)) toInsert.add(lid);
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

        // 7. 付款合并（同类型金额累加成一条）
        writeMergedPayments(pj, group, user);

        // 8. 资料提交合并为一条（取首个非空领取时间）
        if (materialTime != null) {
            ProjMaterial mat = new ProjMaterial();
            mat.setProjectId(pj.getId());
            mat.setSubmitTime(materialTime);
            mat.setStatus("已领取");
            mat.setSubmitStatus("submitted");
            mat.setGuarantorFlag("N");
            mat.setArchiveFlag("N");
            mat.setCreateBy(user);
            materialMapper.insertMaterial(mat);
            ProjMaterialFlow flow = new ProjMaterialFlow();
            flow.setMaterialId(mat.getId());
            flow.setFlowType("领取");
            flow.setOperateTime(materialTime);
            flow.setCreateBy(user);
            materialFlowMapper.insertFlow(flow);
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
            wl.setPriceSource("manual");
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

    /** 付款合并：同类型金额累加成一条，并与已有项目付款累加后 upsert */
    private void writeMergedPayments(ProjProject pj, List<ImportPreviewRow> group, String user) {
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
        // 复用已有项目时，同类型付款与库内累加
        List<ProjPayment> existing = paymentMapper.selectPaymentsByProjectId(pj.getId());
        for (ProjPayment ep : existing) {
            String key = ep.getPaymentType() == null ? "" : ep.getPaymentType();
            ImportPreviewPayment m = merged.get(key);
            if (m != null && ep.getAmount() != null) {
                m.setAmount(m.getAmount().add(ep.getAmount()));
            }
        }
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
            paymentMapper.upsertPayment(pp);
        }
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
    /** 取单元格文本；日期类型视为无文本（返回 null，避免日期 toString 污染备注） */
    private String textOf(List<Object> row, int c) {
        if (row == null || c < 0 || c >= row.size()) return null;
        Object v = row.get(c); if (v == null) return null;
        if (v instanceof Date) return null;
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
    private Date dateCell(List<Object> row, int c) {
        if (row == null || c < 0 || c >= row.size()) return null;
        Object v = row.get(c); if (v == null) return null;
        if (v instanceof Date) return (Date) v;
        try {
            String s = v.toString().trim();
            if (s.isEmpty()) return null;
            if (s.matches("\\d{4}[-/年]\\d{1,2}[-/月]\\d{1,2}日?.*")) {
                String[] parts = s.split("[-/ :年月日]");
                int y = Integer.parseInt(parts[0]);
                int m = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                int d = parts.length > 2 ? (parts[2].isEmpty() ? 1 : Integer.parseInt(parts[2])) : 1;
                Calendar cal = Calendar.getInstance(); cal.clear();
                cal.set(y, m - 1, d);
                return cal.getTime();
            }
            return null;
        } catch (Exception e) { return null; }
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
