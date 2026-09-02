package com.xakcch.web.controller.project;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.xakcch.common.annotation.Log;
import com.xakcch.common.core.controller.BaseController;
import com.xakcch.common.core.domain.AjaxResult;
import com.xakcch.common.enums.BusinessType;
import com.xakcch.project.domain.ProjContractPrice;
import com.xakcch.project.domain.ProjPayment;
import com.xakcch.project.domain.ProjProject;
import com.xakcch.project.domain.ProjWorkload;
import com.xakcch.project.mapper.ProjContractPriceMapper;
import com.xakcch.project.mapper.ProjLeaderMapper;
import com.xakcch.project.mapper.ProjPaymentMapper;
import com.xakcch.project.mapper.ProjWorkloadMapper;
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

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd");

    /**
     * 查询费用结算列表可显隐列的元数据（显隐列面板 + 表格动态渲染用）
     * 树形表格由 Controller 组装三级节点（项目级/人员级/叶子级），此处返回固定列清单
     */
    @GetMapping("/columns")
    public AjaxResult columns()
    {
        List<Map<String, Object>> columns = new ArrayList<>();
        // 项目级 + 人员级 + 叶子级通用字段（默认可见性 = 当前页面展示列）
        addColumn(columns, "projectCode", "工程编号", "text", true, "projectCode");
        addColumn(columns, "projectName", "项目名称", "text", false, "projectName");
        addColumn(columns, "clientUnit", "委托单位", "text", true, "clientUnit");
        addColumn(columns, "projectLocation", "工程地点", "text", true, "projectLocation");
        addColumn(columns, "engineeringProject", "工程项目", "text", false, "engineeringProject");
        addColumn(columns, "leaderNames", "负责人", "text", false, "leaderNames");
        addColumn(columns, "userName", "人员", "text", false, "userName");
        addColumn(columns, "categoryName", "项目类别", "text", false, "categoryName");
        addColumn(columns, "workload", "工作量", "number", true, "workload");
        addColumn(columns, "internalPrice", "内部单价", "money", false, "internalPrice");
        addColumn(columns, "externalPrice", "外部单价", "money", false, "externalPrice");
        addColumn(columns, "internalOutput", "内部产值", "money", true, "internalOutput");
        addColumn(columns, "externalOutput", "外部产值", "money", true, "externalOutput");
        addColumn(columns, "prepayAmount", "预付款", "money", true, "prepayAmount");
        addColumn(columns, "prepayDate", "预付款时间", "date", true, "prepayDate");
        addColumn(columns, "payUnit", "付款单位", "text", true, "payUnit");
        addColumn(columns, "payMethod", "付款方式", "text", true, "payMethod");
        addColumn(columns, "tailAmount", "尾款", "money", true, "tailAmount");
        addColumn(columns, "tailDate", "尾款时间", "date", true, "tailDate");
        addColumn(columns, "refundAmount", "退款金额", "money", true, "refundAmount");
        addColumn(columns, "refundDate", "退款时间", "date", true, "refundDate");
        addColumn(columns, "invoiceStatus", "开票状态", "text", true, "invoiceStatus");
        addColumn(columns, "invoiceNo", "发票号码", "text", true, "invoiceNo");
        addColumn(columns, "invoiceAmount", "开票金额", "money", true, "invoiceAmount");
        addColumn(columns, "payRemark", "备注", "text", true, "payRemark");
        return success(columns);
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
     * 查询费用结算树形列表
     * @param projectStatus 项目状态过滤，多个逗号分隔；默认"已办结,已归档"，传"all"显示全部
     */
    @GetMapping("/treeList")
    public AjaxResult treeList(ProjProject project,
        @RequestParam(required = false) String projectStatus)
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
        List<ProjProject> projects = projectService.selectProjectList(project);

        // 一次性批量查询所有项目的工作量与付款，避免逐项目 N+1 查询
        Long[] projectIds = projects.stream().map(ProjProject::getId).toArray(Long[]::new);
        Map<Long, List<ProjWorkload>> workloadMap = projectIds.length == 0 ? Collections.emptyMap()
            : workloadMapper.selectWorkloadsByProjectIds(projectIds).stream()
                .collect(Collectors.groupingBy(ProjWorkload::getProjectId));
        Map<Long, List<ProjPayment>> paymentMap = projectIds.length == 0 ? Collections.emptyMap()
            : paymentMapper.selectPaymentsByProjectIds(projectIds).stream()
                .collect(Collectors.groupingBy(ProjPayment::getProjectId));

        List<Map<String, Object>> tree = new ArrayList<>();
        for (ProjProject p : projects)
        {
            Map<String, Object> projectNode = buildProjectNode(p);
            // 从批量查询结果中取该项目的工作量与付款
            List<ProjWorkload> workloads = workloadMap.getOrDefault(p.getId(), Collections.emptyList());
            List<ProjPayment> payments = paymentMap.getOrDefault(p.getId(), Collections.emptyList());

            // 填充付款信息到项目节点
            fillPaymentInfo(projectNode, payments, p.getId());

            // 按人员分组构建工作量树（保留 children 字段，兼容旧接口调用方；前端平表模式不再使用）
            List<Map<String, Object>> userChildren = buildUserChildren(workloads, p.getId());

            // 汇总项目级工作量
            BigDecimal totalWorkload = BigDecimal.ZERO;
            BigDecimal totalExternalOutput = BigDecimal.ZERO;
            for (ProjWorkload w : workloads)
            {
                if (w.getWorkload() != null) totalWorkload = totalWorkload.add(w.getWorkload());
                if (w.getExternalOutput() != null) totalExternalOutput = totalExternalOutput.add(w.getExternalOutput());
            }
            // 内部产值合计（含「管线新测 + 管线修测」保底 6000）
            BigDecimal totalInternalOutput = calcInternalOutputTotal(workloads);

            projectNode.put("workload", totalWorkload);
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
            boolean isVoided = "已作废".equals(invStatus);
            BigDecimal recv = (BigDecimal) projectNode.get("receivedAmount");
            boolean hasPaid = recv != null && recv.compareTo(BigDecimal.ZERO) > 0;
            String invoicePaymentStatus;
            if (isVoided) invoicePaymentStatus = "voided";
            else if (hasInvoice && !hasPaid) invoicePaymentStatus = "invoiced_unpaid";
            else if (hasInvoice && hasPaid) invoicePaymentStatus = "invoiced_paid";
            else invoicePaymentStatus = "not_invoiced";
            projectNode.put("invoicePaymentStatus", invoicePaymentStatus);
            projectNode.put("children", userChildren);
            tree.add(projectNode);
        }
        return success(tree);
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
                w.setUnitPrice(toBigDecimal(wl.get("unitPrice")));
                w.setRemark((String) wl.getOrDefault("remark", ""));
                Object subItemNoObj = wl.get("subItemNo");
                if (subItemNoObj != null)
                {
                    Long subNo = toLong(subItemNoObj);
                    if (subNo != null) w.setSubItemNo(subNo.intValue());
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
                w.setUnitPrice(toBigDecimal(wl.get("unitPrice")));
                w.setRemark((String) wl.getOrDefault("remark", ""));
                Object subItemNoObj = wl.get("subItemNo");
                if (subItemNoObj != null)
                {
                    Long subNo = toLong(subItemNoObj);
                    if (subNo != null) w.setSubItemNo(subNo.intValue());
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
     * 规则：内部工作量中任一存在「管线新测」或「管线修测」时，
     *      内部产值合计 = 其他内部产值 + max(管线新测产值 + 管线修测产值, 6000)。
     *      保底差额只体现在合计中，不改变各行的 internalOutput。
     *
     * @param workloads 该项目全部工作量
     * @return 内部产值合计（含保底）
     */
    private BigDecimal calcInternalOutputTotal(List<ProjWorkload> workloads)
    {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal reviseSum = BigDecimal.ZERO;
        boolean hasRevise = false;
        for (ProjWorkload w : workloads)
        {
            if (w.getInternalOutput() == null) continue;
            total = total.add(w.getInternalOutput());
            if ("管线新测".equals(w.getBillingCategory()) || "管线修测".equals(w.getBillingCategory()))
            {
                reviseSum = reviseSum.add(w.getInternalOutput());
                hasRevise = true;
            }
        }
        if (hasRevise && reviseSum.compareTo(INTERNAL_REVISE_MIN) < 0)
        {
            total = total.subtract(reviseSum).add(INTERNAL_REVISE_MIN);
        }
        return total;
    }

    /** 构建项目级树节点 */
    private Map<String, Object> buildProjectNode(ProjProject p)
    {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", "p" + p.getId());
        node.put("projectId", p.getId());
        node.put("projectCode", p.getProjectCode());
        node.put("projectName", p.getProjectName());
        node.put("clientUnit", p.getClientUnit());
        node.put("projectLocation", p.getProjectLocation());
        node.put("engineeringProject", p.getEngineeringProject());
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
            node.put("invoiceNo", invoiceSource.getInvoiceNo());
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

    /** 按人员分组构建二级树节点 */
    private List<Map<String, Object>> buildUserChildren(List<ProjWorkload> workloads, Long projectId)
    {
        Map<Long, List<ProjWorkload>> grouped = workloads.stream()
            .collect(Collectors.groupingBy(ProjWorkload::getUserId, LinkedHashMap::new, Collectors.toList()));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, List<ProjWorkload>> entry : grouped.entrySet())
        {
            Long userId = entry.getKey();
            List<ProjWorkload> wList = entry.getValue();
            String userName = wList.get(0).getUserName();

            Map<String, Object> userNode = new LinkedHashMap<>();
            userNode.put("id", "p" + projectId + "_u" + userId);
            userNode.put("userName", userName);

            // 汇总该人员的工作量
            BigDecimal uWorkload = BigDecimal.ZERO;
            BigDecimal uInternalOutput = BigDecimal.ZERO;
            BigDecimal uExternalOutput = BigDecimal.ZERO;
            List<Map<String, Object>> leafChildren = new ArrayList<>();

            for (ProjWorkload w : wList)
            {
                if (w.getWorkload() != null) uWorkload = uWorkload.add(w.getWorkload());
                if (w.getInternalOutput() != null) uInternalOutput = uInternalOutput.add(w.getInternalOutput());
                if (w.getExternalOutput() != null) uExternalOutput = uExternalOutput.add(w.getExternalOutput());

                Map<String, Object> leaf = new LinkedHashMap<>();
                leaf.put("id", "w" + w.getId());
                leaf.put("categoryName", w.getCategoryName());
                leaf.put("categoryId", w.getCategoryId());
                leaf.put("workload", w.getWorkload());
                leaf.put("internalPrice", w.getInternalPrice());
                leaf.put("externalPrice", w.getExternalPrice());
                leaf.put("internalOutput", w.getInternalOutput());
                leaf.put("externalOutput", w.getExternalOutput());
                leafChildren.add(leaf);
            }

            userNode.put("workload", uWorkload);
            userNode.put("internalOutput", uInternalOutput);
            userNode.put("externalOutput", uExternalOutput);
            userNode.put("children", leafChildren);
            result.add(userNode);
        }
        return result;
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
        pm.setInvoiceNo((String) payMap.get("invoiceNo"));
        pm.setInvoiceDate(toDate(payMap.get("invoiceDate")));
        pm.setInvoiceAmount(toBigDecimal(payMap.get("invoiceAmount")));
        // 开票状态自动推断：勾选作废→已作废；有发票信息→已开；否则 null（未开，不再手选）
        String invoiceStatus = (String) payMap.get("invoiceStatus");
        boolean hasInvoice = pm.getInvoiceNo() != null || pm.getInvoiceDate() != null
            || (pm.getInvoiceAmount() != null && pm.getInvoiceAmount().compareTo(BigDecimal.ZERO) > 0);
        if ("已作废".equals(invoiceStatus))
        {
            pm.setInvoiceStatus("已作废");
        }
        else if (hasInvoice)
        {
            pm.setInvoiceStatus("已开");
        }
        else
        {
            pm.setInvoiceStatus(null);
        }
        // 空值防御：付款金额、付款日期、发票信息均为空且未标记作废时不插入记录
        if (pm.getAmount() == null && pm.getPayTime() == null && !hasInvoice && !"已作废".equals(invoiceStatus)) return;
        pm.setRemark(remark);
        pm.setCreateBy(username);
        paymentMapper.upsertPayment(pm);
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
