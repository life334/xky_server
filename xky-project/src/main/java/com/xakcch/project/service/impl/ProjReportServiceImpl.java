package com.xakcch.project.service.impl;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.xakcch.common.exception.ServiceException;
import com.xakcch.common.utils.SecurityUtils;
import com.xakcch.project.domain.ProjFieldDef;
import com.xakcch.project.domain.ProjReportField;
import com.xakcch.project.domain.ProjReportFilter;
import com.xakcch.project.domain.ProjReportLog;
import com.xakcch.project.domain.ProjReportTemplate;
import com.xakcch.project.mapper.ProjFieldDefMapper;
import com.xakcch.project.mapper.ProjReportFieldMapper;
import com.xakcch.project.mapper.ProjReportFilterMapper;
import com.xakcch.project.mapper.ProjReportLogMapper;
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
        return filterMapper.deleteFilterById(id);
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
    public Map<String, Object> preview(Long templateId, Map<String, Object> filter)
    {
        ProjReportTemplate template = getTemplate(templateId);
        List<Map<String, Object>> rows = queryRows(filter);
        // 按模板字段顺序解析展示值（与导出一致，所见即所得）
        List<ProjReportField> fields = template.getFieldList();
        List<List<Object>> displayRows = new ArrayList<>();
        for (Map<String, Object> row : rows)
        {
            List<Object> d = new ArrayList<>();
            if (fields != null)
            {
                for (ProjReportField f : fields)
                {
                    d.add(ReportFieldPool.resolveValue(f, row));
                }
            }
            displayRows.add(d);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("template", template);
        result.put("total", rows.size());
        result.put("rows", displayRows.size() > 50 ? new ArrayList<>(displayRows.subList(0, 50)) : displayRows);
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
        ProjReportTemplate template = getTemplate(templateId);
        List<ProjReportField> fields = template.getFieldList();
        if (fields == null || fields.isEmpty())
        {
            throw new ServiceException("模板未配置字段，无法导出");
        }
        List<Map<String, Object>> rows = queryRows(filter);
        fields = enrichHeaderGroupFromSource(template, fields);

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
        String time = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fileName = template.getTemplateName() + "_" + time + suffix;

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
                ReportExcelExporter.exportBuiltin(out, template, fields, rows);
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

        String time = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String tplName = template.getTemplateName() == null || template.getTemplateName().isEmpty()
                ? "临时导出" : template.getTemplateName();
        String fileName = tplName + "_" + time + ".xlsx";

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

    /** 查询数据行（剥离前端辅助键，如 _filterName），并注入行号 */
    private List<Map<String, Object>> queryRows(Map<String, Object> filter)
    {
        Map<String, Object> f = new HashMap<>();
        if (filter != null)
        {
            for (Map.Entry<String, Object> e : filter.entrySet())
            {
                if (!e.getKey().startsWith("_") && e.getValue() != null
                        && !"".equals(e.getValue().toString().trim()))
                {
                    f.put(e.getKey(), e.getValue().toString().trim());
                }
            }
        }
        List<Map<String, Object>> rows = reportDataMapper.selectProjectRows(f);
        for (int i = 0; i < rows.size(); i++)
        {
            rows.get(i).put("rowNo", i + 1);
        }
        return rows;
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
}
