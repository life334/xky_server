package com.xakcch.project.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.xakcch.common.utils.SecurityUtils;
import com.xakcch.project.domain.ProjContractPrice;
import com.xakcch.project.mapper.ProjContractPriceMapper;
import com.xakcch.project.mapper.ProjProjectMapper;
import com.xakcch.project.service.IProjContractPriceService;
import com.xakcch.project.service.IProjPriceRecalcService;

/**
 * 合同单价明细 Service 实现
 *
 * @author liuyonghui
 */
@Service
public class ProjContractPriceServiceImpl implements IProjContractPriceService
{
    @Autowired
    private ProjContractPriceMapper priceMapper;

    @Autowired
    private ProjProjectMapper projectMapper;

    @Autowired
    private IProjPriceRecalcService priceRecalcService;

    @Override
    public List<ProjContractPrice> getCategoryTreeWithPrice(Long contractId)
    {
        return priceMapper.selectCategoryTreeWithPrice(contractId);
    }

    @Override
    public List<ProjContractPrice> getPriceListByContractId(Long contractId)
    {
        return priceMapper.selectPriceListByContractId(contractId);
    }

    /**
     * 批量保存：遍历传入的 priceList，有 id 则 update，无 id 且（单价或起步量）非空则 insert，
     * 单价与起步量皆空且已有 id 则 delete。
     * 保存后比对新旧（单价 + 起步量）签名，得出被改动的计费方式(billingId)集合，仅重算关联该项目的外部工作量。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int savePrices(Long contractId, List<ProjContractPrice> priceList)
    {
        // 1. 变更前快照：billingId -> 签名（单价 | 起步量）
        Map<Long, String> oldSigMap = new HashMap<>();
        List<ProjContractPrice> before = priceMapper.selectPriceListByContractId(contractId);
        if (before != null)
        {
            for (ProjContractPrice cp : before)
            {
                if (cp.getBillingId() != null)
                {
                    oldSigMap.put(cp.getBillingId(), priceSig(cp.getPrice(), cp.getContractMinQuantity()));
                }
            }
        }

        int count = 0;
        String username = SecurityUtils.getUsername();

        for (ProjContractPrice p : priceList)
        {
            p.setContractId(contractId);

            boolean priceEmpty = isEmptyPrice(p.getPrice());
            boolean minEmpty = isEmptyPrice(p.getContractMinQuantity());

            if (p.getId() != null)
            {
                // 已有记录：单价与起步量都清空 → 删除；否则更新
                if (priceEmpty && minEmpty)
                {
                    p.setUpdateBy(username);
                    priceMapper.deletePrice(p);
                }
                else
                {
                    p.setUpdateBy(username);
                    priceMapper.updatePrice(p);
                }
                count++;
            }
            else if ((!priceEmpty || !minEmpty) && p.getBillingId() != null)
            {
                // 新记录：单价或起步量任一非空 → 插入
                p.setCreateBy(username);
                priceMapper.insertPrice(p);
                count++;
            }
            // 两者皆空的跳过（保持未配置状态）
        }

        // 2. 变更后快照 + 比对，得出被改动的 billingId 集合
        Map<Long, String> newSigMap = new HashMap<>();
        List<ProjContractPrice> after = priceMapper.selectPriceListByContractId(contractId);
        if (after != null)
        {
            for (ProjContractPrice cp : after)
            {
                if (cp.getBillingId() != null)
                {
                    newSigMap.put(cp.getBillingId(), priceSig(cp.getPrice(), cp.getContractMinQuantity()));
                }
            }
        }
        Set<Long> changedBillingIds = new HashSet<>();
        for (Map.Entry<Long, String> e : oldSigMap.entrySet())
        {
            String ns = newSigMap.get(e.getKey());
            if (!java.util.Objects.equals(e.getValue(), ns))
            {
                changedBillingIds.add(e.getKey());
            }
        }
        for (Long bid : newSigMap.keySet())
        {
            if (!oldSigMap.containsKey(bid))
            {
                changedBillingIds.add(bid);
            }
        }

        // 3. 对该合同关联的所有项目，仅重算被改动的计费方式对应的外部工作量单价/起步量
        if (!changedBillingIds.isEmpty())
        {
            List<Map<String, Object>> projects = projectMapper.selectProjectsByContractId(contractId);
            if (projects != null)
            {
                List<Long> billingIdList = new ArrayList<>(changedBillingIds);
                Set<Long> recalcProjectIds = new HashSet<>();
                for (Map<String, Object> pm : projects)
                {
                    Object pid = pm.get("project_id");
                    if (pid == null) pid = pm.get("id");
                    if (pid == null) continue;
                    Long projectId = Long.valueOf(pid.toString());
                    if (!recalcProjectIds.add(projectId)) continue;   // 去重（合同单价 JOIN 可能产生重复行）
                    // 触发点二非关联变化，手改价(manual)保持不动 → forceReset=false
                    priceRecalcService.recalcExternalPrices(projectId, billingIdList, false);
                }
            }
        }

        return count;
    }

    /** 单价为空判定（null 或负数视为未配置） */
    private boolean isEmptyPrice(BigDecimal v)
    {
        return v == null || v.compareTo(BigDecimal.ZERO) < 0;
    }

    /** 单价 + 起步量 的比对签名（null 归一化，值归一化去尾零，避免 1 与 1.00 误判为变化） */
    private String priceSig(BigDecimal price, BigDecimal minQuantity)
    {
        return norm(price) + "|" + norm(minQuantity);
    }

    private String norm(BigDecimal v)
    {
        return v == null ? "" : v.stripTrailingZeros().toPlainString();
    }
}
