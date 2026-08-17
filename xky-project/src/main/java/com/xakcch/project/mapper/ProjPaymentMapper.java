package com.xakcch.project.mapper;

import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import com.xakcch.project.domain.ProjPayment;

/**
 * 付款记录 数据层
 *
 * @author liuyonghui
 */
public interface ProjPaymentMapper
{
    /**
     * 查询付款记录列表
     */
    public List<ProjPayment> selectPaymentList(ProjPayment payment);

    /**
     * 根据ID查询
     */
    public ProjPayment selectPaymentById(Long id);

    /**
     * 新增
     */
    public int insertPayment(ProjPayment payment);

    /**
     * 修改
     */
    public int updatePayment(ProjPayment payment);

    /**
     * 批量删除（逻辑删除）
     */
    public int deletePaymentByIds(Long[] ids);

    /**
     * 按项目ID和付款类型查询付款记录
     *
     * @param projectId 项目ID
     * @return 付款记录列表
     */
    public List<ProjPayment> selectPaymentsByProjectId(Long projectId);

    /**
     * Upsert 付款记录（按 project_id + payment_type 唯一）
     *
     * @param payment 付款记录
     * @return 结果
     */
    public int upsertPayment(ProjPayment payment);

    /**
     * 项目收款总览列表（按项目维度聚合付款：合同额、已收、未收、进度、状态）
     *
     * @param params 查询参数（projectName, paymentStatus）
     * @return 收款总览列表
     */
    public List<Map<String, Object>> selectPaymentOverviewList(Map<String, Object> params);

    /**
     * 项目收款总览 KPI 统计（总数/未付款/部分付款/已结清/应收合计/已收合计）
     *
     * @return KPI 统计
     */
    public Map<String, Object> selectPaymentOverviewStats();

    /**
     * 按合同ID聚合该合同下所有项目的实收总额
     *
     * @param contractId 合同ID
     * @return 收款总额
     */
    public BigDecimal selectReceivedAmountByContractId(Long contractId);

    /**
     * 按合同ID查询各项目的到账明细（预付款/尾款/进度款分类合计 + 项目基础信息）
     *
     * @param contractId 合同ID
     * @return 各项目到账明细列表
     */
    public List<Map<String, Object>> selectReceivedDetailByContractId(Long contractId);

    /**
     * 整组逻辑删除某项目的退款记录（payment_type='refund'，保存退款=先删后插）
     *
     * @param projectId 项目ID
     * @param updateBy 操作人
     * @return 影响行数
     */
    public int deleteRefundsByProjectId(@org.apache.ibatis.annotations.Param("projectId") Long projectId,
                                        @org.apache.ibatis.annotations.Param("updateBy") String updateBy);
}
