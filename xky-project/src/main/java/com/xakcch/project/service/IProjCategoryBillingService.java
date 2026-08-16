package com.xakcch.project.service;

import java.util.List;
import com.xakcch.project.domain.ProjCategoryBilling;

/**
 * 项目类别计费方式 服务层
 *
 * @author liuyonghui
 */
public interface IProjCategoryBillingService
{
    /**
     * 查询计费方式列表（可按类别ID、计费类型过滤）
     *
     * @param billing 查询条件
     * @return 计费方式集合
     */
    public List<ProjCategoryBilling> selectBillingList(ProjCategoryBilling billing);

    /**
     * 查询类别下的全部计费方式
     *
     * @param categoryId 类别ID
     * @return 计费方式集合
     */
    public List<ProjCategoryBilling> selectBillingByCategoryId(Long categoryId);

    /**
     * 查询所有已使用的计费类别（去重）
     *
     * @return 计费类别名称集合
     */
    public List<String> selectBillingCategoryOptions();

    /**
     * 保存类别的计费方式（全量覆盖：先逻辑删除旧数据，再插入新数据）
     *
     * @param categoryId 类别ID
     * @param billings 计费方式列表（含 internal / external 两种类型）
     * @param operName 操作人
     * @return 插入条数
     */
    public int saveBilling(Long categoryId, List<ProjCategoryBilling> billings, String operName);
}
