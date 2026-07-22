package com.xakcch.project.mapper;

import java.util.List;
import com.xakcch.project.domain.ProjContractPrice;

/**
 * 合同单价明细 数据层
 *
 * @author liuyonghui
 */
public interface ProjContractPriceMapper
{
    /**
     * 查询指定合同的全部类别树 + 已填单价（LEFT JOIN proj_category）
     *
     * @param contractId 合同ID
     * @return 类别树（含单价）
     */
    public List<ProjContractPrice> selectCategoryTreeWithPrice(Long contractId);

    /**
     * 查询指定合同已有单价的记录
     *
     * @param contractId 合同ID
     * @return 已填单价的记录列表
     */
    public List<ProjContractPrice> selectPriceListByContractId(Long contractId);

    /**
     * 插入一条合同单价
     *
     * @param price 合同单价对象
     * @return 结果
     */
    public int insertPrice(ProjContractPrice price);

    /**
     * 更新一条合同单价
     *
     * @param price 合同单价对象
     * @return 结果
     */
    public int updatePrice(ProjContractPrice price);

    /**
     * 删除指定合同+类别的单价记录
     *
     * @param price 合同单价对象（含 contractId + categoryId）
     * @return 结果
     */
    public int deletePrice(ProjContractPrice price);

    /**
     * 删除指定合同下全部单价记录
     *
     * @param contractId 合同ID
     * @return 结果
     */
    public int deletePriceByContractId(Long contractId);
}
