package com.xakcch.project.service.impl;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.xakcch.common.utils.SecurityUtils;
import com.xakcch.project.domain.ProjContractPrice;
import com.xakcch.project.mapper.ProjContractPriceMapper;
import com.xakcch.project.service.IProjContractPriceService;

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
     * 批量保存：遍历传入的 priceList，有 id 则 update，无 id 且 price 不为空则 insert，price 为空且已有 id 则 delete
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int savePrices(Long contractId, List<ProjContractPrice> priceList)
    {
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
                // 新记录，有有效单价 → 插入
                p.setCreateBy(username);
                priceMapper.insertPrice(p);
                count++;
            }
            // price 为空的跳过（保持未配置状态）
        }
        return count;
    }
}
