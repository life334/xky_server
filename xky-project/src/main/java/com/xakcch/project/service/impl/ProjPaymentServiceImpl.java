package com.xakcch.project.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.xakcch.project.domain.ProjPayment;
import com.xakcch.project.mapper.ProjPaymentMapper;
import com.xakcch.project.service.IProjPaymentService;

/**
 * 付款记录 业务层实现
 *
 * @author liuyonghui
 */
@Service
public class ProjPaymentServiceImpl implements IProjPaymentService
{
    @Autowired
    private ProjPaymentMapper paymentMapper;

    @Override
    public List<ProjPayment> selectPaymentList(ProjPayment payment)
    {
        return paymentMapper.selectPaymentList(payment);
    }

    @Override
    public ProjPayment selectPaymentById(Long id)
    {
        return paymentMapper.selectPaymentById(id);
    }

    @Override
    public int insertPayment(ProjPayment payment)
    {
        return paymentMapper.insertPayment(payment);
    }

    @Override
    public int updatePayment(ProjPayment payment)
    {
        return paymentMapper.updatePayment(payment);
    }

    @Override
    public int deletePaymentByIds(Long[] ids)
    {
        return paymentMapper.deletePaymentByIds(ids);
    }
}
