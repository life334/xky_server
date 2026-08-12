package com.xakcch.project.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.xakcch.common.exception.ServiceException;
import com.xakcch.project.domain.ProjContract;
import com.xakcch.project.domain.ProjFieldDef;
import com.xakcch.project.domain.ProjMaterial;
import com.xakcch.project.domain.ProjProject;
import com.xakcch.project.mapper.ProjContractMapper;
import com.xakcch.project.mapper.ProjFieldDefMapper;
import com.xakcch.project.mapper.ProjMaterialMapper;
import com.xakcch.project.mapper.ProjProjectMapper;
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

    @Autowired
    private ProjProjectMapper projectMapper;

    @Autowired
    private ProjMaterialMapper materialMapper;

    @Autowired
    private ProjFieldDefMapper fieldDefMapper;

    /** 合同状态流转规则 */
    private static final Map<String, List<String>> STATUS_TRANSITIONS = new HashMap<>();
    static {
        STATUS_TRANSITIONS.put("draft",     Arrays.asList("signed", "cancelled"));
        STATUS_TRANSITIONS.put("signed",    Arrays.asList("ongoing", "cancelled"));
        STATUS_TRANSITIONS.put("ongoing",   Arrays.asList("completed", "cancelled"));
        STATUS_TRANSITIONS.put("completed", Arrays.asList("archived", "cancelled"));
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
        List<ProjContract> list = contractMapper.selectContractList(contract);
        // 批量填充付款明细（避免 N+1）
        fillPaidList(list);
        return list;
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
        int rows = contractMapper.insertContract(contract);
        tryAutoArchive(contract.getId());
        return rows;
    }

    /**
     * 修改合同
     */
    @Override
    public int updateContract(ProjContract contract)
    {
        int rows = contractMapper.updateContract(contract);
        tryAutoArchive(contract.getId());
        return rows;
    }

    /**
     * 自动归档：合同 archive_path 填写后，检查关联的已办结项目是否满足归档条件
     */
    private void tryAutoArchive(Long contractId)
    {
        if (contractId == null) return;
        ProjContract contract = contractMapper.selectContractById(contractId);
        if (contract == null || contract.getArchivePath() == null || contract.getArchivePath().isEmpty()) return;

        // 获取该合同下所有项目
        List<Map<String, Object>> projectMaps = projectMapper.selectProjectsByContractId(contractId);
        for (Map<String, Object> pm : projectMaps)
        {
            Long projectId = toLong(pm.get("project_id"));
            if (projectId == null) continue;
            ProjProject project = projectMapper.selectProjectById(projectId);
            if (project == null || !"closed".equals(project.getStatus())) continue;

            ProjMaterial matQuery = new ProjMaterial();
            matQuery.setProjectId(projectId);
            List<ProjMaterial> materials = materialMapper.selectMaterialList(matQuery);
            boolean hasResultType = materials.stream().anyMatch(m -> m.getResultType() != null && !m.getResultType().isEmpty());
            if (hasResultType)
            {
                projectMapper.updateProjectStatus(projectId, "archived");
            }
        }
    }

    private Long toLong(Object obj)
    {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).longValue();
        try { return Long.parseLong(obj.toString()); } catch (Exception e) { return null; }
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
        if ("archived".equals(currentStatus) || "cancelled".equals(currentStatus))
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

    /**
     * 批量填充合同的付款明细列表（避免 N+1）
     */
    private void fillPaidList(List<ProjContract> list)
    {
        if (list == null || list.isEmpty()) return;
        List<Long> ids = list.stream()
                .map(ProjContract::getId)
                .filter(id -> id != null)
                .collect(Collectors.toList());
        if (ids.isEmpty()) return;

        List<Map<String, Object>> allPayments = contractMapper.selectPaidListByContractIds(ids);
        // 按 contractId 分组
        Map<Long, List<Map<String, Object>>> grouped = new HashMap<>();
        if (allPayments != null)
        {
            for (Map<String, Object> row : allPayments)
            {
                Object cidObj = row.get("contractId");
                Long cid = cidObj instanceof Number ? ((Number) cidObj).longValue() : null;
                if (cid != null)
                {
                    grouped.computeIfAbsent(cid, k -> new ArrayList<>()).add(row);
                }
            }
        }
        // 回填到每个合同对象
        for (ProjContract c : list)
        {
            List<Map<String, Object>> payments = grouped.get(c.getId());
            c.setPaidList(payments != null ? payments : new ArrayList<>());
        }
    }

    @Override
    public List<Map<String, Object>> getStatusCounts()
    {
        return contractMapper.selectContractStatusCounts();
    }

    /** 允许去重查询的字段白名单（防 SQL 注入） */
    private static final List<String> DISTINCT_FIELDS = Arrays.asList("client_unit", "contact_name", "contract_type");

    @Override
    public List<String> getDistinctValues(String field)
    {
        String column = field == null ? "" : field.trim();
        // 驼峰转下划线
        if (column.contains("_") == false)
        {
            column = column.replaceAll("([A-Z])", "_$1").toLowerCase();
        }
        if (!DISTINCT_FIELDS.contains(column))
        {
            throw new ServiceException("不支持的字段: " + field);
        }
        return contractMapper.selectDistinctValues(column);
    }

    /**
     * 合同列表可显隐列元数据：
     * 业务字段（含 JOIN/计算展示字段）→ 系统字段 → 动态字段（proj_field_def）
     * 物理表新增列时，未在固定清单中的列会自动追加到业务组末尾（默认隐藏）
     */
    @Override
    public List<Map<String, Object>> getListColumns()
    {
        List<Map<String, Object>> columns = new ArrayList<>();

        // ---- 业务字段（固定顺序，默认可见性 = 当前页面展示列） ----
        addColumn(columns, "contractNo", "合同编号", "text", "business", true, "contractNo");
        addColumn(columns, "contractName", "合同名称", "text", "business", true, "contractName");
        addColumn(columns, "clientUnit", "委托单位", "text", "business", true, "clientUnit");
        addColumn(columns, "contractType", "合同类型", "dict", "business", true, "contractType");
        addColumn(columns, "contractAmount", "合同金额", "money", "business", true, "contractAmount");
        addColumn(columns, "projectCount", "关联项目数", "count", "business", true, "projectCount");
        addColumn(columns, "paymentProgress", "付款进度", "progress", "business", true, "paymentProgress");
        addColumn(columns, "attachment", "附件", "attachment", "business", true, "attachment");
        addColumn(columns, "paidCount", "付款笔数", "count", "business", false, "paidCount");
        addColumn(columns, "paidTotal", "已付款总额", "money", "business", false, "paidTotal");
        addColumn(columns, "contactName", "联系人", "text", "business", true, "contactName");
        addColumn(columns, "contactPhone", "联系电话", "text", "business", false, "contactPhone");
        addColumn(columns, "signDate", "签署日期", "date", "business", true, "signDate");
        addColumn(columns, "entrustDate", "委托时间", "date", "business", false, "entrustDate");
        addColumn(columns, "auditDate", "审核日期", "date", "business", false, "auditDate");
        addColumn(columns, "returnDate", "返回日期", "date", "business", false, "returnDate");
        addColumn(columns, "finishDate", "完成日期", "date", "business", false, "finishDate");
        addColumn(columns, "archiveDate", "归档日期", "date", "business", false, "archiveDate");
        addColumn(columns, "archivePath", "归档路径", "text", "business", false, "archivePath");
        addColumn(columns, "contractPeriod", "合同期限", "text", "business", false, "contractPeriod");
        addColumn(columns, "paymentTerms", "付款条款", "text", "business", false, "paymentTerms");
        addColumn(columns, "receivedAmount", "已到账金额", "money", "business", false, "receivedAmount");
        addColumn(columns, "status", "状态", "dict", "business", true, "status");
        addColumn(columns, "isSettled", "是否结算", "text", "business", false, "isSettled");
        addColumn(columns, "remark", "备注", "text", "business", false, "remark");

        // ---- 物理表新增列自动发现（不在固定清单中的列 → 业务组末尾，默认隐藏） ----
        Set<String> known = new HashSet<>(Arrays.asList(
            "contract_no", "contract_name", "client_unit", "contact_name", "contact_phone",
            "contract_type", "contract_amount", "sign_date", "entrust_date", "audit_date",
            "return_date", "finish_date", "archive_date", "archive_path", "contract_period",
            "payment_terms", "received_amount", "status", "is_settled", "remark",
            "id", "create_by", "create_time", "update_by", "update_time", "del_flag", "extra_data"));
        List<Map<String, Object>> tableColumns = contractMapper.selectTableColumns("proj_contract");
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
        addColumn(columns, "createTime", "创建时间", "date", "system", true, "createTime");
        addColumn(columns, "updateBy", "更新人", "text", "system", false, "updateBy");
        addColumn(columns, "updateTime", "更新时间", "date", "system", false, "updateTime");

        // ---- 动态字段（用户自定义，默认隐藏，值从 extra_data JSONB 读取） ----
        List<ProjFieldDef> fieldDefs = fieldDefMapper.selectFieldDefByTableName("proj_contract");
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
