package com.xakcch.project.mapper;

import java.util.List;
import java.util.Map;
import com.xakcch.project.domain.ProjContract;

/**
 * 合同表 数据层
 *
 * @author liuyonghui
 */
public interface ProjContractMapper
{
    /**
     * 查询合同详情
     *
     * @param id 合同ID
     * @return 合同
     */
    public ProjContract selectContractById(Long id);

    /**
     * 查询合同列表（分页）
     *
     * @param contract 查询条件
     * @return 合同集合
     */
    public List<ProjContract> selectContractList(ProjContract contract);

    /**
     * 校验合同编号是否唯一
     *
     * @param contract 合同信息
     * @return 合同
     */
    public ProjContract checkContractNoUnique(ProjContract contract);

    /**
     * 新增合同
     *
     * @param contract 合同
     * @return 结果
     */
    public int insertContract(ProjContract contract);

    /**
     * 修改合同
     *
     * @param contract 合同
     * @return 结果
     */
    public int updateContract(ProjContract contract);

    /**
     * 删除合同（逻辑删除）
     *
     * @param id 合同ID
     * @return 结果
     */
    public int deleteContractById(Long id);

    /**
     * 批量删除合同（逻辑删除）
     *
     * @param ids 需要删除的合同ID
     * @return 结果
     */
    public int deleteContractByIds(Long[] ids);

    /**
     * 查询指定合同的全部已有单价记录（含类别名称）
     *
     * @param contractId 合同ID
     * @return 单价列表（Map: price_id, contract_id, category_id, price, category_name）
     */
    public List<Map<String, Object>> selectPriceListByContractId(Long contractId);
}
