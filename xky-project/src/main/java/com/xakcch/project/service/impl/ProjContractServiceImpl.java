package com.xakcch.project.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.xakcch.common.core.domain.entity.SysDictData;
import com.xakcch.common.exception.ServiceException;
import com.xakcch.common.utils.DateUtils;
import com.xakcch.common.utils.DictUtils;
import com.xakcch.common.utils.SecurityUtils;
import com.xakcch.project.domain.ProjContract;
import com.xakcch.project.domain.ProjFieldDef;
import com.xakcch.project.domain.ProjMaterial;
import com.xakcch.project.domain.ProjProject;
import com.xakcch.project.mapper.ProjContractMapper;
import com.xakcch.project.mapper.ProjFieldDefMapper;
import com.xakcch.project.mapper.ProjMaterialMapper;
import com.xakcch.project.mapper.ProjProjectMapper;
import com.xakcch.project.report.DynamicColumnExcelExporter;
import com.xakcch.project.service.IProjContractService;
import com.xakcch.project.service.IProjNotifyService;

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

    @Autowired
    private IProjNotifyService notifyService;

    /**
     * 合同状态流转规则（字典 proj_contract_status：进行中 / 待返回 / 已完成 / 已取消）
     * <p>「待返回」= 本单位已盖章并寄出、等待客户盖章返回；客户退回则可重回「进行中」。
     * 已完成 / 已取消 为终态。
     */
    private static final Map<String, List<String>> STATUS_TRANSITIONS = new HashMap<>();
    static {
        STATUS_TRANSITIONS.put("ongoing",        Arrays.asList("pending_return", "completed", "cancelled"));
        STATUS_TRANSITIONS.put("pending_return", Arrays.asList("ongoing", "completed", "cancelled"));
        // 已完成、已取消 → 终态，不允许流转
    }

    /** 合法状态码值集合 */
    private static final List<String> VALID_STATUS = Arrays.asList("ongoing", "pending_return", "completed", "cancelled");

    /** 合同导出单次行数上限（与项目列表导出保持一致） */
    private static final int EXPORT_MAX_ROWS = 50000;

    /** 动态字段取值用：extra_data 是 JSONB 文本，逐行解析（与资料管理页同一套 Jackson 用法） */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 导出列 key → 取值器（与 getListColumns() 的 key 一一对应） */
    private static final Map<String, Function<ProjContract, Object>> EXPORT_ACCESSORS;

    /** 导出列 key → 类型（与 getListColumns() 的 type 一致，决定格式化方式） */
    private static final Map<String, String> EXPORT_TYPES;

    /** 导出列 key → 字典类型（仅 type='dict' 的列需要；不同列用不同字典，不可统一） */
    private static final Map<String, String> EXPORT_DICT_TYPES;
    static
    {
        Map<String, Function<ProjContract, Object>> a = new HashMap<>();
        a.put("contractNo", ProjContract::getContractNo);
        a.put("contractName", ProjContract::getContractName);
        a.put("clientUnit", ProjContract::getClientUnit);
        a.put("contractType", ProjContract::getContractType);
        a.put("contractAmount", ProjContract::getContractAmount);
        a.put("projectCount", ProjContract::getProjectCount);
        a.put("paidCount", ProjContract::getPaidCount);
        a.put("paidTotal", ProjContract::getPaidTotal);
        a.put("contactName", ProjContract::getContactName);
        a.put("contactPhone", ProjContract::getContactPhone);
        a.put("signDate", ProjContract::getSignDate);
        a.put("entrustDate", ProjContract::getEntrustDate);
        a.put("archiveDate", ProjContract::getArchiveDate);
        a.put("archivePath", ProjContract::getArchivePath);
        a.put("contractPeriod", ProjContract::getContractPeriod);
        a.put("paymentTerms", ProjContract::getPaymentTerms);
        a.put("receivedAmount", ProjContract::getReceivedAmount);
        a.put("status", ProjContract::getStatus);
        a.put("isSettled", ProjContract::getIsSettled);
        a.put("remark", ProjContract::getRemark);
        a.put("id", ProjContract::getId);
        a.put("createBy", ProjContract::getCreateBy);
        a.put("createTime", ProjContract::getCreateTime);
        a.put("updateBy", ProjContract::getUpdateBy);
        a.put("updateTime", ProjContract::getUpdateTime);
        // ⚠️ 刻意不含 attachment（附件）与 paymentProgress（付款进度）：
        //    两者在列表页是「预览/下载按钮」与「进度条」，导出成文本没有意义，
        //    因此取值器留空 → 即便请求里手改 fields 也会被 buildExportData 忽略。
        EXPORT_ACCESSORS = Collections.unmodifiableMap(a);

        Map<String, String> t = new HashMap<>();
        t.put("contractNo", "text");
        t.put("contractName", "text");
        t.put("clientUnit", "text");
        t.put("contractType", "dict");
        t.put("contractAmount", "money");
        t.put("projectCount", "count");
        t.put("paidCount", "count");
        t.put("paidTotal", "money");
        t.put("contactName", "text");
        t.put("contactPhone", "text");
        t.put("signDate", "date");
        t.put("entrustDate", "date");
        t.put("archiveDate", "date");
        t.put("archivePath", "text");
        t.put("contractPeriod", "text");
        t.put("paymentTerms", "text");
        t.put("receivedAmount", "money");
        t.put("status", "dict");
        t.put("isSettled", "flag");
        t.put("remark", "text");
        t.put("id", "number");
        t.put("createBy", "text");
        t.put("createTime", "date");
        t.put("updateBy", "text");
        t.put("updateTime", "date");
        EXPORT_TYPES = Collections.unmodifiableMap(t);

        Map<String, String> d = new HashMap<>();
        d.put("contractType", "proj_contract_type");
        d.put("status", "proj_contract_status");
        EXPORT_DICT_TYPES = Collections.unmodifiableMap(d);
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
     * 导出合同列表：按调用方指定的字段顺序输出「表头 + 行数据」
     */
    @Override
    public Map<String, Object> exportContracts(ProjContract contract, List<String> fields, List<Long> idScope)
    {
        ProjContract q = (contract == null) ? new ProjContract() : contract;
        // 导出与分页无关：清掉分页参数，避免调用方误传导致只导一页
        q.getParams().remove("pageNum");
        q.getParams().remove("pageSize");
        // 「仅已勾选的行」：id 集合下推到 SQL，避免先查全表再内存过滤
        if (idScope != null && !idScope.isEmpty())
        {
            q.getParams().put("exportIds", idScope);
        }
        // 导出不需要付款明细（fillPaidList 会额外查 N 次），直接查列表
        List<ProjContract> list = contractMapper.selectContractList(q);
        if (list.size() > EXPORT_MAX_ROWS)
        {
            throw new ServiceException("导出条数过多（" + list.size() + " 条，上限 "
                    + EXPORT_MAX_ROWS + " 条），请缩小筛选范围后重试");
        }
        return buildExportData(fields, list);
    }

    /**
     * 按 fields 顺序组装导出数据。
     * <p>
     * 列元数据（label / type）全部取自 {@link #getListColumns()}，不在此处另建清单，
     * 避免与列表显隐列面板分叉；未知 key 一律忽略（防止导出任意内部字段）。
     */
    private Map<String, Object> buildExportData(List<String> fields, List<ProjContract> list)
    {
        Map<String, Map<String, Object>> metaMap = new HashMap<>();
        for (Map<String, Object> c : getListColumns())
        {
            metaMap.put(String.valueOf(c.get("key")), c);
        }
        // 字典列整批预载：每个字典类型只取一次（详见 loadDictMaps 注释）
        Map<String, Map<String, String>> dictMaps = loadDictMaps(fields);

        List<DynamicColumnExcelExporter.Column> headers = new ArrayList<>();
        List<String> validKeys = new ArrayList<>();
        if (fields != null)
        {
            for (String key : fields)
            {
                if (key == null || key.trim().isEmpty())
                {
                    continue;
                }
                String k = key.trim();
                Map<String, Object> meta = metaMap.get(k);
                if (meta == null)
                {
                    continue;
                }
                // 动态字段（extra_data JSONB）不在静态取值器表里，走 JSON 解析；
                // 其余列必须有取值器，否则（附件 / 付款进度这类渲染型列）跳过，避免导出空列
                boolean dynamic = "dynamic".equals(String.valueOf(meta.get("group")));
                if (!dynamic && !EXPORT_ACCESSORS.containsKey(k))
                {
                    continue;
                }
                String type = String.valueOf(meta.get("type"));
                // 金额 / 计数列导出右对齐，其余左对齐
                boolean numeric = "money".equals(type) || "count".equals(type) || "number".equals(type);
                headers.add(new DynamicColumnExcelExporter.Column(
                        String.valueOf(meta.get("label")), widthOf(type), numeric));
                validKeys.add(k);
            }
        }

        List<List<Object>> rows = new ArrayList<>();
        for (ProjContract c : list)
        {
            List<Object> row = new ArrayList<>();
            for (String key : validKeys)
            {
                row.add(formatValue(c, key, dictMaps));
            }
            rows.add(row);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("headers", headers);
        result.put("rows", rows);
        result.put("total", rows.size());
        return result;
    }

    /**
     * 按导出字段涉及到的字典类型，一次性加载 value → label 映射。
     * <p>
     * ⚠️ 性能要点：{@link DictUtils#getDictLabel} 每次调用都会「Redis 读 + 全量反序列化
     * + 重建 HashMap」（项目列表导出实测单个字典单元格 ≈8.4ms，3743 行 × 16 字段 = 67s）。
     * 改为预先按字典类型建映射后，逐行纯内存查表。
     */
    private Map<String, Map<String, String>> loadDictMaps(List<String> fields)
    {
        Map<String, Map<String, String>> result = new HashMap<>();
        if (fields == null)
        {
            return result;
        }
        for (String key : fields)
        {
            if (key == null)
            {
                continue;
            }
            String dictType = EXPORT_DICT_TYPES.get(key.trim());
            if (dictType == null || result.containsKey(dictType))
            {
                continue;
            }
            Map<String, String> map = new HashMap<>();
            List<SysDictData> datas = DictUtils.getDictCache(dictType);
            if (datas != null)
            {
                for (SysDictData d : datas)
                {
                    if (d != null && d.getDictValue() != null)
                    {
                        map.put(d.getDictValue(), d.getDictLabel());
                    }
                }
            }
            result.put(dictType, map);
        }
        return result;
    }

    /** 单个单元格取值：字典转中文标签、日期统一 yyyy-MM-dd、是否结算转中文 */
    private Object formatValue(ProjContract c, String key, Map<String, Map<String, String>> dictMaps)
    {
        Function<ProjContract, Object> acc = EXPORT_ACCESSORS.get(key);
        if (acc == null)
        {
            // 无静态取值器 ⇒ 动态字段：值在 extra_data（JSONB）里，按 key 取
            return extraDataValue(c, key);
        }
        Object raw = acc.apply(c);
        if (raw == null || "".equals(raw))
        {
            return "";
        }
        String type = EXPORT_TYPES.get(key);
        if ("dict".equals(type))
        {
            String dictType = EXPORT_DICT_TYPES.get(key);
            if (dictType != null)
            {
                String val = String.valueOf(raw);
                Map<String, String> dm = dictMaps.get(dictType);
                String label = (dm == null) ? null : dm.get(val);
                if (label == null)
                {
                    // 预载缺失（字典在导出期间被改过）才回落逐次查询，保证不丢标签
                    label = DictUtils.getDictLabel(dictType, val);
                }
                return (label == null || label.isEmpty()) ? val : label;
            }
            return String.valueOf(raw);
        }
        if ("date".equals(type))
        {
            // 用全限定名：本文件已有 java.sql.Date 相关用法，避免额外 import 引入同名冲突
            return (raw instanceof java.util.Date)
                    ? DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD, (java.util.Date) raw)
                    : String.valueOf(raw);
        }
        if ("flag".equals(type))
        {
            // 「是否结算」是 0/1 标志位，与列表页 settledFlagText 保持一致
            String v = String.valueOf(raw).trim();
            if ("1".equals(v))
            {
                return "已结算";
            }
            if ("0".equals(v))
            {
                return "未结算";
            }
            return v;
        }
        // 金额 / 计数 / ID 保留数字，导出右对齐
        if (raw instanceof Number)
        {
            return raw;
        }
        return String.valueOf(raw);
    }

    /**
     * 动态字段取值：从 extra_data（JSONB 文本）里按 key 取，多值数组用「、」连接
     * —— 与列表页 extraText() 的展示口径保持一致（项目类型存的就是数组）。
     */
    private Object extraDataValue(ProjContract c, String key)
    {
        String json = c.getExtraData();
        if (json == null || json.trim().isEmpty())
        {
            return "";
        }
        try
        {
            JsonNode node = MAPPER.readTree(json).get(key);
            if (node == null || node.isNull())
            {
                return "";
            }
            if (node.isArray())
            {
                StringBuilder sb = new StringBuilder();
                for (JsonNode n : node)
                {
                    String s = n.isValueNode() ? n.asText() : n.toString();
                    if (s != null && !s.isEmpty())
                    {
                        if (sb.length() > 0)
                        {
                            sb.append('、');
                        }
                        sb.append(s);
                    }
                }
                return sb.toString();
            }
            return node.isValueNode() ? node.asText() : node.toString();
        }
        catch (Exception e)
        {
            // 单行 JSON 坏了不能拖垮整批导出
            return "";
        }
    }

    /** 列宽（字符数）：日期/字典窄列，文本与金额宽列；与前端列宽视觉保持一致 */
    private int widthOf(String type)
    {
        if ("date".equals(type))
        {
            return 12;
        }
        if ("dict".equals(type) || "count".equals(type))
        {
            return 10;
        }
        if ("money".equals(type))
        {
            return 18;
        }
        return 16;
    }

    /**
     * 校验合同编号是否唯一
     */
    @Override
    public boolean checkContractNoUnique(ProjContract contract)
    {
        // 合同编号允许为空（编号须等合同签署完成后才有）：空编号不参与唯一性校验。
        // 否则第二份无编号合同会命中第一份的空串，被误报「合同编号已存在」。
        if (contract.getContractNo() == null || contract.getContractNo().trim().isEmpty())
        {
            return true;
        }
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
        // 新增合同 → 事务提交后给档案管理员派发「合同待签署」待办
        // ⚠️ 合同导入走 ProjContractImportServiceImpl 直连 mapper，不经过本方法，故不会推送（已与客户确认）
        if (rows > 0 && contract.getId() != null)
        {
            registerContractNotify(contract.getId());
        }
        return rows;
    }

    /**
     * 注册"合同新增事务提交后"的通知派发（afterCommit，防回滚已推送）。
     * 操作人角色判定在通知服务内完成：操作人自身含 role-doc 则跳过。
     */
    private void registerContractNotify(Long contractId)
    {
        final String operator;
        final Long operatorUserId;
        try
        {
            operator = SecurityUtils.getUsername();
            operatorUserId = SecurityUtils.getUserId();
        }
        catch (Exception ex)
        {
            // 非登录上下文（理论不出现）：按无操作人处理，角色判定跳过
            notifyService.onContractCreated(contractId, "system", null);
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive())
        {
            // 无事务上下文：直接派发
            notifyService.onContractCreated(contractId, operator, operatorUserId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
        {
            @Override
            public void afterCommit()
            {
                try
                {
                    notifyService.onContractCreated(contractId, operator, operatorUserId);
                }
                catch (Exception ex)
                {
                    // 通知失败不影响合同建档主流程
                    ex.printStackTrace();
                }
            }
        });
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
        String currentStatus = normalizeContractStatus(contract.getStatus());
        // 终态不可变更
        if ("completed".equals(currentStatus) || "cancelled".equals(currentStatus))
        {
            throw new ServiceException("当前状态【" + currentStatus + "】为终态，不允许变更");
        }
        if (targetStatus == null || targetStatus.trim().isEmpty())
        {
            throw new ServiceException("请选择目标状态");
        }
        if (!VALID_STATUS.contains(targetStatus))
        {
            throw new ServiceException("不支持的目标状态【" + targetStatus + "】");
        }
        // 校验流转规则
        List<String> allowed = STATUS_TRANSITIONS.get(currentStatus);
        if (allowed == null || !allowed.contains(targetStatus))
        {
            throw new ServiceException("不允许从【" + currentStatus + "】变更为【" + targetStatus + "】");
        }
        return contractMapper.updateContractStatus(id, targetStatus);
    }

    /** 历史/空状态归一：空值视为「进行中」（与数据库迁移口径一致） */
    private String normalizeContractStatus(String status)
    {
        if (status == null || status.trim().isEmpty())
        {
            return "ongoing";
        }
        return status;
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
        addColumn(columns, "archiveDate", "归档日期", "date", "business", false, "archiveDate");
        addColumn(columns, "archivePath", "归档路径", "text", "business", false, "archivePath");
        addColumn(columns, "contractPeriod", "合同期限", "text", "business", false, "contractPeriod");
        addColumn(columns, "paymentTerms", "付款条款", "text", "business", false, "paymentTerms");
        addColumn(columns, "receivedAmount", "已到账金额", "money", "business", false, "receivedAmount");
        addColumn(columns, "status", "状态", "dict", "business", true, "status");
        addColumn(columns, "isSettled", "是否结算", "text", "business", false, "isSettled");
        addColumn(columns, "remark", "备注", "text", "business", false, "remark");

        // ---- 物理表新增列自动发现（不在固定清单中的列 → 业务组末尾，默认隐藏） ----
        // 注：audit_date / return_date / finish_date 已停止录入，但物理列保留在库中，
        //     仍在 known 白名单里，避免被「物理列自动发现」重新加回可显隐列清单。
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
