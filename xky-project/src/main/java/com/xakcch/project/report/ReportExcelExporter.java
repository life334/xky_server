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
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Font;
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
     * 需要"按单位名称排序 + 合并单位名称单元格 + 到账时间按单位汇总"的内置模板文件关键字
     * （模板1「只定未验及补之前扣除项目」zdyw_report.xls）
     */
    public static final String UNIT_MERGE_TEMPLATE_KEYWORD = "zdyw_report";

    /**
     * 仅"按单位名称排序 + 合并单位名称单元格"、到账时间逐条显示的内置模板文件关键字
     * （模板6「补验线」byx_report.xls：结构同 zdyw，但到账时间不按单位汇总）
     */
    public static final String UNIT_MERGE_NO_PAY_SUMMARY_KEYWORD = "byx_report";

    /**
     * 内置模板原样导出
     *
     * @param out       输出流（响应体）
     * @param template  模板定义（templateFile 指向 classpath 或绝对路径）
     * @param fields    模板字段（按 column_index 定位列）
     * @param rows      数据行
     * @param yearMonth 导出年月 [年, 月]，用于替换标题中的"20XX年X月"；为 null 则不替换
     */
    public static void exportBuiltin(OutputStream out, ProjReportTemplate template,
            List<ProjReportField> fields, List<Map<String, Object>> rows,
            String[] yearMonth) throws Exception
    {
        Workbook wb = openTemplate(template);
        try
        {
            Sheet sheet = wb.getSheetAt(0);
            // 标题中的年月实时替换（如"地下空间工程中心2026年7月…" → 筛选年月）
            replaceTitleYearMonth(wb, template, yearMonth);
            fillDataRows(wb, sheet, template, fields, rows, false);
            // 单位合并模板：同单位多条记录合并单位名称单元格；到账时间按模板类型
            // zdyw（只定未验）整组合并写汇总描述，byx（补验线）逐条显示
            if (isUnitMergeTemplate(template))
            {
                applyUnitMerge(sheet, template, fields, rows, isPayTimeSummaryTemplate(template));
            }
            // ★ 数据行行高自适应：按各列实际内容长度（结合列宽/合并区域宽度）估算
            // 所需行数，超长内容换行完整显示；短内容保持模板基础行高不变
            applyAutoRowHeight(wb, sheet, template, rows);
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
        // 遍历样式行所有单元格（含无字段定义的列，如"需预留/需补"，保证导出后边框完整）
        Row styleRow = sheet.getRow(dataZeroIdx);
        short savedHeight = (styleRow != null) ? styleRow.getHeight() : 300;
        // 基准字体：样式行第 1 列（序号列）的字体；数据行全列统一使用该字体，
        // 避免模板数据行各列字体大小/粗细参差（如 zdyw_report 存在 9/10/12pt 混用）导致导出不一致
        Font baseFont = null;
        if (styleRow != null)
        {
            Cell c0 = styleRow.getCell(0);
            if (c0 != null)
            {
                baseFont = wb.getFontAt(c0.getCellStyle().getFontIndex());
            }
        }
        Map<Integer, CellStyle> savedStyles = new HashMap<>();
        if (styleRow != null)
        {
            short lastCell = styleRow.getLastCellNum();
            for (int ci = 0; ci < lastCell; ci++)
            {
                Cell sc = styleRow.getCell(ci);
                if (sc != null)
                {
                    CellStyle style = wb.createCellStyle();
                    style.cloneStyleFrom(sc.getCellStyle());
                    if (baseFont != null)
                    {
                        style.setFont(baseFont);
                    }
                    // ★ 自动换行：配合导出后的行高自适应（applyAutoRowHeight），
                    // 超长内容可换行显示完整，短内容不受影响（仍单行显示）
                    style.setWrapText(true);
                    // key 统一为 1-based 列号（与 ProjReportField.columnIndex 一致）
                    savedStyles.put(ci + 1, style);
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

        // ★ 填充数据行：移除旧行 → 创建全新行 → 先全列复制样式 → 再按字段写值
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

            // 1) 所有列复制样式（含无字段列，保证边框/底纹完整）
            for (Map.Entry<Integer, CellStyle> e : savedStyles.entrySet())
            {
                Cell cell = row.createCell(e.getKey() - 1);
                cell.setCellStyle(e.getValue());
            }
            // 2) 字段列写值
            for (ProjReportField f : fields)
            {
                Integer colIdx = f.getColumnIndex();
                if (colIdx == null || colIdx <= 0)
                {
                    continue;
                }
                Cell cell = row.getCell(colIdx - 1);
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
            // 全列复制样式
            for (Map.Entry<Integer, CellStyle> e : savedStyles.entrySet())
            {
                Cell cell = sum.createCell(e.getKey() - 1);
                cell.setCellStyle(e.getValue());
            }
            for (ProjReportField f : fields)
            {
                Integer colIdx = f.getColumnIndex();
                if (colIdx == null || colIdx <= 0)
                {
                    continue;
                }
                int ci = colIdx - 1;
                Cell cell = sum.getCell(ci);
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
        // 「验线（2/3）」两列随模板对调赋值，合计行需跟随有值的一列：
        //   模板6 补验线：需补=到账×2/3（求和），需预留=空（不求和）
        //   其它模板(如模板1 zdyw)：需预留=到账×2/3（求和），需补=空（不求和）
        boolean isByx = f.getTemplateId() != null && f.getTemplateId() == 6L;
        if ("reservedAmount".equals(key))
        {
            return isByx ? "string" : "number";
        }
        if ("needSupplement".equals(key))
        {
            return isByx ? "number" : "string";
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
     * 标题年月实时替换：将标题单元格中形如"2026年7月"的文本替换为导出筛选年月。
     * 模板标题通常为合并单元格，值位于该行第一个有值的单元格。
     */
    private static void replaceTitleYearMonth(Workbook wb, ProjReportTemplate template, String[] yearMonth)
    {
        if (yearMonth == null || yearMonth.length < 2 || template.getTitleRow() == null
                || template.getTitleRow() <= 0)
        {
            return;
        }
        Row titleRow = wb.getSheetAt(0).getRow(template.getTitleRow() - 1);
        if (titleRow == null)
        {
            return;
        }
        String year = yearMonth[0];
        String month = yearMonth[1];
        for (Cell c : titleRow)
        {
            if (c.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING)
            {
                String text = c.getStringCellValue();
                if (text != null && text.matches(".*\\d{4}年\\d{1,2}月.*"))
                {
                    c.setCellValue(text.replaceAll("\\d{4}年\\d{1,2}月", year + "年" + month + "月"));
                }
            }
        }
    }

    /**
     * 单位合并（内置单位合并模板专用）：
     * 1. 单位名称列：同单位连续多条记录合并为一个单元格（保留首行值）
     * 2. 到账时间列（mergePayTimeSummary=true 时）：同单位整组合并，统一写汇总描述
     *    （如"2026年8月13日到账823元"，由 Service 层计算注入行键 _unitSummary，单条记录同样写汇总）；
     *    为 false 时到账时间保持逐条显示（补验线报表）
     */
    private static void applyUnitMerge(Sheet sheet, ProjReportTemplate template,
            List<ProjReportField> fields, List<Map<String, Object>> rows, boolean mergePayTimeSummary)
    {
        if (rows == null || rows.isEmpty())
        {
            return;
        }
        // 反查单位名称列 / 到账时间列（按字段 key + column_index）
        int unitCol = -1;
        int payTimeCol = -1;
        for (ProjReportField f : fields)
        {
            if (f.getColumnIndex() == null || f.getColumnIndex() <= 0)
            {
                continue;
            }
            if ("clientUnit".equals(f.getFieldKey()) && unitCol < 0)
            {
                unitCol = f.getColumnIndex() - 1;
            }
            else if ("lastPayTime".equals(f.getFieldKey()) && payTimeCol < 0)
            {
                payTimeCol = f.getColumnIndex() - 1;
            }
        }
        int dataZeroIdx = (template.getDataStartRow() == null ? 3 : template.getDataStartRow()) - 1;
        if (dataZeroIdx < 0)
        {
            dataZeroIdx = 0;
        }

        int i = 0;
        while (i < rows.size())
        {
            String unit = toStr(rows.get(i).get("clientUnit"));
            int end = i;
            while (end + 1 < rows.size() && eq(toStr(rows.get(end + 1).get("clientUnit")), unit))
            {
                end++;
            }
            int rowStart = dataZeroIdx + i;
            int rowEnd = dataZeroIdx + end;

            // 到账时间列：整组统一写汇总描述（单条记录同样处理；补验线模板逐条显示跳过）
            if (payTimeCol >= 0 && mergePayTimeSummary)
            {
                String summary = toStr(rows.get(i).get("_unitSummary"));
                for (int r = rowStart; r <= rowEnd; r++)
                {
                    Row rr = sheet.getRow(r);
                    if (rr != null)
                    {
                        Cell c = rr.getCell(payTimeCol);
                        if (c != null)
                        {
                            c.setCellValue(summary == null ? "" : summary);
                        }
                    }
                }
            }
            // 单位名称列：多条记录合并为一个单元格（保留首行值，其余行清空）
            if (unitCol >= 0 && end > i)
            {
                if (rowEnd > rowStart)
                {
                    sheet.addMergedRegion(new CellRangeAddress(rowStart, rowEnd, unitCol, unitCol));
                }
                for (int r = rowStart + 1; r <= rowEnd; r++)
                {
                    Row rr = sheet.getRow(r);
                    if (rr != null)
                    {
                        Cell c = rr.getCell(unitCol);
                        if (c != null)
                        {
                            c.setCellValue("");
                        }
                    }
                }
            }
            i = end + 1;
        }
    }

    /** 是否为单位合并模板（按模板文件关键字识别，与 Service 层保持一致） */
    private static boolean isUnitMergeTemplate(ProjReportTemplate template)
    {
        return template != null && template.getTemplateFile() != null
                && (template.getTemplateFile().toLowerCase().contains(UNIT_MERGE_TEMPLATE_KEYWORD)
                    || template.getTemplateFile().toLowerCase().contains(UNIT_MERGE_NO_PAY_SUMMARY_KEYWORD));
    }

    /** 到账时间是否按单位汇总（zdyw 汇总整组合并；byx 补验线逐条显示） */
    private static boolean isPayTimeSummaryTemplate(ProjReportTemplate template)
    {
        return template != null && template.getTemplateFile() != null
                && template.getTemplateFile().toLowerCase().contains(UNIT_MERGE_TEMPLATE_KEYWORD);
    }

    /**
     * 数据行行高自适应：逐行扫描各列内容，按列宽（含合并区域总宽）与基准字号估算
     * 所需显示行数，设置 `maxLines × 单行高度` 的行高，保证换行后内容完整可见。
     * 单行即可放下的内容保持模板基础行高不变。
     */
    private static void applyAutoRowHeight(Workbook wb, Sheet sheet, ProjReportTemplate template,
            List<Map<String, Object>> rows)
    {
        if (rows == null || rows.isEmpty())
        {
            return;
        }
        int dataZeroIdx = (template.getDataStartRow() == null ? 3 : template.getDataStartRow()) - 1;
        if (dataZeroIdx < 0)
        {
            dataZeroIdx = 0;
        }
        // 基础行高与基准字号（与 fillDataRows 保持一致：样式行第 1 列字体）
        Row styleRow = sheet.getRow(dataZeroIdx);
        short baseHeight = (styleRow != null) ? styleRow.getHeight() : 300;
        float fontSize = 12f;
        if (styleRow != null)
        {
            Cell c0 = styleRow.getCell(0);
            if (c0 != null)
            {
                fontSize = wb.getFontAt(c0.getCellStyle().getFontIndex()).getFontHeightInPoints();
            }
        }
        // 单行高度：1pt = 20 twip，1.35 行距系数（中文/全角留余量）
        short lineTwip = (short) Math.round(fontSize * 20 * 1.35);
        if (lineTwip < 300)
        {
            lineTwip = 300;
        }
        // 预取合并区域：单元格位于合并区域内时，按区域总列宽估算
        List<CellRangeAddress> merges = sheet.getMergedRegions();

        for (int i = 0; i < rows.size(); i++)
        {
            Row row = sheet.getRow(dataZeroIdx + i);
            if (row == null)
            {
                continue;
            }
            int maxLines = 1;
            short lastCell = row.getLastCellNum();
            for (int ci = 0; ci < lastCell; ci++)
            {
                Cell cell = row.getCell(ci);
                if (cell == null)
                {
                    continue;
                }
                String text = cellText(cell);
                if (text == null || text.isEmpty())
                {
                    continue;
                }
                // 列宽（字符单位，1/256 字符宽）：合并区域内按区域总宽
                int widthChars = sheet.getColumnWidth(ci) / 256;
                for (CellRangeAddress m : merges)
                {
                    if (m.isInRange(row.getRowNum(), ci))
                    {
                        widthChars = 0;
                        for (int cc = m.getFirstColumn(); cc <= m.getLastColumn(); cc++)
                        {
                            widthChars += sheet.getColumnWidth(cc) / 256;
                        }
                        break;
                    }
                }
                // 留出左右边距（约 1 字符），避免按满宽估算导致换行后仍溢出
                int lines = estimateWrapLines(text, Math.max(widthChars - 1, 2));
                if (lines > maxLines)
                {
                    maxLines = lines;
                }
            }
            if (maxLines > 1)
            {
                // 行高上限 4095 twip（Excel 限制约 204pt）
                row.setHeight((short) Math.min(Math.max(baseHeight, maxLines * lineTwip), 4095));
            }
        }
    }

    /** 单元格显示文本（供行高估算）：字符串取原文，数值去科学计数法，日期按固定 10 字符估 */
    private static String cellText(Cell cell)
    {
        if (cell == null)
        {
            return "";
        }
        CellType type = cell.getCellType();
        if (type == CellType.STRING)
        {
            return cell.getStringCellValue();
        }
        if (type == CellType.NUMERIC)
        {
            if (DateUtil.isCellDateFormatted(cell))
            {
                return "yyyy-MM-dd"; // 日期格式固定 10 字符
            }
            double d = cell.getNumericCellValue();
            return new BigDecimal(Double.toString(d)).stripTrailingZeros().toPlainString();
        }
        if (type == CellType.BOOLEAN)
        {
            return Boolean.toString(cell.getBooleanCellValue());
        }
        return "";
    }

    /**
     * 估算文本在指定列宽（单位：英文字符）下换行所需行数。
     * 中文/全角字符按 2 个英文字符宽计；显式换行符（\n）强制换行。
     */
    private static int estimateWrapLines(String text, int widthChars)
    {
        if (text == null || text.isEmpty() || widthChars <= 0)
        {
            return 1;
        }
        int lines = 0;
        String[] segs = text.split("\n", -1);
        for (String seg : segs)
        {
            lines += estimateSegmentLines(seg, widthChars);
        }
        return Math.max(lines, 1);
    }

    /** 单段文本（不含显式换行）在指定列宽下的行数 */
    private static int estimateSegmentLines(String seg, int widthChars)
    {
        if (seg.isEmpty())
        {
            return 1;
        }
        double w = 0;
        int lines = 1;
        for (int i = 0; i < seg.length(); i++)
        {
            char c = seg.charAt(i);
            double cw = (c > 0x7F) ? 2.0 : 1.0;
            if (w + cw > widthChars)
            {
                lines++;
                w = cw;
            }
            else
            {
                w += cw;
            }
        }
        return lines;
    }

    private static String toStr(Object o)
    {
        return o == null ? null : o.toString();
    }

    private static boolean eq(String a, String b)
    {
        return a == null ? b == null : a.equals(b);
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
