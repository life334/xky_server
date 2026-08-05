package com.xakcch.project.service.impl;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.xakcch.common.exception.ServiceException;
import com.xakcch.project.domain.ProjContract;
import com.xakcch.project.domain.ProjMaterial;
import com.xakcch.project.domain.ProjMaterialFlow;
import com.xakcch.project.domain.ProjProject;
import com.xakcch.project.mapper.ProjContractMapper;
import com.xakcch.project.mapper.ProjMaterialFlowMapper;
import com.xakcch.project.mapper.ProjMaterialMapper;
import com.xakcch.project.mapper.ProjProjectMapper;
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

    @Autowired
    private ProjProjectMapper projectMapper;

    @Autowired
    private ProjContractMapper contractMapper;

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
        int rows = materialMapper.insertMaterial(material);
        tryAutoArchive(material.getProjectId());
        return rows;
    }

    @Override
    public int updateMaterial(ProjMaterial material)
    {
        int rows = materialMapper.updateMaterial(material);
        tryAutoArchive(material.getProjectId());
        return rows;
    }

    /**
     * 自动归档判断：项目资料 result_type 不为空 且 合同 archive_path 不为空 → 项目状态改为已归档
     */
    private void tryAutoArchive(Long projectId)
    {
        if (projectId == null) return;
        ProjProject project = projectMapper.selectProjectById(projectId);
        if (project == null || !"closed".equals(project.getStatus())) return;

        // 检查该项目是否有资料成果类型不为空
        ProjMaterial query = new ProjMaterial();
        query.setProjectId(projectId);
        List<ProjMaterial> materials = materialMapper.selectMaterialList(query);
        boolean hasResultType = materials.stream().anyMatch(m -> m.getResultType() != null && !m.getResultType().isEmpty());
        if (!hasResultType) return;

        // 检查合同归档目录
        if (project.getContractId() == null) return;
        ProjContract contract = contractMapper.selectContractById(project.getContractId());
        if (contract == null || contract.getArchivePath() == null || contract.getArchivePath().isEmpty()) return;

        // 满足条件 → 自动归档
        projectMapper.updateProjectStatus(projectId, "archived");
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
        if (!"pending".equals(material.getStatus()) && !"returned".equals(material.getStatus()))
            throw new ServiceException("当前状态【" + material.getStatus() + "】不允许领取");

        // 更新资料状态
        materialMapper.updateMaterialStatus(materialId, "received", userName);

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
        if (!"received".equals(material.getStatus()))
            throw new ServiceException("当前状态不允许归还");

        materialMapper.updateMaterialStatus(materialId, "returned", userName);

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

    @Override
    public List<Map<String, Object>> getStatusCounts()
    {
        return materialMapper.selectMaterialStatusCounts();
    }
}
