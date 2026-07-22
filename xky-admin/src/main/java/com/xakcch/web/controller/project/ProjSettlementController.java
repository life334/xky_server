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
import com.xakcch.project.domain.ProjPayment;
import com.xakcch.project.domain.ProjProject;
import com.xakcch.project.domain.ProjWorkload;
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

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd");

    /**
     * 查询费用结算树形列表（仅含已办结项目）
     */
    @PreAuthorize("@ss.hasPermi('project:settlement:list')")
    @GetMapping("/treeList")
    public AjaxResult treeList(ProjProject project)
    {
        // 只查已办结项目
        project.setStatus("已办结");
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
            BigDecimal totalInternal = BigDecimal.ZERO;
            BigDecimal totalExternal = BigDecimal.ZERO;
            BigDecimal totalInternalOutput = BigDecimal.ZERO;
            BigDecimal totalExternalOutput = BigDecimal.ZERO;
            for (ProjWorkload w : workloads)
            {
                if (w.getInternalWorkload() != null) totalInternal = totalInternal.add(w.getInternalWorkload());
                if (w.getExternalWorkload() != null) totalExternal = totalExternal.add(w.getExternalWorkload());
                if (w.getInternalOutput() != null) totalInternalOutput = totalInternalOutput.add(w.getInternalOutput());
                if (w.getExternalOutput() != null) totalExternalOutput = totalExternalOutput.add(w.getExternalOutput());
            }

            projectNode.put("internalWorkload", totalInternal);
            projectNode.put("externalWorkload", totalExternal);
            projectNode.put("internalOutput", totalInternalOutput);
            projectNode.put("externalOutput", totalExternalOutput);
            projectNode.put("children", userChildren);
            tree.add(projectNode);
        }
        return success(tree);
    }

    /**
     * 查询项目结算详情（含工作量明细 + 付款记录，供编辑弹窗使用）
     */
    @PreAuthorize("@ss.hasPermi('project:settlement:query')")
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

        Map<String, Object> result = new HashMap<>();
        result.put("project", project);
        result.put("workloads", workloads);
        result.put("payments", payments);
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
        savePaymentIfPresent((Map<String, Object>) params.get("prepay"), projectId, "预付款", username, remark);
        savePaymentIfPresent((Map<String, Object>) params.get("tail"), projectId, "尾款", username, remark);

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
            if ("预付款".equals(pm.getPaymentType())) prepay = pm;
            else if ("尾款".equals(pm.getPaymentType())) tail = pm;
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
            BigDecimal uInternal = BigDecimal.ZERO;
            BigDecimal uExternal = BigDecimal.ZERO;
            BigDecimal uInternalOutput = BigDecimal.ZERO;
            BigDecimal uExternalOutput = BigDecimal.ZERO;
            List<Map<String, Object>> leafChildren = new ArrayList<>();

            for (ProjWorkload w : wList)
            {
                if (w.getInternalWorkload() != null) uInternal = uInternal.add(w.getInternalWorkload());
                if (w.getExternalWorkload() != null) uExternal = uExternal.add(w.getExternalWorkload());
                if (w.getInternalOutput() != null) uInternalOutput = uInternalOutput.add(w.getInternalOutput());
                if (w.getExternalOutput() != null) uExternalOutput = uExternalOutput.add(w.getExternalOutput());

                Map<String, Object> leaf = new LinkedHashMap<>();
                leaf.put("id", "w" + w.getId());
                leaf.put("categoryName", w.getCategoryName());
                leaf.put("categoryId", w.getCategoryId());
                leaf.put("internalWorkload", w.getInternalWorkload());
                leaf.put("externalWorkload", w.getExternalWorkload());
                leaf.put("internalPrice", w.getInternalPrice());
                leaf.put("externalPrice", w.getExternalPrice());
                leaf.put("internalOutput", w.getInternalOutput());
                leaf.put("externalOutput", w.getExternalOutput());
                leafChildren.add(leaf);
            }

            userNode.put("internalWorkload", uInternal);
            userNode.put("externalWorkload", uExternal);
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
        Object payTime = payMap.get("payTime");
        if (payTime instanceof Date)
        {
            pm.setPayTime((Date) payTime);
        }
        pm.setPayUnit((String) payMap.get("payUnit"));
        pm.setPayMethod((String) payMap.get("payMethod"));
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
}
