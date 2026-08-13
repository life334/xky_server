package com.xakcch.project.report;

import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import com.xakcch.common.exception.ServiceException;
import com.xakcch.project.domain.ProjReportField;
import com.xakcch.project.domain.ProjReportTemplate;

/**
 * 报表 Excel 导出器（POI）
 *
 * <p>两种模式：</p>
 * <ul>
 *   <li><b>内置模板原样导出</b>：打开客户模板文件 → 定位数据起始行 → 逐行填充数据并复制行样式，
 *       标题/合并单元格/表头 100% 还原，列结构不变。</li>
 *   <li><b>自定义模板动态列导出</b>：读取来源内置模板的标题/表头风格 → 新建 Workbook 按勾选字段
 *       动态生成列，风格继承、列结构自定义。</li>
 * </ul>
 *
 * @author liuyonghui
 */
public class ReportExcelExporter
{
    /**
     * 内置模板原样导出
     *
     * @param out      输出流（响应体）
     * @param template 模板定义（templateFile 指向 classpath 或绝对路径）
     * @param fields   模板字段（按 column_index 定位列）
     * @param rows     数据行
     */
    public static void exportBuiltin(OutputStream out, ProjReportTemplate template,
            List<ProjReportField> fields, List<Map<String, Object>> rows) throws Exception
    {
        Workbook wb = openTemplate(template);
        try
        {
            Sheet sheet = wb.getSheetAt(0);
            fillDataRows(wb, sheet, template, fields, rows, false);
            // 默认显示第一个 sheet（模板可能有多个 sheet，第 2 个通常为空）
            wb.setActiveSheet(0);
            sheet.setSelected(true);
            wb.write(out);
        }
        finally
        {
            wb.close();
        }
    }

    /**
     * 自定义模板动态列导出（新建 Workbook，风格继承来源内置模板）
     */
    public static void exportCustom(OutputStream out, ProjReportTemplate template,
            List<ProjReportField> fields, List<Map<String, Object>> rows,
            ProjReportTemplate sourceTemplate) throws Exception
    {
        // 打开来源内置模板，读取标题/表头/数据行样式与标题文本
        Workbook srcWb = null;
        String sheetName = "报表";
        String titleText = null;
        CellStyle headerStyle = null;
        CellStyle dataStyle = null;
        if (sourceTemplate != null)
        {
            srcWb = openTemplate(sourceTemplate);
            Sheet srcSheet = srcWb.getSheetAt(0);
            sheetName = srcSheet.getSheetName();
            if (sourceTemplate.getTitleRow() != null && sourceTemplate.getTitleRow() > 0)
            {
                Row titleRow = srcSheet.getRow(sourceTemplate.getTitleRow() - 1);
                if (titleRow != null && titleRow.getCell(0) != null)
                {
                    Cell c = titleRow.getCell(0);
                    if (c.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING)
                    {
                        titleText = c.getStringCellValue();
                    }
                }
            }
            if (sourceTemplate.getHeaderRow() != null && sourceTemplate.getHeaderRow() > 0)
            {
                Row hr = srcSheet.getRow(sourceTemplate.getHeaderRow() - 1);
                if (hr != null && hr.getCell(0) != null)
                {
                    headerStyle = srcWb.createCellStyle();
                    headerStyle.cloneStyleFrom(hr.getCell(0).getCellStyle());
                }
            }
            if (sourceTemplate.getDataStartRow() != null && sourceTemplate.getDataStartRow() > 0)
            {
                Row dr = srcSheet.getRow(sourceTemplate.getDataStartRow() - 1);
                if (dr != null && dr.getCell(0) != null)
                {
                    dataStyle = srcWb.createCellStyle();
                    dataStyle.cloneStyleFrom(dr.getCell(0).getCellStyle());
                }
            }
        }

        Workbook wb = new XSSFWorkbook();
        try
        {
            Sheet sheet = wb.createSheet(sheetName.length() > 31 ? sheetName.substring(0, 31) : sheetName);
            int colCount = fields.size();

            // 标题行（跨所有列居中）
            int currentRow = 0;
            if (titleText != null && !titleText.isEmpty())
            {
                Row title = sheet.createRow(0);
                Cell tc = title.createCell(0);
                tc.setCellValue(titleText);
                if (headerStyle != null)
                {
                    CellStyle ts = wb.createCellStyle();
                    ts.cloneStyleFrom(headerStyle);
                    ts.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);
                    tc.setCellStyle(ts);
                }
                if (colCount > 1)
                {
                    sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, colCount - 1));
                }
                title.setHeight((short) 400);
                currentRow = 1;
            }

            // 表头行（支持多级表头：有 headerGroup 时写分组行+字段名行，无则单行）
            boolean hasGroup = false;
            for (ProjReportField hf : fields)
            {
                if (hf.getHeaderGroup() != null && !hf.getHeaderGroup().trim().isEmpty())
                {
                    hasGroup = true;
                    break;
                }
            }
            if (hasGroup)
            {
                // 二级表头：分组行 + 字段名行
                Row groupRow = sheet.createRow(currentRow);
                groupRow.setHeight((short) 300);
                Row fieldRow = sheet.createRow(currentRow + 1);
                fieldRow.setHeight((short) 300);
                int gi = 0;
                while (gi < colCount)
                {
                    ProjReportField f = fields.get(gi);
                    String group = f.getHeaderGroup();
                    if (group == null || group.trim().isEmpty())
                    {
                        // 无分组：垂直合并两行，字段名写在字段名行
                        Cell gc = groupRow.createCell(gi);
                        Cell fc = fieldRow.createCell(gi);
                        fc.setCellValue(f.getFieldLabel() == null ? f.getFieldKey() : f.getFieldLabel());
                        if (headerStyle != null)
                        {
                            CellStyle gs = wb.createCellStyle();
                            gs.cloneStyleFrom(headerStyle);
                            gc.setCellStyle(gs);
                            CellStyle fs = wb.createCellStyle();
                            fs.cloneStyleFrom(headerStyle);
                            fc.setCellStyle(fs);
                        }
                        sheet.addMergedRegion(new CellRangeAddress(currentRow, currentRow + 1, gi, gi));
                        int w = f.getWidth() == null ? 14 : f.getWidth();
                        sheet.setColumnWidth(gi, Math.min(w * 256, 12000));
                        gi++;
                    }
                    else
                    {
                        // 有分组：横向合并同组连续字段
                        int start = gi;
                        while (gi < colCount && group.equals(fields.get(gi).getHeaderGroup()))
                        {
                            ProjReportField cf = fields.get(gi);
                            Cell fc = fieldRow.createCell(gi);
                            fc.setCellValue(cf.getFieldLabel() == null ? cf.getFieldKey() : cf.getFieldLabel());
                            if (headerStyle != null)
                            {
                                CellStyle fs = wb.createCellStyle();
                                fs.cloneStyleFrom(headerStyle);
                                fc.setCellStyle(fs);
                            }
                            int w = cf.getWidth() == null ? 14 : cf.getWidth();
                            sheet.setColumnWidth(gi, Math.min(w * 256, 12000));
                            gi++;
                        }
                        Cell gc = groupRow.createCell(start);
                        gc.setCellValue(group);
                        if (headerStyle != null)
                        {
                            CellStyle gs = wb.createCellStyle();
                            gs.cloneStyleFrom(headerStyle);
                            gc.setCellStyle(gs);
                        }
                        if (gi - 1 > start)
                        {
                            sheet.addMergedRegion(new CellRangeAddress(currentRow, currentRow, start, gi - 1));
                        }
                    }
                }
                currentRow += 2;
            }
            else
            {
                // 一级表头：单行
                Row header = sheet.createRow(currentRow);
                header.setHeight((short) 300);
                for (int i = 0; i < colCount; i++)
                {
                    ProjReportField f = fields.get(i);
                    Cell c = header.createCell(i);
                    c.setCellValue(f.getFieldLabel() == null ? f.getFieldKey() : f.getFieldLabel());
                    if (headerStyle != null)
                    {
                        CellStyle hs = wb.createCellStyle();
                        hs.cloneStyleFrom(headerStyle);
                        c.setCellStyle(hs);
                    }
                    int w = f.getWidth() == null ? 14 : f.getWidth();
                    sheet.setColumnWidth(i, Math.min(w * 256, 12000));
                }
                currentRow++;
            }

            // 数据行
            for (Map<String, Object> row : rows)
            {
                Row r = sheet.createRow(currentRow++);
                r.setHeight((short) 300);
                for (int i = 0; i < colCount; i++)
                {
                    ProjReportField f = fields.get(i);
                    Cell c = r.createCell(i);
                    if (dataStyle != null)
                    {
                        CellStyle ds = wb.createCellStyle();
                        ds.cloneStyleFrom(dataStyle);
                        c.setCellStyle(ds);
                    }
                    Object v = ReportFieldPool.resolveValue(f, row);
                    setCellValue(wb, c, v);
                }
            }

            // 合计行
            if ("Y".equals(template.getHasSummaryRow()) && !rows.isEmpty())
            {
                Row sum = sheet.createRow(currentRow);
                sum.setHeight((short) 300);
                for (int i = 0; i < colCount; i++)
                {
                    ProjReportField f = fields.get(i);
                    Cell c = sum.createCell(i);
                    if (dataStyle != null)
                    {
                        CellStyle ds = wb.createCellStyle();
                        ds.cloneStyleFrom(dataStyle);
                        c.setCellStyle(ds);
                    }
                    if (i == 0)
                    {
                        c.setCellValue("合计");
                    }
                    else if ("number".equals(fieldType(f)))
                    {
                        BigDecimal total = BigDecimal.ZERO;
                        for (Map<String, Object> row : rows)
                        {
                            Object v = ReportFieldPool.resolveValue(f, row);
                            if (v instanceof Number)
                            {
                                total = total.add(new BigDecimal(v.toString()));
                            }
                        }
                        c.setCellValue(total.doubleValue());
                    }
                }
            }

            wb.write(out);
        }
        finally
        {
            wb.close();
            if (srcWb != null)
            {
                srcWb.close();
            }
        }
    }

    /**
     * 填充数据行（内置模板模式）：移除旧数据行 → 创建全新行和 cell → 设样式和值。
     * 采用"删除+重建"而非"原地修改"，规避 HSSFWorkbook 修改已有记录的序列化问题。
     */
    private static void fillDataRows(Workbook wb, Sheet sheet, ProjReportTemplate template,
            List<ProjReportField> fields, List<Map<String, Object>> rows, boolean addSummary) throws Exception
    {
        int dataZeroIdx = (template.getDataStartRow() == null ? 3 : template.getDataStartRow()) - 1;
        if (dataZeroIdx < 0)
        {
            dataZeroIdx = 0;
        }

        // ★ 先保存样式行中各列的样式（后续删除行时 styleRow 引用会失效）
        Row styleRow = sheet.getRow(dataZeroIdx);
        short savedHeight = (styleRow != null) ? styleRow.getHeight() : 300;
        Map<Integer, CellStyle> savedStyles = new HashMap<>();
        if (styleRow != null)
        {
            for (ProjReportField f : fields)
            {
                Integer ci = f.getColumnIndex();
                if (ci != null && ci > 0 && !savedStyles.containsKey(ci))
                {
                    Cell sc = styleRow.getCell(ci - 1);
                    if (sc != null)
                    {
                        CellStyle style = wb.createCellStyle();
                        style.cloneStyleFrom(sc.getCellStyle());
                        savedStyles.put(ci, style);
                    }
                }
            }
        }

        int existing = sheet.getLastRowNum() - dataZeroIdx + 1;
        if (existing < 0)
        {
            existing = 0;
        }

        int need = rows.size() + (addSummary || "Y".equals(template.getHasSummaryRow()) ? 1 : 0);

        // 若模板历史数据行比需要写的多，清空多余行
        if (existing > need)
        {
            for (int r = dataZeroIdx + need; r <= sheet.getLastRowNum(); r++)
            {
                Row rr = sheet.getRow(r);
                if (rr != null)
                {
                    sheet.removeRow(rr);
                }
            }
        }

        // ★ 填充数据行：移除旧行 → 创建全新行 → 创建全新 cell → 设样式和值
        for (int i = 0; i < rows.size(); i++)
        {
            int rowIdx = dataZeroIdx + i;
            Row oldRow = sheet.getRow(rowIdx);
            if (oldRow != null)
            {
                sheet.removeRow(oldRow);
            }
            Row row = sheet.createRow(rowIdx);
            row.setHeight(savedHeight);

            for (ProjReportField f : fields)
            {
                Integer colIdx = f.getColumnIndex();
                if (colIdx == null || colIdx <= 0)
                {
                    continue;
                }
                int ci = colIdx - 1;
                Cell cell = row.createCell(ci);
                CellStyle style = savedStyles.get(colIdx);
                if (style != null)
                {
                    cell.setCellStyle(style);
                }
                Object v = ReportFieldPool.resolveValue(f, rows.get(i));
                setCellValue(wb, cell, v);
            }
        }

        // 合计行
        if ("Y".equals(template.getHasSummaryRow()) && !rows.isEmpty())
        {
            int sumIdx = dataZeroIdx + rows.size();
            Row oldSum = sheet.getRow(sumIdx);
            if (oldSum != null)
            {
                sheet.removeRow(oldSum);
            }
            Row sum = sheet.createRow(sumIdx);
            sum.setHeight(savedHeight);
            for (ProjReportField f : fields)
            {
                Integer colIdx = f.getColumnIndex();
                if (colIdx == null || colIdx <= 0)
                {
                    continue;
                }
                int ci = colIdx - 1;
                Cell cell = sum.createCell(ci);
                CellStyle style = savedStyles.get(colIdx);
                if (style != null)
                {
                    cell.setCellStyle(style);
                }
                if (ci == 0)
                {
                    cell.setCellValue("合计");
                }
                else if ("number".equals(fieldType(f)))
                {
                    BigDecimal total = BigDecimal.ZERO;
                    for (Map<String, Object> row : rows)
                    {
                        Object v = ReportFieldPool.resolveValue(f, row);
                        if (v instanceof Number)
                        {
                            total = total.add(new BigDecimal(v.toString()));
                        }
                    }
                    cell.setCellValue(total.doubleValue());
                }
            }
        }
    }

    /** 字段类型（用于合计识别）：来自字段池元数据 */
    private static String fieldType(ProjReportField f)
    {
        String key = f.getFieldKey();
        if (key == null)
        {
            return "string";
        }
        if (key.contains("Amount") || key.contains("amount")
                || "durationRequire".equals(key) || "totalDuration".equals(key)
                || "debtMonths".equals(key))
        {
            return "number";
        }
        if (key.contains("Date") || key.contains("Time"))
        {
            return "date";
        }
        return "string";
    }

    /**
     * 按类型写入单元格值。
     * 日期类型追加 "yyyy-MM-dd" 数字格式，避免 Excel 显示为序列号（如 46241.466）。
     */
    private static void setCellValue(Workbook wb, Cell cell, Object v)
    {
        if (v == null)
        {
            return;
        }
        if (v instanceof Number)
        {
            cell.setCellValue(((Number) v).doubleValue());
        }
        else if (v instanceof Date)
        {
            cell.setCellValue((Date) v);
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.cloneStyleFrom(cell.getCellStyle());
            dateStyle.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("yyyy-MM-dd"));
            cell.setCellStyle(dateStyle);
        }
        else if (v instanceof Boolean)
        {
            cell.setCellValue((Boolean) v);
        }
        else
        {
            cell.setCellValue(v.toString());
        }
    }

    /**
     * 打开模板文件（支持 classpath: 前缀与绝对路径）
     */
    private static Workbook openTemplate(ProjReportTemplate template) throws Exception
    {
        String file = template.getTemplateFile();
        if (file == null || file.isEmpty())
        {
            throw new ServiceException("模板文件未配置");
        }
        InputStream in;
        if (file.startsWith("classpath:"))
        {
            in = new ClassPathResource(file.substring("classpath:".length())).getInputStream();
        }
        else
        {
            in = new FileSystemResource(file).getInputStream();
        }
        try (InputStream is = in)
        {
            return WorkbookFactory.create(is);
        }
    }
}
