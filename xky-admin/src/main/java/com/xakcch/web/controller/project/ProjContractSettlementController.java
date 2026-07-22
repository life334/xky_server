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

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd");

    /**
     * 查询合同结算树形列表
     */
    @PreAuthorize("@ss.hasPermi('project:contractSettlement:list')")
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
            contractNode.put("receivedAmount", c.getReceivedAmount());
            contractNode.put("remark", c.getRemark());

            // 子级：关联项目
            List<Map<String, Object>> projects = projectMapper.selectProjectsByContractId(c.getId());
            List<Map<String, Object>> projectChildren = new ArrayList<>();

            // 汇总合同单价（去重后拼接展示）
            Set<String> categoryPriceSet = new LinkedHashSet<>();
            for (Map<String, Object> p : projects)
            {
                String catName = (String) p.get("category_name");
                Object priceObj = p.get("contract_price");
                if (catName != null)
                {
                    String priceStr = priceObj != null ? priceObj.toString() : "—";
                    categoryPriceSet.add(catName + " " + priceStr);
                }

                Map<String, Object> child = new LinkedHashMap<>();
                child.put("id", "c" + c.getId() + "_p" + p.get("project_id"));
                child.put("projectCode", p.get("project_code"));
                child.put("projectName", p.get("project_name"));
                child.put("engineeringProject", p.get("engineering_project"));
                child.put("clientUnit", p.get("client_unit"));
                child.put("categoryName", p.get("category_name"));
                child.put("contractPrice", priceObj);
                projectChildren.add(child);
            }

            // 合同单价汇总文字
            int priceCount = categoryPriceSet.size();
            contractNode.put("priceSummary", priceCount > 0 ? priceCount + "项" : "—");
            contractNode.put("priceDetail", categoryPriceSet);

            contractNode.put("children", projectChildren);
            tree.add(contractNode);
        }
        return success(tree);
    }

    /**
     * 查询指定合同的单价明细（弹窗用，按类别展开）
     */
    @PreAuthorize("@ss.hasPermi('project:contractSettlement:query')")
    @GetMapping("/priceDetail/{contractId}")
    public AjaxResult priceDetail(@PathVariable Long contractId)
    {
        List<Map<String, Object>> prices = contractMapper.selectPriceListByContractId(contractId);
        return success(prices);
    }

    /**
     * 保存合同结算（已到账 + 期限 + 支付条件 + 备注）
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
        contract.setReceivedAmount(toBigDecimal(params.get("receivedAmount")));
        contract.setContractPeriod((String) params.get("contractPeriod"));
        contract.setPaymentTerms((String) params.get("paymentTerms"));
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
