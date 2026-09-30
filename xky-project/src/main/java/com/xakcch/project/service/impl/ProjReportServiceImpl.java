package com.xakcch.project.service.impl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.xakcch.common.config.LandPatchConfig;
import com.xakcch.common.exception.ServiceException;
import com.xakcch.common.utils.SecurityUtils;
import com.xakcch.project.domain.ProjFieldDef;
import com.xakcch.project.domain.ProjReportField;
import com.xakcch.project.domain.ProjReportFilter;
import com.xakcch.project.domain.ProjReportLog;
import com.xakcch.project.domain.ProjReportSubmitBatch;
import com.xakcch.project.domain.ProjReportSubmitLog;
import com.xakcch.project.domain.ProjReportTemplate;
import com.xakcch.project.mapper.ProjFieldDefMapper;
import com.xakcch.project.mapper.ProjReportFieldMapper;
import com.xakcch.project.mapper.ProjReportFilterMapper;
import com.xakcch.project.mapper.ProjReportLogMapper;
import com.xakcch.project.mapper.ProjReportSubmitMapper;
import com.xakcch.project.mapper.ProjReportTemplateMapper;
import com.xakcch.project.mapper.ReportDataMapper;
import com.xakcch.project.report.ReportExcelExporter;
import com.xakcch.project.report.ReportFieldPool;
import com.xakcch.project.service.IProjReportService;

/**
 * 报表导出 业务层实现
 *
 * @author liuyonghui
 */
@Service
public class ProjReportServiceImpl implements IProjReportService
{
    @Autowired
    private ProjReportTemplateMapper templateMapper;

    @Autowired
    private ProjReportFieldMapper fieldMapper;

    @Autowired
    private ProjReportFilterMapper filterMapper;

    @Autowired
    private ProjReportLogMapper logMapper;

    @Autowired
    private ReportDataMapper reportDataMapper;

    @Autowired
    private ProjFieldDefMapper fieldDefMapper;

    @Autowired
    private ProjReportSubmitMapper submitMapper;

    /** 预览默认每页条数（生产数据量大：预览只取一页，避免全量取数） */
    private static final int DEFAULT_PREVIEW_PAGE_SIZE = 20;
    /** 预览每页上限（防止前端传入过大 pageSize 一次拉爆） */
    private static final int MAX_PREVIEW_PAGE_SIZE = 200;

    /**
     * 「到账金额/到账时间」按预付款、尾款拆行的模板文件关键字（模板4 scsr / 模板7 dyxk）：
     * 同一项目若预付款与尾款均有到账，则拆成两行（各自金额/时间对应），其余列跨行合并。
     */
    private static final List<String> PAY_SPLIT_TEMPLATE_KEYWORDS = List.of("scsr_report", "dyxk_report");

    // ==================== 模板 ====================

    @Override
    public List<ProjReportTemplate> listTemplates(ProjReportTemplate query)
    {
        return templateMapper.selectTemplateList(query);
    }

    @Override
    public ProjReportTemplate getTemplate(Long id)
    {
        ProjReportTemplate template = templateMapper.selectTemplateById(id);
        if (template == null)
        {
            throw new ServiceException("模板不存在");
        }
        List<ProjReportField> fields = fieldMapper.selectFieldsByTemplateId(id);
        sortFields(fields);
        template.setFieldList(fields);
        return template;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveTemplate(ProjReportTemplate template)
    {
        String username = SecurityUtils.getUsername();
        Long id = template.getId();
        if (id == null)
        {
            if (template.getTemplateType() == null)
            {
                template.setTemplateType("custom");
            }
            template.setCreateBy(username);
            templateMapper.insertTemplate(template);
            id = template.getId();
        }
        else
        {
            template.setUpdateBy(username);
            templateMapper.updateTemplate(template);
        }
        // 字段清单重存（先逻辑删，再插入）
        if (template.getFieldList() != null && !template.getFieldList().isEmpty())
        {
            fieldMapper.deleteFieldsByTemplateId(id);
            List<ProjReportField> fields = template.getFieldList();
            for (int i = 0; i < fields.size(); i++)
            {
                ProjReportField f = fields.get(i);
                if (f.getSortOrder() == null)
                {
                    f.setSortOrder(i + 1);
                }
                if (f.getWidth() == null)
                {
                    f.setWidth(14);
                }
            }
            fieldMapper.batchInsertFields(id, fields);
        }
        return id;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteTemplate(Long id)
    {
        fieldMapper.deleteFieldsByTemplateId(id);
        return templateMapper.deleteTemplateById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long copyTemplate(Long id, String newName)
    {
        ProjReportTemplate source = getTemplate(id);
        if (source == null)
        {
            throw new ServiceException("来源模板不存在");
        }
        ProjReportTemplate copy = new ProjReportTemplate();
        copy.setTemplateName(newName == null || newName.isEmpty()
                ? source.getTemplateName() + "（副本）" : newName);
        copy.setTemplateType("custom");
        copy.setSubjectTable(source.getSubjectTable());
        copy.setSourceTemplateId(source.getId());
        copy.setTitleRow(source.getTitleRow());
        copy.setHeaderRow(source.getHeaderRow());
        copy.setDataStartRow(source.getDataStartRow());
        copy.setHasSummaryRow(source.getHasSummaryRow());
        copy.setDefaultFilter(source.getDefaultFilter());
        copy.setRemark(source.getRemark());
        copy.setCreateBy(SecurityUtils.getUsername());
        templateMapper.insertTemplate(copy);
        Long newId = copy.getId();
        // 复制字段（自定义模板不保留 column_index）
        List<ProjReportField> fields = source.getFieldList();
        if (fields != null && !fields.isEmpty())
        {
            List<ProjReportField> newFields = new ArrayList<>();
            for (ProjReportField f : fields)
            {
                ProjReportField nf = new ProjReportField();
                nf.setFieldKey(f.getFieldKey());
                nf.setFieldLabel(f.getFieldLabel());
                nf.setFieldSource(f.getFieldSource());
                nf.setJoinTable(f.getJoinTable());
                nf.setSortOrder(f.getSortOrder());
                nf.setWidth(f.getWidth());
                nf.setHeaderGroup(f.getHeaderGroup());
                newFields.add(nf);
            }
            fieldMapper.batchInsertFields(newId, newFields);
        }
        return newId;
    }

    @Override
    public List<ProjReportField> listTemplateFields(Long templateId)
    {
        List<ProjReportField> fields = fieldMapper.selectFieldsByTemplateId(templateId);
        sortFields(fields);
        return fields;
    }

    @Override
    public int saveTemplateDefaultFilter(Long id, String defaultFilter)
    {
        ProjReportTemplate template = templateMapper.selectTemplateById(id);
        if (template == null)
        {
            throw new ServiceException("模板不存在");
        }
        ProjReportTemplate update = new ProjReportTemplate();
        update.setId(id);
        update.setDefaultFilter(defaultFilter);
        update.setUpdateBy(SecurityUtils.getUsername());
        return templateMapper.updateTemplate(update);
    }

    private void sortFields(List<ProjReportField> fields)
    {
        fields.sort((a, b) -> {
            int sa = a.getSortOrder() == null ? 0 : a.getSortOrder();
            int sb = b.getSortOrder() == null ? 0 : b.getSortOrder();
            if (sa != sb)
            {
                return Integer.compare(sa, sb);
            }
            int ca = a.getColumnIndex() == null ? 0 : a.getColumnIndex();
            int cb = b.getColumnIndex() == null ? 0 : b.getColumnIndex();
            return Integer.compare(ca, cb);
        });
    }

    // ==================== 筛选方案 ====================

    @Override
    public List<ProjReportFilter> listFilters(ProjReportFilter query)
    {
        return filterMapper.selectFilterList(query);
    }

    @Override
    public ProjReportFilter getFilter(Long id)
    {
        return filterMapper.selectFilterById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveFilter(ProjReportFilter filter)
    {
        String username = SecurityUtils.getUsername();
        if (filter.getId() == null)
        {
            filter.setCreateBy(username);
            filterMapper.insertFilter(filter);
            return filter.getId();
        }
        filter.setUpdateBy(username);
        filterMapper.updateFilter(filter);
        return filter.getId();
    }

    @Override
    public int deleteFilter(Long id)
    {
        ProjReportFilter exist = filterMapper.selectFilterById(id);
        if (exist == null)
        {
            throw new ServiceException("筛选方案不存在或已删除");
        }
        // 方案全局共享，仅创建者可删除，防止误删他人方案
        String username = SecurityUtils.getUsername();
        if (!username.equals(exist.getCreateBy()))
        {
            throw new ServiceException("只能删除自己创建的筛选方案");
        }
        return filterMapper.deleteFilterById(id);
    }

    @Override
    public int renameFilter(Long id, String filterName)
    {
        ProjReportFilter exist = filterMapper.selectFilterById(id);
        if (exist == null)
        {
            throw new ServiceException("筛选方案不存在或已删除");
        }
        // 方案全局共享，仅创建者可重命名，防止误改他人方案
        String username = SecurityUtils.getUsername();
        if (!username.equals(exist.getCreateBy()))
        {
            throw new ServiceException("只能重命名自己创建的筛选方案");
        }
        ProjReportFilter upd = new ProjReportFilter();
        upd.setId(id);
        upd.setFilterName(filterName);
        upd.setUpdateBy(username);
        return filterMapper.updateFilter(upd);
    }

    // ==================== 字段池 ====================

    @Override
    public List<Map<String, Object>> getFieldPool()
    {
        // 查询项目主体的动态字段
        ProjFieldDef query = new ProjFieldDef();
        query.setTableName(ReportFieldPool.SUBJECT_PROJECT);
        query.setStatus("0");
        List<ProjFieldDef> dyn = fieldDefMapper.selectFieldDefList(query);
        return ReportFieldPool.getFieldPool(dyn);
    }

    // ==================== 导出 ====================

    @Override
    public Map<String, Object> preview(Long templateId, Map<String, Object> filter, Integer pageNum, Integer pageSize)
    {
        ProjReportTemplate template = getTemplate(templateId);
        // 分页参数（默认每页 20 条；上限 200，防止一次拉爆）
        int size = (pageSize == null || pageSize <= 0) ? DEFAULT_PREVIEW_PAGE_SIZE
                : Math.min(pageSize, MAX_PREVIEW_PAGE_SIZE);
        int page = (pageNum == null || pageNum <= 0) ? 1 : pageNum;
        int offset = (page - 1) * size;

        Map<String, Object> qf = buildQueryFilter(filter);
        // 命中总数：独立计数查询（不再「全量取数后 size()」，这是预览慢的根因）
        Long totalObj = reportDataMapper.selectProjectRowCount(qf);
        int total = totalObj == null ? 0 : totalObj.intValue();

        // 只取当前页（SQL 侧 limit/offset）；rowNo 按 offset 续号，跨页连续
        List<Map<String, Object>> rows = reportDataMapper.selectProjectRows(qf, size, offset);
        for (int i = 0; i < rows.size(); i++)
        {
            rows.get(i).put("rowNo", offset + i + 1);
        }
        // 单位合并模板：合并开关开启时按单位排序（SQL 已按委托单位排序，分页保持同单位连续，无需再排）
        if (isUnitMergeTemplate(template) && isMergeUnitCells(filter))
        {
            // zdyw：到账时间列显示单位汇总描述（独立聚合查询，分页后仍为该单位全量口径，不失真）
            if (isPayTimeSummaryKeyword(template))
            {
                applyUnitPaySummary(rows, qf);
            }
        }
        // 到账金额/到账时间按预付款、尾款拆行（模板4/7，仅这两类；其它模板不受影响）
        if (isPaySplitTemplate(template))
        {
            rows = expandPaySplitRows(rows);
        }
        // 按模板字段顺序解析展示值（与导出一致，所见即所得）
        List<ProjReportField> fields = template.getFieldList();
        // 合并模式且为「只定未验」（zdyw）模板：到账时间列显示单位汇总描述（与导出 Excel 一致）
        boolean paySummary = isMergeUnitCells(filter) && isPayTimeSummaryKeyword(template);
        List<List<Object>> displayRows = new ArrayList<>();
        List<String> codes = new ArrayList<>();
        List<Integer> groupSizes = new ArrayList<>();
        for (Map<String, Object> row : rows)
        {
            List<Object> d = new ArrayList<>();
            if (fields != null)
            {
                for (ProjReportField fld : fields)
                {
                    Object v;
                    if (paySummary && "lastPayTime".equals(fld.getFieldKey()) && row.get("_unitSummary") != null)
                    {
                        v = row.get("_unitSummary");
                    }
                    else
                    {
                        v = ReportFieldPool.resolveValue(fld, row);
                    }
                    d.add(v);
                }
            }
            displayRows.add(d);
            String code = toStr(row.get("projectCode"));
            codes.add(code == null ? "" : code);
            Object gs = row.get("_grpSize");
            groupSizes.add(gs instanceof Number ? ((Number) gs).intValue() : 1);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("template", template);
        result.put("total", total);
        result.put("pageNum", page);
        result.put("pageSize", size);
        result.put("rows", displayRows);
        // 当前页工程编号（与 rows 顺序一致；拆行后同一项目的多行共享同一编号，供前端合并勾选）
        result.put("codes", codes);
        // 当前页每行的项目分组行数（>1 表示该行所属项目被拆成多行，前端据此合并「到账金额/时间」以外的列）
        result.put("groupSizes", groupSizes);
        // 已上报状态 { projectCode: submitTime }，前端标记已上报行
        Map<String, Object> submitted = new HashMap<>();
        List<String> queryCodes = new ArrayList<>();
        for (String code : codes)
        {
            if (code != null && !code.isEmpty())
            {
                queryCodes.add(code);
            }
        }
        // 上报状态来源 { projectCode: 'log' | 'history' }：
        //   log     = 真实上报（proj_report_submit_log 有记录且 batch_id 非空，即走过「导出并上报领导」）
        //   history = 历史导入补录（有记录但 batch_id 为空）或未落库的派生兜底（时间列有值）
        // 口径说明：只按「有无上报记录」判定会出现「上报时间有值、上报状态却显示未上报」的自相矛盾，
        //          故状态与「上报时间」列同源（有上报时间即已上报），同时保留来源供前端区分展示。
        Map<String, Object> submitSource = new HashMap<>();
        if (!queryCodes.isEmpty())
        {
            for (ProjReportSubmitLog sl : submitMapper.selectLogsByCodes(queryCodes))
            {
                submitted.put(sl.getProjectCode(), sl.getSubmitTime());
                submitSource.put(sl.getProjectCode(), sl.getBatchId() == null ? "history" : "log");
            }
        }
        result.put("submitted", submitted);
        for (Map<String, Object> row : rows)
        {
            String code = toStr(row.get("projectCode"));
            if (code == null || code.isEmpty() || submitSource.containsKey(code))
            {
                continue;
            }
            if (hasReportTimeValue(fields, row))
            {
                submitSource.put(code, "history");
            }
        }
        result.put("submitSource", submitSource);
        // 当月是否已上报过（按月上报控制：当月已上报则前端置灰上报复选框）
        result.put("monthSubmitted", countMonthSubmittedLogs() > 0);
        fields = enrichHeaderGroupFromSource(template, fields);
        result.put("headerTree", buildHeaderTree(fields));
        return result;
    }

    /**
     * 构建多级表头树（与 Excel 模板表头一致，供前端预览渲染嵌套 el-table-column）。
     *
     * <p>节点结构：</p>
     * <ul>
     *   <li>叶子：{ label, colIndex（展示行数组下标）, fieldKey, width, isGroup:false }</li>
     *   <li>分组：{ label, isGroup:true, children:[叶子...] }（同一 header_group 且位置连续的字段合并为一组）</li>
     * </ul>
     */
    private List<Map<String, Object>> buildHeaderTree(List<ProjReportField> fields)
    {
        List<Map<String, Object>> tree = new ArrayList<>();
        if (fields == null || fields.isEmpty())
        {
            return tree;
        }
        int i = 0;
        while (i < fields.size())
        {
            ProjReportField f = fields.get(i);
            String group = f.getHeaderGroup();
            if (group == null || group.trim().isEmpty())
            {
                // 一级表头：叶子节点
                Map<String, Object> leaf = new HashMap<>();
                leaf.put("label", f.getFieldLabel() == null ? f.getFieldKey() : f.getFieldLabel());
                leaf.put("colIndex", i);
                leaf.put("fieldKey", f.getFieldKey());
                leaf.put("width", f.getWidth());
                leaf.put("isGroup", false);
                tree.add(leaf);
                i++;
                continue;
            }
            // 二级表头：合并同一分组内位置连续的字段
            List<Map<String, Object>> children = new ArrayList<>();
            while (i < fields.size() && group.equals(fields.get(i).getHeaderGroup()))
            {
                ProjReportField cf = fields.get(i);
                Map<String, Object> leaf = new HashMap<>();
                leaf.put("label", cf.getFieldLabel() == null ? cf.getFieldKey() : cf.getFieldLabel());
                leaf.put("colIndex", i);
                leaf.put("fieldKey", cf.getFieldKey());
                leaf.put("width", cf.getWidth());
                leaf.put("isGroup", false);
                children.add(leaf);
                i++;
            }
            Map<String, Object> node = new HashMap<>();
            node.put("label", group);
            node.put("isGroup", true);
            node.put("children", children);
            tree.add(node);
        }
        return tree;
    }

    /**
     * 自定义模板字段缺失多级表头分组（headerGroup）时，按 fieldKey 从来源内置模板继承。
     * 用于"基于内置模板新增字段"场景下保留原有多级表头结构。
     */
    private List<ProjReportField> enrichHeaderGroupFromSource(ProjReportTemplate template, List<ProjReportField> fields)
    {
        if (fields == null || fields.isEmpty())
        {
            return fields;
        }
        boolean needEnrich = false;
        for (ProjReportField f : fields)
        {
            if (f.getHeaderGroup() == null || f.getHeaderGroup().trim().isEmpty())
            {
                needEnrich = true;
                break;
            }
        }
        if (!needEnrich)
        {
            return fields;
        }
        if (template == null || !"custom".equals(template.getTemplateType())
                || template.getSourceTemplateId() == null)
        {
            return fields;
        }
        ProjReportTemplate source = getTemplate(template.getSourceTemplateId());
        if (source == null || source.getFieldList() == null || source.getFieldList().isEmpty())
        {
            return fields;
        }
        Map<String, String> keyToGroup = new HashMap<>();
        for (ProjReportField sf : source.getFieldList())
        {
            if (sf.getHeaderGroup() != null && !sf.getHeaderGroup().trim().isEmpty())
            {
                keyToGroup.putIfAbsent(sf.getFieldKey(), sf.getHeaderGroup());
            }
        }
        if (keyToGroup.isEmpty())
        {
            return fields;
        }
        for (ProjReportField f : fields)
        {
            if ((f.getHeaderGroup() == null || f.getHeaderGroup().trim().isEmpty())
                    && keyToGroup.containsKey(f.getFieldKey()))
            {
                f.setHeaderGroup(keyToGroup.get(f.getFieldKey()));
            }
        }
        return fields;
    }

    @Override
    public void exportReport(Long templateId, Map<String, Object> filter, HttpServletResponse response)
    {
        exportReport(templateId, filter, null, response);
    }

    @Override
    public void exportReport(Long templateId, Map<String, Object> filter, List<String> projectCodes,
            HttpServletResponse response)
    {
        exportReport(templateId, filter, projectCodes, null, response);
    }

    @Override
    public void exportReport(Long templateId, Map<String, Object> filter, List<String> projectCodes,
            List<String> excludedCodes, HttpServletResponse response)
    {
        ProjReportTemplate template = getTemplate(templateId);
        List<ProjReportField> fields = template.getFieldList();
        if (fields == null || fields.isEmpty())
        {
            throw new ServiceException("模板未配置字段，无法导出");
        }
        List<Map<String, Object>> rows = queryRows(filter);
        // 勾选过滤：优先包含式(projectCodes)，否则排除式(excludedCodes)；都为空 = 全量导出。
        // 预览已按「全量 - 用户取消勾选」的排除式提交，故「默认全量导出、可排除个别行」的语义保持不变。
        if (projectCodes != null && !projectCodes.isEmpty())
        {
            rows = filterRowsByCodes(rows, projectCodes);
        }
        else if (excludedCodes != null && !excludedCodes.isEmpty())
        {
            rows = removeRowsByCodes(rows, excludedCodes);
            renumberRows(rows);
        }
        // 到账金额/时间拆行（模板4/7）：一行一项目 → 预付款/尾款各一行（序号沿用项目号，导出处跨行合并其它列）
        if (isPaySplitTemplate(template))
        {
            rows = expandPaySplitRows(rows);
        }
        fields = enrichHeaderGroupFromSource(template, fields);
        // 单位合并模板：仅当用户勾选「按单位合并显示」（默认不合并）时才按单位排序 + 计算到账汇总描述
        boolean mergeUnitCells = isMergeUnitCells(filter);
        if (isUnitMergeTemplate(template) && mergeUnitCells)
        {
            prepareUnitMergeRows(rows);
        }

        ProjReportTemplate sourceTemplate = null;
        if ("custom".equals(template.getTemplateType()) && template.getSourceTemplateId() != null)
        {
            sourceTemplate = templateMapper.selectTemplateById(template.getSourceTemplateId());
        }

        String suffix;
        if ("custom".equals(template.getTemplateType()))
        {
            suffix = ".xlsx";
        }
        else
        {
            String file = template.getTemplateFile();
            suffix = (file != null && file.toLowerCase().endsWith(".xls")) ? ".xls" : ".xlsx";
        }
        // 导出年月（标题实时替换 + 文件名占位符共用）
        String[] yearMonth = exportYearMonth(template, filter);
        String fileName = buildExportFileName(template, filter, suffix);

        try
        {
            response.setContentType("application/vnd.ms-excel");
            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name()).replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + encoded);

            OutputStream out = response.getOutputStream();
            if ("custom".equals(template.getTemplateType()))
            {
                ReportExcelExporter.exportCustom(out, template, fields, rows, sourceTemplate);
            }
            else
            {
                ReportExcelExporter.exportBuiltin(out, template, fields, rows, yearMonth, mergeUnitCells);
            }
            out.flush();
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new ServiceException("导出失败：" + e.getMessage());
        }

        // 记录导出历史
        ProjReportLog log = new ProjReportLog();
        log.setTemplateId(templateId);
        log.setTemplateName(template.getTemplateName());
        log.setFilterName(filter == null ? null : (String) filter.get("_filterName"));
        log.setFilterConfig(filterToJson(filter));
        log.setRowCount(rows.size());
        log.setExportBy(SecurityUtils.getUsername());
        log.setFileName(fileName);
        try
        {
            logMapper.insertLog(log);
        }
        catch (Exception e)
        {
            // 日志写入失败不应影响已导出的文件
            System.out.println("[ReportExport] insertLog failed (ignored): " + e.getMessage());
        }
    }

    @Override
    public void exportByConfig(ProjReportTemplate template, Map<String, Object> filter, HttpServletResponse response)
    {
        List<ProjReportField> fields = template.getFieldList();
        if (fields == null || fields.isEmpty())
        {
            throw new ServiceException("未配置字段，无法导出");
        }
        List<Map<String, Object>> rows = queryRows(filter);
        fields = enrichHeaderGroupFromSource(template, fields);

        ProjReportTemplate sourceTemplate = null;
        if (template.getSourceTemplateId() != null)
        {
            sourceTemplate = templateMapper.selectTemplateById(template.getSourceTemplateId());
        }

        String tplName = template.getTemplateName() == null || template.getTemplateName().isEmpty()
                ? "临时导出" : template.getTemplateName();
        String fileName = buildExportFileName(template, filter, ".xlsx");

        try
        {
            response.setContentType("application/vnd.ms-excel");
            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name()).replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + encoded);

            OutputStream out = response.getOutputStream();
            ReportExcelExporter.exportCustom(out, template, fields, rows, sourceTemplate);
            out.flush();
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new ServiceException("导出失败：" + e.getMessage());
        }

        // 记录导出历史（templateId 记来源模板，标识临时导出）
        ProjReportLog log = new ProjReportLog();
        log.setTemplateId(template.getSourceTemplateId());
        log.setTemplateName(tplName + "（临时）");
        log.setFilterName(filter == null ? null : (String) filter.get("_filterName"));
        log.setFilterConfig(filterToJson(filter));
        log.setRowCount(rows.size());
        log.setExportBy(SecurityUtils.getUsername());
        log.setFileName(fileName);
        try
        {
            logMapper.insertLog(log);
        }
        catch (Exception e)
        {
            System.out.println("[ReportExport] insertLog failed (ignored): " + e.getMessage());
        }
    }

    /**
     * 构建导出文件名：
     * - 模板 file_name 有值 → 直接使用（含 {year}/{month} 占位符则从筛选条件提取年月替换；
     *   未带后缀自动补后缀）
     * - file_name 为空 → 兜底：templateName + 时间戳 + 后缀
     */
    private String buildExportFileName(ProjReportTemplate template, Map<String, Object> filter, String suffix)
    {
        String pattern = template.getFileName();
        if (pattern != null && !pattern.isEmpty())
        {
            String name = pattern;
            if (pattern.contains("{year}") || pattern.contains("{month}"))
            {
                String[] ym = exportYearMonth(template, filter);
                name = pattern.replace("{year}", ym[0]).replace("{month}", ym[1]);
            }
            // 模板 file_name 若未带后缀则补后缀
            if (suffix != null && !suffix.isEmpty() && !name.toLowerCase().endsWith(suffix))
            {
                name += suffix;
            }
            return name;
        }
        // 兜底：模板名 + 时间戳
        String tplName = template.getTemplateName() == null || template.getTemplateName().isEmpty()
                ? "临时导出" : template.getTemplateName();
        String time = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        return tplName + "_" + time + suffix;
    }

    /**
     * 导出年月：模板4「市场性任务到账收入确认表」标题/文件名固定取当前系统年月
     * （月度确认表按导出时刻的月份实时变化，与筛选条件无关）；
     * 其它模板沿用 extractYearMonth（筛选取筛选月，无筛选取当前月）。
     */
    private String[] exportYearMonth(ProjReportTemplate template, Map<String, Object> filter)
    {
        if (template != null && Long.valueOf(4L).equals(template.getId()))
        {
            LocalDate now = LocalDate.now();
            return new String[]{
                String.valueOf(now.getYear()),
                String.valueOf(now.getMonthValue())
            };
        }
        return extractYearMonth(filter);
    }

    /**
     * 从筛选条件中提取年月：
     * 按优先级检查各日期范围的 Begin 值，取第一个有值的解析年月
     * 无日期筛选 → 当前年月
     */
    private String[] extractYearMonth(Map<String, Object> filter)
    {
        if (filter != null)
        {
            String[] dateKeys = {"createTimeBegin", "archiveDateBegin", "signDateBegin",
                    "lastPayTimeBegin", "entrustDateBegin", "assignDateBegin"};
            DateTimeFormatter[] formatters = {
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd"),
                DateTimeFormatter.ofPattern("yyyyMMdd")
            };
            for (String key : dateKeys)
            {
                Object val = filter.get(key);
                if (val == null || val.toString().trim().isEmpty())
                {
                    continue;
                }
                String dateStr = val.toString().trim();
                // 截取前 10 位（兼容 "2026-07-01 12:00:00" 格式）
                if (dateStr.length() > 10)
                {
                    dateStr = dateStr.substring(0, 10);
                }
                for (DateTimeFormatter fmt : formatters)
                {
                    try
                    {
                        LocalDate date = LocalDate.parse(dateStr, fmt);
                        return new String[]{
                            String.valueOf(date.getYear()),
                            String.valueOf(date.getMonthValue())
                        };
                    }
                    catch (Exception ignored)
                    {
                    }
                }
            }
        }
        // 兜底：当前年月
        LocalDate now = LocalDate.now();
        return new String[]{
            String.valueOf(now.getYear()),
            String.valueOf(now.getMonthValue())
        };
    }

    /** 查询数据行（剥离前端辅助键，如 _filterName），并注入行号（全量，导出/上报用） */
    private List<Map<String, Object>> queryRows(Map<String, Object> filter)
    {
        return queryRows(filter, null, null);
    }

    /** 查询数据行（分页重载）：limit/offset 为 null 时取全量；rowNo 按 offset 续号（跨页连续） */
    private List<Map<String, Object>> queryRows(Map<String, Object> filter, Integer limit, Integer offset)
    {
        Map<String, Object> f = buildQueryFilter(filter);
        List<Map<String, Object>> rows = reportDataMapper.selectProjectRows(f, limit, offset);
        int base = (offset == null) ? 0 : offset;
        for (int i = 0; i < rows.size(); i++)
        {
            rows.get(i).put("rowNo", base + i + 1);
        }
        return rows;
    }

    /** 构建查询筛选条件（剥离前端辅助键 '_xxx'，空值忽略，值统一转字符串） */
    private Map<String, Object> buildQueryFilter(Map<String, Object> filter)
    {
        Map<String, Object> f = new HashMap<>();
        if (filter != null)
        {
            for (Map.Entry<String, Object> e : filter.entrySet())
            {
                if (!e.getKey().startsWith("_") && e.getValue() != null
                        && !e.getValue().toString().trim().isEmpty())
                {
                    f.put(e.getKey(), e.getValue().toString().trim());
                }
            }
        }
        return f;
    }

    // ==================== 单位合并模板（内置 zdyw_report） ====================

    /** 单位合并模板判断（模板文件关键字与导出器保持一致：zdyw/byx 均按单位排序合并） */
    private boolean isUnitMergeTemplate(ProjReportTemplate template)
    {
        return template != null && template.getTemplateFile() != null
                && (template.getTemplateFile().toLowerCase()
                        .contains(ReportExcelExporter.UNIT_MERGE_TEMPLATE_KEYWORD)
                    || template.getTemplateFile().toLowerCase()
                        .contains(ReportExcelExporter.UNIT_MERGE_NO_PAY_SUMMARY_KEYWORD)
                    || template.getTemplateFile().toLowerCase()
                        .contains(ReportExcelExporter.UNIT_MERGE_CONTRACT_AMOUNT_KEYWORD));
    }

    /** 用户是否勾选「按单位合并显示」（请求体 mergeUnitCells → filter 约定键 _mergeUnitCells；默认不合并） */
    private boolean isMergeUnitCells(Map<String, Object> filter)
    {
        return filter != null && Boolean.TRUE.equals(filter.get("_mergeUnitCells"));
    }

    /** 是否「只定未验」（zdyw）模板：合并时到账时间列显示单位汇总描述 */
    private boolean isPayTimeSummaryKeyword(ProjReportTemplate template)
    {
        return template != null && template.getTemplateFile() != null
                && template.getTemplateFile().toLowerCase()
                        .contains(ReportExcelExporter.UNIT_MERGE_TEMPLATE_KEYWORD);
    }

    /**
     * 单位合并模板数据准备：
     * 1. 按单位名称排序（稳定排序，相同单位保持原相对顺序；单位为空排最后）
     * 2. 按单位分组汇总到账总额，生成到账描述（如"2026年8月13日到账823元"），
     *    注入行键 _unitSummary，供导出器合并"到账时间"列时写入
     */
    private void prepareUnitMergeRows(List<Map<String, Object>> rows)
    {
        if (rows == null || rows.isEmpty())
        {
            return;
        }
        rows.sort((a, b) -> {
            String ua = toStr(a.get("clientUnit"));
            String ub = toStr(b.get("clientUnit"));
            if (ua == null)
            {
                return ub == null ? 0 : 1;
            }
            if (ub == null)
            {
                return -1;
            }
            int cmp = ua.compareTo(ub);
            if (cmp != 0)
            {
                return cmp;
            }
            // 同单位内按合同聚拢（同合同多条相邻，供合同金额列合并单元格；无合同排最后）
            Object ca = a.get("contractId");
            Object cb = b.get("contractId");
            if (ca == null)
            {
                return cb == null ? 0 : 1;
            }
            if (cb == null)
            {
                return -1;
            }
            return ca.toString().compareTo(cb.toString());
        });
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy年M月d日");
        int i = 0;
        while (i < rows.size())
        {
            String unit = toStr(rows.get(i).get("clientUnit"));
            int end = i;
            BigDecimal total = toNum(rows.get(i).get("receivedAmount"));
            while (end + 1 < rows.size() && java.util.Objects.equals(toStr(rows.get(end + 1).get("clientUnit")), unit))
            {
                end++;
                BigDecimal amt = toNum(rows.get(end).get("receivedAmount"));
                if (amt != null)
                {
                    total = total == null ? amt : total.add(amt);
                }
            }
            // 到账汇总描述：日期取该单位内「最近到账时间」（与预览 selectUnitPaySummary 口径一致）
            Date maxPay = null;
            for (int r = i; r <= end; r++)
            {
                Object pt = rows.get(r).get("lastPayTime");
                if (pt instanceof Date && (maxPay == null || ((Date) pt).after(maxPay)))
                {
                    maxPay = (Date) pt;
                }
            }
            String summary = "";
            if (maxPay != null)
            {
                String amtStr = total == null ? "0"
                        : total.setScale(2, BigDecimal.ROUND_HALF_UP).stripTrailingZeros().toPlainString();
                summary = sdf.format(maxPay) + "到账" + amtStr + "元";
            }
            for (int r = i; r <= end; r++)
            {
                rows.get(r).put("_unitSummary", summary);
            }
            i = end + 1;
        }
        // 单位排序后重编序号：保证序号按最终展示顺序 1..N 连续（不断号）
        renumberRows(rows);
    }

    /** 行序号重编：按列表当前顺序重新赋值 rowNo=1..N（勾选过滤/单位排序后调用，导出序号从 1 连续） */
    private void renumberRows(List<Map<String, Object>> rows)
    {
        if (rows == null)
        {
            return;
        }
        for (int i = 0; i < rows.size(); i++)
        {
            rows.get(i).put("rowNo", i + 1);
        }
    }

    // ==================== 到账拆行 / 单位到账汇总 / 排除式过滤 ====================

    /** 是否「到账金额/时间按预付款、尾款拆行」模板（模板4 scsr / 模板7 dyxk） */
    private boolean isPaySplitTemplate(ProjReportTemplate template)
    {
        if (template == null || template.getTemplateFile() == null)
        {
            return false;
        }
        String file = template.getTemplateFile().toLowerCase();
        for (String kw : PAY_SPLIT_TEMPLATE_KEYWORDS)
        {
            if (file.contains(kw))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * 到账金额/到账时间按到账类型拆行（仅模板4/7）：
     *   同一项目若预付款与尾款均有到账 → 拆 2 行（预付款行在前、尾款行在后），
     *     每行「到账金额」= 该类型到账金额、「到账时间」= 该类型到账时间；其余列沿用项目值（导出/预览据此跨行合并）。
     *   只有一类到账 → 1 行（金额/时间即该类型，与合计一致）；无到账 → 1 行（到账金额/时间空）。
     *   同一类型多笔 → SQL 侧已合计（金额求和 + 取最后一笔时间），不逐笔展开。
     * 行上注入 _grpKey（项目编号）/ _grpSize（拆行数），供前端与导出器合并「到账金额/时间」以外的列。
     */
    private List<Map<String, Object>> expandPaySplitRows(List<Map<String, Object>> rows)
    {
        List<Map<String, Object>> out = new ArrayList<>();
        if (rows == null)
        {
            return out;
        }
        for (Map<String, Object> row : rows)
        {
            Object advAmt = row.get("advanceAmount");
            Object advTime = row.get("advancePayTime");
            Object finAmt = row.get("finalAmount");
            Object finTime = row.get("finalPayTime");
            // ⚠️ 金额列 SQL 用 sum(... else 0) 聚合，无该类到账时值为 0（非 null）
            //    ⇒ 判定「有没有这类到账」必须看「金额非零 或 到账时间非空」，
            //      否则每个有到账的项目都会被无脑拆成 2 行（第二行空行）。
            boolean hasAdv = payPartPresent(advAmt, advTime);
            boolean hasFin = payPartPresent(finAmt, finTime);

            List<Object[]> parts = new ArrayList<>();
            if (hasAdv)
            {
                parts.add(new Object[]{advAmt, advTime});
            }
            if (hasFin)
            {
                parts.add(new Object[]{finAmt, finTime});
            }
            // 分组键优先取项目主键 id（项目编号理论上可能重复，id 唯一）
            Object idObj = row.get("id");
            String grpKey = idObj != null ? String.valueOf(idObj) : toStr(row.get("projectCode"));
            if (parts.isEmpty())
            {
                Map<String, Object> r = new LinkedHashMap<>(row);
                r.put("_grpKey", grpKey);
                r.put("_grpSize", 1);
                out.add(r);
            }
            else
            {
                for (Object[] p : parts)
                {
                    Map<String, Object> r = new LinkedHashMap<>(row);
                    // 覆盖为「该类型」的到账金额/时间（其余列保持项目级原值）
                    r.put("receivedAmount", p[0]);
                    r.put("lastPayTime", p[1]);
                    r.put("_grpKey", grpKey);
                    r.put("_grpSize", parts.size());
                    out.add(r);
                }
            }
        }
        return out;
    }

    /** 该到账类型是否实际存在：到账时间非空 或 金额非零（金额列 SQL 缺省为 0，不能只判 null） */
    private boolean payPartPresent(Object amount, Object payTime)
    {
        if (payTime instanceof Date)
        {
            return true;
        }
        BigDecimal amt = toNum(amount);
        return amt != null && amt.signum() != 0;
    }

    /**
     * zdyw 单位合并：把当前页各行的「到账时间」列替换为单位汇总描述（单位 → 到账合计 + 最近到账时间）。
     * 数据来自独立聚合查询 selectUnitPaySummary（全量口径），分页后仍正确、不失真。
     */
    private void applyUnitPaySummary(List<Map<String, Object>> rows, Map<String, Object> qf)
    {
        if (rows == null || rows.isEmpty())
        {
            return;
        }
        List<Map<String, Object>> summaries = reportDataMapper.selectUnitPaySummary(qf);
        Map<String, Map<String, Object>> byUnit = new HashMap<>();
        for (Map<String, Object> s : summaries)
        {
            byUnit.put(toStr(s.get("unit")), s);
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy年M月d日");
        for (Map<String, Object> row : rows)
        {
            String unit = toStr(row.get("clientUnit"));
            Map<String, Object> s = byUnit.get(unit == null ? "" : unit);
            String summary = "";
            if (s != null && s.get("payTime") instanceof Date)
            {
                BigDecimal amt = toNum(s.get("amount"));
                String amtStr = amt == null ? "0"
                        : amt.setScale(2, BigDecimal.ROUND_HALF_UP).stripTrailingZeros().toPlainString();
                summary = sdf.format((Date) s.get("payTime")) + "到账" + amtStr + "元";
            }
            row.put("_unitSummary", summary);
        }
    }

    /** 按工程编号移除数据行（排除式导出：默认全量导出，仅剔除用户取消勾选的项目） */
    private List<Map<String, Object>> removeRowsByCodes(List<Map<String, Object>> rows, List<String> codes)
    {
        if (codes == null || codes.isEmpty() || rows == null)
        {
            return rows;
        }
        Set<String> set = new HashSet<>(codes);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows)
        {
            String code = toStr(row.get("projectCode"));
            if (code == null || !set.contains(code))
            {
                result.add(row);
            }
        }
        return result;
    }

    /** 模板中「上报时间」类字段（与报表列同源；用于「上报状态」判定，保证两列口径一致） */
    private static final Set<String> REPORT_TIME_FIELD_KEYS =
            Set.of("submitTimeYm", "relatedLastSubmitTime", "createTimeYm");

    /**
     * 该行在本模板的「上报时间」列是否有值。
     * 取值走与预览/导出完全相同的出口（ReportFieldPool.resolveValue），
     * 因此「上报状态」与「上报时间」列不可能出现一列有值、另一列说未上报的矛盾。
     */
    private boolean hasReportTimeValue(List<ProjReportField> fields, Map<String, Object> row)
    {
        if (fields == null)
        {
            return false;
        }
        for (ProjReportField f : fields)
        {
            if (!REPORT_TIME_FIELD_KEYS.contains(f.getFieldKey()))
            {
                continue;
            }
            Object v = ReportFieldPool.resolveValue(f, row);
            if (v != null && !v.toString().trim().isEmpty())
            {
                return true;
            }
        }
        return false;
    }

    private String toStr(Object o)
    {
        return o == null ? null : o.toString();
    }

    private BigDecimal toNum(Object o)
    {
        if (o == null)
        {
            return null;
        }
        if (o instanceof BigDecimal)
        {
            return (BigDecimal) o;
        }
        if (o instanceof Number)
        {
            return new BigDecimal(o.toString());
        }
        try
        {
            return new BigDecimal(o.toString());
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private String filterToJson(Map<String, Object> filter)
    {
        if (filter == null)
        {
            return "{}";
        }
        Map<String, Object> f = new HashMap<>();
        for (Map.Entry<String, Object> e : filter.entrySet())
        {
            if (!e.getKey().startsWith("_"))
            {
                f.put(e.getKey(), e.getValue());
            }
        }
        return com.alibaba.fastjson2.JSON.toJSONString(f);
    }

    // ==================== 导出历史 ====================

    @Override
    public List<ProjReportLog> listLogs(ProjReportLog query)
    {
        return logMapper.selectLogList(query);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reExport(Long logId, HttpServletResponse response)
    {
        ProjReportLog log = logMapper.selectLogById(logId);
        if (log == null)
        {
            throw new ServiceException("导出记录不存在");
        }
        ProjReportTemplate template = templateMapper.selectTemplateById(log.getTemplateId());
        if (template == null)
        {
            throw new ServiceException("原模板已删除，无法重导");
        }
        Map<String, Object> filter = new HashMap<>();
        String config = log.getFilterConfig();
        if (config != null && !config.isEmpty())
        {
            filter = com.alibaba.fastjson2.JSON.parseObject(config);
        }
        exportReport(template.getId(), filter, response);
    }

    @Override
    public int deleteLog(Long id)
    {
        return logMapper.deleteLogById(id);
    }

    // ==================== 上报领导 ====================

    /** 当月（自然月）已存在的上报记录数（begin <= submit_time < end） */
    private int countMonthSubmittedLogs()
    {
        java.time.YearMonth ym = java.time.YearMonth.now();
        Date begin = java.sql.Timestamp.valueOf(ym.atDay(1).atStartOfDay());
        Date end = java.sql.Timestamp.valueOf(ym.plusMonths(1).atDay(1).atStartOfDay());
        return submitMapper.countLogsBetween(begin, end);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitReport(Long templateId, Map<String, Object> filter,
            List<String> projectCodes, List<String> excludedCodes, String remark)
    {
        ProjReportTemplate template = getTemplate(templateId);
        List<ProjReportField> fields = template.getFieldList();
        if (fields == null || fields.isEmpty())
        {
            throw new ServiceException("模板未配置字段，无法上报");
        }
        // 仅「只定未验及补之前扣除项目」（zdyw_report）支持上报领导
        if (template.getTemplateFile() == null || !template.getTemplateFile()
                .toLowerCase().contains(ReportExcelExporter.UNIT_MERGE_TEMPLATE_KEYWORD))
        {
            throw new ServiceException("仅「只定未验及补之前扣除项目」报表支持上报领导");
        }
        // 按月上报：当月已存在上报记录则拒绝重复上报（记录终身一次由 UNIQUE(project_code) 保证）
        if (countMonthSubmittedLogs() > 0)
        {
            throw new ServiceException("本月已上报过，不可重复上报，下月可再上报");
        }
        // 1. 取数 + 勾选过滤（包含式优先，其次排除式；未勾选/已排除记录不上报、不记录上报时间）
        List<Map<String, Object>> rows = queryRows(filter);
        if (projectCodes != null && !projectCodes.isEmpty())
        {
            rows = filterRowsByCodes(rows, projectCodes);
        }
        else if (excludedCodes != null && !excludedCodes.isEmpty())
        {
            rows = removeRowsByCodes(rows, excludedCodes);
            renumberRows(rows);
        }
        if (rows.isEmpty())
        {
            throw new ServiceException("请至少勾选一条记录");
        }
        fields = enrichHeaderGroupFromSource(template, fields);
        if (isUnitMergeTemplate(template))
        {
            prepareUnitMergeRows(rows);
        }
        String username = SecurityUtils.getUsername();
        String batchNo = genSubmitBatchNo();
        String suffix = (template.getTemplateFile() != null
                && template.getTemplateFile().toLowerCase().endsWith(".xls")) ? ".xls" : ".xlsx";
        String fileName = buildExportFileName(template, filter, suffix);

        // 2. 生成快照文件（服务器留档，可下载对质）
        String dir = LandPatchConfig.getProfile() + "/reportSnapshot/" + batchNo;
        File dirFile = new File(dir);
        if (!dirFile.exists() && !dirFile.mkdirs())
        {
            throw new ServiceException("上报快照目录创建失败");
        }
        String snapshotPath = dir + "/" + fileName;
        try (FileOutputStream fos = new FileOutputStream(snapshotPath))
        {
            String[] yearMonth = exportYearMonth(template, filter);
            // 上报快照为官方留档格式：始终按单位合并（与历史快照口径一致），不受导出开关影响
            ReportExcelExporter.exportBuiltin(fos, template, fields, rows, yearMonth, true);
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new ServiceException("上报快照生成失败：" + e.getMessage());
        }

        // 3. 批次 + 记录级上报时间（UNIQUE(project_code) 锁定，已上报跳过）
        ProjReportSubmitBatch batch = new ProjReportSubmitBatch();
        batch.setBatchNo(batchNo);
        batch.setTemplateId(templateId);
        batch.setSubmitBy(username);
        batch.setProjectCount(rows.size());
        batch.setFilterDesc(buildFilterDesc(filter));
        batch.setSnapshotFile(snapshotPath);
        batch.setRemark(remark);
        submitMapper.insertBatch(batch);

        int newCount = 0;
        for (Map<String, Object> row : rows)
        {
            String code = toStr(row.get("projectCode"));
            if (code == null || code.isEmpty())
            {
                continue;
            }
            ProjReportSubmitLog log = new ProjReportSubmitLog();
            log.setProjectCode(code);
            log.setProjectName(toStr(row.get("projectName")));
            log.setUnitName(toStr(row.get("clientUnit")));
            log.setSubmitBy(username);
            log.setBatchId(batch.getId());
            newCount += submitMapper.insertLogIgnore(log);
        }
        int skippedCount = rows.size() - newCount;

        Map<String, Object> result = new HashMap<>();
        result.put("batchId", batch.getId());
        result.put("batchNo", batchNo);
        result.put("newCount", newCount);
        result.put("skippedCount", skippedCount);
        result.put("totalCount", rows.size());
        result.put("snapshotFileName", fileName);
        return result;
    }

    @Override
    public List<ProjReportSubmitBatch> listSubmitBatches(ProjReportSubmitBatch query)
    {
        return submitMapper.selectBatchList(query);
    }

    @Override
    public ProjReportSubmitBatch getSubmitBatch(Long id)
    {
        ProjReportSubmitBatch batch = submitMapper.selectBatchById(id);
        if (batch == null)
        {
            throw new ServiceException("批次不存在");
        }
        batch.setLogs(submitMapper.selectLogsByBatchId(id));
        return batch;
    }

    @Override
    public List<ProjReportSubmitLog> listSubmitLogs(ProjReportSubmitLog query)
    {
        return submitMapper.selectLogList(query);
    }

    @Override
    public List<ProjReportSubmitLog> listSubmittedStatus(List<String> projectCodes)
    {
        if (projectCodes == null || projectCodes.isEmpty())
        {
            return new ArrayList<>();
        }
        return submitMapper.selectLogsByCodes(projectCodes);
    }

    @Override
    public void downloadSnapshot(Long batchId, HttpServletResponse response)
    {
        ProjReportSubmitBatch batch = submitMapper.selectBatchById(batchId);
        if (batch == null || "2".equals(batch.getDelFlag()))
        {
            throw new ServiceException("批次不存在");
        }
        String path = batch.getSnapshotFile();
        if (path == null || path.isEmpty())
        {
            throw new ServiceException("批次无快照文件");
        }
        File file = new File(path);
        if (!file.exists())
        {
            throw new ServiceException("快照文件不存在，可能已被清理");
        }
        String fileName = file.getName();
        try
        {
            response.setContentType("application/vnd.ms-excel");
            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name()).replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + encoded);
            OutputStream out = response.getOutputStream();
            try (java.io.FileInputStream fis = new java.io.FileInputStream(file))
            {
                byte[] buf = new byte[8192];
                int len;
                while ((len = fis.read(buf)) > 0)
                {
                    out.write(buf, 0, len);
                }
            }
            out.flush();
        }
        catch (IOException e)
        {
            throw new ServiceException("快照下载失败：" + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteSubmitBatch(Long id)
    {
        if (!SecurityUtils.isAdmin())
        {
            throw new ServiceException("仅管理员可删除上报批次");
        }
        submitMapper.deleteLogsByBatchId(id);
        return submitMapper.deleteBatchById(id);
    }

    @Override
    public int deleteSubmitLog(Long id)
    {
        if (!SecurityUtils.isAdmin())
        {
            throw new ServiceException("仅管理员可删除上报记录");
        }
        return submitMapper.deleteLogById(id);
    }

    @Override
    public int updateSubmitLogTime(Long id, String submitTime)
    {
        if (!SecurityUtils.isAdmin())
        {
            throw new ServiceException("仅管理员可修改历史补录的上报时间");
        }
        if (id == null)
        {
            throw new ServiceException("记录ID不能为空");
        }
        String text = submitTime == null ? "" : submitTime.trim();
        if (text.isEmpty())
        {
            throw new ServiceException("上报时间不能为空");
        }
        Date time = parseReportTime(text);
        if (time == null)
        {
            throw new ServiceException("上报时间格式不正确，应为 yyyy-MM-dd");
        }
        // SQL 内以 batch_id is null 守卫：真实上报行不会被命中（上报时间锁定不可改）
        int n = submitMapper.updateLogSubmitTime(id, time);
        if (n == 0)
        {
            throw new ServiceException("修改失败：该记录不存在，或不是历史导入补录记录（真实上报时间不可修改）");
        }
        return n;
    }

    /** 解析上报时间：支持 yyyy-MM-dd 与 yyyy-MM-dd HH:mm[:ss]（补录值是历史事实，按天粒度足够） */
    private Date parseReportTime(String text)
    {
        String s = text.replace('T', ' ').trim();
        try
        {
            if (s.length() > 10)
            {
                if (s.length() == 16)
                {
                    s = s + ":00";
                }
                return java.sql.Timestamp.valueOf(s.substring(0, 19));
            }
            return java.sql.Timestamp.valueOf(java.time.LocalDate.parse(s).atStartOfDay());
        }
        catch (Exception e)
        {
            return null;
        }
    }

    // ==================== 上报辅助 ====================

    /** 生成批次号：SB + yyyyMMdd + 3位当日序号（同步防并发） */
    private synchronized String genSubmitBatchNo()
    {
        String prefix = "SB" + new SimpleDateFormat("yyyyMMdd").format(new Date());
        String max = submitMapper.selectMaxBatchNoByPrefix(prefix);
        int seq = 1;
        if (max != null && max.length() == prefix.length() + 3)
        {
            try
            {
                seq = Integer.parseInt(max.substring(prefix.length())) + 1;
            }
            catch (Exception ignored)
            {
            }
        }
        return prefix + String.format("%03d", seq);
    }

    /** 按勾选工程编号过滤数据行 */
    private List<Map<String, Object>> filterRowsByCodes(List<Map<String, Object>> rows, List<String> codes)
    {
        if (codes == null || codes.isEmpty())
        {
            return rows;
        }
        Set<String> set = new HashSet<>(codes);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows)
        {
            String code = toStr(row.get("projectCode"));
            if (code != null && set.contains(code))
            {
                result.add(row);
            }
        }
        // 勾选过滤后重编序号：导出的记录序号从 1 开始连续（不沿用全量预览的原序号）
        renumberRows(result);
        return result;
    }

    /** 批次筛选范围描述：优先筛选方案名，其次关键条件拼串 */
    private String buildFilterDesc(Map<String, Object> filter)
    {
        if (filter == null)
        {
            return null;
        }
        Object name = filter.get("_filterName");
        if (name != null && !name.toString().trim().isEmpty())
        {
            return name.toString().trim();
        }
        StringBuilder sb = new StringBuilder();
        String[] keys = {"projectCode", "projectName", "clientUnit", "projectStatus",
                "createTimeBegin", "createTimeEnd"};
        for (String k : keys)
        {
            Object v = filter.get(k);
            if (v != null && !v.toString().trim().isEmpty())
            {
                if (sb.length() > 0)
                {
                    sb.append("；");
                }
                sb.append(k).append("=").append(v.toString().trim());
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }
}
