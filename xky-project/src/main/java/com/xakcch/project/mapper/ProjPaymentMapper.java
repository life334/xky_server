package com.xakcch.project.mapper;

import java.util.List;
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
}
