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
        addColumn(columns, "output", "总产值", "money", false, "output");
        addColumn(columns, "prepayAmount", "预付款", "money", true, "prepayAmount");
        addColumn(columns, "prepayDate", "预付款时间", "date", true, "prepayDate");
        addColumn(columns, "payUnit", "付款单位", "text", true, "payUnit");
        addColumn(columns, "payMethod", "付款方式", "text", true, "payMethod");
        addColumn(columns, "tailAmount", "尾款", "money", true, "tailAmount");
        addColumn(columns, "tailDate", "尾款时间", "date", true, "tailDate");
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

        List<Map<String, Object>> tree = new ArrayList<>();
        for (ProjProject p : projects)
        {
            Map<String, Object> projectNode = buildProjectNode(p);
            // 查询该项目的工作量
            List<ProjWorkload> workloads = workloadMapper.selectWorkloadsByProjectId(p.getId());
            // 查询该项目的付款记录
            List<ProjPayment> payments = paymentMapper.selectPaymentsByProjectId(p.getId());

            // 填充付款信息到项目节点
            fillPaymentInfo(projectNode, payments, p.getId());

            // 按人员分组构建工作量树
            List<Map<String, Object>> userChildren = buildUserChildren(workloads, p.getId());

            // 汇总项目级工作量
            BigDecimal totalWorkload = BigDecimal.ZERO;
            BigDecimal totalInternalOutput = BigDecimal.ZERO;
            BigDecimal totalExternalOutput = BigDecimal.ZERO;
            for (ProjWorkload w : workloads)
            {
                if (w.getWorkload() != null) totalWorkload = totalWorkload.add(w.getWorkload());
                if (w.getInternalOutput() != null) totalInternalOutput = totalInternalOutput.add(w.getInternalOutput());
                if (w.getExternalOutput() != null) totalExternalOutput = totalExternalOutput.add(w.getExternalOutput());
            }

            projectNode.put("workload", totalWorkload);
            projectNode.put("internalOutput", totalInternalOutput);
            projectNode.put("externalOutput", totalExternalOutput);
            projectNode.put("output", totalInternalOutput.add(totalExternalOutput));
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

        // 产值汇总（内部 + 外部）
        BigDecimal internalOutput = BigDecimal.ZERO;
        BigDecimal externalOutput = BigDecimal.ZERO;
        for (ProjWorkload w : workloads)
        {
            if (w.getInternalOutput() != null) internalOutput = internalOutput.add(w.getInternalOutput());
            if (w.getExternalOutput() != null) externalOutput = externalOutput.add(w.getExternalOutput());
        }
        BigDecimal totalOutput = internalOutput.add(externalOutput);

        // 已收款汇总
        BigDecimal receivedAmount = BigDecimal.ZERO;
        for (ProjPayment pm : payments)
        {
            if (pm.getAmount() != null) receivedAmount = receivedAmount.add(pm.getAmount());
        }
        BigDecimal pendingAmount = totalOutput.subtract(receivedAmount);

        // 结算状态：overdue=超额收款 / settled=已结清 / pending=未结清
        String settlementStatus;
        if (pendingAmount.compareTo(BigDecimal.ZERO) < 0)
        {
            settlementStatus = "overdue";
        }
        else if (pendingAmount.compareTo(BigDecimal.ZERO) == 0 && totalOutput.compareTo(BigDecimal.ZERO) > 0)
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
        result.put("totalOutput", totalOutput);
        result.put("receivedAmount", receivedAmount);
        result.put("pendingAmount", pendingAmount);
        result.put("settlementStatus", settlementStatus);
        // 付款明细（预付款/尾款各一条，供详情页产值结算tab展示）
        result.put("payments", payments);
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
                w.setRemark((String) wl.getOrDefault("remark", ""));
                w.setCreateBy(username);
                workloadMapper.upsertWorkload(w);
            }
        }

        return success();
    }

    // ===== Private helpers =====

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
        for (ProjPayment pm : payments)
        {
            if ("advance".equals(pm.getPaymentType())) prepay = pm;
            else if ("final".equals(pm.getPaymentType())) tail = pm;
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
            userNode.put("output", uInternalOutput.add(uExternalOutput));
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
        // 空值防御：金额和日期都为空时不插入付款记录
        if (pm.getAmount() == null && pm.getPayTime() == null) return;
        pm.setPayUnit((String) payMap.get("payUnit"));
        pm.setPayMethod((String) payMap.get("payMethod"));
        pm.setInvoiceNo((String) payMap.get("invoiceNo"));
        pm.setInvoiceDate(toDate(payMap.get("invoiceDate")));
        pm.setInvoiceAmount(toBigDecimal(payMap.get("invoiceAmount")));
        pm.setInvoiceStatus((String) payMap.get("invoiceStatus"));
        pm.setRemark(remark);
        pm.setCreateBy(username);
        paymentMapper.upsertPayment(pm);
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
