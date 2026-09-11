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
     * 批量保存：遍历传入的 priceList，有 id 则 update，无 id 且 price 不为空则 insert，price 为空且已有 id 则 delete。
     * 保存后比对新旧单价，得出被改动的计费方式(billingId)集合，仅重算关联该项目的外部工作量单价。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int savePrices(Long contractId, List<ProjContractPrice> priceList)
    {
        // 1. 变更前快照：billingId -> price（用于比对得出变更集）
        Map<Long, BigDecimal> oldPriceMap = new HashMap<>();
        List<ProjContractPrice> before = priceMapper.selectPriceListByContractId(contractId);
        if (before != null)
        {
            for (ProjContractPrice cp : before)
            {
                if (cp.getBillingId() != null)
                {
                    oldPriceMap.put(cp.getBillingId(), cp.getPrice());
                }
            }
        }

        int count = 0;
        String username = SecurityUtils.getUsername();

        for (ProjContractPrice p : priceList)
        {
            p.setContractId(contractId);

            if (p.getId() != null)
            {
                // 已有记录的更新或删除
                if (p.getPrice() == null || p.getPrice().compareTo(BigDecimal.ZERO) < 0)
                {
                    // 清空了单价 → 删除
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
            else if (p.getPrice() != null && p.getPrice().compareTo(BigDecimal.ZERO) >= 0)
            {
                // 新记录，有有效单价 → 插入（必须带 billingId）
                if (p.getBillingId() == null) continue;
                p.setCreateBy(username);
                priceMapper.insertPrice(p);
                count++;
            }
            // price 为空的跳过（保持未配置状态）
        }

        // 2. 变更后快照 + 比对，得出被改动的 billingId 集合
        Map<Long, BigDecimal> newPriceMap = new HashMap<>();
        List<ProjContractPrice> after = priceMapper.selectPriceListByContractId(contractId);
        if (after != null)
        {
            for (ProjContractPrice cp : after)
            {
                if (cp.getBillingId() != null)
                {
                    newPriceMap.put(cp.getBillingId(), cp.getPrice());
                }
            }
        }
        Set<Long> changedBillingIds = new HashSet<>();
        for (Map.Entry<Long, BigDecimal> e : oldPriceMap.entrySet())
        {
            BigDecimal np = newPriceMap.get(e.getKey());
            if (!priceEquals(e.getValue(), np))
            {
                changedBillingIds.add(e.getKey());
            }
        }
        for (Long bid : newPriceMap.keySet())
        {
            if (!oldPriceMap.containsKey(bid))
            {
                changedBillingIds.add(bid);
            }
        }

        // 3. 对该合同关联的所有项目，仅重算被改动的计费方式对应的外部工作量单价
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

    /** 单价相等判断（含 null 语义：都为空视为相等） */
    private boolean priceEquals(BigDecimal a, BigDecimal b)
    {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.compareTo(b) == 0;
    }
}
