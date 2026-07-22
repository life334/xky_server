package com.xakcch.project.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
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
}
