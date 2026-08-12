package com.xakcch.project.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
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

    /**
     * 更新合同状态
     *
     * @param id 合同ID
     * @param status 目标状态
     * @return 结果
     */
    public int updateContractStatus(@Param("id") Long id, @Param("status") String status);

    /**
     * 查询超时合同：登记时间超过7天 且 完成日期为空
     *
     * @return 超时合同列表
     */
    public List<Map<String, Object>> selectTimeoutContracts();

    /**
     * 按状态统计合同数量（状态胶囊导航用）
     *
     * @return [{ status: "signed", cnt: 10 }, ...]
     */
    public List<Map<String, Object>> selectContractStatusCounts();

    /**
     * 查询字段去重值（高级筛选下拉选项用）
     *
     * @param field 列名（白名单校验）
     * @return 去重值列表
     */
    public List<String> selectDistinctValues(@Param("field") String field);

    /**
     * 批量查询合同的付款明细（聚合关联项目），避免 N+1
     *
     * @param contractIds 合同ID列表
     * @return 付款明细列表，包含 contractId / paymentId / amount / payTime / payUnit / paymentType / projectId / projectName
     */
    public List<Map<String, Object>> selectPaidListByContractIds(List<Long> contractIds);

    /**
     * 查询 proj_contract 表的物理列元数据（information_schema 动态读取，列显隐功能用）
     *
     * @param tableName 表名（proj_contract）
     * @return [{ columnName: "contract_no", columnComment: "合同编号" }, ...]
     */
    public List<Map<String, Object>> selectTableColumns(@Param("tableName") String tableName);
}
