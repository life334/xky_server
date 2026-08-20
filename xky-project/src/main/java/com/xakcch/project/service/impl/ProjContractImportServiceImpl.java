package com.xakcch.project.service.impl;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import com.xakcch.common.utils.SecurityUtils;
import com.xakcch.project.domain.*;
import com.xakcch.project.domain.vo.*;
import com.xakcch.project.mapper.*;
import com.xakcch.project.service.IProjContractImportService;

/**
 * 合同数据导入 服务实现
 *
 * @author liuyonghui
 */
@Service
public class ProjContractImportServiceImpl implements IProjContractImportService
{
    // ====================== 会话缓存（参考 ProjImportServiceImpl） ======================
    static class SessionEntry {
        final ContractImportPreviewResponse resp;
        /** 计费方式列表（commit 时反查 categoryId 用） */
        final List<ProjCategoryBilling> billingRecords;
        final long createAt;
        final AtomicBoolean committing = new AtomicBoolean(false);
        volatile ImportCommitResult commitResult;
        SessionEntry(ContractImportPreviewResponse r, List<ProjCategoryBilling> b) {
            this.resp = r; this.billingRecords = b; this.createAt = System.currentTimeMillis();
        }
        boolean expired() { return System.currentTimeMillis() - createAt > 2 * 3600 * 1000L; }
    }
    private static final Map<String, SessionEntry> SESSION = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService CLEANER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "proj-contract-import-session-cleaner"); t.setDaemon(true); return t;
    });
    static {
        CLEANER.scheduleAtFixedRate(() -> {
            try { SESSION.entrySet().removeIf(e -> e.getValue().expired()); }
            catch (Throwable ignore) { /* ignore */ }
        }, 10, 10, TimeUnit.MINUTES);
    }

    // ====================== 依赖注入 ======================
    @Autowired private ProjContractMapper contractMapper;
    @Autowired private ProjContractPriceMapper contractPriceMapper;
    @Autowired private ProjCategoryBillingMapper billingMapper;
    @Autowired private ProjImportLogMapper importLogMapper;
    @Autowired private PlatformTransactionManager txManager;

    private transient TransactionTemplate _txRequiresNew;
    private TransactionTemplate txRequiresNew() {
        if (_txRequiresNew == null) {
            DefaultTransactionDefinition def = new DefaultTransactionDefinition();
            def.setName("proj-contract-import-row");
            def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            _txRequiresNew = new TransactionTemplate(txManager, def);
        }
        return _txRequiresNew;
    }

    private ObjectMapper om = new ObjectMapper();

    // 单价行解析正则：类别+数字+/+单位  例 "导线2976/km"
    private static final Pattern PRICE_LINE_PATTERN = Pattern.compile("^(.+?)(\\d+(?:\\.\\d+)?)/(.+)$");
    // 合同金额中提取数字
    private static final Pattern AMOUNT_NUMBER_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)");
    // 签订日期  例 "2024.9.29" / "2024-12-04"
    private static final Pattern SIGN_DATE_PATTERN = Pattern.compile("(\\d{4})[./-](\\d{1,2})[./-](\\d{1,2})");

    // ====================== 预览 ======================
    @Override
    public ContractImportPreviewResponse previewContract(MultipartFile file) throws Exception {
        // 1. 加载外部计费方式
        ProjCategoryBilling bq = new ProjCategoryBilling();
        bq.setBillingType("external");
        bq.setStatus("0");
        List<ProjCategoryBilling> billings = billingMapper.selectBillingList(bq);

        // 2. 读取 Excel（所有 sheet 页）
        List<List<List<Object>>> allSheets = readAllSheets(file);

        // 3. 逐 sheet 解析
        ContractImportPreviewResponse fullResp = new ContractImportPreviewResponse();
        for (List<List<Object>> sheetRows : allSheets) {
            parseSheet(sheetRows, billings, fullResp.getRows());
        }

        // 4. 合同编号重复检查
        for (ContractImportPreviewResponse.ContractImportRow row : fullResp.getRows()) {
            if (StringUtils.isBlank(row.getContractNo())) continue;
            ProjContract dup = contractMapper.checkContractNoUnique(
                new ProjContract() {{ setContractNo(row.getContractNo()); }});
            if (dup != null) {
                row.setDuplicate(true);
                row.getErrors().add("合同编号已存在");
            }
        }

        // 5. 分类：ready / problem
        List<ContractImportPreviewResponse.ContractImportRow> readyRows = new ArrayList<>();
        List<ContractImportPreviewResponse.ContractImportRow> problemRows = new ArrayList<>();
        for (ContractImportPreviewResponse.ContractImportRow r : fullResp.getRows()) {
            if (r.isDuplicate()) { problemRows.add(r); continue; }
            if (r.getErrors() != null && !r.getErrors().isEmpty()) { problemRows.add(r); continue; }
            if (StringUtils.isBlank(r.getContractNo())) {
                r.getErrors().add("合同编号为空");
                problemRows.add(r); continue;
            }
            readyRows.add(r);
        }

        // 6. 统计
        fullResp.setTotalRows(fullResp.getRows().size());
        int dupCnt = 0, errCnt = 0;
        for (ContractImportPreviewResponse.ContractImportRow r : problemRows) {
            if (r.isDuplicate()) dupCnt++;
            else if (r.getErrors() != null && !r.getErrors().isEmpty()) errCnt++;
        }
        fullResp.setDuplicateCount(dupCnt);
        fullResp.setErrorCount(errCnt);
        fullResp.setReadyCount(readyRows.size());

        // 7. 问题摘要
        ContractImportPreviewResponse.ProblemSummary ps = new ContractImportPreviewResponse.ProblemSummary();
        if (dupCnt > 0) ps.setDuplicateDesc(dupCnt + " 行的合同编号已存在于系统中，重复导入将自动跳过");
        if (errCnt > 0) ps.setErrorDesc(errCnt + " 行缺少关键字段，无法导入");
        fullResp.setProblemSummary(ps);

        // 8. 生成 token，缓存全量数据
        String token = UUID.randomUUID().toString().replace("-", "");
        fullResp.setToken(token);
        SESSION.put(token, new SessionEntry(fullResp, billings));

        // 9. 构建轻量响应（仅含可导入行 + 问题行）
        ContractImportPreviewResponse lightResp = new ContractImportPreviewResponse();
        lightResp.setToken(token);
        lightResp.setTotalRows(fullResp.getTotalRows());
        lightResp.setReadyCount(fullResp.getReadyCount());
        lightResp.setDuplicateCount(dupCnt);
        lightResp.setErrorCount(errCnt);
        lightResp.setProblemSummary(ps);
        lightResp.getRows().addAll(readyRows);
        lightResp.getProblemRows().addAll(problemRows);
        return lightResp;
    }

    // ====================== 提交导入 ======================
    @Override
    public ImportCommitResult commitContract(ImportCommitRequest req) {
        SessionEntry entry = SESSION.get(req.getToken());
        if (entry == null || entry.expired()) throw new RuntimeException("导入会话已过期，请重新解析");
        // 重复提交保护
        if (entry.commitResult != null) return entry.commitResult;
        if (!entry.committing.compareAndSet(false, true)) {
            long start = System.currentTimeMillis();
            while (entry.commitResult == null && entry.committing.get() && System.currentTimeMillis() - start < 90 * 1000L) {
                try { Thread.sleep(500L); } catch (InterruptedException ignore) { Thread.currentThread().interrupt(); break; }
            }
            if (entry.commitResult != null) return entry.commitResult;
            throw new RuntimeException("正在导入中，请稍后到导入日志查看结果");
        }
        ContractImportPreviewResponse cached = entry.resp;
        List<ProjCategoryBilling> billings = entry.billingRecords;
        // 合同导入不使用前端回传的 rows（ImportCommitRequest.rows 类型为 ImportPreviewRow），
        // 始终使用预览缓存中的 ContractImportRow
        List<ContractImportPreviewResponse.ContractImportRow> rows = cached.getRows();
        ImportCommitResult result = new ImportCommitResult();
        long t0 = System.currentTimeMillis();
        final int[] counter = {0, 0, 0}; // succ, skip, fail
        final String user = SecurityUtils.getUsername();
        final ProjImportLog log = new ProjImportLog();
        log.setFileName("contract_upload.xls");
        log.setTotalRows(rows.size());
        log.setStatus("running");
        log.setCreateBy(user);
        // 1. log insert 独立事务
        runInNewTx(() -> importLogMapper.insertImportLog(log));
        try {
            for (ContractImportPreviewResponse.ContractImportRow row : rows) {
                if (row.isDuplicate()) {
                    counter[1]++;
                    ImportCommitResult.RowDetail d = new ImportCommitResult.RowDetail();
                    d.setExcelRow(row.getExcelRow());
                    d.setProjectCode(row.getContractNo());
                    d.setReason("合同编号已存在");
                    result.getSkippedDetails().add(d);
                    continue;
                }
                if (row.getErrors() != null && !row.getErrors().isEmpty()) {
                    counter[2]++;
                    ImportCommitResult.RowDetail d = new ImportCommitResult.RowDetail();
                    d.setExcelRow(row.getExcelRow());
                    d.setProjectCode(row.getContractNo());
                    d.setReason(String.join("；", row.getErrors()));
                    result.getFailedDetails().add(d);
                    continue;
                }
                if (StringUtils.isBlank(row.getContractNo())) {
                    counter[2]++;
                    ImportCommitResult.RowDetail d = new ImportCommitResult.RowDetail();
                    d.setExcelRow(row.getExcelRow());
                    d.setProjectCode(row.getContractNo());
                    d.setReason("合同编号为空");
                    result.getFailedDetails().add(d);
                    continue;
                }
                // 2. 单行写入：REQUIRES_NEW
                try {
                    Throwable[] err = {null};
                    runInNewTx(() -> {
                        try {
                            writeOneRow(row, billings, user);
                        } catch (Throwable t) { err[0] = t; throw t; }
                    });
                    if (err[0] != null) throw new RuntimeException(err[0].getMessage(), err[0]);
                    counter[0]++;
                } catch (Exception ex) {
                    counter[2]++;
                    ImportCommitResult.RowDetail d = new ImportCommitResult.RowDetail();
                    d.setExcelRow(row.getExcelRow());
                    d.setProjectCode(row.getContractNo());
                    String msg = ex.getCause() != null && ex.getCause().getMessage() != null
                        ? ex.getCause().getMessage() : ex.getMessage();
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
            try {
                runInNewTx(() -> importLogMapper.updateImportLog(log));
            } catch (Exception ignoreLog) {
                System.err.println("[proj-contract-import] log update FAILED id=" + log.getId() + " -> " + ignoreLog.getMessage());
            }
            // 缓存结果
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
        if (result.getFailedDetails() == null) result.setFailedDetails(new ArrayList<>());
        if (result.getSkippedDetails() == null) result.setSkippedDetails(new ArrayList<>());
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

    /** 单行写入（必须运行在独立事务中） */
    private void writeOneRow(ContractImportPreviewResponse.ContractImportRow row,
                              List<ProjCategoryBilling> billings, String user) {
        if (StringUtils.isBlank(row.getContractNo())) throw new RuntimeException("合同编号为空");
        // 再次校验唯一性（防止 preview 到 commit 期间被新增）
        ProjContract dup = contractMapper.checkContractNoUnique(
            new ProjContract() {{ setContractNo(row.getContractNo()); }});
        if (dup != null) throw new RuntimeException("合同编号已存在");

        ProjContract c = new ProjContract();
        c.setContractNo(row.getContractNo());
        c.setContractName(StringUtils.isNotBlank(row.getContractName()) ? row.getContractName() : row.getContractNo());
        c.setClientUnit(row.getClientUnit());
        c.setContractType(row.getContractType());
        c.setContractAmount(row.getContractAmount());
        c.setSignDate(row.getSignDate());
        c.setStatus("signed");
        c.setIsSettled("0");
        c.setRemark(row.getRemark());
        c.setCreateBy(user);
        contractMapper.insertContract(c);

        // 写入单价明细（仅有 matchedBillingId 的才写入）
        if (row.getPriceItems() != null && billings != null) {
            Map<Long, ProjCategoryBilling> billingMap = new HashMap<>();
            for (ProjCategoryBilling b : billings) billingMap.put(b.getId(), b);
            for (ContractImportPreviewResponse.ContractPriceItem item : row.getPriceItems()) {
                if (item.getMatchedBillingId() == null) continue;
                ProjCategoryBilling b = billingMap.get(item.getMatchedBillingId());
                if (b == null || b.getCategoryId() == null) continue;
                ProjContractPrice p = new ProjContractPrice();
                p.setContractId(c.getId());
                p.setCategoryId(b.getCategoryId());
                p.setBillingId(item.getMatchedBillingId());
                p.setPrice(item.getUnitPrice());
                p.setCreateBy(user);
                contractPriceMapper.insertPrice(p);
            }
        }
    }

    // ====================== 获取问题行明细（下载Excel用） ======================
    @Override
    public List<ProblemRowDetail> getContractProblems(String token, String type) {
        SessionEntry entry = SESSION.get(token);
        if (entry == null || entry.expired()) throw new RuntimeException("会话已过期，请重新解析");
        ContractImportPreviewResponse cached = entry.resp;
        List<ProblemRowDetail> all = new ArrayList<>();
        for (ContractImportPreviewResponse.ContractImportRow r : cached.getRows()) {
            boolean isTarget = false;
            if ("duplicate".equals(type) && r.isDuplicate()) isTarget = true;
            else if ("error".equals(type) && !r.isDuplicate()
                && r.getErrors() != null && !r.getErrors().isEmpty()) isTarget = true;
            if (!isTarget) continue;
            all.add(buildProblemDetail(r));
        }
        return all;
    }

    private ProblemRowDetail buildProblemDetail(ContractImportPreviewResponse.ContractImportRow r) {
        List<String> problems = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        String probType;
        if (r.isDuplicate()) {
            probType = "已存在";
            problems.add("合同编号已存在于系统");
            suggestions.add("如需更新请在系统中修改");
        } else if (r.getErrors() != null && !r.getErrors().isEmpty()) {
            probType = "无法导入";
            problems.addAll(r.getErrors());
            suggestions.add("请补全Excel中缺失的关键字段");
        } else {
            probType = "待修正";
            for (ContractImportPreviewResponse.ContractPriceItem item : r.getPriceItems()) {
                if (item.getMatchedBillingId() == null) {
                    problems.add("计费类别「" + item.getBillingCategory() + "」无法匹配");
                    suggestions.add("请在系统中确认计费类别配置");
                }
            }
            if (problems.isEmpty()) {
                problems.add("数据待确认");
                suggestions.add("请检查数据后重新上传");
            }
        }
        ProblemRowDetail d = new ProblemRowDetail();
        d.setExcelRow(r.getExcelRow());
        d.setProjectCode(r.getContractNo());
        d.setClientUnit(r.getClientUnit());
        d.setEngineeringProject(r.getContractName());
        d.setProblemType(probType);
        d.setProblemDetail(String.join("；", problems));
        d.setSuggestion(String.join("；", suggestions.isEmpty() ? Collections.singletonList("-") : suggestions));
        return d;
    }

    // ====================== 读取 Excel（所有 sheet） ======================
    private List<List<List<Object>>> readAllSheets(MultipartFile file) throws Exception {
        List<List<List<Object>>> result = new ArrayList<>();
        try (InputStream is = file.getInputStream();
             HSSFWorkbook wb = new HSSFWorkbook(is)) {
            DataFormatter fmt = new DataFormatter();
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet sheet = wb.getSheetAt(s);
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
                        } else if (cell.getCellType() == CellType.BOOLEAN) {
                            v = cell.getBooleanCellValue();
                        } else if (cell.getCellType() == CellType.FORMULA) {
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
                result.add(rows);
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

    // ====================== 解析单个 sheet ======================
    private void parseSheet(List<List<Object>> rows, List<ProjCategoryBilling> billings,
                             List<ContractImportPreviewResponse.ContractImportRow> outRows) {
        if (rows == null || rows.isEmpty()) return;
        // 找表头行（含"合同编号"）
        int headerRowIdx = -1;
        for (int i = 0; i < Math.min(rows.size(), 10); i++) {
            List<Object> r = rows.get(i);
            if (r == null) continue;
            for (Object o : r) {
                if (o != null && o.toString().contains("合同编号")) { headerRowIdx = i; break; }
            }
            if (headerRowIdx >= 0) break;
        }
        if (headerRowIdx < 0) return;
        List<Object> header = rows.get(headerRowIdx);
        Map<String, Integer> colMap = new HashMap<>();
        for (int c = 0; c < header.size(); c++) {
            Object v = header.get(c);
            if (v == null) continue;
            String s = v.toString().trim();
            if (s.isEmpty()) continue;
            if (!colMap.containsKey(s)) colMap.put(s, c);
        }
        int colContractNo    = firstMatch(colMap, "合同编号");
        int colClientUnit    = firstMatch(colMap, "单位名称|委托单位");
        int colContractName  = firstMatch(colMap, "项目名称|合同名称");
        int colSignDate      = firstMatch(colMap, "签订时间|签订日期|签署时间|签署日期");
        int colContractAmt   = firstMatch(colMap, "合同金额|合同总价");
        int colPrice         = firstMatch(colMap, "单价");
        int colRemark        = firstMatch(colMap, "备注");

        for (int r = headerRowIdx + 1; r < rows.size(); r++) {
            List<Object> row = rows.get(r);
            if (row == null || row.isEmpty()) continue;
            String contractNo = strCell(row, colContractNo);
            // 空行跳过
            if (StringUtils.isBlank(contractNo)
                && StringUtils.isBlank(strCell(row, colContractName))
                && StringUtils.isBlank(strCell(row, colClientUnit))) continue;

            ContractImportPreviewResponse.ContractImportRow cr = new ContractImportPreviewResponse.ContractImportRow();
            cr.setExcelRow(r + 1);
            cr.setContractNo(StringUtils.isNotBlank(contractNo) ? contractNo.trim() : null);
            cr.setClientUnit(strCell(row, colClientUnit));
            cr.setContractName(strCell(row, colContractName));
            cr.setSignDate(parseSignDate(getCell(row, colSignDate)));
            cr.setContractAmount(parseContractAmount(getCell(row, colContractAmt)));
            cr.setRemark(strCell(row, colRemark));

            // 解析单价列
            List<ContractImportPreviewResponse.ContractPriceItem> priceItems =
                parsePriceItems(getCell(row, colPrice), billings);
            cr.getPriceItems().addAll(priceItems);

            // 合同类型：有单价行 → 单价合同，无 → 总价合同
            cr.setContractType(priceItems.isEmpty() ? "总价合同" : "单价合同");

            outRows.add(cr);
        }
    }

    // ====================== 解析单价列 ======================
    private List<ContractImportPreviewResponse.ContractPriceItem> parsePriceItems(
            Object v, List<ProjCategoryBilling> billings) {
        List<ContractImportPreviewResponse.ContractPriceItem> items = new ArrayList<>();
        if (v == null) return items;
        String s = v.toString().trim();
        if (s.isEmpty()) return items;
        // 按换行分割（\n 或 \r\n）
        String[] lines = s.split("\\r?\\n");
        for (String line : lines) {
            if (line == null) continue;
            line = line.trim();
            if (line.isEmpty()) continue;
            Matcher m = PRICE_LINE_PATTERN.matcher(line);
            if (!m.matches()) continue;
            ContractImportPreviewResponse.ContractPriceItem item = new ContractImportPreviewResponse.ContractPriceItem();
            item.setBillingCategory(m.group(1).trim());
            try {
                item.setUnitPrice(new BigDecimal(m.group(2)));
            } catch (Exception e) {
                continue;
            }
            item.setPriceUnit(m.group(3).trim());
            // 模糊匹配计费类别
            matchBillingCategory(item, billings);
            items.add(item);
        }
        return items;
    }

    // ====================== 计费类别模糊匹配（Jaccard 相似度） ======================
    private void matchBillingCategory(ContractImportPreviewResponse.ContractPriceItem item,
                                        List<ProjCategoryBilling> billings) {
        if (billings == null || billings.isEmpty()) {
            item.setWarning("系统中无外部计费类别配置");
            return;
        }
        String query = item.getBillingCategory();
        if (query == null || query.isEmpty()) {
            item.setWarning("计费类别为空");
            return;
        }
        ProjCategoryBilling best = null;
        double bestScore = 0;
        for (ProjCategoryBilling b : billings) {
            String dbCat = b.getBillingCategory() == null ? "" : b.getBillingCategory().trim();
            double sc = similarity(query, dbCat);
            if (best == null || sc > bestScore) {
                best = b; bestScore = sc;
            }
        }
        if (best != null && bestScore >= 0.5) {
            item.setMatchedBillingId(best.getId());
            item.setMatchedBillingCategory(best.getBillingCategory());
            if (bestScore < 1.0) {
                item.setWarning("计费类别「" + query + "」匹配度" + String.format("%.2f", bestScore)
                    + "，匹配到「" + best.getBillingCategory() + "」");
            }
        } else if (best != null) {
            item.setWarning("计费类别「" + query + "」匹配度" + String.format("%.2f", bestScore)
                + "，未达到阈值，请手动选择");
        } else {
            item.setWarning("未匹配到计费类别「" + query + "」");
        }
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

    // ====================== 合同金额解析 ======================
    private BigDecimal parseContractAmount(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return new BigDecimal(v.toString());
        String s = v.toString().trim();
        if (s.isEmpty()) return null;
        s = s.replace(",", "");
        Matcher m = AMOUNT_NUMBER_PATTERN.matcher(s);
        if (m.find()) {
            try { return new BigDecimal(m.group(1)); } catch (Exception e) { return null; }
        }
        // 非数字（如"单价合同"）则为 null
        return null;
    }

    // ====================== 签订日期解析 ======================
    private Date parseSignDate(Object v) {
        if (v == null) return null;
        if (v instanceof Date) return (Date) v;
        String s = v.toString().trim();
        if (s.isEmpty()) return null;
        Matcher m = SIGN_DATE_PATTERN.matcher(s);
        if (m.find()) {
            try {
                int y = Integer.parseInt(m.group(1));
                int mo = Integer.parseInt(m.group(2));
                int d = Integer.parseInt(m.group(3));
                Calendar cal = Calendar.getInstance();
                cal.clear();
                cal.set(y, mo - 1, d);
                return cal.getTime();
            } catch (Exception e) { return null; }
        }
        return null;
    }

    // ====================== 工具函数 ======================
    private int firstMatch(Map<String, Integer> map, String regex) {
        Pattern p = Pattern.compile(regex);
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            if (p.matcher(e.getKey()).find()) return e.getValue();
        }
        return -1;
    }
    private String strCell(List<Object> row, int c) {
        if (row == null || c < 0 || c >= row.size()) return null;
        Object v = row.get(c);
        if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }
    private Object getCell(List<Object> row, int c) {
        if (row == null || c < 0 || c >= row.size()) return null;
        return row.get(c);
    }
}
