package com.xakcch.project.report;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
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
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
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
 *   <li>表头：蓝底（{@code #4472C4}）白字 + 加粗 + 水平垂直居中 + 细边框</li>
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

    /** 表头底色：Excel 表格蓝 #4472C4（R,G,B） */
    private static final byte[] HEADER_RGB = new byte[]{(byte) 0x44, (byte) 0x72, (byte) 0xC4};

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
     * 二级表头子列（即最终数据列）
     */
    public static class Sub
    {
        private final String label;

        private final int width;

        private final boolean numeric;

        public Sub(String label, int width, boolean numeric)
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
     * 两级表头分组块
     *
     * <p>第 1 行放「一级表头」，第 2 行放「二级表头」。两种形态：</p>
     * <ul>
     *   <li>{@link #single(String, int, boolean)} 普通列 —— 竖向跨第 1~2 行合并，
     *       用于项目字段列、合计列这些不属于任何分组的独立列；</li>
     *   <li>{@link #group(String, List)} 分组列 —— 一级表头横向跨 N 列（N = 子列数），
     *       第 2 行逐个展开子列名，用于「外部工作量 / 外部单价 / …」这类一对多的列组。</li>
     * </ul>
     */
    public static class Block
    {
        /** 一级表头文案 */
        private final String label;

        private final List<Sub> subs;

        /** true = 竖向跨两行合并（普通列）；false = 横向跨 N 列（分组列） */
        private final boolean verticalMerge;

        private Block(String label, List<Sub> subs, boolean verticalMerge)
        {
            this.label = label == null ? "" : label;
            this.subs = subs;
            this.verticalMerge = verticalMerge;
        }

        /** 普通列：竖向跨第 1~2 行合并 */
        public static Block single(String label, int width, boolean numeric)
        {
            return new Block(label, Collections.singletonList(new Sub(label, width, numeric)), true);
        }

        /** 分组列：一级表头横向跨 subs.size() 列，第 2 行展开子列名 */
        public static Block group(String label, List<Sub> subs)
        {
            return new Block(label, subs == null ? Collections.emptyList() : subs, false);
        }

        public String getLabel()
        {
            return label;
        }

        public List<Sub> getSubs()
        {
            return subs;
        }

        public boolean isVerticalMerge()
        {
            return verticalMerge;
        }
    }

    /**
     * 导出动态列 Excel（单级表头）
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

    /**
     * 导出两级表头 Excel（工作量明细列式展开用）
     *
     * <p>与 {@link #export} 完全独立的一条代码路径：单级表头的 {@link #export} 行为一字未改，
     * 已经接入的页面（项目列表、合同列表等）不受影响。</p>
     *
     * <p>表格形态：</p>
     * <pre>
     * 第 1 行  工程编号 | 项目名称 |   外部工作量   |   外部产值   | 外部产值合计
     * 第 2 行  （合并区）|（合并区）| 常规(起2)|加急 | 常规 | 加急 | （合并区）
     * 第 3 行   数据 …
     * </pre>
     *
     * @param out        输出流（响应体）
     * @param sheetName  工作表名（各页面按自身业务命名）
     * @param blocks     列块（按从左到右顺序，决定最终列顺序）
     * @param freezeCols 冻结的列数（用于横向滚动时项目标识列常驻）；0 = 不冻结列
     * @param rows       数据行；每行按 blocks 展开后的列顺序存放显示值（String / Number / Date / null），
     *                   null 一律留空
     * @throws IOException 写流失败
     */
    public static void exportWithGroups(OutputStream out, String sheetName, List<Block> blocks,
                                        int freezeCols, List<List<Object>> rows) throws IOException
    {
        Workbook wb = new XSSFWorkbook();
        try
        {
            String name = (sheetName == null || sheetName.trim().isEmpty()) ? "Sheet1" : sheetName.trim();
            Sheet sheet = wb.createSheet(name);
            CellStyle headerStyle = buildHeaderStyle(wb);
            CellStyle textStyle = buildDataStyle(wb, false);
            CellStyle numStyle = buildDataStyle(wb, true);
            CellStyle dateStyle = buildDateStyle(wb);

            // 先按块展开出「最终列」清单：列顺序 = blocks 顺序 + 块内子列顺序
            List<Integer> widths = new ArrayList<>();
            List<Boolean> numerics = new ArrayList<>();
            if (blocks != null)
            {
                for (Block b : blocks)
                {
                    for (Sub s : b.getSubs())
                    {
                        widths.add(s.getWidth());
                        numerics.add(s.isNumeric());
                    }
                }
            }
            int colCount = widths.size();

            Row row1 = sheet.createRow(0);
            row1.setHeight(HEADER_HEIGHT);
            Row row2 = sheet.createRow(1);
            row2.setHeight(HEADER_HEIGHT);

            int col = 0;
            for (Block b : (blocks == null ? Collections.<Block>emptyList() : blocks))
            {
                int span = b.getSubs().size();
                if (span <= 0)
                {
                    continue;
                }
                // 第 1 行：一级表头（横向跨 span 列；普通列再竖向跨到第 2 行）
                Cell c1 = row1.createCell(col);
                c1.setCellValue(b.getLabel());
                c1.setCellStyle(headerStyle);
                // 被合并的后续单元格也要建出来并套样式，否则合并区边框会缺一块
                for (int k = 1; k < span; k++)
                {
                    row1.createCell(col + k).setCellStyle(headerStyle);
                }
                sheet.addMergedRegion(new CellRangeAddress(0, b.isVerticalMerge() ? 1 : 0, col, col + span - 1));

                if (!b.isVerticalMerge())
                {
                    // 第 2 行：展开子列名
                    for (int k = 0; k < span; k++)
                    {
                        Cell c2 = row2.createCell(col + k);
                        c2.setCellValue(b.getSubs().get(k).getLabel());
                        c2.setCellStyle(headerStyle);
                    }
                }
                col += span;
            }

            for (int i = 0; i < colCount; i++)
            {
                sheet.setColumnWidth(i, Math.min(widths.get(i) * 256, MAX_WIDTH));
            }
            // 冻结前两行表头 + 指定列数，横向滚动时项目标识列常驻
            sheet.createFreezePane(Math.max(0, Math.min(freezeCols, colCount)), 2);

            if (rows != null)
            {
                int rowIdx = 2;
                for (List<Object> row : rows)
                {
                    Row r = sheet.createRow(rowIdx++);
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

    /** 单行明细文本最多展示的行数（超出截断，避免个别数据把行高撑爆） */
    private static final int MAX_WRAP_LINES = 40;

    /** 换行文本列每行高度（1/20 磅）：15 磅 ≈ 300，与 Excel 默认行高一致 */
    private static final short WRAP_LINE_HEIGHT = 300;

    /**
     * 单级表头列定义（支持「单元格内换行」的文本列）
     *
     * <p>与 {@link Column} 的区别：多了 wrap 标志。wrap = true 的列按单元格内换行显示，
     * 行高按该行实际行数自适应，用于「一个单元格里逐行列出工作量明细」这类场景。</p>
     */
    public static class WrapColumn
    {
        private final String label;

        private final int width;

        private final boolean numeric;

        /** true = 单元格内换行显示 + 行高自适应 */
        private final boolean wrap;

        private WrapColumn(String label, int width, boolean numeric, boolean wrap)
        {
            this.label = label == null ? "" : label;
            this.width = width <= 0 ? DEFAULT_WIDTH : width;
            this.numeric = numeric;
            this.wrap = wrap;
        }

        /** 普通文本列 */
        public static WrapColumn text(String label, int width)
        {
            return new WrapColumn(label, width, false, false);
        }

        /** 换行文本列（单元格内按 \n 逐行显示） */
        public static WrapColumn wrap(String label, int width)
        {
            return new WrapColumn(label, width, false, true);
        }

        /** 数值列（右对齐） */
        public static WrapColumn number(String label, int width)
        {
            return new WrapColumn(label, width, true, false);
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

        public boolean isWrap()
        {
            return wrap;
        }
    }

    /**
     * 导出单级表头 Excel（支持单元格内换行的文本列）
     *
     * <p>与 {@link #export}、{@link #exportWithGroups} 完全独立的一条代码路径，
     * 既有导出（项目列表 / 合同列表）的行为一字未改。</p>
     *
     * <p>换行列要点：POI 里只设 {@code setWrapText(true)} 并不会撑高行 —— Excel 会把文字裁掉，
     * 必须按该行实际行数手动 {@code setRowHeight}，否则导出来「看着只有一行」。</p>
     *
     * @param out        输出流（响应体）
     * @param sheetName  工作表名
     * @param columns    列定义（按导出顺序）
     * @param freezeCols 冻结的列数（横向滚动时项目标识列常驻）；0 = 不冻结列
     * @param rows       数据行；每行按 columns 顺序存放显示值；null / 空串一律留空
     * @throws IOException 写流失败
     */
    public static void exportWithWrap(OutputStream out, String sheetName, List<WrapColumn> columns,
                                      int freezeCols, List<List<Object>> rows) throws IOException
    {
        Workbook wb = new XSSFWorkbook();
        try
        {
            String name = (sheetName == null || sheetName.trim().isEmpty()) ? "Sheet1" : sheetName.trim();
            Sheet sheet = wb.createSheet(name);
            CellStyle headerStyle = buildHeaderStyle(wb);
            CellStyle textStyle = buildDataStyle(wb, false);
            CellStyle numStyle = buildDataStyle(wb, true);
            CellStyle dateStyle = buildDateStyle(wb);
            CellStyle wrapStyle = buildWrapStyle(wb);

            int colCount = columns == null ? 0 : columns.size();

            Row headerRow = sheet.createRow(0);
            headerRow.setHeight(HEADER_HEIGHT);
            for (int i = 0; i < colCount; i++)
            {
                WrapColumn col = columns.get(i);
                Cell c = headerRow.createCell(i);
                c.setCellValue(col.getLabel());
                c.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, Math.min(col.getWidth() * 256, MAX_WIDTH));
            }
            // 冻结表头行 + 指定列数
            sheet.createFreezePane(Math.max(0, Math.min(freezeCols, colCount)), 1);

            if (rows != null)
            {
                int rowIdx = 1;
                for (List<Object> row : rows)
                {
                    Row r = sheet.createRow(rowIdx++);
                    int lines = 1;
                    for (int i = 0; i < colCount; i++)
                    {
                        Object v = (row != null && i < row.size()) ? row.get(i) : null;
                        Cell c = r.createCell(i);
                        if (columns.get(i).isWrap())
                        {
                            writeWrapCell(c, v, wrapStyle, numStyle, dateStyle);
                            if (v instanceof String)
                            {
                                lines = Math.max(lines, countLines((String) v));
                            }
                        }
                        else
                        {
                            writeCell(c, v, textStyle, numStyle, dateStyle);
                        }
                    }
                    // 行高按本行最多的明细行数自适应（换行文本列不撑高就会被裁掉）
                    r.setHeight((short) (Math.min(lines, MAX_WRAP_LINES) * WRAP_LINE_HEIGHT));
                }
            }
            wb.write(out);
        }
        finally
        {
            wb.close();
        }
    }

    /** 换行文本列写值：文本走换行样式，数值 / 日期仍按各自样式（这类列原则上只有文本） */
    private static void writeWrapCell(Cell c, Object v, CellStyle wrapStyle, CellStyle numStyle, CellStyle dateStyle)
    {
        if (v == null)
        {
            c.setCellStyle(wrapStyle);
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
        c.setCellStyle(wrapStyle);
    }

    /** 文本中的行数：按 \n 计数 + 1；空串算 1 行 */
    private static int countLines(String s)
    {
        if (s == null || s.isEmpty())
        {
            return 1;
        }
        int n = 1;
        for (int i = 0; i < s.length(); i++)
        {
            if (s.charAt(i) == '\n')
            {
                n++;
            }
        }
        return n;
    }

    /** 换行文本样式：自动换行 + 顶端对齐（多行文本从顶部往下排才好看）+ 左对齐 + 细边框 */
    private static CellStyle buildWrapStyle(Workbook wb)
    {
        CellStyle s = wb.createCellStyle();
        s.setWrapText(true);
        s.setVerticalAlignment(VerticalAlignment.TOP);
        s.setAlignment(HorizontalAlignment.LEFT);
        applyBorder(s);
        return s;
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
     * 表头样式：蓝底白字 + 加粗 + 居中 + 细边框
     *
     * <p>原为「25% 灰」（{@code IndexedColors.GREY_25_PERCENT}，约 #C0C0C0）黑字，观感发脏，
     * 2026-10-20 按用户反馈改为 Excel 经典表格蓝 {@code #4472C4} + 白色加粗字。</p>
     *
     * <p>⚠️ 该样式为三条导出路径（{@link #export} / {@link #exportWithGroups} / {@link #exportWithWrap}）
     * 共用，改这里会同时影响项目列表、合同列表与费用结算的导出外观（这是有意为之：保持全系统一致）。</p>
     */
    private static CellStyle buildHeaderStyle(Workbook wb)
    {
        CellStyle s = wb.createCellStyle();
        // xlsx 用真彩（XSSFColor）而不是调色板索引：索引色里没有合适的蓝
        if (s instanceof XSSFCellStyle)
        {
            ((XSSFCellStyle) s).setFillForegroundColor(new XSSFColor(HEADER_RGB, null));
        }
        else
        {
            s.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
        }
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        applyBorder(s);
        Font f = wb.createFont();
        f.setBold(true);
        f.setColor(IndexedColors.WHITE.getIndex());
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
