package com.xakcch.project.service.impl;

import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.xakcch.common.exception.ServiceException;
import com.xakcch.project.domain.ProjMaterial;
import com.xakcch.project.domain.ProjMaterialFlow;
import com.xakcch.project.mapper.ProjMaterialFlowMapper;
import com.xakcch.project.mapper.ProjMaterialMapper;
import com.xakcch.project.service.IProjMaterialService;

/**
 * 资料提交 业务层实现
 *
 * @author liuyonghui
 */
@Service
public class ProjMaterialServiceImpl implements IProjMaterialService
{
    @Autowired
    private ProjMaterialMapper materialMapper;

    @Autowired
    private ProjMaterialFlowMapper flowMapper;

    @Override
    public List<ProjMaterial> selectMaterialList(ProjMaterial material)
    {
        return materialMapper.selectMaterialList(material);
    }

    @Override
    public ProjMaterial selectMaterialById(Long id)
    {
        return materialMapper.selectMaterialById(id);
    }

    @Override
    public int insertMaterial(ProjMaterial material)
    {
        return materialMapper.insertMaterial(material);
    }

    @Override
    public int updateMaterial(ProjMaterial material)
    {
        return materialMapper.updateMaterial(material);
    }

    @Override
    public int deleteMaterialByIds(Long[] ids)
    {
        return materialMapper.deleteMaterialByIds(ids);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void borrowMaterial(Long materialId, Long guarantorId, String remark, Long userId, String userName)
    {
        // 校验资料状态
        ProjMaterial material = materialMapper.selectMaterialById(materialId);
        if (material == null) throw new ServiceException("资料不存在");
        if (!"待领取".equals(material.getStatus()) && !"已归还".equals(material.getStatus()))
            throw new ServiceException("当前状态【" + material.getStatus() + "】不允许领取");

        // 更新资料状态
        materialMapper.updateMaterialStatus(materialId, "已领取", userName);

        // 写入流转记录
        ProjMaterialFlow flow = new ProjMaterialFlow();
        flow.setMaterialId(materialId);
        flow.setFlowType("领取");
        flow.setUserId(userId);
        flow.setGuarantorId(guarantorId);
        flow.setOperateTime(new Date());
        flow.setRemark(remark);
        flowMapper.insertFlow(flow);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void returnMaterial(Long materialId, String remark, Long userId, String userName)
    {
        ProjMaterial material = materialMapper.selectMaterialById(materialId);
        if (material == null) throw new ServiceException("资料不存在");
        if (!"已领取".equals(material.getStatus()))
            throw new ServiceException("当前状态【" + material.getStatus() + "】不允许归还");

        materialMapper.updateMaterialStatus(materialId, "已归还", userName);

        ProjMaterialFlow flow = new ProjMaterialFlow();
        flow.setMaterialId(materialId);
        flow.setFlowType("归还");
        flow.setUserId(userId);
        flow.setOperateTime(new Date());
        flow.setRemark(remark);
        flowMapper.insertFlow(flow);
    }

    @Override
    public List<ProjMaterialFlow> getFlowList(Long materialId)
    {
        return flowMapper.selectFlowListByMaterialId(materialId);
    }
}
