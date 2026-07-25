package com.xakcch.project.service.impl;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.xakcch.common.exception.ServiceException;
import com.xakcch.project.domain.ProjContract;
import com.xakcch.project.mapper.ProjContractMapper;
import com.xakcch.project.service.IProjContractService;

/**
 * 合同表 服务层实现
 *
 * @author liuyonghui
 */
@Service
public class ProjContractServiceImpl implements IProjContractService
{
    @Autowired
    private ProjContractMapper contractMapper;

    /** 合同状态流转规则 */
    private static final Map<String, List<String>> STATUS_TRANSITIONS = new HashMap<>();
    static {
        STATUS_TRANSITIONS.put("草稿",   Arrays.asList("已签署", "已取消"));
        STATUS_TRANSITIONS.put("已签署", Arrays.asList("执行中", "已取消"));
        STATUS_TRANSITIONS.put("执行中", Arrays.asList("已完成", "已取消"));
        STATUS_TRANSITIONS.put("已完成", Arrays.asList("已归档", "已取消"));
        // 已归档、已取消 → 终态，不允许流转
    }

    /**
     * 查询合同详情
     */
    @Override
    public ProjContract selectContractById(Long id)
    {
        return contractMapper.selectContractById(id);
    }

    /**
     * 查询合同列表
     */
    @Override
    public List<ProjContract> selectContractList(ProjContract contract)
    {
        return contractMapper.selectContractList(contract);
    }

    /**
     * 校验合同编号是否唯一
     */
    @Override
    public boolean checkContractNoUnique(ProjContract contract)
    {
        Long id = contract.getId() == null ? -1L : contract.getId();
        ProjContract info = contractMapper.checkContractNoUnique(contract);
        if (info != null && info.getId().longValue() != id.longValue())
        {
            return false;
        }
        return true;
    }

    /**
     * 新增合同
     */
    @Override
    public int insertContract(ProjContract contract)
    {
        return contractMapper.insertContract(contract);
    }

    /**
     * 修改合同
     */
    @Override
    public int updateContract(ProjContract contract)
    {
        return contractMapper.updateContract(contract);
    }

    /**
     * 删除合同
     */
    @Override
    public int deleteContractById(Long id)
    {
        return contractMapper.deleteContractById(id);
    }

    /**
     * 批量删除合同
     */
    @Override
    public int deleteContractByIds(Long[] ids)
    {
        return contractMapper.deleteContractByIds(ids);
    }

    /**
     * 变更合同状态（校验流转规则）
     */
    @Override
    public int changeContractStatus(Long id, String targetStatus)
    {
        ProjContract contract = contractMapper.selectContractById(id);
        if (contract == null)
        {
            throw new ServiceException("合同不存在");
        }
        String currentStatus = contract.getStatus();
        // 终态不可变更
        if ("已归档".equals(currentStatus) || "已取消".equals(currentStatus))
        {
            throw new ServiceException("当前状态【" + currentStatus + "】为终态，不允许变更");
        }
        // 校验流转规则
        List<String> allowed = STATUS_TRANSITIONS.get(currentStatus);
        if (allowed == null || !allowed.contains(targetStatus))
        {
            throw new ServiceException("不允许从【" + currentStatus + "】变更为【" + targetStatus + "】");
        }
        return contractMapper.updateContractStatus(id, targetStatus);
    }
}
