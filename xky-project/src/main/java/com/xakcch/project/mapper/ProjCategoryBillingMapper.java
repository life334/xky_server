package com.xakcch.project.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.xakcch.project.domain.ProjCategoryBilling;

/**
 * 项目类别计费方式 数据层
 *
 * @author liuyonghui
 */
public interface ProjCategoryBillingMapper
{
    /**
     * 查询计费方式列表（可按类别ID、计费类型过滤，全量查询时供列表页徽标展示）
     *
     * @param billing 查询条件（categoryId / billingType 可选）
     * @return 计费方式集合
     */
    public List<ProjCategoryBilling> selectBillingList(ProjCategoryBilling billing);

    /**
     * 查询所有已使用的计费类别（去重，供下拉可创建选项）
     *
     * @return 计费类别名称集合
     */
    public List<String> selectBillingCategoryOptions();

    /**
     * 新增计费方式
     *
     * @param billing 计费方式
     * @return 结果
     */
    public int insertBilling(ProjCategoryBilling billing);

    /**
     * 按类别ID逻辑删除全部计费方式（保存时先删后插）
     *
     * @param categoryId 类别ID
     * @return 结果
     */
    public int deleteBillingByCategoryId(@Param("categoryId") Long categoryId);
}
