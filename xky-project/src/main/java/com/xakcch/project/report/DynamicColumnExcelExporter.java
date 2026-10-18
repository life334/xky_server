package com.xakcch.project.report;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * 动态列 Excel 导出器（项目列表「选择字段导出」用）
 *
 * <p>与报表导出（{@link ReportExcelExporter}）的区别：报表依赖模板文件克隆样式，
 * 本导出器不依赖任何模板，按调用方给定的「列定义 + 行数据」直接生成 xlsx，
 * 适用于列表页这种列结构由用户运行时决定的场景。</p>
 *
 * <p>导出规格（与用户确认）：</p>
 * <ul>
 *   <li>表头：灰底（{@code IndexedColors.GREY_25_PERCENT}）+ 加粗 + 水平垂直居中 + 细边框</li>
 *   <li>数据行：细边框 + 垂直居中；文本左对齐 / 数值右对齐；日期统一 yyyy-MM-dd</li>
 *   <li>列宽：按列定义给定宽度（单位：字符数），上限 12000（1/256 字符）</li>
 *   <li>无标题行：第一行即表头，便于直接作为数据表使用（筛选 / 透视 / 导入）</li>
 *   <li>冻结窗格：冻结表头行（第 1 行），长表滚动时表头常驻</li>
 * </ul>
 *
 * @author liuyonghui
 */
public class DynamicColumnExcelExporter
{
    /** 默认列宽（字符数） */
    private static final int DEFAULT_WIDTH = 16;

    /** Excel 单列最大宽度（1/256 字符） */
    private static final int MAX_WIDTH = 12000;

    /** 表头行高（1/20 磅） */
    private static final short HEADER_HEIGHT = 380;

    /**
     * 列定义
     */
    public static class Column
    {
        /** 表头文案 */
        private final String label;

        /** 列宽（字符数） */
        private final int width;

        /** 是否数值列（右对齐） */
        private final boolean numeric;

        public Column(String label, int width, boolean numeric)
        {
            this.label = label == null ? "" : label;
            this.width = width <= 0 ? DEFAULT_WIDTH : width;
            this.numeric = numeric;
        }

        public String getLabel()
        {
            return label;
        }

        public int getWidth()
        {
            return width;
        }

        public boolean isNumeric()
        {
            return numeric;
        }
    }

    /**
     * 导出动态列 Excel
     *
     * @param out     输出流（响应体）
     * @param columns 列定义（按导出顺序）
     * @param rows    数据行；每行按 columns 顺序存放显示值（String / Number / Date / null）
     * @throws IOException 写流失败
     */
    public static void export(OutputStream out, List<Column> columns, List<List<Object>> rows)
            throws IOException
    {
        Workbook wb = new XSSFWorkbook();
        try
        {
            Sheet sheet = wb.createSheet("项目列表");
            CellStyle headerStyle = buildHeaderStyle(wb);
            CellStyle textStyle = buildDataStyle(wb, false);
            CellStyle numStyle = buildDataStyle(wb, true);
            CellStyle dateStyle = buildDateStyle(wb);

            int colCount = columns == null ? 0 : columns.size();

            // 表头行（第 1 行即表头，不输出标题行）
            Row headerRow = sheet.createRow(0);
            headerRow.setHeight(HEADER_HEIGHT);
            for (int i = 0; i < colCount; i++)
            {
                Column col = columns.get(i);
                Cell c = headerRow.createCell(i);
                c.setCellValue(col.getLabel());
                c.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, Math.min(col.getWidth() * 256, MAX_WIDTH));
            }
            // 冻结表头行（第 1 行）常驻
            sheet.createFreezePane(0, 1);

            // 数据行
            if (rows != null)
            {
                for (List<Object> row : rows)
                {
                    Row r = sheet.createRow(sheet.getLastRowNum() + 1);
                    for (int i = 0; i < colCount; i++)
                    {
                        Object v = (row != null && i < row.size()) ? row.get(i) : null;
                        Cell c = r.createCell(i);
                        writeCell(c, v, textStyle, numStyle, dateStyle);
                    }
                }
            }
            wb.write(out);
        }
        finally
        {
            wb.close();
        }
    }

    /** 按值类型写单元格并套用对应样式 */
    private static void writeCell(Cell c, Object v, CellStyle textStyle, CellStyle numStyle, CellStyle dateStyle)
    {
        if (v == null)
        {
            c.setCellStyle(textStyle);
            return;
        }
        if (v instanceof Date)
        {
            c.setCellValue((Date) v);
            c.setCellStyle(dateStyle);
            return;
        }
        if (v instanceof Number)
        {
            c.setCellValue(((Number) v).doubleValue());
            c.setCellStyle(numStyle);
            return;
        }
        c.setCellValue(String.valueOf(v));
        c.setCellStyle(textStyle);
    }

    /**
     * 表头样式：灰底 + 加粗 + 居中 + 细边框
     *
     * <p>⚠️ 注意 POI 有两套别名：{@code BLACK1}(0) 与 {@code BLACK}(8) 相差 8。
     * xlsx 调色板的前 8 位（0~7）是系统保留色，颜色段从 8 起算，
     * 因此「25% 灰」的正确索引是 22（= {@code GREY_25_PERCENT}，约 #C0C0C0），
     * 不要按 0 起算写成 14。</p>
     */
    private static CellStyle buildHeaderStyle(Workbook wb)
    {
        CellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        applyBorder(s);
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        return s;
    }

    /** 数据行样式：细边框 + 垂直居中 + 文本左 / 数值右 */
    private static CellStyle buildDataStyle(Workbook wb, boolean numeric)
    {
        CellStyle s = wb.createCellStyle();
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setAlignment(numeric ? HorizontalAlignment.RIGHT : HorizontalAlignment.LEFT);
        applyBorder(s);
        return s;
    }

    /** 日期列样式：yyyy-MM-dd + 居中 + 细边框 */
    private static CellStyle buildDateStyle(Workbook wb)
    {
        CellStyle s = wb.createCellStyle();
        DataFormat fmt = wb.createDataFormat();
        s.setDataFormat(fmt.getFormat("yyyy-MM-dd"));
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setAlignment(HorizontalAlignment.CENTER);
        applyBorder(s);
        return s;
    }

    /** 四周细边框 */
    private static void applyBorder(CellStyle s)
    {
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        s.setTopBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        s.setBottomBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        s.setLeftBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        s.setRightBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
    }
}
