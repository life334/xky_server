package com.xakcch.project.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.xakcch.common.exception.ServiceException;
import com.xakcch.common.utils.StringUtils;
import com.xakcch.project.domain.ProjCategoryBilling;
import com.xakcch.project.mapper.ProjCategoryBillingMapper;
import com.xakcch.project.service.IProjCategoryBillingService;

/**
 * 项目类别计费方式 服务层实现
 *
 * @author liuyonghui
 */
@Service
public class ProjCategoryBillingServiceImpl implements IProjCategoryBillingService
{
    @Autowired
    private ProjCategoryBillingMapper billingMapper;

    /**
     * 查询计费方式列表
     */
    @Override
    public List<ProjCategoryBilling> selectBillingList(ProjCategoryBilling billing)
    {
        return billingMapper.selectBillingList(billing);
    }

    /**
     * 查询类别下的全部计费方式
     */
    @Override
    public List<ProjCategoryBilling> selectBillingByCategoryId(Long categoryId)
    {
        ProjCategoryBilling query = new ProjCategoryBilling();
        query.setCategoryId(categoryId);
        return billingMapper.selectBillingList(query);
    }

    /**
     * 查询所有已使用的计费类别
     */
    @Override
    public List<String> selectBillingCategoryOptions()
    {
        return billingMapper.selectBillingCategoryOptions();
    }

    /**
     * 保存类别的计费方式（全量覆盖）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int saveBilling(Long categoryId, List<ProjCategoryBilling> billings, String operName)
    {
        if (categoryId == null)
        {
            throw new ServiceException("类别ID不能为空");
        }
        // 先逻辑删除旧数据
        billingMapper.deleteBillingByCategoryId(categoryId);
        if (billings == null || billings.isEmpty())
        {
            return 0;
        }
        int count = 0;
        int internalOrder = 0, externalOrder = 0;
        for (ProjCategoryBilling billing : billings)
        {
            if (!"internal".equals(billing.getBillingType()) && !"external".equals(billing.getBillingType()))
            {
                throw new ServiceException("计费类型非法：" + billing.getBillingType());
            }
            if (StringUtils.isEmpty(billing.getBillingCategory()))
            {
                throw new ServiceException("计费类别不能为空");
            }
            if (billing.getUnitPrice() == null)
            {
                throw new ServiceException("计费类别【" + billing.getBillingCategory() + "】单价不能为空");
            }
            if (StringUtils.isEmpty(billing.getPriceUnit()))
            {
                throw new ServiceException("计费类别【" + billing.getBillingCategory() + "】计价单位不能为空");
            }
            billing.setId(null);
            billing.setCategoryId(categoryId);
            billing.setSortOrder("internal".equals(billing.getBillingType()) ? internalOrder++ : externalOrder++);
            billing.setCreateBy(operName);
            billingMapper.insertBilling(billing);
            count++;
        }
        return count;
    }
}
