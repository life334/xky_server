package com.xakcch.web.controller.project;

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
import com.xakcch.project.domain.ProjContract;
import com.xakcch.project.mapper.ProjContractMapper;
import com.xakcch.project.mapper.ProjPaymentMapper;
import com.xakcch.project.mapper.ProjProjectMapper;

/**
 * 合同结算 控制层
 *
 * @author liuyonghui
 */
@RestController
@RequestMapping("/project/contractSettlement")
public class ProjContractSettlementController extends BaseController
{
    @Autowired
    private ProjContractMapper contractMapper;

    @Autowired
    private ProjProjectMapper projectMapper;

    @Autowired
    private ProjPaymentMapper paymentMapper;

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd");

    /**
     * 查询合同结算树形列表
     */
    @GetMapping("/treeList")
    public AjaxResult treeList()
    {
        List<ProjContract> contracts = contractMapper.selectContractList(new ProjContract());
        List<Map<String, Object>> tree = new ArrayList<>();

        for (ProjContract c : contracts)
        {
            Map<String, Object> contractNode = new LinkedHashMap<>();
            contractNode.put("id", "c" + c.getId());
            contractNode.put("contractId", c.getId());
            contractNode.put("contractName", c.getContractName());
            contractNode.put("contractNo", c.getContractNo());
            contractNode.put("contractAmount", c.getContractAmount());
            contractNode.put("signDate", c.getSignDate() != null ? DATE_FMT.format(c.getSignDate()) : "");
            contractNode.put("contractPeriod", c.getContractPeriod());
            contractNode.put("paymentTerms", c.getPaymentTerms());
            // 实时计算已到账 = 该合同下所有项目的付款总额
            contractNode.put("receivedAmount", paymentMapper.selectReceivedAmountByContractId(c.getId()));
            contractNode.put("isSettled", c.getIsSettled());
            contractNode.put("remark", c.getRemark());

            // 子级：关联项目
            List<Map<String, Object>> projects = projectMapper.selectProjectsByContractId(c.getId());
            List<Map<String, Object>> projectChildren = new ArrayList<>();
            for (Map<String, Object> p : projects)
            {
                Map<String, Object> child = new LinkedHashMap<>();
                child.put("id", "c" + c.getId() + "_p" + p.get("project_id"));
                child.put("projectCode", p.get("project_code"));
                child.put("projectName", p.get("project_name"));
                child.put("engineeringProject", p.get("engineering_project"));
                child.put("clientUnit", p.get("client_unit"));
                child.put("contactName", p.get("contact_name"));
                child.put("contactPhone", p.get("contact_phone"));
                child.put("projectLocation", p.get("project_location"));
                child.put("status", p.get("status"));
                child.put("categoryName", p.get("category_name"));
                child.put("contractPrice", p.get("contract_price"));
                projectChildren.add(child);
            }

            // 合同单价汇总（直接从 proj_contract_price 查，不依赖项目）
            List<Map<String, Object>> priceList = contractMapper.selectPriceListByContractId(c.getId());
            Set<String> categoryPriceSet = new LinkedHashSet<>();
            for (Map<String, Object> item : priceList)
            {
                String parentName = (String) item.get("parent_name");
                String catName = (String) item.get("category_name");
                Object priceObj = item.get("price");
                if (catName != null)
                {
                    String fullName = parentName != null ? parentName + " > " + catName : catName;
                    String priceStr = priceObj != null ? priceObj.toString() : "";
                    categoryPriceSet.add(fullName + " " + priceStr);
                }
            }
            contractNode.put("priceDetail", categoryPriceSet);

            contractNode.put("children", projectChildren);
            tree.add(contractNode);
        }
        return success(tree);
    }

    /**
     * 查询指定合同的单价明细（弹窗用，按类别展开）
     */
    @GetMapping("/priceDetail/{contractId}")
    public AjaxResult priceDetail(@PathVariable Long contractId)
    {
        List<Map<String, Object>> prices = contractMapper.selectPriceListByContractId(contractId);
        return success(prices);
    }

    /**
     * 查询指定合同的到账明细（按项目聚合：预付款/尾款/进度款分类合计）
     */
    @GetMapping("/receivedDetail/{contractId}")
    public AjaxResult receivedDetail(@PathVariable Long contractId)
    {
        List<Map<String, Object>> detail = paymentMapper.selectReceivedDetailByContractId(contractId);
        return success(detail);
    }

    /**
     * 保存合同结算（期限 + 支付条件 + 是否结算 + 备注）
     * 注：已到账金额由系统根据关联项目的付款记录实时计算，不再手动编辑
     */
    @PreAuthorize("@ss.hasPermi('project:contractSettlement:edit')")
    @Log(title = "合同结算", businessType = BusinessType.UPDATE)
    @Transactional(rollbackFor = Exception.class)
    @PutMapping
    public AjaxResult save(@RequestBody Map<String, Object> params)
    {
        Long contractId = toLong(params.get("contractId"));
        if (contractId == null)
        {
            return error("合同ID不能为空");
        }

        ProjContract contract = new ProjContract();
        contract.setId(contractId);
        contract.setContractPeriod((String) params.get("contractPeriod"));
        contract.setPaymentTerms((String) params.get("paymentTerms"));
        contract.setIsSettled((String) params.get("isSettled"));
        contract.setRemark((String) params.get("remark"));
        contract.setUpdateBy(getUsername());
        contractMapper.updateContract(contract);

        return success();
    }

    // ===== helpers =====

    private Long toLong(Object obj)
    {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).longValue();
        try { return Long.parseLong(obj.toString()); } catch (Exception e) { return null; }
    }

    private java.math.BigDecimal toBigDecimal(Object obj)
    {
        if (obj == null) return null;
        if (obj instanceof java.math.BigDecimal) return (java.math.BigDecimal) obj;
        try { return new java.math.BigDecimal(obj.toString()); } catch (Exception e) { return null; }
    }
}
