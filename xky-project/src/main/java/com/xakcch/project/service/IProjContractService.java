package com.xakcch.project.service;

import java.util.List;
import java.util.Map;
import com.xakcch.project.domain.ProjContract;

/**
 * 合同表 服务层
 *
 * @author liuyonghui
 */
public interface IProjContractService
{
    /**
     * 查询合同详情
     *
     * @param id 合同ID
     * @return 合同
     */
    public ProjContract selectContractById(Long id);

    /**
     * 查询合同列表
     *
     * @param contract 查询条件
     * @return 合同集合
     */
    public List<ProjContract> selectContractList(ProjContract contract);

    /**
     * 校验合同编号是否唯一
     *
     * @param contract 合同信息
     * @return 结果 true=唯一 false=不唯一
     */
    public boolean checkContractNoUnique(ProjContract contract);

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
     * 删除合同
     *
     * @param id 合同ID
     * @return 结果
     */
    public int deleteContractById(Long id);

    /**
     * 批量删除合同
     *
     * @param ids 需要删除的合同ID
     * @return 结果
     */
    public int deleteContractByIds(Long[] ids);

    /**
     * 变更合同状态（校验流转规则）
     *
     * @param id 合同ID
     * @param targetStatus 目标状态
     * @return 结果
     */
    public int changeContractStatus(Long id, String targetStatus);

    /**
     * 统计各状态下的合同数量
     *
     * @return [{ status: "signed", cnt: 10 }, ...]
     */
    public List<Map<String, Object>> getStatusCounts();

    /**
     * 查询字段去重值（高级筛选下拉选项用）
     *
     * @param field 字段名（白名单校验）
     * @return 去重值列表
     */
    public List<String> getDistinctValues(String field);

    /**
     * 合同列表可显隐列元数据（显隐列面板 + 表格动态渲染用）
     *
     * @return [{ key, label, type, group, defaultVisible, prop }, ...]
     */
    public List<Map<String, Object>> getListColumns();
}
