package com.xakcch.web.controller.project;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.xakcch.common.annotation.Log;
import com.xakcch.common.constant.HttpStatus;
import com.xakcch.common.core.controller.BaseController;
import com.xakcch.common.core.domain.AjaxResult;
import com.xakcch.common.core.page.TableDataInfo;
import com.xakcch.common.enums.BusinessType;
import com.xakcch.common.exception.ServiceException;
import com.xakcch.common.utils.DateUtils;
import com.xakcch.common.utils.StringUtils;
import com.xakcch.common.utils.file.FileUtils;
import com.xakcch.project.domain.ProjContractPrice;
import com.xakcch.project.domain.ProjPayment;
import com.xakcch.project.domain.ProjProject;
import com.xakcch.project.domain.ProjWorkload;
import com.xakcch.project.mapper.ProjCategoryBillingMapper;
import com.xakcch.project.mapper.ProjContractPriceMapper;
import com.xakcch.project.mapper.ProjLeaderMapper;
import com.xakcch.project.mapper.ProjPaymentMapper;
import com.xakcch.project.mapper.ProjWorkloadMapper;
import com.xakcch.project.report.DynamicColumnExcelExporter;
import com.xakcch.project.service.IProjProjectService;

/**
 * 费用结算 控制层
 *
 * @author liuyonghui
 */
@RestController
@RequestMapping("/project/settlement")
public class ProjSettlementController extends BaseController
{
    @Autowired
    private IProjProjectService projectService;

    @Autowired
    private ProjWorkloadMapper workloadMapper;

    @Autowired
    private ProjPaymentMapper paymentMapper;

    @Autowired
    private ProjContractPriceMapper contractPriceMapper;

    @Autowired
    private ProjLeaderMapper leaderMapper;

    @Autowired
    private ProjCategoryBillingMapper categoryBillingMapper;

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd");

    /** 无计费类别的工作量归入此类别，保证明细不丢行 */
    private static final String UNCATEGORIZED = "未分类";

    /** 内部产值保底涉及的两个计费类别（与 calcInternalOutputTotal 同口径） */
    private static final String CAT_REVISE_NEW = "管线新测";

    private static final String CAT_REVISE_FIX = "管线修测";

    /**
     * 导出时不单独成列的字段。
     * <ul>
     *   <li>{@code workload} 内外工作量单位不同，相加无业务含义，且信息已被工作量明细文本承载</li>
     *   <li>{@code internalPrice} / {@code externalPrice} 按用户要求，单价不导出</li>
     * </ul>
     */
    private static final Set<String> EXPORT_AGG_KEYS = new HashSet<>(Arrays.asList(
            "workload", "internalPrice", "externalPrice"));

    /** 导出为「单元格内多行文本」的工作量列（不再是数字） */
    private static final Set<String> WORKLOAD_TEXT_KEYS = new HashSet<>(Arrays.asList(
            "internalWorkload", "externalWorkload"));

    /** 负责人（工作量登记人）列 key：按登记人拆行后每行的区分依据，由用户勾选决定是否出列 */
    private static final String KEY_USER_NAME = "userName";

    /** 工作量明细文本列的列宽（字符数）：要能容下「管线探测（含数据）：12.5千米」 */
    private static final int WORKLOAD_TEXT_WIDTH = 30;

    /** 单个单元格最多列出的明细行数（超出截断并追加「…等 N 项」，防止个别项目把行高撑爆） */
    private static final int MAX_DETAIL_LINES = 30;

    /** user_id 为空的工作量的分组键 */
    private static final String NULL_USER_KEY = "#NULL#";

    /** user_id 为空的工作量在「负责人」列显示的名字 */
    private static final String UNKNOWN_USER_NAME = "未指定";

    /** 「全部」量级阈值：pageSize 达到该值时不分页（与前端 Pagination 组件 ALL_SIZE 一致） */
    private static final int ALL_SIZE = 100000;

    /** 单次导出条数上限（与项目列表导出保持一致） */
    private static final int EXPORT_MAX_ROWS = 50000;

    /** 开票状态码 → 中文：与前端 utils/projStatus.js 的 INVOICE_STATUS_MAP 保持一致 */
    private static final Map<String, String> INVOICE_STATUS_TEXT = buildInvoiceStatusText();

    private static Map<String, String> buildInvoiceStatusText()
    {
        Map<String, String> m = new HashMap<>();
        m.put("invoiced", "已开");
        m.put("pending", "未开");
        m.put("voided", "已作废");
        m.put("cancelled", "已作废");
        m.put("canceled", "已作废");
        m.put("invalid", "已作废");
        return Collections.unmodifiableMap(m);
    }

    /**
     * 查询费用结算列表可显隐列的元数据（显隐列面板 + 表格动态渲染用）
     * 树形表格由 Controller 组装三级节点（项目级/人员级/叶子级），此处返回固定列清单
     */
    @GetMapping("/columns")
    public AjaxResult columns()
    {
        return success(buildColumnMeta());
    }

    /**
     * 列元数据唯一出处：显隐列面板、表格动态渲染、导出表头三者共用，避免分叉。
     */
    private List<Map<String, Object>> buildColumnMeta()
    {
        List<Map<String, Object>> columns = new ArrayList<>();
        // 项目级 + 人员级 + 叶子级通用字段（默认可见性 = 当前页面展示列）
        addColumn(columns, "projectCode", "工程编号", "text", true, "projectCode");
        addColumn(columns, "relatedProjectCode", "关联定线", "text", true, "relatedProjectCode");
        addColumn(columns, "projectName", "项目名称", "text", false, "projectName");
        addColumn(columns, "clientUnit", "委托单位", "text", true, "clientUnit");
        addColumn(columns, "projectLocation", "工程地点", "text", true, "projectLocation");
        // 「负责人」= 工作量登记人（导出按它拆行，页面展开行里的「负责人」也是它）。
        // 项目级 leaderNames 已从清单中移除：与它同名易混淆，且拆行后每行都一样、没有信息量。
        addColumn(columns, "userName", "负责人", "text", false, "userName");
        // 项目类别：2026-10-20 起默认可见（需求：费用结算页要能直接看到项目类别）
        addColumn(columns, "categoryName", "项目类别", "text", true, "categoryName");
        // 工作量与其产值成对排列（内部工作量→内部产值→外部工作量→外部产值），便于左右对照
        addColumn(columns, "internalWorkload", "内部工作量", "number", true, "internalWorkload");
        addColumn(columns, "internalOutput", "内部产值", "money", true, "internalOutput");
        addColumn(columns, "externalWorkload", "外部工作量", "number", true, "externalWorkload");
        addColumn(columns, "externalOutput", "外部产值", "money", true, "externalOutput");
        addColumn(columns, "workload", "工作量", "number", false, "workload");
        addColumn(columns, "internalPrice", "内部单价", "money", false, "internalPrice");
        addColumn(columns, "externalPrice", "外部单价", "money", false, "externalPrice");
        addColumn(columns, "prepayAmount", "预付款", "money", true, "prepayAmount");
        addColumn(columns, "prepayDate", "预付款时间", "date", true, "prepayDate");
        addColumn(columns, "payUnit", "付款单位", "text", true, "payUnit");
        addColumn(columns, "payMethod", "付款方式", "text", true, "payMethod");
        addColumn(columns, "tailAmount", "尾款", "money", true, "tailAmount");
        addColumn(columns, "tailDate", "尾款时间", "date", true, "tailDate");
        addColumn(columns, "refundAmount", "退款金额", "money", true, "refundAmount");
        addColumn(columns, "refundDate", "退款时间", "date", true, "refundDate");
        addColumn(columns, "invoiceStatus", "开票状态", "text", true, "invoiceStatus");
        addColumn(columns, "invoiceUnit", "开票单位", "text", true, "invoiceUnit");
        addColumn(columns, "invoiceAmount", "开票金额", "money", true, "invoiceAmount");
        addColumn(columns, "payRemark", "备注", "text", true, "payRemark");
        return columns;
    }

    /** 组装单列元数据 */
    private void addColumn(List<Map<String, Object>> columns, String key, String label,
                           String type, boolean defaultVisible, String prop)
    {
        Map<String, Object> col = new HashMap<>();
        col.put("key", key);
        col.put("label", label);
        col.put("type", type);
        col.put("group", "business");
        col.put("defaultVisible", defaultVisible);
        col.put("prop", prop);
        columns.add(col);
    }

    /**
     * 查询费用结算树形列表（后端分页）
     * @param projectStatus 项目状态过滤，多个逗号分隔；默认"已办结,已归档"，传"all"显示全部
     * @param pageNum 页码（默认 1）
     * @param pageSize 每页条数；传 100000 表示「全部」（不分页）
     */
    @GetMapping("/treeList")
    public TableDataInfo treeList(ProjProject project,
        @RequestParam(required = false) String projectStatus,
        @RequestParam(required = false) Integer pageNum,
        @RequestParam(required = false) Integer pageSize)
    {
        if (projectStatus == null || projectStatus.isEmpty())
        {
            projectStatus = "closed,archived";
        }
        if (!"all".equals(projectStatus))
        {
            List<String> statusList = Arrays.asList(projectStatus.split(","));
            project.getParams().put("statusList", statusList);
        }

        int num = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;
        // 「全部」量级（与前端 Pagination 组件 ALL_SIZE 约定一致）→ 不做分页，一次返回全部
        boolean paginate = size < ALL_SIZE;

        List<ProjProject> projects;
        long total;
        if (paginate)
        {
            PageHelper.startPage(num, size);
            projects = projectService.selectProjectList(project);
            total = new PageInfo<>(projects).getTotal();
        }
        else
        {
            projects = projectService.selectProjectList(project);
            total = projects.size();
        }

        // 一次性批量查询本页项目的工作量与付款，避免逐项目 N+1 查询
        Long[] projectIds = projects.stream().map(ProjProject::getId).toArray(Long[]::new);
        Map<Long, List<ProjWorkload>> workloadMap = projectIds.length == 0 ? Collections.emptyMap()
            : workloadMapper.selectWorkloadsByProjectIds(projectIds).stream()
                .collect(Collectors.groupingBy(ProjWorkload::getProjectId));
        Map<Long, List<ProjPayment>> paymentMap = projectIds.length == 0 ? Collections.emptyMap()
            : paymentMapper.selectPaymentsByProjectIds(projectIds).stream()
                .collect(Collectors.groupingBy(ProjPayment::getProjectId));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (ProjProject p : projects)
        {
            rows.add(buildSettlementNode(p, workloadMap, paymentMap));
        }

        TableDataInfo rspData = new TableDataInfo();
        rspData.setCode(HttpStatus.SUCCESS);
        rspData.setMsg("查询成功");
        rspData.setRows(rows);
        rspData.setTotal(total);
        return rspData;
    }

    /**
     * 导出费用结算列表（可选范围 + 可选字段；一个项目 × 一个内部栏 = 一行）
     *
     * <p>form 参数：
     * <ul>
     *   <li>{@code fields}   列 key 逗号串，按导出顺序；为空时回退为「默认可见列」</li>
     *   <li>{@code scope}    {@code filter}（默认，全部筛选结果）/ {@code selected}（仅已勾选的行）</li>
     *   <li>{@code ids}      {@code scope=selected} 时的项目 id 逗号串</li>
     *   <li>{@code projectStatus} 项目状态过滤（逗号分隔 / all），默认与列表一致「已办结,已归档」</li>
     * </ul>
     *
     * <p><b>行粒度</b>：只看<b>内部</b>——按「栏」= 负责人 + 子项（{@code user_id} + {@code sub_item_no}）
     * 拆行，一栏一行（保底本来就是按栏算的，不拆开对不上账）。<b>外部不参与拆行</b>：整个项目的外部
     * 工作量合成一份，放在该项目的<b>第一行</b>（其余行留空，避免同一笔外部产值被多行重复求和）。
     * 只有外部没内部的项目 → 一项目一行，负责人取外部工作量的登记人；一条工作量都没有的项目
     * 仍保留一行（明细留空），不丢项目。项目级字段每行重复写满，便于在 Excel 里筛选 / 透视。</p>
     *
     * <p><b>工作量列</b>（internalWorkload / externalWorkload）不是数字，而是一个单元格内
     * 按工作量行逐条换行的文本，形如 {@code 管线探测（含数据）：6.64千米}；同一计费类别多条就重复列多行，
     * 不做合并（重复行看起来完全一样是正常的，它们的产值已分别计入）。不写子项名后缀，也不标起步量。</p>
     *
     * <p><b>产值列</b>：外部产值是项目外部合计（只写在第一行）；内部产值是该栏的产值，
     * 按「栏」套用保底 6000 —— 与页面合计完全同源：触发保底的栏直接是 6000（未触发就是实际值），
     * 不再另起「保底补差」行；因此同一项目各行求和 == 页面上的项目产值合计。</p>
     *
     * <p>⚠️ 字段清单以 {@link #buildColumnMeta()} 为唯一出处，未知 key 一律忽略
     *    （既防越权导出内部字段，也防参数拼错导致空列）。</p>
     */
    @PreAuthorize("@ss.hasPermi('project:settlement:export')")
    @Log(title = "费用结算", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, ProjProject project,
                       @RequestParam(required = false) String projectStatus,
                       @RequestParam(required = false) String fields,
                       @RequestParam(required = false) String scope,
                       @RequestParam(required = false) String ids) throws IOException
    {
        List<ProjProject> projects = resolveExportProjects(project, projectStatus, scope, ids);

        List<String> fieldKeys = splitFields(fields);
        if (fieldKeys.isEmpty())
        {
            // 未指定字段 → 回退为默认可见列（与首次进入页面的表格一致）
            for (Map<String, Object> c : buildColumnMeta())
            {
                if (Boolean.TRUE.equals(c.get("defaultVisible")))
                {
                    fieldKeys.add(String.valueOf(c.get("key")));
                }
            }
        }
        // 注意：不再自动补「负责人」列 —— 列集合与顺序完全按弹窗里勾选的字段来，
        // 勾了就在、没勾就不出，位置也严格跟随拖拽结果（此前会在第 1 列凭空多出一列）。
        // 没勾「负责人」时，拆出的多行靠工作量明细与产值区分。

        // 与 treeList 同口径：一次性批量查询工作量与付款，避免逐项目 N+1
        Map<Long, List<ProjWorkload>> workloadMap = loadWorkloadMap(projects);
        Map<Long, List<ProjPayment>> paymentMap = loadPaymentMap(projects);

        // 计费类别排序字典：控制明细文本的逐行顺序（查不到退化为按类别名排序）
        Map<String, Integer> catOrder = loadCategoryOrder();

        // 表头：项目字段列（单价 / 总工作量列自动剔除）+ 工作量文本列 + 该登记人产值列
        Map<String, Map<String, Object>> metaMap = new LinkedHashMap<>();
        for (Map<String, Object> c : buildColumnMeta())
        {
            metaMap.put(String.valueOf(c.get("key")), c);
        }
        List<DynamicColumnExcelExporter.WrapColumn> columns = new ArrayList<>();
        List<String> validKeys = new ArrayList<>();
        for (String key : fieldKeys)
        {
            Map<String, Object> meta = metaMap.get(key);
            if (meta == null || EXPORT_AGG_KEYS.contains(key))
            {
                continue;
            }
            String type = String.valueOf(meta.get("type"));
            String label = String.valueOf(meta.get("label"));
            if (WORKLOAD_TEXT_KEYS.contains(key))
            {
                columns.add(DynamicColumnExcelExporter.WrapColumn.wrap(label, WORKLOAD_TEXT_WIDTH));
            }
            else if ("number".equals(type) || "money".equals(type))
            {
                columns.add(DynamicColumnExcelExporter.WrapColumn.number(label, widthOf(type)));
            }
            else
            {
                columns.add(DynamicColumnExcelExporter.WrapColumn.text(label, widthOf(type)));
            }
            validKeys.add(key);
        }

        List<List<Object>> rows = new ArrayList<>();
        for (ProjProject p : projects)
        {
            Map<String, Object> node = buildSettlementNode(p, workloadMap, paymentMap);
            List<ProjWorkload> workloads = workloadMap.getOrDefault(p.getId(), Collections.emptyList());

            // ① 外部：整个项目汇总成一份，不按人 / 不按栏拆行（重复的计费类别就在单元格里重复列）
            String extText = buildWorkloadText(workloads, "external", catOrder);
            BigDecimal extOutput = sumOutput(workloads, true);

            // ② 内部：按「栏」= 负责人 + 子项 分组，一栏一行（保底是按栏算的，拆开才能对上账）
            Map<String, List<ProjWorkload>> byLane = new LinkedHashMap<>();
            Map<String, String> laneOwner = new LinkedHashMap<>();
            for (ProjWorkload w : workloads)
            {
                if (!"internal".equals(w.getBillingType()))
                {
                    continue;
                }
                String uk = w.getUserId() == null ? NULL_USER_KEY : String.valueOf(w.getUserId());
                String lane = uk + "#" + (w.getSubItemNo() == null ? "" : w.getSubItemNo());
                byLane.computeIfAbsent(lane, k -> new ArrayList<>()).add(w);
                String nm = w.getUserName();
                if (nm != null && !nm.trim().isEmpty())
                {
                    laneOwner.putIfAbsent(lane, nm.trim());
                }
            }

            if (workloads.isEmpty())
            {
                // 没登记过工作量的项目：仍导出一行（项目信息 + 空明细），避免整条项目被吞掉
                rows.add(buildRow(validKeys, metaMap, node, "", null, null, null, null));
                continue;
            }
            if (byLane.isEmpty())
            {
                // 只有外部工作量的项目：一行，外部汇总；负责人取外部工作量的登记人（可能多人）
                rows.add(buildRow(validKeys, metaMap, node, ownerNamesOf(workloads),
                        extText, extOutput, null, null));
                continue;
            }

            boolean firstLaneRow = true;
            for (Map.Entry<String, List<ProjWorkload>> e : byLane.entrySet())
            {
                List<ProjWorkload> lane = e.getValue();
                String owner = laneOwner.getOrDefault(e.getKey(), UNKNOWN_USER_NAME);
                // 外部只在第一行出现（否则同一笔外部工作量会在多行里重复求和）
                rows.add(buildRow(validKeys, metaMap, node, owner,
                        firstLaneRow ? extText : null,
                        firstLaneRow ? extOutput : null,
                        buildWorkloadText(lane, "internal", catOrder),
                        laneInternalOutput(lane)));
                firstLaneRow = false;
            }
            // 注：不再补「保底补差」行 —— 每行内部产值已按「栏」各自套用保底（触发就是 6000），
            // 各行求和天然等于项目内部产值合计，多一行反而让人以为算错
        }

        String fileName = "费用结算_" + DateUtils.parseDateToStr("yyyyMMddHHmmss", new java.util.Date()) + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        FileUtils.setAttachmentResponseHeader(response, fileName);
        // 单级表头 + 单元格内换行的工作量明细；冻结前 2 列，横拉时工程编号 / 关联定线常驻
        DynamicColumnExcelExporter.exportWithWrap(response.getOutputStream(), "费用结算", columns, 2, rows);
    }

    // ===== 拆行导出辅助 =====

    /**
     * 组装一行数据（一个项目 × 一个内部栏；纯外部项目则一项目一行）
     *
     * <p>值由调用方算好后传入，本方法只负责按列顺序铺开 —— 外部与内部的取值口径不同
     * （外部整项目汇总、内部按栏），放在这里算容易写串。</p>
     *
     * @param node       项目级结算信息（付款 / 发票 / 结算状态等，每行重复写）
     * @param ownerName  该行「负责人」列取值
     * @param extText    外部工作量明细文本（非首行传 null）
     * @param extOutput  外部产值（非首行传 null）
     * @param innerText  内部工作量明细文本
     * @param innerOutput 内部产值
     */
    private List<Object> buildRow(List<String> validKeys, Map<String, Map<String, Object>> metaMap,
                                  Map<String, Object> node, String ownerName,
                                  String extText, BigDecimal extOutput,
                                  String innerText, BigDecimal innerOutput)
    {
        List<Object> row = new ArrayList<>();
        for (String key : validKeys)
        {
            Map<String, Object> meta = metaMap.get(key);
            String type = meta == null ? "text" : String.valueOf(meta.get("type"));
            if (KEY_USER_NAME.equals(key))
            {
                row.add(ownerName);
            }
            else if ("externalWorkload".equals(key))
            {
                row.add(extText);
            }
            else if ("internalWorkload".equals(key))
            {
                row.add(innerText);
            }
            else if ("externalOutput".equals(key))
            {
                row.add(extOutput);
            }
            else if ("internalOutput".equals(key))
            {
                row.add(innerOutput);
            }
            else
            {
                row.add(formatCell(node.get(key), type, key));
            }
        }
        return row;
    }

    /**
     * 一栏的内部产值（套用保底 6000 后）
     *
     * <p>与页面项目合计同源（{@link #calcInternalOutputTotal}）：本栏「管线新测 + 管线修测」不足 6000 按 6000 计。
     * 该栏没有任何内部产值时返回 null（单元格留空，不填 0）。</p>
     */
    private BigDecimal laneInternalOutput(List<ProjWorkload> lane)
    {
        if (lane == null || lane.isEmpty())
        {
            return null;
        }
        boolean any = false;
        for (ProjWorkload w : lane)
        {
            if (w.getInternalOutput() != null)
            {
                any = true;
                break;
            }
        }
        return any ? calcInternalOutputTotal(lane) : null;
    }

    /** 项目里所有工作量登记人的姓名（去重、保持顺序）；一个都解析不出来时返回「未指定」 */
    private String ownerNamesOf(List<ProjWorkload> workloads)
    {
        List<String> names = new ArrayList<>();
        for (ProjWorkload w : workloads)
        {
            String nm = w.getUserName();
            if (nm != null && !nm.trim().isEmpty() && !names.contains(nm.trim()))
            {
                names.add(nm.trim());
            }
        }
        return names.isEmpty() ? UNKNOWN_USER_NAME : String.join("、", names);
    }

    /**
     * 工作量明细文本：一个单元格内按工作量行逐条换行。
     *
     * <p>格式 {@code 类别名：2.1千米}（不带子项名后缀，也不标起步量）。同一计费类别有多条
     * 就重复列多行（不合并）；无该类型的工作量返回 null（单元格留空，不填 0）。</p>
     */
    private String buildWorkloadText(List<ProjWorkload> workloads, String billingType,
                                     Map<String, Integer> catOrder)
    {
        if (workloads == null || workloads.isEmpty())
        {
            return null;
        }
        List<ProjWorkload> list = new ArrayList<>();
        for (ProjWorkload w : workloads)
        {
            if (billingType.equals(w.getBillingType()))
            {
                list.add(w);
            }
        }
        if (list.isEmpty())
        {
            return null;
        }
        // 按计费类别排序（sort_order）；List.sort 稳定排序，同类别保持原始录入顺序
        list.sort((a, b) -> {
            int oa = catOrder.getOrDefault(catName(a.getBillingCategory()), 9999);
            int ob = catOrder.getOrDefault(catName(b.getBillingCategory()), 9999);
            return oa != ob ? Integer.compare(oa, ob) : 0;
        });

        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (ProjWorkload w : list)
        {
            if (n == MAX_DETAIL_LINES)
            {
                sb.append("\n…等 ").append(list.size() - n).append(" 项");
                break;
            }
            if (n > 0)
            {
                sb.append('\n');
            }
            // 起步量（min_quantity）不再写进明细：对账看的是实际工作量，标出来反而碍眼
            sb.append(catName(w.getBillingCategory())).append("：");
            BigDecimal wl = w.getWorkload();
            if (wl != null)
            {
                sb.append(fmtNum(wl));
                String unit = w.getPriceUnit();
                if (unit != null && !unit.trim().isEmpty())
                {
                    sb.append(' ').append(unit.trim());
                }
            }
            // 子项名不写进明细行：与计费类别混在一起会让人误以为是另一类工作量
            n++;
        }
        return sb.toString();
    }

    /** 该登记人某类型的产值合计；一条都没有时返回 null（单元格留空） */
    private BigDecimal sumOutput(List<ProjWorkload> workloads, boolean external)
    {
        if (workloads == null || workloads.isEmpty())
        {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        boolean any = false;
        for (ProjWorkload w : workloads)
        {
            if (external != "external".equals(w.getBillingType()))
            {
                continue;
            }
            any = true;
            BigDecimal v = external ? w.getExternalOutput() : w.getInternalOutput();
            if (v != null)
            {
                sum = sum.add(v);
            }
        }
        return any ? sum : null;
    }

    /**
     * 计费类别排序字典：来自 proj_category_billing.sort_order。
     *
     * <p>只是锦上添花：查不到就退化成按类别名排序，不影响导出内容。</p>
     */
    private Map<String, Integer> loadCategoryOrder()
    {
        Map<String, Integer> orderMap = new HashMap<>();
        try
        {
            for (Map<String, Object> row : categoryBillingMapper.selectBillingCategoryOrders())
            {
                Object nameObj = row.get("name") != null ? row.get("name") : row.get("NAME");
                if (nameObj == null)
                {
                    continue;
                }
                Object ordObj = row.get("sortOrder") != null ? row.get("sortOrder") : row.get("sortorder");
                int ord = (ordObj instanceof Number) ? ((Number) ordObj).intValue() : 9999;
                String name = catName(String.valueOf(nameObj));
                Integer old = orderMap.get(name);
                if (old == null || ord < old)
                {
                    orderMap.put(name, ord);
                }
            }
        }
        catch (Exception ignored)
        {
            // 排序字典查不到不影响导出
        }
        return orderMap;
    }

    /** 数值文本化：去掉多余的零，最多保留 2 位小数（2.10 → 2.1） */
    private String fmtNum(BigDecimal v)
    {
        if (v == null)
        {
            return "";
        }
        BigDecimal s = v.stripTrailingZeros();
        if (s.scale() > 2)
        {
            s = s.setScale(2, RoundingMode.HALF_UP);
        }
        return s.toPlainString();
    }

    /**
     * 导出 / 类别清单共用的筛选解析。
     *
     * <p>⚠️ 两处必须完全同口径，否则用户在弹窗里勾的类别与最终导出的类别会对不上。</p>
     */
    private List<ProjProject> resolveExportProjects(ProjProject project, String projectStatus,
                                                    String scope, String ids)
    {
        String status = (projectStatus == null || projectStatus.isEmpty()) ? "closed,archived" : projectStatus;
        if (!"all".equals(status))
        {
            project.getParams().put("statusList", Arrays.asList(status.split(",")));
        }
        // 导出与分页无关：清掉分页参数，避免调用方误传导致只导一页
        project.getParams().remove("pageNum");
        project.getParams().remove("pageSize");
        if ("selected".equals(scope))
        {
            List<Long> idScope = toIdList(ids);
            if (idScope.isEmpty())
            {
                throw new ServiceException("请先勾选要导出的项目");
            }
            project.getParams().put("exportIds", idScope);
        }
        List<ProjProject> projects = projectService.selectProjectList(project);
        if (projects.size() > EXPORT_MAX_ROWS)
        {
            throw new ServiceException("导出条数过多（" + projects.size() + " 条，上限 "
                    + EXPORT_MAX_ROWS + " 条），请缩小筛选范围后重试");
        }
        return projects;
    }

    /** 批量加载项目的工作量明细（避免逐项目 N+1） */
    private Map<Long, List<ProjWorkload>> loadWorkloadMap(List<ProjProject> projects)
    {
        if (projects == null || projects.isEmpty())
        {
            return Collections.emptyMap();
        }
        Long[] ids = projects.stream().map(ProjProject::getId).toArray(Long[]::new);
        return workloadMapper.selectWorkloadsByProjectIds(ids).stream()
                .collect(Collectors.groupingBy(ProjWorkload::getProjectId));
    }

    /** 批量加载项目的付款记录 */
    private Map<Long, List<ProjPayment>> loadPaymentMap(List<ProjProject> projects)
    {
        if (projects == null || projects.isEmpty())
        {
            return Collections.emptyMap();
        }
        Long[] ids = projects.stream().map(ProjProject::getId).toArray(Long[]::new);
        return paymentMapper.selectPaymentsByProjectIds(ids).stream()
                .collect(Collectors.groupingBy(ProjPayment::getProjectId));
    }

    /** 计费类别名归一化：空值统一归入「未分类」，保证明细不丢行 */
    private String catName(String cat)
    {
        return (cat == null || cat.trim().isEmpty()) ? UNCATEGORIZED : cat.trim();
    }

    /** 列宽（字符数）：日期窄列、金额/数值中列、文本宽列；与前端 colWidth 视觉保持一致 */
    private int widthOf(String type)
    {
        if ("date".equals(type))
        {
            return 12;
        }
        if ("money".equals(type) || "number".equals(type))
        {
            return 14;
        }
        return 16;
    }

    /** 单个单元格取值：金额/数值保留数字（导出右对齐），日期统一 yyyy-MM-dd，开票状态转中文 */
    private Object formatCell(Object raw, String type, String key)
    {
        if (raw == null || "".equals(raw))
        {
            return "";
        }
        if ("invoiceStatus".equals(key))
        {
            String v = String.valueOf(raw);
            return INVOICE_STATUS_TEXT.getOrDefault(v, INVOICE_STATUS_TEXT.getOrDefault(v.toLowerCase(), v));
        }
        if ("date".equals(type))
        {
            return (raw instanceof java.util.Date)
                    ? DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD, (java.util.Date) raw)
                    : String.valueOf(raw);
        }
        if ("money".equals(type) || "number".equals(type))
        {
            if (raw instanceof Number)
            {
                return raw;
            }
            try
            {
                return new BigDecimal(String.valueOf(raw));
            }
            catch (NumberFormatException e)
            {
                return String.valueOf(raw);
            }
        }
        return String.valueOf(raw);
    }

    /** 逗号串 id → Long 列表（「仅导出已勾选的行」用） */
    private List<Long> toIdList(String ids)
    {
        List<Long> list = new ArrayList<>();
        if (ids == null || ids.trim().isEmpty())
        {
            return list;
        }
        for (String s : ids.split(","))
        {
            if (s == null || s.trim().isEmpty())
            {
                continue;
            }
            try
            {
                list.add(Long.valueOf(s.trim()));
            }
            catch (NumberFormatException e)
            {
                throw new ServiceException("项目ID格式非法：" + s);
            }
        }
        return list;
    }

    /** 逗号串 → 字段 key 列表 */
    private List<String> splitFields(String fields)
    {
        List<String> list = new ArrayList<>();
        if (fields == null || fields.trim().isEmpty())
        {
            return list;
        }
        for (String s : fields.split(","))
        {
            if (s == null || s.trim().isEmpty())
            {
                continue;
            }
            list.add(s.trim());
        }
        return list;
    }

    /** 录入状态胶囊的全局计数（工作量/到账/发票），忽略录入状态筛选本身 */
    @GetMapping("/entryStatusCounts")
    public AjaxResult entryStatusCounts(ProjProject project,
        @RequestParam(required = false) String projectStatus)
    {
        if (projectStatus == null || projectStatus.isEmpty())
        {
            projectStatus = "closed,archived";
        }
        if (!"all".equals(projectStatus))
        {
            project.getParams().put("statusList", Arrays.asList(projectStatus.split(",")));
        }
        // 计数为全库（当前筛选）口径，不受录入状态筛选影响
        project.setWorkloadEntry(null);
        project.setPaymentEntry(null);
        project.setInvoiceUnpaid(null);
        Map<String, Object> counts = projectService.selectEntryStatusCounts(project);
        return success(counts == null ? new HashMap<>() : counts);
    }

    /** 组装单个项目结算节点（付款信息 + 工作量汇总 + 结算状态 + 发票付款状态） */
    private Map<String, Object> buildSettlementNode(ProjProject p,
        Map<Long, List<ProjWorkload>> workloadMap, Map<Long, List<ProjPayment>> paymentMap)
    {
        Map<String, Object> projectNode = buildProjectNode(p);
        // 从批量查询结果中取该项目的工作量与付款
        List<ProjWorkload> workloads = workloadMap.getOrDefault(p.getId(), Collections.emptyList());
        List<ProjPayment> payments = paymentMap.getOrDefault(p.getId(), Collections.emptyList());

        // 填充付款信息到项目节点
        fillPaymentInfo(projectNode, payments, p.getId());

        // 汇总项目级工作量（内部工作量 / 外部工作量分别独立汇总，不相加：
        // 内外工作量单位不同（公里/宗/平方公里），相加无业务含义，仅作参考值保留统一字段 workload）
        BigDecimal totalWorkload = BigDecimal.ZERO;
        BigDecimal totalInternalWorkload = BigDecimal.ZERO;
        BigDecimal totalExternalWorkload = BigDecimal.ZERO;
        BigDecimal totalExternalOutput = BigDecimal.ZERO;
        for (ProjWorkload w : workloads)
        {
            if (w.getWorkload() != null) totalWorkload = totalWorkload.add(w.getWorkload());
            if (w.getWorkload() != null)
            {
                if ("internal".equals(w.getBillingType())) totalInternalWorkload = totalInternalWorkload.add(w.getWorkload());
                else if ("external".equals(w.getBillingType())) totalExternalWorkload = totalExternalWorkload.add(w.getWorkload());
            }
            if (w.getExternalOutput() != null) totalExternalOutput = totalExternalOutput.add(w.getExternalOutput());
        }
        // 内部产值合计（含「管线新测 + 管线修测」保底 6000）
        BigDecimal totalInternalOutput = calcInternalOutputTotal(workloads);

        projectNode.put("workload", totalWorkload);
        projectNode.put("internalWorkload", totalInternalWorkload);
        projectNode.put("externalWorkload", totalExternalWorkload);
        // 工作量明细（按计费类别 + 计价单位聚合，供列表悬浮展示内部/外部构成）
        projectNode.put("internalWorkloadDetail", groupWorkloadDetail(workloads, "internal"));
        projectNode.put("externalWorkloadDetail", groupWorkloadDetail(workloads, "external"));
        projectNode.put("internalOutput", totalInternalOutput);
        projectNode.put("externalOutput", totalExternalOutput);
        // 录入状态计数（供前端胶囊筛选：工作量记录数 / 到账记录数）
        projectNode.put("workloadCount", workloads.size());
        int paymentCnt = 0;
        for (ProjPayment pm : payments)
        {
            if ("advance".equals(pm.getPaymentType()) || "final".equals(pm.getPaymentType())) paymentCnt++;
        }
        projectNode.put("paymentCount", paymentCnt);
        // 结算状态 + 已收/待收差额（结算总额 = 外部产值合计，与编辑页面口径一致）
        fillSettlementSummary(projectNode, payments, totalExternalOutput);
        // 开票/付款组合状态：not_invoiced 未开未付 / invoiced_unpaid 已开未付 / invoiced_paid 已开已付 / voided 已作废
        String invStatus = (String) projectNode.get("invoiceStatus");
        BigDecimal invAmt = (BigDecimal) projectNode.get("invoiceAmount");
        String invDate = (String) projectNode.get("invoiceDate");
        boolean hasInvoice = (invDate != null && !invDate.isEmpty())
            || (invAmt != null && invAmt.compareTo(BigDecimal.ZERO) > 0);
        boolean isVoided = isVoidedStatus(invStatus);
        BigDecimal recv = (BigDecimal) projectNode.get("receivedAmount");
        boolean hasPaid = recv != null && recv.compareTo(BigDecimal.ZERO) > 0;
        String invoicePaymentStatus;
        if (isVoided) invoicePaymentStatus = "voided";
        else if (hasInvoice && !hasPaid) invoicePaymentStatus = "invoiced_unpaid";
        else if (hasInvoice && hasPaid) invoicePaymentStatus = "invoiced_paid";
        else invoicePaymentStatus = "not_invoiced";
        projectNode.put("invoicePaymentStatus", invoicePaymentStatus);
        return projectNode;
    }

    /**
     * 查询项目产值结算总览（聚合接口：产值 + 收款 + 结算状态，供项目详情页产值结算tab使用）
     */
    @GetMapping("/overview/{projectId}")
    public AjaxResult overview(@PathVariable Long projectId)
    {
        ProjProject project = projectService.selectProjectById(projectId);
        if (project == null)
        {
            return error("项目不存在");
        }
        List<ProjWorkload> workloads = workloadMapper.selectWorkloadsByProjectId(projectId);
        List<ProjPayment> payments = paymentMapper.selectPaymentsByProjectId(projectId);

        // 产值汇总（内部产值与外部产值各自独立，不相加；结算总额 = 外部产值）
        BigDecimal internalOutput = calcInternalOutputTotal(workloads);
        BigDecimal externalOutput = BigDecimal.ZERO;
        for (ProjWorkload w : workloads)
        {
            if (w.getExternalOutput() != null) externalOutput = externalOutput.add(w.getExternalOutput());
        }
        // 结算总额 = 外部产值合计（与编辑页面口径一致）
        BigDecimal settlementAmount = externalOutput;
        // 已收款汇总（退款按负数计入：已收 = 预付款 + 尾款 - 退款合计）
        BigDecimal receivedAmount = BigDecimal.ZERO;
        BigDecimal refundAmount = BigDecimal.ZERO;
        List<ProjPayment> refunds = new ArrayList<>();
        for (ProjPayment pm : payments)
        {
            if (pm.getAmount() == null) continue;
            if ("refund".equals(pm.getPaymentType()))
            {
                refundAmount = refundAmount.add(pm.getAmount());
                receivedAmount = receivedAmount.subtract(pm.getAmount());
                refunds.add(pm);
            }
            else
            {
                receivedAmount = receivedAmount.add(pm.getAmount());
            }
        }
        BigDecimal pendingAmount = settlementAmount.subtract(receivedAmount);

        // 结算状态：overdue=超额收款 / settled=已结清 / pending=未结清
        String settlementStatus;
        if (pendingAmount.compareTo(BigDecimal.ZERO) < 0)
        {
            settlementStatus = "overdue";
        }
        else if (pendingAmount.compareTo(BigDecimal.ZERO) == 0 && settlementAmount.compareTo(BigDecimal.ZERO) > 0)
        {
            settlementStatus = "settled";
        }
        else
        {
            settlementStatus = "pending";
        }

        Map<String, Object> result = new HashMap<>();
        result.put("internalOutput", internalOutput);
        result.put("externalOutput", externalOutput);
        result.put("settlementAmount", settlementAmount);
        result.put("receivedAmount", receivedAmount);
        result.put("refundAmount", refundAmount);
        result.put("pendingAmount", pendingAmount);
        result.put("settlementStatus", settlementStatus);
        // 付款明细（预付款/尾款各一条，供详情页产值结算tab展示）
        result.put("payments", payments);
        // 退款明细（多笔，供详情页展示）
        result.put("refunds", refunds);
        return success(result);
    }

    /**
     * 查询项目结算详情（含工作量明细 + 付款记录，供编辑弹窗使用）
     */
    @GetMapping("/{projectId}")
    public AjaxResult getDetail(@PathVariable Long projectId)
    {
        ProjProject project = projectService.selectProjectById(projectId);
        if (project == null)
        {
            return error("项目不存在");
        }
        List<ProjWorkload> workloads = workloadMapper.selectWorkloadsByProjectId(projectId);
        List<ProjPayment> payments = paymentMapper.selectPaymentsByProjectId(projectId);

        // 查询项目关联合同的类别单价（用于前端自动带出外部单价）
        List<ProjContractPrice> contractPrices = Collections.emptyList();
        Long contractId = project.getContractId();
        if (contractId != null)
        {
            contractPrices = contractPriceMapper.selectPriceListByContractId(contractId);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("project", project);
        result.put("workloads", workloads);
        result.put("payments", payments);
        // 退款记录（payment_type='refund' 多笔，编辑弹窗退款小节数据源）
        List<ProjPayment> refunds = payments.stream()
            .filter(pm -> "refund".equals(pm.getPaymentType()))
            .sorted(Comparator.comparing(ProjPayment::getPayTime, Comparator.nullsLast(Comparator.naturalOrder())))
            .collect(Collectors.toList());
        result.put("refunds", refunds);
        result.put("contractPrices", contractPrices);
        result.put("leaderIds", leaderMapper.selectLeaderIdsByProjectId(projectId));
        return success(result);
    }

    /**
     * 保存费用结算（Upsert 工作量 + 付款记录）
     */
    @PreAuthorize("@ss.hasPermi('project:settlement:edit')")
    @Log(title = "费用结算", businessType = BusinessType.UPDATE)
    @Transactional(rollbackFor = Exception.class)
    @PutMapping
    public AjaxResult save(@RequestBody Map<String, Object> params)
    {
        Long projectId = toLong(params.get("projectId"));
        if (projectId == null)
        {
            return error("项目ID不能为空");
        }

        String username = getUsername();
        String remark = (String) params.getOrDefault("remark", "");

        // 1. Save payments（预付款 + 尾款）
        savePaymentIfPresent((Map<String, Object>) params.get("prepay"), projectId, "advance", username, remark);
        savePaymentIfPresent((Map<String, Object>) params.get("tail"), projectId, "final", username, remark);

        // 1.1 Save refunds（退款多笔，整组替换：先逻辑删旧退款行，再逐笔插入）
        saveRefunds(params.get("refunds"), projectId, username);

        // 2. Save workloads
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> workloadList = (List<Map<String, Object>>) params.get("workloads");
        if (workloadList != null && !workloadList.isEmpty())
        {
            for (Map<String, Object> wl : workloadList)
            {
                ProjWorkload w = new ProjWorkload();
                w.setProjectId(projectId);
                w.setUserId(toLong(wl.get("userId")));
                w.setCategoryId(toLong(wl.get("categoryId")));
                w.setInternalWorkload(toBigDecimal(wl.get("internalWorkload")));
                w.setExternalWorkload(toBigDecimal(wl.get("externalWorkload")));
                w.setInternalPrice(toBigDecimal(wl.get("internalPrice")));
                w.setExternalPrice(toBigDecimal(wl.get("externalPrice")));
                w.setInternalOutput(toBigDecimal(wl.get("internalOutput")));
                w.setExternalOutput(toBigDecimal(wl.get("externalOutput")));
                w.setWorkload(toBigDecimal(wl.get("workload")));
                w.setPriceSource((String) wl.getOrDefault("priceSource", "manual"));
                w.setBillingType((String) wl.getOrDefault("billingType", ""));
                w.setBillingCategory((String) wl.getOrDefault("billingCategory", ""));
                w.setPriceUnit((String) wl.getOrDefault("priceUnit", ""));
                w.setMinQuantity(toBigDecimal(wl.get("minQuantity")));
                Object minSrcObj = wl.get("minQuantitySource");
                w.setMinQuantitySource(minSrcObj != null ? minSrcObj.toString() : null);
                w.setUnitPrice(toBigDecimal(wl.get("unitPrice")));
                w.setRemark((String) wl.getOrDefault("remark", ""));
                Object subItemNoObj = wl.get("subItemNo");
                if (subItemNoObj != null)
                {
                    Long subNo = toLong(subItemNoObj);
                    if (subNo != null) w.setSubItemNo(subNo.intValue());
                }
                Object dupNoObj = wl.get("dupNo");
                if (dupNoObj != null)
                {
                    Long dupNo = toLong(dupNoObj);
                    if (dupNo != null) w.setDupNo(dupNo.intValue());
                }
                w.setSubItemName((String) wl.getOrDefault("subItemName", ""));
                w.setCreateBy(username);
                workloadMapper.upsertWorkload(w);
            }
        }

        return success();
    }

    /**
     * 保存工作量明细（独立保存，不涉及付款）
     */
    @PreAuthorize("@ss.hasPermi('project:settlement:edit')")
    @Log(title = "工作量明细", businessType = BusinessType.UPDATE)
    @Transactional(rollbackFor = Exception.class)
    @PutMapping("/workload")
    public AjaxResult saveWorkload(@RequestBody Map<String, Object> params)
    {
        Long projectId = toLong(params.get("projectId"));
        if (projectId == null)
        {
            return error("项目ID不能为空");
        }

        String username = getUsername();

        // 保存前先逻辑删除该项目所有工作量（全量替换，避免外部工作量 user_id 为 null 时 ON CONFLICT 失效导致重复插入）
        workloadMapper.deleteWorkloadsByProjectId(projectId);

        // Save workloads only
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> workloadList = (List<Map<String, Object>>) params.get("workloads");
        if (workloadList != null && !workloadList.isEmpty())
        {
            for (Map<String, Object> wl : workloadList)
            {
                ProjWorkload w = new ProjWorkload();
                w.setProjectId(projectId);
                w.setUserId(toLong(wl.get("userId")));
                w.setCategoryId(toLong(wl.get("categoryId")));
                w.setInternalWorkload(toBigDecimal(wl.get("internalWorkload")));
                w.setExternalWorkload(toBigDecimal(wl.get("externalWorkload")));
                w.setInternalPrice(toBigDecimal(wl.get("internalPrice")));
                w.setExternalPrice(toBigDecimal(wl.get("externalPrice")));
                w.setInternalOutput(toBigDecimal(wl.get("internalOutput")));
                w.setExternalOutput(toBigDecimal(wl.get("externalOutput")));
                w.setWorkload(toBigDecimal(wl.get("workload")));
                w.setPriceSource((String) wl.getOrDefault("priceSource", "manual"));
                w.setBillingType((String) wl.getOrDefault("billingType", ""));
                w.setBillingCategory((String) wl.getOrDefault("billingCategory", ""));
                w.setPriceUnit((String) wl.getOrDefault("priceUnit", ""));
                w.setMinQuantity(toBigDecimal(wl.get("minQuantity")));
                Object minSrcObj = wl.get("minQuantitySource");
                w.setMinQuantitySource(minSrcObj != null ? minSrcObj.toString() : null);
                w.setUnitPrice(toBigDecimal(wl.get("unitPrice")));
                w.setRemark((String) wl.getOrDefault("remark", ""));
                Object subItemNoObj = wl.get("subItemNo");
                if (subItemNoObj != null)
                {
                    Long subNo = toLong(subItemNoObj);
                    if (subNo != null) w.setSubItemNo(subNo.intValue());
                }
                Object dupNoObj = wl.get("dupNo");
                if (dupNoObj != null)
                {
                    Long dupNo = toLong(dupNoObj);
                    if (dupNo != null) w.setDupNo(dupNo.intValue());
                }
                w.setSubItemName((String) wl.getOrDefault("subItemName", ""));
                w.setCreateBy(username);
                workloadMapper.upsertWorkload(w);
            }
        }

        return success();
    }

    /**
     * 保存到账信息（付款 + 开票，独立保存，不涉及工作量）
     */
    @PreAuthorize("@ss.hasPermi('project:settlement:edit')")
    @Log(title = "到账信息", businessType = BusinessType.UPDATE)
    @Transactional(rollbackFor = Exception.class)
    @PutMapping("/payment")
    public AjaxResult savePayment(@RequestBody Map<String, Object> params)
    {
        Long projectId = toLong(params.get("projectId"));
        if (projectId == null)
        {
            return error("项目ID不能为空");
        }

        String username = getUsername();
        String remark = (String) params.getOrDefault("remark", "");

        // Save payments（预付款 + 尾款）
        savePaymentIfPresent((Map<String, Object>) params.get("prepay"), projectId, "advance", username, remark);
        savePaymentIfPresent((Map<String, Object>) params.get("tail"), projectId, "final", username, remark);

        // Save refunds（退款多笔，整组替换）
        saveRefunds(params.get("refunds"), projectId, username);

        return success();
    }

    // ===== Private helpers =====

    /** 内部工作量「管线新测 / 管线修测」合并保底产值（元） */
    private static final BigDecimal INTERNAL_REVISE_MIN = new BigDecimal("6000");

    /**
     * 计算内部产值合计（含「管线新测 + 管线修测」保底 6000）
     * 规则（2026-10-15 起）：保底按「栏」= (userId, subItemNo) 各自计算 ——
     *      每栏内部产值 = 该栏其他内部产值 + max(该栏「管线新测」+「管线修测」产值, 6000)；
     *      内部产值合计 = 各栏之和。
     *      保底差额只体现在合计中，不改变各行的 internalOutput。
     *      ⚠️ 与 ProjWorkloadMapper.xml#outputProjectAgg 同口径（内层分栏保底、外层求和）。
     *
     * @param workloads 该项目全部工作量
     * @return 内部产值合计（含保底）
     */
    private BigDecimal calcInternalOutputTotal(List<ProjWorkload> workloads)
    {
        // 栏 key -> 该栏内部产值合计；栏 key -> 该栏「管线新测/修测」产值合计
        Map<String, BigDecimal> laneTotal = new LinkedHashMap<>();
        Map<String, BigDecimal> laneRevise = new LinkedHashMap<>();
        for (ProjWorkload w : workloads)
        {
            if (w.getInternalOutput() == null) continue;
            String key = w.getUserId() + "#" + w.getSubItemNo();
            BigDecimal out = w.getInternalOutput();
            laneTotal.merge(key, out, BigDecimal::add);
            if ("管线新测".equals(w.getBillingCategory()) || "管线修测".equals(w.getBillingCategory()))
            {
                laneRevise.merge(key, out, BigDecimal::add);
            }
        }
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> e : laneTotal.entrySet())
        {
            BigDecimal laneSum = e.getValue();
            BigDecimal reviseSum = laneRevise.get(e.getKey());
            if (reviseSum != null && reviseSum.compareTo(INTERNAL_REVISE_MIN) < 0)
            {
                laneSum = laneSum.subtract(reviseSum).add(INTERNAL_REVISE_MIN);
            }
            total = total.add(laneSum);
        }
        return total;
    }

    /**
     * 按计费类别 + 计价单位聚合工作量明细（供列表「内部/外部工作量」列悬浮展示构成）
     * 返回结构：[{ category, unit, unitPrice, value, count }]，按 value 降序、类别名升序
     *
     * @param workloads  该项目全部工作量
     * @param billingType internal=内部 / external=外部
     * @return 聚合明细列表（无匹配则为空列表）
     */
    private List<Map<String, Object>> groupWorkloadDetail(List<ProjWorkload> workloads, String billingType)
    {
        // key = 计费类别 + "\u0001" + 计价单位，保证同类别同单位合并、同类别不同单位分开
        Map<String, Map<String, Object>> agg = new LinkedHashMap<>();
        for (ProjWorkload w : workloads)
        {
            if (!billingType.equals(w.getBillingType())) continue;
            if (w.getWorkload() == null) continue;
            String category = w.getBillingCategory() == null || w.getBillingCategory().isEmpty()
                ? "未分类" : w.getBillingCategory();
            String unit = w.getPriceUnit() == null ? "" : w.getPriceUnit();
            String key = category + "\u0001" + unit;
            Map<String, Object> item = agg.get(key);
            if (item == null)
            {
                item = new LinkedHashMap<>();
                item.put("category", category);
                item.put("unit", unit);
                item.put("unitPrice", null);
                item.put("value", BigDecimal.ZERO);
                item.put("count", 0);
                agg.put(key, item);
            }
            // 单价：取该组首个非空值（同类别同单位的单价一致，仅作展示）
            if (item.get("unitPrice") == null && w.getUnitPrice() != null)
            {
                item.put("unitPrice", w.getUnitPrice());
            }
            item.put("value", ((BigDecimal) item.get("value")).add(w.getWorkload()));
            item.put("count", ((Integer) item.get("count")) + 1);
        }
        List<Map<String, Object>> list = new ArrayList<>(agg.values());
        // 值降序；值相同时按类别名升序，保证展示顺序稳定
        list.sort((a, b) -> {
            int cmp = ((BigDecimal) b.get("value")).compareTo((BigDecimal) a.get("value"));
            if (cmp != 0) return cmp;
            return ((String) a.get("category")).compareTo((String) b.get("category"));
        });
        return list;
    }

    /** 构建项目级树节点 */
    private Map<String, Object> buildProjectNode(ProjProject p)
    {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", "p" + p.getId());
        node.put("projectId", p.getId());
        node.put("projectCode", p.getProjectCode());
        // 关联定线编号（验线项目关联的定线工程；selectProjectList 已 coalesce 关联项目编号与手输文本）
        node.put("relatedProjectCode", p.getRelatedProjectCode());
        node.put("projectName", p.getProjectName());
        node.put("clientUnit", p.getClientUnit());
        node.put("projectLocation", p.getProjectLocation());
        node.put("engineeringProject", p.getEngineeringProject());
        // 项目类别名称：列表「项目类别」列（列元数据 key/prop = categoryName）数据源
        node.put("categoryName", p.getCategoryName());
        node.put("leaderNames", p.getLeaderNames());
        return node;
    }

    /** 填充付款信息 */
    private void fillPaymentInfo(Map<String, Object> node, List<ProjPayment> payments, Long projectId)
    {
        ProjPayment prepay = null;
        ProjPayment tail = null;
        BigDecimal refundAmount = BigDecimal.ZERO;
        Date lastRefundTime = null;
        for (ProjPayment pm : payments)
        {
            if ("advance".equals(pm.getPaymentType())) prepay = pm;
            else if ("final".equals(pm.getPaymentType())) tail = pm;
            else if ("refund".equals(pm.getPaymentType()))
            {
                if (pm.getAmount() != null) refundAmount = refundAmount.add(pm.getAmount());
                if (pm.getPayTime() != null && (lastRefundTime == null || pm.getPayTime().after(lastRefundTime)))
                {
                    lastRefundTime = pm.getPayTime();
                }
            }
        }
        if (prepay != null)
        {
            node.put("prepayAmount", prepay.getAmount());
            node.put("prepayDate", prepay.getPayTime() != null ? DATE_FMT.format(prepay.getPayTime()) : "");
            node.put("payUnit", prepay.getPayUnit());
            node.put("payMethod", prepay.getPayMethod());
            node.put("payRemark", prepay.getRemark());
        }
        if (tail != null)
        {
            node.put("tailAmount", tail.getAmount());
            node.put("tailDate", tail.getPayTime() != null ? DATE_FMT.format(tail.getPayTime()) : "");
        }
        // 退款信息（多笔合计金额 + 最近一次时间；明细在编辑弹窗/展开行查看）
        node.put("refundAmount", refundAmount);
        node.put("refundDate", lastRefundTime != null ? DATE_FMT.format(lastRefundTime) : "");
        // 发票信息（预付款优先，无预付款取尾款）
        ProjPayment invoiceSource = prepay != null ? prepay : tail;
        if (invoiceSource != null)
        {
            node.put("invoiceUnit", invoiceSource.getInvoiceUnit());
            node.put("invoiceDate", invoiceSource.getInvoiceDate() != null ? DATE_FMT.format(invoiceSource.getInvoiceDate()) : "");
            node.put("invoiceAmount", invoiceSource.getInvoiceAmount());
            node.put("invoiceStatus", invoiceSource.getInvoiceStatus());
        }
    }

    /** 填充结算核对摘要：已收 / 待收差额 / 结算状态（settled=已结清 pending=未结清 overdue=超额）
     *  已收 = 预付款 + 尾款 - 退款合计（refund 类型按负数计入）
     *  @param settlementAmount 结算总额（= 外部产值合计） */
    private void fillSettlementSummary(Map<String, Object> node, List<ProjPayment> payments, BigDecimal settlementAmount)
    {
        BigDecimal receivedAmount = BigDecimal.ZERO;
        for (ProjPayment pm : payments)
        {
            if (pm.getAmount() == null) continue;
            if ("refund".equals(pm.getPaymentType()))
            {
                receivedAmount = receivedAmount.subtract(pm.getAmount());
            }
            else
            {
                receivedAmount = receivedAmount.add(pm.getAmount());
            }
        }
        BigDecimal pendingAmount = settlementAmount.subtract(receivedAmount);

        String settlementStatus;
        if (pendingAmount.compareTo(BigDecimal.ZERO) < 0)
        {
            settlementStatus = "overdue";
        }
        else if (pendingAmount.compareTo(BigDecimal.ZERO) == 0 && settlementAmount.compareTo(BigDecimal.ZERO) > 0)
        {
            settlementStatus = "settled";
        }
        else
        {
            settlementStatus = "pending";
        }

        node.put("receivedAmount", receivedAmount);
        node.put("pendingAmount", pendingAmount);
        node.put("settlementStatus", settlementStatus);
    }

    /** 保存单条付款记录（upsert） */
    private void savePaymentIfPresent(Map<String, Object> payMap, Long projectId, String type, String username, String remark)
    {
        if (payMap == null) return;
        ProjPayment pm = new ProjPayment();
        pm.setProjectId(projectId);
        pm.setPaymentType(type);
        pm.setAmount(toBigDecimal(payMap.get("amount")));
        pm.setPayTime(toDate(payMap.get("payTime")));
        pm.setPayUnit((String) payMap.get("payUnit"));
        pm.setPayMethod((String) payMap.get("payMethod"));
        pm.setInvoiceUnit((String) payMap.get("invoiceUnit"));
        pm.setInvoiceDate(toDate(payMap.get("invoiceDate")));
        pm.setInvoiceAmount(toBigDecimal(payMap.get("invoiceAmount")));
        // 开票状态自动推断（库里统一存英文码值，中文由前端 utils/projStatus.js 映射）：
        //   勾选作废 → voided；有发票信息 → invoiced；否则 → pending（不再手选）
        String invoiceStatus = (String) payMap.get("invoiceStatus");
        boolean voided = isVoidedStatus(invoiceStatus);
        // ⚠️ 必须用 isNotBlank：invoice_unit 列默认值为空串 ''，若用 != null 判断，
        //    存量行(默认 '')回填后原样回传会把开票状态误判为 invoiced。
        boolean hasInvoice = StringUtils.isNotBlank(pm.getInvoiceUnit()) || pm.getInvoiceDate() != null
            || (pm.getInvoiceAmount() != null && pm.getInvoiceAmount().compareTo(BigDecimal.ZERO) > 0);
        if (voided)
        {
            pm.setInvoiceStatus("voided");
        }
        else if (hasInvoice)
        {
            pm.setInvoiceStatus("invoiced");
        }
        else
        {
            // 显式写 pending 而非 null：一是与导入口径一致，二是清空发票信息时能把状态正确回退。
            // ⚠️ 不要写中文「未开」，库里一律存码值。
            pm.setInvoiceStatus("pending");
        }
        // 空值防御：付款金额、付款日期、发票信息均为空且未标记作废时不插入记录
        if (pm.getAmount() == null && pm.getPayTime() == null && !hasInvoice && !voided) return;
        pm.setRemark(remark);
        pm.setCreateBy(username);
        paymentMapper.upsertPayment(pm);
    }

    /**
     * 是否「已作废」开票状态。
     *
     * <p>库里统一存英文码值 {@code voided}（开票状态值域：pending 未开 / invoiced 已开 / voided 已作废），
     * 中文只是前端的展示映射。此处额外兼容历史存量里的中文值「已作废」，保证过渡期回显与再保存一致。</p>
     */
    private static boolean isVoidedStatus(String status)
    {
        return "voided".equals(status) || "已作废".equals(status);
    }

    /** 保存退款记录（多笔，整组替换：先逻辑删该项目旧退款行，再逐笔插入 payment_type='refund'）
     *  退款金额/时间/方式/原因 分别映射 amount / pay_time / pay_method / remark */
    private void saveRefunds(Object refundsObj, Long projectId, String username)
    {
        // 无论是否传入，先整组删除旧退款行（传空数组=清空退款）
        paymentMapper.deleteRefundsByProjectId(projectId, username);

        if (!(refundsObj instanceof List)) return;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> refunds = (List<Map<String, Object>>) refundsObj;
        for (Map<String, Object> rf : refunds)
        {
            if (rf == null) continue;
            BigDecimal amount = toBigDecimal(rf.get("amount"));
            Date payTime = toDate(rf.get("payTime"));
            // 空值防御：金额和时间都为空的行不插入
            if (amount == null && payTime == null) continue;
            ProjPayment pm = new ProjPayment();
            pm.setProjectId(projectId);
            pm.setPaymentType("refund");
            pm.setAmount(amount);
            pm.setPayTime(payTime);
            pm.setPayMethod((String) rf.get("payMethod"));
            pm.setRemark((String) rf.getOrDefault("remark", ""));
            pm.setCreateBy(username);
            paymentMapper.insertPayment(pm);
        }
    }

    // ===== Type conversion helpers =====

    private Long toLong(Object obj)
    {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).longValue();
        try { return Long.parseLong(obj.toString()); } catch (Exception e) { return null; }
    }

    private BigDecimal toBigDecimal(Object obj)
    {
        if (obj == null) return null;
        if (obj instanceof BigDecimal) return (BigDecimal) obj;
        try { return new BigDecimal(obj.toString()); } catch (Exception e) { return null; }
    }

    private Date toDate(Object obj)
    {
        if (obj == null) return null;
        if (obj instanceof Date) return (Date) obj;
        try { return DATE_FMT.parse(obj.toString()); } catch (Exception e) { return null; }
    }
}
