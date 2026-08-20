package com.xakcch.project.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xakcch.common.core.domain.entity.SysUser;
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
    @Autowired private com.xakcch.system.mapper.SysUserMapper userMapper;

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
            if (r.isDuplicate()) { problemRows.add(r); continue; }
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
            boolean missLeader = StringUtils.isNotBlank(r.getLeaderName()) && r.getLeaderId() == null;
            boolean missBill = r.getWorkloads().stream().anyMatch(w -> w.getBillingId() == null);
            boolean hasWarn = (r.getWarnings() != null && !r.getWarnings().isEmpty())
                || r.getWorkloads().stream().anyMatch(w -> w.getWarning() != null && !w.getWarning().isEmpty());
            if (missCat || missLeader || missBill || hasWarn) {
                problemRows.add(r); continue;
            }
            if (r.getWorkloads().isEmpty()) {
                r.getWarnings().add("无工作量数据");
                problemRows.add(r); continue;
            }
            readyRows.add(r);
        }
        fullResp.setTotalRows(fullResp.getRows().size());
        // 统计问题行分类
        int dupCnt = 0, errCnt = 0, warnCnt = 0;
        for (ImportPreviewRow r : problemRows) {
            if (r.isDuplicate()) dupCnt++;
            else if (r.getErrors() != null && !r.getErrors().isEmpty()) errCnt++;
            else warnCnt++;
        }
        fullResp.setDuplicateCount(dupCnt);
        fullResp.setErrorCount(errCnt);
        fullResp.setWarningCount(warnCnt);
        fullResp.setReadyCount(readyRows.size());
        // 问题摘要
        ImportPreviewResponse.ProblemSummary ps = new ImportPreviewResponse.ProblemSummary();
        if (warnCnt > 0) ps.setWarningDesc(warnCnt + " 行数据存在未匹配字段（项目类别/负责人/计费类别等），请查看下方明细，修正Excel后重新上传");
        if (dupCnt > 0) ps.setDuplicateDesc(dupCnt + " 行的工程编号已存在于系统中，重复导入将自动跳过");
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
        lightResp.setDuplicateCount(dupCnt);
        lightResp.setErrorCount(errCnt);
        lightResp.setProblemSummary(ps);
        lightResp.getCategoryOptions().addAll(fullResp.getCategoryOptions());
        lightResp.getBillingOptions().addAll(fullResp.getBillingOptions());
        lightResp.getUserOptions().addAll(fullResp.getUserOptions());
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
        if (r.isDuplicate()) {
            probType = "已存在";
            problems.add("工程编号已存在于系统");
            suggestions.add("如需更新请在系统中修改");
        } else if (r.getErrors() != null && !r.getErrors().isEmpty()) {
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
            if (StringUtils.isNotBlank(r.getLeaderName()) && r.getLeaderId() == null) {
                problems.add("负责人「" + r.getLeaderName() + "」无法匹配到系统用户");
                suggestions.add("请在系统中确认用户昵称，或修改Excel中的负责人姓名");
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
            if (r.getWorkloads().isEmpty()) {
                problems.add("无工作量数据");
                suggestions.add("请在Excel中补充工作量列");
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
            if ("duplicate".equals(type) && r.isDuplicate()) isTarget = true;
            else if ("error".equals(type) && r.getErrors() != null && !r.getErrors().isEmpty()) isTarget = true;
            else if ("warning".equals(type) && !r.isDuplicate()
                && (r.getErrors() == null || r.getErrors().isEmpty())
                && (r.getProjectCategoryId() == null
                    || (StringUtils.isNotBlank(r.getLeaderName()) && r.getLeaderId() == null)
                    || r.getWorkloads().stream().anyMatch(w -> w.getBillingId() == null)
                    || (r.getWarnings() != null && !r.getWarnings().isEmpty())
                    || r.getWorkloads().stream().anyMatch(w -> w.getWarning() != null && !w.getWarning().isEmpty())
                    || r.getWorkloads().isEmpty()
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
        SysUser uq = new SysUser();
        uq.setStatus("0");
        List<SysUser> us = userMapper.selectUserList(uq);
        for (SysUser u : us) {
            ImportPreviewResponse.UserOption uo = new ImportPreviewResponse.UserOption();
            uo.setUserId(u.getUserId()); uo.setNickName(u.getNickName()); uo.setUserName(u.getUserName());
            resp.getUserOptions().add(uo);
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
        Map<Integer, String> outputCol = new LinkedHashMap<>();
        List<Object> headerL2 = headerRowIdx > 0 ? rows.get(headerRowIdx - 1) : null;
        for (int c = 0; c < headerL1.size(); c++) {
            Object v = headerL1.get(c);
            if (v == null) continue;
            String s = v.toString().trim();
            if (s.isEmpty()) continue;
            colMap.put(s, c);
        }
        // 工作量列：只从顶层表头含"工作量"的组中找（管线定验线实测工作量/管线图工作量/其它工作量）
        // 产值列：从顶层表头含"内部产值"/"外部产值"的组中找
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
                } else if (top.contains("内部产值")) {
                    outputCol.put(c, sub); // 内部产值金额列
                } else if (top.contains("外部产值")) {
                    outputCol.put(c, sub); // 外部产值金额列
                }
            }
        }
        int colProjectCode = firstMatch(colMap, "工程编号");
        int colClientUnit  = firstMatch(colMap, "委托单位");
        int colProject     = firstMatch(colMap, "委托任务");
        int colLocation    = firstMatch(colMap, "委托地点|工程地点");
        int colLeader      = firstMatch(colMap, "项目负责人|负责人");
        int colFinish      = firstMatch(colMap, "验收日期|办结日期");
        int colInternalTot = firstMatch(colMap, "内部产值.*合计|内部产值合计|内部合计");
        int colExternalTot = firstMatch(colMap, "外部产值.*合计|外部产值合计|外部合计");
        int colPayTime     = firstMatch(colMap, "到账时间|付款时间");
        int colMaterial    = firstMatch(colMap, "资料领取");
        
        Set<String> existCodes = new HashSet<>(projectMapper.selectExistProjectCodes());
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
            if (existCodes.contains(pr.getProjectCode())) {
                pr.setDuplicate(true);
                pr.getWarnings().add("工程编号已存在，导入时将跳过");
            }
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
                FuzzyResult2 fr = fuzzyMatchUser(pr.getLeaderName(), resp.getUserOptions());
                if (fr != null && fr.score >= 0.5) {
                    pr.setLeaderId(fr.userId);
                    pr.setLeaderScore(fr.score);
                } else if (fr != null) {
                    pr.getWarnings().add("负责人匹配度" + String.format("%.2f", fr.score) + "，请手动选择");
                } else {
                    pr.getWarnings().add("未匹配到负责人，请手动选择");
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
                // 子表头含(内部)的是内部工作量，否则是外部工作量
                boolean isInt = rawHeader.contains("(内部)") || rawHeader.contains("（内部）");
                w.setBillingType(isInt ? "internal" : "external");
                // 提取计费类别名：去掉(内部)/(外部)及空白
                String billingCatName = rawHeader.replaceAll("[（(](内部|外部)[）)]", "").trim();
                FuzzyBilling fb = fuzzyMatchBilling(billingCatName, w.getBillingType(), resp.getBillingOptions(), pr.getProjectCategoryId());
                if (fb != null && fb.score >= 0.5) {
                    w.setBillingId(fb.billingId);
                    w.setBillingCategory(fb.billingCategory);
                    w.setCategoryId(fb.categoryId);
                    w.setScore(fb.score);
                    w.setPriceUnit(fb.priceUnit);
                    w.setMinQuantity(fb.minQuantity);
                } else if (fb != null) {
                    w.setWarning("计费类别「" + billingCatName + "」匹配度" + String.format("%.2f", fb.score) + "，请手动选择");
                } else {
                    w.setWarning("未匹配到计费类别「" + billingCatName + "」，请手动选择");
                }
                pr.getWorkloads().add(w);
            }
            // 解析产值金额（outputCol: 顶层"内部产值"/"外部产值"组的列）
            BigDecimal internalSum = BigDecimal.ZERO;
            BigDecimal externalSum = BigDecimal.ZERO;
            for (Map.Entry<Integer, String> me : outputCol.entrySet()) {
                BigDecimal amt = numCell(row, me.getKey());
                if (amt == null || amt.signum() == 0) continue;
                // 根据顶层表头判断内/外
                String topHdr = "";
                if (headerL2 != null && me.getKey() < headerL2.size() && headerL2.get(me.getKey()) != null) {
                    topHdr = headerL2.get(me.getKey()).toString().trim();
                }
                if (topHdr.contains("内部")) internalSum = internalSum.add(amt);
                else if (topHdr.contains("外部")) externalSum = externalSum.add(amt);
            }
            if (internalSum.signum() > 0) pr.setInternalTotalFromExcel(internalSum);
            if (externalSum.signum() > 0) pr.setExternalTotalFromExcel(externalSum);
            resp.getRows().add(pr);
        }
    }

    private void recalcPrices(ImportPreviewResponse resp) {
        for (ImportPreviewRow row : resp.getRows()) {
            if (row.isDuplicate()) continue;
            List<ImportPreviewWorkload> internals = row.getWorkloads().stream().filter(w-> "internal".equals(w.getBillingType())).collect(Collectors.toList());
            List<ImportPreviewWorkload> externals = row.getWorkloads().stream().filter(w-> "external".equals(w.getBillingType())).collect(Collectors.toList());
            calcGroup(internals, row.getInternalTotalFromExcel(), true, row);
            calcGroup(externals, row.getExternalTotalFromExcel(), false, row);
        }
    }
    private void calcGroup(List<ImportPreviewWorkload> list, BigDecimal totalFromExcel, boolean isInternal, ImportPreviewRow row) {
        if (list.isEmpty() || totalFromExcel == null || totalFromExcel.signum() == 0) {
            if (isInternal) row.setInternalTotalCalced(BigDecimal.ZERO); else row.setExternalTotalCalced(BigDecimal.ZERO);
            return;
        }
        BigDecimal wlSum = list.stream().map(w -> w.getWorkload() == null ? BigDecimal.ZERO : w.getWorkload())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (wlSum.signum() == 0) return;
        BigDecimal sumOut = BigDecimal.ZERO;
        for (int i = 0; i < list.size(); i++) {
            ImportPreviewWorkload w = list.get(i);
            BigDecimal wl = w.getWorkload() == null ? BigDecimal.ZERO : w.getWorkload();
            BigDecimal unitPrice = totalFromExcel.multiply(wl).divide(wlSum, 4, RoundingMode.HALF_UP);
            w.setUnitPrice(unitPrice);
            if (i < list.size() - 1) {
                BigDecimal out = unitPrice.multiply(wl).setScale(2, RoundingMode.HALF_UP);
                w.setOutput(out);
                sumOut = sumOut.add(out);
            }
        }
        ImportPreviewWorkload last = list.get(list.size() - 1);
        BigDecimal tail = totalFromExcel.subtract(sumOut).setScale(2, RoundingMode.HALF_UP);
        last.setOutput(tail);
        BigDecimal wl = last.getWorkload() == null ? BigDecimal.ZERO : last.getWorkload();
        if (wl.signum() > 0) {
            last.setUnitPrice(tail.divide(wl, 4, RoundingMode.HALF_UP));
        }
        if (isInternal) row.setInternalTotalCalced(sumOut.add(tail)); else row.setExternalTotalCalced(sumOut.add(tail));
    }

    private void parseSheet2AndMerge(Map<String, List<List<Object>>> book, ImportPreviewResponse resp) {
        List<List<Object>> rows = null;
        boolean first = true;
        for (Map.Entry<String,List<List<Object>>> e : book.entrySet()) {
            if (first) { first = false; continue; }
            rows = e.getValue(); break;
        }
        if (rows == null || rows.isEmpty()) return;
        int headerRowIdx = -1;
        for (int i = 0; i < Math.min(rows.size(), 5); i++) {
            List<Object> r = rows.get(i);
            for (Object o : r) if (o != null && o.toString().contains("工程编号")) { headerRowIdx = i; break; }
            if (headerRowIdx >= 0) break;
        }
        if (headerRowIdx < 0) return;
        List<Object> h = rows.get(headerRowIdx);
        Map<String,Integer> colMap = new HashMap<>();
        for (int c = 0; c < h.size(); c++) {
            Object v = h.get(c); if (v == null) continue;
            colMap.put(v.toString().trim(), c);
        }
        int colProjectCode = firstMatch(colMap, "工程编号");
        int colPrepay      = firstMatch(colMap, "预付款金额|预付款");
        int colPrepayTime  = firstMatch(colMap, "预付款.*时间|预付时间");
        int colPrepayWay   = firstMatch(colMap, "预付款.*方式|支付方式预付款");
        int colFinalAmt    = firstMatch(colMap, "尾款.*金额|尾款支付金额|尾款");
        int colFinalTime   = firstMatch(colMap, "尾款.*时间|尾款支付时间");
        int colFinalWay    = firstMatch(colMap, "尾款.*方式|尾款支付方式");
        int colRemark      = firstMatch(colMap, "备注");
        Map<String, ImportPreviewRow> codeMap = resp.getRows().stream()
            .collect(Collectors.toMap(ImportPreviewRow::getProjectCode, x -> x, (a,b) -> a));
        for (int r = headerRowIdx + 1; r < rows.size(); r++) {
            List<Object> row = rows.get(r);
            String code = strCell(row, colProjectCode);
            if (StringUtils.isBlank(code)) continue;
            ImportPreviewRow pr = codeMap.get(code.trim());
            if (pr == null) continue;
            BigDecimal preAmt = numCell(row, colPrepay);
            if (preAmt != null && preAmt.signum() > 0) {
                ImportPreviewPayment p = new ImportPreviewPayment();
                p.setPaymentType("预付款");
                p.setAmount(preAmt);
                p.setPayTime(dateCell(row, colPrepayTime));
                p.setPayMethod(strCell(row, colPrepayWay));
                p.setSource("sheet2预付款");
                pr.getPayments().add(p);
            }
            BigDecimal finalAmt = numCell(row, colFinalAmt);
            Date finalTime = dateCell(row, colFinalTime);
            String finalWay = strCell(row, colFinalWay);
            String remark = strCell(row, colRemark);
            if ((finalTime == null || finalWay == null || finalAmt == null) && StringUtils.isNotBlank(remark)) {
                ParsedRemark prm = parseRemark(remark);
                if (finalTime == null && prm.month != null) {
                    Calendar cal = Calendar.getInstance();
                    cal.clear();
                    cal.set(prm.year != null ? prm.year : 2024, prm.month - 1, 1);
                    finalTime = cal.getTime();
                }
                if (finalAmt == null && prm.amount != null) finalAmt = prm.amount;
                if (finalWay == null && prm.method != null) finalWay = prm.method;
            }
            if (finalAmt != null && finalAmt.signum() > 0) {
                ImportPreviewPayment p = new ImportPreviewPayment();
                p.setPaymentType("尾款");
                p.setAmount(finalAmt);
                p.setPayTime(finalTime);
                p.setPayMethod(finalWay);
                p.setSource("sheet2尾款" + (finalTime == null && StringUtils.isNotBlank(remark) ? "(备注解析)" : ""));
                pr.getPayments().add(p);
            }
        }
    }

    static class ParsedRemark { Integer month, year; BigDecimal amount; String method; }
    private ParsedRemark parseRemark(String rmk) {
        ParsedRemark prm = new ParsedRemark();
        List<String> nums = new ArrayList<>();
        Matcher m = Pattern.compile("(\\d+)(?:\\.(\\d+))?").matcher(rmk);
        while (m.find()) nums.add(m.group());
        for (String n : nums) {
            boolean isFloat = n.contains(".");
            if (isFloat) {
                prm.amount = new BigDecimal(n).multiply(new BigDecimal("10000"));
            } else {
                int iv = Integer.parseInt(n);
                if (iv >= 1 && iv <= 12 && prm.month == null) prm.month = iv;
                else if (iv >= 1900 && iv <= 2100) prm.year = iv;
            }
        }
        if (rmk.contains("转账")) prm.method = "转账";
        else if (rmk.contains("现金")) prm.method = "现金";
        else if (rmk.contains("电汇")) prm.method = "电汇";
        return prm;
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
            for (ImportPreviewRow row : rows) {
                // 已存在 / 错误判定（不涉及DB写入，不在新事务里也行）
                if (row.isDuplicate()) {
                    counter[1]++;
                    ImportCommitResult.RowDetail d = new ImportCommitResult.RowDetail();
                    d.setExcelRow(row.getExcelRow()); d.setProjectCode(row.getProjectCode());
                    d.setReason("工程编号已存在");
                    result.getSkippedDetails().add(d);
                    continue;
                }
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
                // 2. 单行写入：REQUIRES_NEW，任何DB错误只回滚该行
                try {
                    Throwable[] err = {null};
                    runInNewTx(() -> {
                        try {
                            writeOneRow(row, user);
                        } catch (Throwable t) { err[0] = t; throw t; }
                    });
                    if (err[0] != null) throw new RuntimeException(err[0].getMessage(), err[0]);
                    counter[0]++;
                } catch (Exception ex) {
                    counter[2]++;
                    ImportCommitResult.RowDetail d = new ImportCommitResult.RowDetail();
                    d.setExcelRow(row.getExcelRow()); d.setProjectCode(row.getProjectCode());
                    String msg = ex.getCause() != null && ex.getCause().getMessage() != null
                        ? ex.getCause().getMessage() : ex.getMessage();
                    // 去掉可能超长的 PSQLException 堆栈前缀(只留第一行)
                    if (msg != null && msg.contains("\n")) msg = msg.split("\n")[0];
                    d.setReason(msg);
                    result.getFailedDetails().add(d);
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

    /** 单行写入（必须运行在独立事务中，任何异常都会只回滚本行） */
    private void writeOneRow(ImportPreviewRow row, String user) {
        if (row.getProjectCode() == null) throw new RuntimeException("工程编号为空");
        ProjProject dup = projectMapper.checkProjectCodeUnique(new ProjProject() {{ setProjectCode(row.getProjectCode()); }});
        if (dup != null) throw new RuntimeException("工程编号已存在");
        if (row.getProjectCategoryId() == null) throw new RuntimeException("项目类别未选择");
        for (ImportPreviewWorkload w : row.getWorkloads()) {
            if (w.getBillingId() == null) {
                String disp = w.getBillingCategoryRaw() == null ? "" : w.getBillingCategoryRaw()
                    .replaceAll("[（(](内部|外部)[）)]", "").trim();
                throw new RuntimeException("工作项未匹配计费类别：" + disp);
            }
        }
        ProjProject pj = new ProjProject();
        pj.setProjectCode(row.getProjectCode());
        pj.setProjectName(StringUtils.isNotBlank(row.getEngineeringProject()) ? row.getEngineeringProject() : row.getProjectCode());
        pj.setEngineeringProject(row.getEngineeringProject());
        pj.setProjectCategoryId(row.getProjectCategoryId());
        pj.setClientUnit(row.getClientUnit());
        pj.setProjectLocation(row.getProjectLocation());
        pj.setStatus("closed");
        pj.setCloseTime(row.getFinishDate());
        pj.setAssignDate(row.getFinishDate());
        pj.setCreateBy(user);
        projectMapper.insertProject(pj);
        if (row.getLeaderId() != null) {
            leaderMapper.insertProjectLeaders(pj.getId(), new Long[]{row.getLeaderId()}, user);
        }
        ProjTask task = new ProjTask();
        task.setProjectId(pj.getId());
        task.setUserId(row.getLeaderId());
        task.setTaskName(StringUtils.isNotBlank(row.getEngineeringProject()) ? row.getEngineeringProject() : pj.getProjectName());
        task.setStatus("finished");
        task.setActualFinishDate(row.getFinishDate());
        task.setRequiredFinishDate(row.getFinishDate());
        task.setAssignDate(row.getFinishDate());
        task.setCreateBy(user);
        taskMapper.insertTask(task);
        // 按组合键(categoryId, billingType, billingCategory)聚合，避免uk_workload_billing唯一键冲突
        // 原因：Excel中3个工作量组(定验线/管线图/其它)可能有同名二级表头，匹配后组合键完全相同
        Map<String, ImportPreviewWorkload> mergedWl = new LinkedHashMap<>();
        for (ImportPreviewWorkload w : row.getWorkloads()) {
            if (w.getBillingId() == null || w.getCategoryId() == null) continue;
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
            workloadMapper.insertWorkload(wl);
        }
        for (ImportPreviewPayment pm : row.getPayments()) {
            if (pm.getAmount() == null || pm.getAmount().signum() == 0) continue;
            ProjPayment pp = new ProjPayment();
            pp.setProjectId(pj.getId());
            pp.setPaymentType(pm.getPaymentType());
            pp.setAmount(pm.getAmount());
            pp.setPayTime(pm.getPayTime());
            pp.setPayUnit(pm.getPayUnit());
            pp.setPayMethod(pm.getPayMethod());
            pp.setReceivedStatus("received");
            pp.setInvoiceStatus("pending");
            pp.setRemark(pm.getSource());
            pp.setCreateBy(user);
            paymentMapper.insertPayment(pp);
        }
        if (row.getMaterialSubmitTime() != null) {
            ProjMaterial mat = new ProjMaterial();
            mat.setProjectId(pj.getId());
            mat.setSubmitTime(row.getMaterialSubmitTime());
            mat.setStatus("已领取");
            mat.setSubmitStatus("submitted");
            mat.setGuarantorFlag("N");
            mat.setArchiveFlag("N");
            mat.setCreateBy(user);
            materialMapper.insertMaterial(mat);
            ProjMaterialFlow flow = new ProjMaterialFlow();
            flow.setMaterialId(mat.getId());
            flow.setFlowType("领取");
            flow.setOperateTime(row.getMaterialSubmitTime());
            flow.setCreateBy(user);
            materialFlowMapper.insertFlow(flow);
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
    private String strCell(List<Object> row, int c) {
        if (row == null || c < 0 || c >= row.size()) return null;
        Object v = row.get(c); if (v == null) return null;
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
            if (s.matches("\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}.*")) {
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

    static class FuzzyResult<T> { T item; double score; }
    static class FuzzyResult2 { Long userId; String nickName; double score; }
    static class FuzzyBilling { Long billingId; Long categoryId; String billingCategory; String priceUnit; BigDecimal minQuantity; double score; }

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
    private FuzzyResult2 fuzzyMatchUser(String query, List<ImportPreviewResponse.UserOption> opts) {
        if (opts == null || opts.isEmpty()) return null;
        FuzzyResult2 best = null;
        for (ImportPreviewResponse.UserOption o : opts) {
            double s1 = similarity(query, o.getNickName() == null ? "" : o.getNickName());
            double s2 = similarity(query, o.getUserName() == null ? "" : o.getUserName());
            double s = Math.max(s1, s2);
            if (best == null || s > best.score) {
                FuzzyResult2 r = new FuzzyResult2(); r.userId = o.getUserId(); r.nickName = o.getNickName(); r.score = s; best = r;
            }
        }
        return best;
    }
    private FuzzyBilling fuzzyMatchBilling(String rawHeader, String billingType,
                                           List<ImportPreviewResponse.BillingOption> opts, Long projectCategoryId) {
        if (opts == null || opts.isEmpty()) return null;
        String clean = rawHeader.replaceAll("[（(](内部|外部)[）)]", "").trim();
        if (clean.endsWith("工作量")) clean = clean.substring(0, clean.length() - 3).trim();
        // Normalize: 数据库billingType可能存中文("内部"/"外部")或英文("internal"/"external")
        boolean wantInternal = "internal".equals(billingType);
        // Round-by-round priority:
        // 1. same projectCategoryId + billingType match
        // 2. same projectCategoryId + billingType relaxed
        // 3. billingType relaxed + all categories
        FuzzyBilling best = findBestBilling(clean, opts, wantInternal, projectCategoryId, true, true);
        if (best != null && best.score >= 1.0) return best;
        FuzzyBilling r2 = findBestBilling(clean, opts, wantInternal, projectCategoryId, true, false);
        if (r2 != null && r2.score > (best == null ? 0 : best.score) + 0.001) best = r2;
        if (best != null && best.score >= 1.0) return best;
        FuzzyBilling r3 = findBestBilling(clean, opts, wantInternal, null, false, false);
        if (r3 != null && r3.score > (best == null ? 0 : best.score) + 0.001) best = r3;
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
            String dbCat = o.getBillingCategory() == null ? "" : o.getBillingCategory().trim();
            double s = similarity(clean, dbCat);
            if (best == null || s > best.score) {
                FuzzyBilling b = new FuzzyBilling();
                b.billingId = o.getBillingId(); b.categoryId = o.getCategoryId();
                b.billingCategory = dbCat;
                b.priceUnit = o.getPriceUnit(); b.minQuantity = o.getMinQuantity();
                b.score = s; best = b;
            }
        }
        return best;
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
