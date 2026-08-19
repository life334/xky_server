package com.xakcch.project.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xakcch.common.exception.ServiceException;
import com.xakcch.project.domain.ProjContract;
import com.xakcch.project.domain.ProjFieldDef;
import com.xakcch.project.domain.ProjMaterial;
import com.xakcch.project.domain.ProjMaterialFlow;
import com.xakcch.project.domain.ProjProject;
import com.xakcch.project.mapper.ProjContractMapper;
import com.xakcch.project.mapper.ProjFieldDefMapper;
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

    @Autowired
    private ProjFieldDefMapper fieldDefMapper;

    /** JSON 序列化器：领取时把资料快照存入历史记录 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

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
    public void borrowMaterial(Long materialId, ProjMaterial data, Long userId, String userName)
    {
        // 校验资料存在
        ProjMaterial material = materialMapper.selectMaterialById(materialId);
        if (material == null) throw new ServiceException("资料不存在");

        // 校验担保人：本次标记需要担保但未选择担保人时拦截
        if ("Y".equals(data.getGuarantorFlag()) && data.getGuarantorId() == null)
            throw new ServiceException("已勾选需要担保人，请选择担保人");

        // 状态：pending -> received；received 保持 received（再次领取登记）
        String newStatus = "pending".equals(material.getStatus()) ? "received" : material.getStatus();

        // 交付时间即领取时间，刷新为当前领取时刻（避免历史记录都是同一旧时间）
        Date now = new Date();
        data.setSubmitTime(now);
        // 更新主表本次领取信息（领取时间/是否担保/担保人/备注/状态等）
        data.setId(materialId);
        data.setStatus(newStatus);
        data.setUpdateBy(userName);
        materialMapper.updateMaterial(data);

        // 写入历史记录（领取人 + 担保人 + 领取时间 + 备注）
        ProjMaterialFlow flow = new ProjMaterialFlow();
        flow.setMaterialId(materialId);
        flow.setFlowType("领取");
        flow.setUserId(userId);
        flow.setGuarantorId("Y".equals(data.getGuarantorFlag()) ? data.getGuarantorId() : null);
        flow.setOperateTime(now);
        flow.setRemark(data.getRemark());
        // 快照：记录本次领取时的资料信息（联系人/电话/成果类型等）供历史追溯
        try {
            flow.setSnapshot(MAPPER.writeValueAsString(data));
        } catch (Exception e) {
            flow.setSnapshot(null);
        }
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

    @Override
    public Map<String, Object> checkPayment(Long projectId)
    {
        Map<String, Object> info = materialMapper.selectPaymentInfo(projectId);
        if (info == null || info.isEmpty())
        {
            info = new HashMap<>();
            info.put("contractAmount", 0);
            info.put("receivedAmount", 0);
            info.put("pendingAmount", 0);
            info.put("paymentRatio", 0);
            info.put("hasDebt", false);
        }
        return info;
    }

    @Override
    public void toggleArchive(Long materialId, String updateBy)
    {
        ProjMaterial material = materialMapper.selectMaterialById(materialId);
        if (material == null) throw new ServiceException("资料不存在");
        String newFlag = "Y".equals(material.getArchiveFlag()) ? "N" : "Y";
        materialMapper.updateArchiveFlag(materialId, newFlag, updateBy);
    }

    /**
     * 资料列表可显隐列元数据：
     * 业务字段（含 JOIN 展示字段）→ 系统字段 → 动态字段（proj_field_def）
     * 物理表新增列时，未在固定清单中的列会自动追加到业务组末尾（默认隐藏）
     */
    @Override
    public List<Map<String, Object>> getListColumns()
    {
        List<Map<String, Object>> columns = new ArrayList<>();

        // ---- 业务字段（固定顺序，默认可见性 = 当前页面展示列） ----
        addColumn(columns, "projectCode", "工程编号", "text", "business", true, "projectCode");
        addColumn(columns, "engineeringProject", "委托任务", "text", "business", true, "engineeringProject");
        addColumn(columns, "projectLocation", "工程地点", "text", "business", true, "projectLocation");
        addColumn(columns, "projectName", "项目名称", "text", "business", true, "projectName");
        addColumn(columns, "submitTime", "交付时间", "date", "business", true, "submitTime");
        addColumn(columns, "contactName", "联系人", "text", "business", true, "contactName");
        addColumn(columns, "contactPhone", "联系电话", "text", "business", true, "contactPhone");
        addColumn(columns, "resultType", "成果类型", "dict", "business", true, "resultType");
        addColumn(columns, "archiveDir", "存档目录", "text", "business", true, "archiveDir");
        addColumn(columns, "status", "资料状态", "dict", "business", true, "status");
        addColumn(columns, "receiveTime", "领取时间", "date", "business", true, "receiveTime");
        addColumn(columns, "archiveFlag", "归档状态", "dict", "business", true, "archiveFlag");
        addColumn(columns, "submitStatus", "提交状态", "dict", "business", false, "submitStatus");
        addColumn(columns, "guarantorFlag", "是否担保", "dict", "business", false, "guarantorFlag");
        addColumn(columns, "guarantorId", "担保人", "user", "business", false, "guarantorId");
        addColumn(columns, "remark", "备注", "text", "business", true, "remark");

        // ---- 物理表新增列自动发现（不在固定清单中的列 → 业务组末尾，默认隐藏） ----
        Set<String> known = new HashSet<>(Arrays.asList(
            "id", "project_id", "submit_time", "contact_name", "contact_phone",
            "result_type", "archive_dir", "status", "submit_status", "remark",
            "guarantor_flag", "guarantor_id",
            "archive_flag", "archive_time",
            "extra_data", "del_flag", "create_by", "create_time", "update_by", "update_time"));
        List<Map<String, Object>> tableColumns = materialMapper.selectTableColumns("proj_material");
        if (tableColumns != null)
        {
            for (Map<String, Object> col : tableColumns)
            {
                String columnName = (String) col.get("columnName");
                if (columnName == null || known.contains(columnName))
                {
                    continue;
                }
                String key = snakeToCamel(columnName);
                String label = (String) col.get("columnComment");
                if (label == null || label.trim().isEmpty())
                {
                    label = key;
                }
                addColumn(columns, key, label, "text", "business", false, key);
            }
        }

        // ---- 系统字段（默认隐藏） ----
        addColumn(columns, "id", "ID", "number", "system", false, "id");
        addColumn(columns, "createBy", "创建人", "text", "system", false, "createBy");
        addColumn(columns, "createTime", "创建时间", "date", "system", false, "createTime");
        addColumn(columns, "updateBy", "更新人", "text", "system", false, "updateBy");
        addColumn(columns, "updateTime", "更新时间", "date", "system", false, "updateTime");

        // ---- 动态字段（用户自定义，默认隐藏，值从 extra_data JSONB 读取） ----
        List<ProjFieldDef> fieldDefs = fieldDefMapper.selectFieldDefByTableName("proj_material");
        if (fieldDefs != null)
        {
            for (ProjFieldDef fd : fieldDefs)
            {
                if (fd == null || "1".equals(fd.getStatus()) || "2".equals(fd.getDelFlag()))
                {
                    continue;
                }
                addColumn(columns, fd.getFieldKey(), fd.getFieldLabel(), "dynamic", "dynamic", false, fd.getFieldKey());
            }
        }
        return columns;
    }

    /** 组装单列元数据 */
    private void addColumn(List<Map<String, Object>> columns, String key, String label,
                           String type, String group, boolean defaultVisible, String prop)
    {
        Map<String, Object> col = new HashMap<>();
        col.put("key", key);
        col.put("label", label);
        col.put("type", type);
        col.put("group", group);
        col.put("defaultVisible", defaultVisible);
        col.put("prop", prop);
        columns.add(col);
    }

    /** 数据库列名（snake_case）→ Java 属性名（camelCase） */
    private String snakeToCamel(String name)
    {
        StringBuilder sb = new StringBuilder();
        boolean up = false;
        for (char c : name.toCharArray())
        {
            if (c == '_')
            {
                up = true;
                continue;
            }
            sb.append(up ? Character.toUpperCase(c) : c);
            up = false;
        }
        return sb.toString();
    }
}
