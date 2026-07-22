package com.xakcch.project.service;

import java.util.List;
import com.xakcch.project.domain.ProjContractPrice;

/**
 * 合同单价明细 Service 接口
 *
 * @author liuyonghui
 */
public interface IProjContractPriceService
{
    /**
     * 查询指定合同的全部类别树 + 已填单价
     *
     * @param contractId 合同ID
     * @return 类别树列表（含单价）
     */
    public List<ProjContractPrice> getCategoryTreeWithPrice(Long contractId);

    /**
     * 查询指定合同已有单价的记录
     *
     * @param contractId 合同ID
     * @return 已填单价列表
     */
    public List<ProjContractPrice> getPriceListByContractId(Long contractId);

    /**
     * 批量保存合同单价（先删后插）
     *
     * @param contractId 合同ID
     * @param priceList 单价列表（仅含 categoryId + price）
     * @return 结果
     */
    public int savePrices(Long contractId, List<ProjContractPrice> priceList);
}
