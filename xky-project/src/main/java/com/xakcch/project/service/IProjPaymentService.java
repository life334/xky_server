package com.xakcch.project.service;

import java.util.List;
import java.util.Map;
import com.xakcch.project.domain.ProjPayment;

/**
 * 付款记录 业务层接口
 *
 * @author liuyonghui
 */
public interface IProjPaymentService
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
     * 新增付款记录
     */
    public int insertPayment(ProjPayment payment);

    /**
     * 修改付款记录
     */
    public int updatePayment(ProjPayment payment);

    /**
     * 批量删除
     */
    public int deletePaymentByIds(Long[] ids);

    /**
     * 项目收款总览列表（按项目维度聚合付款）
     */
    public List<Map<String, Object>> selectPaymentOverviewList(Map<String, Object> params);

    /**
     * 项目收款总览 KPI 统计
     */
    public Map<String, Object> selectPaymentOverviewStats();
}
