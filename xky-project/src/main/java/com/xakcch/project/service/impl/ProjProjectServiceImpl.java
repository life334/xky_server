package com.xakcch.project.service.impl;

import java.time.LocalDate;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.xakcch.common.exception.ServiceException;
import com.xakcch.common.utils.DateUtils;
import com.xakcch.common.core.domain.entity.SysDictData;
import com.xakcch.common.utils.DictUtils;
import com.xakcch.common.utils.SecurityUtils;
import com.xakcch.common.utils.WorkdayUtils;
import com.xakcch.project.domain.ProjCategory;
import com.xakcch.project.domain.ProjFieldDef;
import com.xakcch.project.domain.ProjMaterial;
import com.xakcch.project.domain.ProjPayment;
import com.xakcch.project.domain.ProjProject;
import com.xakcch.project.domain.ProjTask;
import com.xakcch.project.mapper.ProjCategoryMapper;
import com.xakcch.project.mapper.ProjCollectionMapper;
import com.xakcch.project.mapper.ProjFieldDefMapper;
import com.xakcch.project.mapper.ProjLeaderMapper;
import com.xakcch.project.mapper.ProjMaterialFlowMapper;
import com.xakcch.project.mapper.ProjMaterialMapper;
import com.xakcch.project.mapper.ProjPaymentMapper;
import com.xakcch.project.mapper.ProjProjectMapper;
import com.xakcch.project.mapper.ProjTaskMapper;
import com.xakcch.project.mapper.ProjWorkloadMapper;
import com.xakcch.project.report.DynamicColumnExcelExporter;
import com.xakcch.project.service.IProjProjectService;
import com.xakcch.project.service.IProjDashboardService;
import com.xakcch.project.service.IProjNotifyService;
import com.xakcch.project.service.IProjPriceRecalcService;
import com.xakcch.project.service.IProjMandateService;
import com.xakcch.system.mapper.SysUserMapper;
import com.xakcch.system.service.ISysWorkdayCalendarService;
import com.xakcch.common.core.domain.entity.SysUser;

/**
 * 项目主表 服务层实现
 *
 * @author liuyonghui
 */
@Service
public class ProjProjectServiceImpl implements IProjProjectService
{
    /** 导出单次的条数上限（防止误操作拖垮内存） */
    private static final int EXPORT_MAX_ROWS = 50000;

    /** 导出列 key → 取值器（与 getListColumns() 的 key 一一对应） */
    private static final Map<String, Function<ProjProject, Object>> EXPORT_ACCESSORS;

    /** 导出列 key → 类型（与 getListColumns() 的 type 一致，决定格式化方式） */
    private static final Map<String, String> EXPORT_TYPES;

    /** 导出列 key → 字典类型（仅 type='dict' 的列需要；不同列用不同字典，不可统一） */
    private static final Map<String, String> EXPORT_DICT_TYPES;

    /** 状态流转规则：key=当前状态, value=允许流转到的目标状态列表 */
    private static final Map<String, List<String>> STATUS_FLOW = new HashMap<>();
    static
    {
        STATUS_FLOW.put("ongoing",  Arrays.asList("closed"));
        STATUS_FLOW.put("closed",   Arrays.asList("archived"));
        STATUS_FLOW.put("archived", Arrays.asList());

        Map<String, Function<ProjProject, Object>> a = new HashMap<>();
        a.put("projectCode", ProjProject::getProjectCode);
        a.put("projectName", ProjProject::getProjectName);
        a.put("engineeringProject", ProjProject::getEngineeringProject);
        a.put("categoryName", ProjProject::getCategoryName);
        a.put("clientUnit", ProjProject::getClientUnit);
        a.put("clientUnitNature", ProjProject::getClientUnitNature);
        a.put("contactName", ProjProject::getContactName);
        a.put("contactPhone", ProjProject::getContactPhone);
        a.put("projectLocation", ProjProject::getProjectLocation);
        a.put("status", ProjProject::getStatus);
        a.put("closeTime", ProjProject::getCloseTime);
        a.put("projectNature", ProjProject::getProjectNature);
        a.put("contractName", ProjProject::getContractName);
        a.put("contractNo", ProjProject::getContractNo);
        a.put("contractAmount", ProjProject::getContractAmount);
        a.put("internalOutput", ProjProject::getInternalOutput);
        a.put("externalOutput", ProjProject::getExternalOutput);
        a.put("leaderNames", ProjProject::getLeaderNames);
        a.put("assignDate", ProjProject::getAssignDate);
        a.put("durationRequire", ProjProject::getDurationRequire);
        a.put("totalDuration", ProjProject::getTotalDuration);
        a.put("relatedProjectCode", ProjProject::getRelatedProjectCode);
        a.put("dataSource", ProjProject::getDataSource);
        a.put("remark", ProjProject::getRemark);
        a.put("id", ProjProject::getId);
        a.put("createBy", ProjProject::getCreateBy);
        a.put("createTime", ProjProject::getCreateTime);
        a.put("updateBy", ProjProject::getUpdateBy);
        a.put("updateTime", ProjProject::getUpdateTime);
        EXPORT_ACCESSORS = Collections.unmodifiableMap(a);

        Map<String, String> t = new HashMap<>();
        t.put("projectCode", "text");
        t.put("projectName", "text");
        t.put("engineeringProject", "text");
        t.put("categoryName", "text");
        t.put("clientUnit", "text");
        t.put("clientUnitNature", "dict");
        t.put("contactName", "text");
        t.put("contactPhone", "text");
        t.put("projectLocation", "text");
        t.put("status", "dict");
        t.put("closeTime", "date");
        t.put("projectNature", "dict");
        t.put("contractName", "text");
        t.put("contractNo", "text");
        t.put("contractAmount", "number");
        t.put("internalOutput", "number");
        t.put("externalOutput", "number");
        t.put("leaderNames", "text");
        t.put("assignDate", "date");
        t.put("durationRequire", "duration");
        t.put("totalDuration", "total");
        t.put("relatedProjectCode", "text");
        t.put("dataSource", "dict");
        t.put("remark", "text");
        t.put("id", "number");
        t.put("createBy", "text");
        t.put("createTime", "date");
        t.put("updateBy", "text");
        t.put("updateTime", "date");
        EXPORT_TYPES = Collections.unmodifiableMap(t);

        Map<String, String> d = new HashMap<>();
        d.put("status", "proj_project_status");
        d.put("clientUnitNature", "proj_client_unit_nature");
        d.put("projectNature", "proj_project_nature");
        d.put("dataSource", "proj_project_source");
        EXPORT_DICT_TYPES = Collections.unmodifiableMap(d);
    }

    @Autowired
    private ProjProjectMapper projectMapper;

    @Autowired
    private IProjDashboardService dashboardService;

    @Autowired
    private IProjNotifyService notifyService;

    @Autowired
    private ProjLeaderMapper leaderMapper;

    @Autowired
    private ProjCategoryMapper categoryMapper;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private ProjTaskMapper taskMapper;

    @Autowired
    private ProjMaterialMapper materialMapper;

    @Autowired
    private ProjPaymentMapper paymentMapper;

    @Autowired
    private ProjWorkloadMapper workloadMapper;

    @Autowired
    private ProjMaterialFlowMapper materialFlowMapper;

    @Autowired
    private ProjCollectionMapper collectionMapper;

    @Autowired
    private ProjFieldDefMapper fieldDefMapper;

    @Autowired
    private ISysWorkdayCalendarService workdayCalendarService;

    @Autowired
    private IProjPriceRecalcService priceRecalcService;

    @Autowired
    private IProjMandateService mandateService;

    /**
     * 查询项目详情
     */
    @Override
    public ProjProject selectProjectById(Long id)
    {
        ProjProject project = projectMapper.selectProjectById(id);
        if (project != null)
        {
            // 回填负责人ID列表，供前端表单回显
            project.setLeaderIds(leaderMapper.selectLeaderIdsByProjectId(id));
            // 回填首笔付款数据
            fillFirstPayment(project);
        }
        return project;
    }

    /**
     * 查询项目列表
     */
    @Override
    public List<ProjProject> selectProjectList(ProjProject project)
    {
        prepareOverdueFilter(project);
        List<ProjProject> list = projectMapper.selectProjectList(project);
        // 逐行标记「在办超期」（背景/字体高亮用）：口径来自驾驶舱唯一算法，与 overdue 筛选同源。
        // 仅当前页行做映射，避免逐行查库；候选集为空时零成本（当前库全为导入项目，恒为空）。
        java.util.Map<Long, Integer> overdueDays = dashboardService.getOngoingOverdueDays();
        if (!overdueDays.isEmpty())
        {
            for (ProjProject p : list)
            {
                if (p.getId() != null)
                {
                    Integer d = overdueDays.get(p.getId());
                    if (d != null)
                    {
                        p.setOverdueDays(d);
                    }
                }
            }
        }
        return list;
    }

    /**
     * 按列表筛选条件查询命中的项目 id（「选中全部筛选结果」批量操作用）
     */
    @Override
    public List<Long> selectProjectIdsByFilter(ProjProject project)
    {
        prepareOverdueFilter(project);
        return projectMapper.selectProjectIdsByFilter(project);
    }

    /**
     * 超期筛选预处理（首页驾驶舱下钻）：工作日口径的超期项目 id 集合由驾驶舱服务统一计算（逻辑唯一出处）。
     * <p>
     * 列表查询（selectProjectList）与「按筛选条件批量操作」（selectProjectIdsByFilter）
     * 共用此方法，避免两处各算一次 ⇒ 口径必然一致。
     */
    private void prepareOverdueFilter(ProjProject project)
    {
        if (project == null || !"true".equals(project.getOverdue()))
        {
            return;
        }
        java.util.List<Long> overdueIds = dashboardService.getOngoingOverdueProjectIds();
        if (overdueIds.isEmpty())
        {
            project.getParams().put("overdueEmpty", Boolean.TRUE);
        }
        else
        {
            project.getParams().put("overdueIds", overdueIds);
        }
    }

    /**
     * 导出项目列表：按调用方指定的字段顺序输出「表头 + 行数据」
     */
    @Override
    public Map<String, Object> exportProjects(ProjProject query, List<String> fields, List<Long> idScope)
    {
        ProjProject q = (query == null) ? new ProjProject() : query;
        // 导出与分页无关：清掉分页参数，避免调用方误传导致只导一页
        q.getParams().remove("pageNum");
        q.getParams().remove("pageSize");
        if (idScope != null && !idScope.isEmpty())
        {
            q.getParams().put("exportIds", idScope);
        }
        prepareOverdueFilter(q);
        List<ProjProject> list = projectMapper.selectProjectList(q);
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
    private Map<String, Object> buildExportData(List<String> fields, List<ProjProject> list)
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
                Map<String, Object> meta = metaMap.get(key.trim());
                if (meta == null)
                {
                    continue;
                }
                String type = String.valueOf(meta.get("type"));
                // 数值列（合同金额 / 内部产值 / 外部产值）导出右对齐，其余左对齐
                headers.add(new DynamicColumnExcelExporter.Column(
                        String.valueOf(meta.get("label")), widthOf(type), "number".equals(type)));
                validKeys.add(key.trim());
            }
        }

        // 进行中项目的「总时长」需实时计算：整批只拉一次日历，再逐行复用（避免 N 行 N 次请求）
        List<ProjProject> pending = new ArrayList<>();
        LocalDate earliest = null;
        for (ProjProject p : list)
        {
            if (!isFinished(p) && p.getAssignDate() != null)
            {
                LocalDate a = WorkdayUtils.toLocalDate(p.getAssignDate());
                if (earliest == null || a.isBefore(earliest))
                {
                    earliest = a;
                }
                pending.add(p);
            }
        }
        LocalDate today = LocalDate.now();
        Map<LocalDate, String> calendarMap = (earliest == null)
                ? null : workdayCalendarService.getCalendarMap(earliest, today);
        Map<Long, Integer> liveDuration = new HashMap<>();
        for (ProjProject p : pending)
        {
            LocalDate a = WorkdayUtils.toLocalDate(p.getAssignDate());
            liveDuration.put(p.getId(),
                    WorkdayUtils.countWorkdays(a, today, calendarMap));
        }

        List<List<Object>> rows = new ArrayList<>();
        for (ProjProject p : list)
        {
            List<Object> row = new ArrayList<>();
            for (String key : validKeys)
            {
                row.add(formatValue(p, key, liveDuration, dictMaps));
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
     * + 重建 HashMap」，实测单个字典单元格 ≈8.4ms（632 行 × 1 字典列 = 5.3s，
     * 全量 3743 行 × 16 字段 = 67s）。改为预先按字典类型建映射后，1.1 万次 Redis 读压缩到 3 次。
     * 字典变更（后台维护字典）不影响正确性：未命中映射时会回落到逐次查询。
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

    /** 单个单元格取值：字典转中文标签、日期统一 yyyy-MM-dd、总时长在办时取实时值 */
    private Object formatValue(ProjProject p, String key, Map<Long, Integer> liveDuration,
                               Map<String, Map<String, String>> dictMaps)
    {
        Function<ProjProject, Object> acc = EXPORT_ACCESSORS.get(key);
        Object raw = (acc == null) ? null : acc.apply(p);
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
        if ("duration".equals(type))
        {
            return String.valueOf(raw) + "天";
        }
        if ("total".equals(type))
        {
            if (isFinished(p))
            {
                return raw + "天";
            }
            Integer d = liveDuration.get(p.getId());
            return (d == null) ? "" : (d + "天");
        }
        // 数值列（合同金额 / 内部产值 / 外部产值）保留数字，导出右对齐
        if (raw instanceof Number)
        {
            return raw;
        }
        return String.valueOf(raw);
    }

    /** 已办结 / 已归档：总时长为冻结值 */
    private boolean isFinished(ProjProject p)
    {
        String st = p.getStatus();
        return "closed".equals(st) || "archived".equals(st);
    }

    /** 列宽（字符数）：日期/字典/工期窄列，文本与数值宽列；与前端 colWidth 视觉保持一致 */
    private int widthOf(String type)
    {
        if ("date".equals(type))
        {
            return 12;
        }
        if ("duration".equals(type) || "total".equals(type) || "dict".equals(type))
        {
            return 10;
        }
        return 16;
    }

    /**
     * 统计费用结算页录入状态全局数量
     */
    @Override
    public Map<String, Object> selectEntryStatusCounts(ProjProject project)
    {
        return projectMapper.selectEntryStatusCounts(project);
    }

    /**
     * 校验工程编号是否唯一
     */
    @Override
    public boolean checkProjectCodeUnique(ProjProject project)
    {
        Long id = project.getId() == null ? -1L : project.getId();
        ProjProject info = projectMapper.checkProjectCodeUnique(project);
        if (info != null && info.getId().longValue() != id.longValue())
        {
            return false;
        }
        return true;
    }

    /**
     * 新增项目（含负责人关联 + 自动创建任务）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertProject(ProjProject project)
    {
        // 默认状态
        if (project.getStatus() == null || project.getStatus().isEmpty())
        {
            project.setStatus("ongoing");
        }
        // 项目来源（手动新增）
        if (project.getDataSource() == null || project.getDataSource().isEmpty())
        {
            project.setDataSource("manual");
        }
        // 指令性任务自动打标：委托单位命中规则关键词 ⇒ 项目性质 = mandate
        // （仅新建时判定，避免覆盖人工在项目列表里的批量调整）
        project.setProjectNature(mandateService.resolveNature(project.getClientUnit()));
        // 项目类别：名称↔ID 互转（保证 engineering_project 与 project_category_id 永远一致）
        resolveCategoryAndLeaders(project);
        // 总时长自动计算（安排日期 → 今天，仅工作日；未办结时保存即重算）
        recalcTotalDuration(project);
        int rows = projectMapper.insertProject(project);

        // 插入负责人关联
        Long[] leaderIds = project.getLeaderIds();
        if (leaderIds != null && leaderIds.length > 0)
        {
            leaderMapper.insertProjectLeaders(project.getId(), leaderIds, project.getCreateBy());
            // 为每个负责人自动创建任务
            createTasksForLeaders(project, Arrays.asList(leaderIds), project.getCreateBy());
        }

        // 保存首笔付款（如有）
        saveFirstPayment(project);

        return rows;
    }

    /**
     * 修改项目（含负责人关联 + 任务增量同步）
     * 新增的负责人 → 创建任务
     * 移除的负责人 → 逻辑删除任务
     * 保留的负责人 → 不动（保留已编辑的工期等信息）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateProject(ProjProject project)
    {
        // 查询变更前的项目（用于来源保持 + 合同关联变化判定）
        ProjProject before = projectMapper.selectProjectById(project.getId());

        // 项目来源（编辑不改来源；若前端未传则保持原值）
        if (project.getDataSource() == null || project.getDataSource().isEmpty())
        {
            if (before != null)
            {
                project.setDataSource(before.getDataSource());
            }
        }

        // 重算触发点一：合同关联是否发生变化（0→A / A→B / A→0）
        Long oldContractId = (before != null) ? before.getContractId() : null;
        Long newContractId = project.getContractId();
        boolean contractChanged = (oldContractId == null && newContractId != null)
            || (oldContractId != null && !oldContractId.equals(newContractId));

        // 总时长兜底：已办结/已归档项目的总时长是冻结快照，
        // 前端不传（或传 null）时回填原值，避免被下面的无条件覆盖写冲成空。
        // （mapper 的 updateProject 里 total_duration 是无条件赋值，没有 <if> 判空）
        if (before != null && project.getTotalDuration() == null && before.getTotalDuration() != null
                && ("closed".equals(before.getStatus()) || "archived".equals(before.getStatus())))
        {
            project.setTotalDuration(before.getTotalDuration());
        }

        // 项目类别：名称↔ID 互转（编辑改「项目类别」文本时同步刷新 project_category_id）
        resolveCategoryAndLeaders(project);
        // 总时长自动计算（安排日期 → 今天，仅工作日；已办结/已归档保持冻结值）
        recalcTotalDuration(project);
        int rows = projectMapper.updateProject(project);

        // 先删后插，更新负责人关联
        leaderMapper.deleteLeadersByProjectId(project.getId());
        Long[] leaderIds = project.getLeaderIds();
        List<Long> newLeaderList = (leaderIds != null) ? Arrays.asList(leaderIds) : new ArrayList<>();
        if (!newLeaderList.isEmpty())
        {
            leaderMapper.insertProjectLeaders(project.getId(), leaderIds, project.getUpdateBy());
        }

        // 增量同步任务
        syncTasksOnLeaderChange(project, newLeaderList, project.getUpdateBy());

        // 保存首笔付款（如有）
        saveFirstPayment(project);

        // 合同关联变化 → 全量重算该项目所有外部工作量单价（先恢复所有单价再按原则重算，含历史手改价）
        if (contractChanged)
        {
            priceRecalcService.recalcExternalPrices(project.getId(), null, true);
        }

        return rows;
    }

    /**
     * 删除项目（含全部关联数据清理）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteProjectById(Long id)
    {
        cascadeDeleteProject(id);
        return projectMapper.deleteProjectById(id);
    }

    /**
     * 批量删除项目（含全部关联数据清理）
     * 采用按项目ID数组批量 IN 删除关联表，避免逐项目循环导致的大量 SQL 往返（如 50 个项目 7 张表 = 350 次 UPDATE → 7 次）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteProjectByIds(Long[] ids)
    {
        cascadeDeleteProjects(ids);
        return projectMapper.deleteProjectByIds(ids);
    }

    /**
     * 级联逻辑删除项目下的全部关联数据
     * 顺序：负责人 → 任务 → 工作量 → 付款 → 催收记录 → 资料流转 → 资料
     * 注意：合同（proj_contract）及其附件为独立实体，一个合同可挂多个项目，不随项目删除
     *
     * @param projectId 项目ID
     */
    private void cascadeDeleteProject(Long projectId)
    {
        leaderMapper.deleteLeadersByProjectId(projectId);       // 负责人关联
        taskMapper.deleteTasksByProjectId(projectId);           // 任务
        workloadMapper.deleteWorkloadsByProjectId(projectId);   // 工作量（费用结算）
        paymentMapper.deletePaymentsByProjectId(projectId);     // 付款记录（费用结算）
        collectionMapper.deleteLogsByProjectId(projectId);      // 催收记录（回款管理）
        materialFlowMapper.deleteFlowsByProjectId(projectId);   // 资料流转记录
        materialMapper.deleteMaterialsByProjectId(projectId);   // 资料提交（资料管理）
    }

    /**
     * 批量级联逻辑删除多个项目下的全部关联数据（每张关联表一条 IN 删除）
     * 顺序：负责人 → 任务 → 工作量 → 付款 → 催收记录 → 资料流转 → 资料
     *
     * @param projectIds 项目ID数组
     */
    private void cascadeDeleteProjects(Long[] projectIds)
    {
        leaderMapper.deleteLeadersByProjectIds(projectIds);       // 负责人关联
        taskMapper.deleteTasksByProjectIds(projectIds);           // 任务
        workloadMapper.deleteWorkloadsByProjectIds(projectIds);   // 工作量（费用结算）
        paymentMapper.deletePaymentsByProjectIds(projectIds);     // 付款记录（费用结算）
        collectionMapper.deleteLogsByProjectIds(projectIds);      // 催收记录（回款管理）
        materialFlowMapper.deleteFlowsByProjectIds(projectIds);   // 资料流转记录
        materialMapper.deleteMaterialsByProjectIds(projectIds);   // 资料提交（资料管理）
    }

    /**
     * 办结项目（状态改为已办结，不可逆）
     * 同时自动创建资料记录，使项目出现在资料管理列表中
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int completeProject(Long id)
    {
        ProjProject project = projectMapper.selectProjectById(id);
        if (project == null)
        {
            throw new ServiceException("项目不存在");
        }
        if ("closed".equals(project.getStatus()))
        {
            throw new ServiceException("该项目已办结，不能重复操作");
        }
        // 办结快照：冻结总时长（安排日期 → 今天，仅工作日），此后不再变化
        snapshotTotalDuration(project);
        int rows = projectMapper.completeProject(id);
        // 自动创建资料记录（默认待领取、待提交；联系人/电话默认取项目当前值，编辑页可改）
        ProjMaterial material = new ProjMaterial();
        material.setProjectId(id);
        material.setStatus("pending");
        material.setSubmitStatus("pending");
        material.setContactName(project.getContactName());
        material.setContactPhone(project.getContactPhone());
        materialMapper.insertMaterial(material);
        // 事务提交后派发通知（档案管理/生产管理待办 + 管理员/质量管理消息；幂等）
        registerCloseNotify(id);
        return rows;
    }

    /**
     * 人工修正项目办结时间（受限操作）
     *
     * <p>办结时间决定产值归属月份、回款应收账期、欠款逾期天数，改之前要求项目处于 closed / archived
     * （进行中项目没有办结时间语义），且不晚于当前时刻；下界不设限（可早于安排日期 / 创建时间）。
     *
     * <p>改完后总时长快照的处理分两种：
     * <ul>
     *   <li>办结 ≥ 安排日期：按「安排日期 → 新办结日期」重算工作日数（正常路径）</li>
     *   <li>办结 &lt; 安排日期：区间反向，转而<b>把安排日期前移</b>到「往前数第 N 个工作日」，
     *       让总时长快照保持原值不变（N = 原总时长）。原总时长为空时无从反推，安排日期与总时长都不动</li>
     * </ul>
     *
     * @return {@code rows} 更新行数；{@code assignDateShifted} 是否前移了安排日期；
     *         {@code newAssignDate} 前移后的安排日期（yyyy-MM-dd）；{@code totalDuration} 保持的总时长
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateProjectCloseTime(Long id, java.util.Date closeTime)
    {
        if (id == null)
        {
            throw new ServiceException("项目ID不能为空");
        }
        if (closeTime == null)
        {
            throw new ServiceException("请选择新的办结时间");
        }
        ProjProject project = projectMapper.selectProjectById(id);
        if (project == null)
        {
            throw new ServiceException("项目不存在或已删除");
        }
        String status = project.getStatus();
        if (!"closed".equals(status) && !"archived".equals(status))
        {
            throw new ServiceException("仅「已办结 / 已归档」的项目可以修改办结时间");
        }

        // 上界仍卡当前时刻（办结不可能发生在未来）；下界不设限，允许早于安排日期 / 创建时间
        if (closeTime.after(new java.util.Date()))
        {
            throw new ServiceException("办结时间不能晚于当前时间");
        }

        Map<String, Object> result = new HashMap<>();
        int rows = projectMapper.updateCloseTimeManually(id, closeTime);
        result.put("rows", rows);

        LocalDate closeDay = WorkdayUtils.toLocalDate(closeTime);
        LocalDate assign = project.getAssignDate() == null ? null : WorkdayUtils.toLocalDate(project.getAssignDate());
        if (assign != null && closeDay != null && closeDay.isBefore(assign))
        {
            // 办结早于安排日期：区间反向。总时长是考核用的冻结快照，不能改，所以反向挪安排日期
            Integer keep = project.getTotalDuration();
            if (keep != null && keep > 0)
            {
                // 日历往前多取一段：反推要跨过 keep 个工作日（含法定假日调休，按 3 倍冗余取）
                Map<LocalDate, String> cal = workdayCalendarService.getCalendarMap(
                        closeDay.minusDays(keep * 3L + 60), closeDay);
                LocalDate newAssign = WorkdayUtils.backwardWorkdays(closeDay, keep, cal);
                projectMapper.updateAssignDateManually(id, java.sql.Date.valueOf(newAssign));
                result.put("assignDateShifted", Boolean.TRUE);
                result.put("newAssignDate", newAssign.toString());
                result.put("totalDuration", keep);
                return result;
            }
            result.put("assignDateShifted", Boolean.FALSE);
            result.put("reason", "原总时长为空，未调整安排日期");
            return result;
        }

        // 正常路径：总时长快照跟随新办结时间重算（安排日期 → 办结日期，仅工作日，含头含尾）
        resnapshotTotalDuration(project, closeDay);
        return result;
    }

    /** 按给定的办结日期重算总时长快照；无安排日期则不动（保持现状语义） */
    private void resnapshotTotalDuration(ProjProject project, LocalDate closeDay)
    {
        if (project.getAssignDate() == null || closeDay == null)
        {
            return;
        }
        LocalDate assign = WorkdayUtils.toLocalDate(project.getAssignDate());
        if (closeDay.isBefore(assign))
        {
            return; // 防御：办结早于安排日期的情况已在上层分流去挪安排日期，正常路径不该走到这里
        }
        Map<LocalDate, String> calendarMap = workdayCalendarService.getCalendarMap(assign, closeDay);
        projectMapper.updateProjectTotalDuration(project.getId(),
                WorkdayUtils.countWorkdays(assign, closeDay, calendarMap));
    }

    /**
     * 变更项目状态（校验流转规则）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int changeProjectStatus(Long id, String targetStatus)
    {
        ProjProject project = projectMapper.selectProjectById(id);
        if (project == null)
        {
            throw new ServiceException("项目不存在");
        }
        String currentStatus = project.getStatus();
        if (currentStatus == null || currentStatus.isEmpty())
        {
            throw new ServiceException("项目当前状态异常");
        }
        if (currentStatus.equals(targetStatus))
        {
            throw new ServiceException("项目已处于该状态");
        }
        List<String> allowed = STATUS_FLOW.get(currentStatus);
        if (allowed == null || !allowed.contains(targetStatus))
        {
            throw new ServiceException("状态不允许从[" + currentStatus + "]变更为[" + targetStatus + "]");
        }
        // 流转到已办结：冻结总时长快照
        if ("closed".equals(targetStatus))
        {
            snapshotTotalDuration(project);
            // 事务提交后派发通知（与办结按钮同一收口）
            registerCloseNotify(id);
        }
        return projectMapper.updateProjectStatus(id, targetStatus);
    }

    /**
     * 注册"办结事务提交后"的通知派发（afterCommit，防回滚已推送）
     */
    private void registerCloseNotify(Long projectId)
    {
        final String operator;
        try
        {
            operator = SecurityUtils.getUsername();
        }
        catch (Exception ex)
        {
            // 非登录上下文（如导入批处理）触发时无操作人
            registerNotifySynchronization(projectId, "system");
            return;
        }
        registerNotifySynchronization(projectId, operator);
    }

    private void registerNotifySynchronization(Long projectId, String operator)
    {
        if (!TransactionSynchronizationManager.isSynchronizationActive())
        {
            // 无事务上下文（理论不出现）：直接派发
            notifyService.onProjectClosed(projectId, operator);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
        {
            @Override
            public void afterCommit()
            {
                try
                {
                    notifyService.onProjectClosed(projectId, operator);
                }
                catch (Exception ex)
                {
                    // 通知失败不影响办结主流程
                    ex.printStackTrace();
                }
            }
        });
    }

    /**
     * 保存前重算项目总时长（安排日期 → 今天，仅工作日，含头含尾）
     * <p>
     * 规则：<br>
     * 1. 未办结（ongoing）→ 每次保存按当天实时重算；<br>
     * 2. 已办结（closed）/ 已归档（archived）→ 总时长已冻结，保持原值不变；<br>
     * 3. 安排日期为空 → 总时长置空。
     *
     * @param project 待保存的项目对象（会就地修改 totalDuration）
     */
    private void recalcTotalDuration(ProjProject project)
    {
        if ("closed".equals(project.getStatus()) || "archived".equals(project.getStatus()))
        {
            return; // 已冻结，不动
        }
        if (project.getAssignDate() == null)
        {
            project.setTotalDuration(null);
            return;
        }
        LocalDate assign = WorkdayUtils.toLocalDate(project.getAssignDate());
        LocalDate today = LocalDate.now();
        Map<LocalDate, String> calendarMap = workdayCalendarService.getCalendarMap(assign, today);
        project.setTotalDuration(WorkdayUtils.countWorkdays(assign, today, calendarMap));
    }

    /**
     * 办结时冻结总时长快照（安排日期 → 今天，仅工作日）
     * 直接写库，此后项目编辑/重算均不再改动该值
     *
     * @param project 已加载的项目对象（含 assignDate）
     */
    private void snapshotTotalDuration(ProjProject project)
    {
        if (project == null || project.getAssignDate() == null)
        {
            // 无安排日期则总时长保持为空，不写
            return;
        }
        LocalDate assign = WorkdayUtils.toLocalDate(project.getAssignDate());
        LocalDate today = LocalDate.now();
        Map<LocalDate, String> calendarMap = workdayCalendarService.getCalendarMap(assign, today);
        int duration = WorkdayUtils.countWorkdays(assign, today, calendarMap);
        projectMapper.updateProjectTotalDuration(project.getId(), duration);
    }

    /**
     * 批量新增项目（区域粘贴）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchInsertProject(List<ProjProject> projectList, String operName)
    {
        return batchInsertProjectReturnIds(projectList, operName).size();
    }

    /**
     * 批量新增项目（区域粘贴），并返回新写入记录的 ID 列表。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> batchInsertProjectReturnIds(List<ProjProject> projectList, String operName)
    {
        if (projectList == null || projectList.isEmpty())
        {
            throw new ServiceException("批量数据不能为空");
        }
        List<Long> insertedIds = new ArrayList<>();
        for (ProjProject project : projectList)
        {
            if (project.getProjectCode() == null || project.getProjectCode().isEmpty())
            {
                continue;
            }
            // 跳过已存在的工程编号
            ProjProject existing = projectMapper.checkProjectCodeUnique(project);
            if (existing != null)
            {
                continue;
            }
            project.setStatus("ongoing");
            project.setCreateBy(operName);
            if (project.getDataSource() == null || project.getDataSource().isEmpty())
            {
                project.setDataSource("manual");
            }
            resolveCategoryAndLeaders(project);
            recalcTotalDuration(project);
            projectMapper.insertProject(project);
            // 插入负责人关联 + 自动创建任务
            Long[] leaderIds = project.getLeaderIds();
            if (leaderIds != null && leaderIds.length > 0)
            {
                leaderMapper.insertProjectLeaders(project.getId(), leaderIds, operName);
                createTasksForLeaders(project, Arrays.asList(leaderIds), operName);
            }
            insertedIds.add(project.getId());
        }
        return insertedIds;
    }

    /**
     * 解析类别名称→ID、负责人姓名→用户ID
     */
    private void resolveCategoryAndLeaders(ProjProject project)
    {
        // 项目类别：以「项目类别名称 engineeringProject」为准反查外键 ID，
        // 保证 engineering_project（文本，列表/报表列展示）与 project_category_id（外键，筛选口径）永远一致。
        String ep = project.getEngineeringProject();
        if (ep != null && !ep.trim().isEmpty())
        {
            ProjCategory byName = categoryMapper.selectCategoryByName(ep.trim());
            if (byName != null)
            {
                project.setProjectCategoryId(byName.getId());
                project.setCategoryName(byName.getName());
            }
        }
        else if (ep == null && project.getProjectCategoryId() != null)
        {
            // 仅给了 ID 未给名称 → 反查回填名称（注：显式传空串="主动清空"，不在此回填）
            ProjCategory byId = categoryMapper.selectCategoryById(project.getProjectCategoryId());
            if (byId != null)
            {
                project.setEngineeringProject(byId.getName());
                project.setCategoryName(byId.getName());
            }
        }
        // 兼容历史调用方：仅传了 categoryName（无文本、无 ID）→ 反查 ID
        if (project.getProjectCategoryId() == null
            && project.getCategoryName() != null && !project.getCategoryName().isEmpty())
        {
            ProjCategory byCn = categoryMapper.selectCategoryByName(project.getCategoryName());
            if (byCn != null)
            {
                project.setProjectCategoryId(byCn.getId());
            }
        }
        // 负责人姓名 → 用户ID（支持逗号分隔多人）
        if (project.getLeaderNames() != null && !project.getLeaderNames().isEmpty())
        {
            String[] names = project.getLeaderNames().split("[,，]");
            java.util.List<Long> userIds = new java.util.ArrayList<>();
            for (String name : names)
            {
                String trimmed = name.trim();
                if (trimmed.isEmpty())
                {
                    continue;
                }
                SysUser user = userMapper.selectUserByUserName(trimmed);
                if (user != null)
                {
                    userIds.add(user.getUserId());
                }
            }
            if (!userIds.isEmpty())
            {
                project.setLeaderIds(userIds.toArray(new Long[0]));
            }
        }
    }

    /**
     * 为负责人批量创建任务（新增项目/新增负责人时使用）
     * 安排日期、工期要求默认带出项目值（可在任务管理中单独调整）
     *
     * @param project   项目对象（含 ID/名称/安排日期/工期要求）
     * @param leaderIds 负责人ID列表
     * @param operName  操作人
     */
    private void createTasksForLeaders(ProjProject project, List<Long> leaderIds, String operName)
    {
        String taskName = (project.getProjectName() != null && !project.getProjectName().isEmpty())
                ? project.getProjectName() : "项目任务";
        for (Long userId : leaderIds)
        {
            ProjTask task = new ProjTask();
            task.setProjectId(project.getId());
            task.setUserId(userId);
            task.setTaskName(taskName);
            // 安排日期带出项目安排日期
            task.setAssignDate(project.getAssignDate());
            // 工期要求带出项目工期要求（Integer 天数 → 拼接 " 天"）
            if (project.getDurationRequire() != null)
            {
                task.setDurationRequire(project.getDurationRequire() + " 天");
            }
            task.setStatus("pending");
            task.setCreateBy(operName);
            taskMapper.insertTask(task);
        }
    }

    /**
     * 修改项目时增量同步任务
     * - 新负责人（不在旧任务列表中）→ 创建任务
     * - 移除的负责人（不在新负责人列表中）→ 逻辑删除任务
     * - 保留的负责人 → 不动（保留已编辑的工期、日期等信息）
     *
     * @param projectId     项目ID
     * @param projectName   项目名称
     * @param newLeaderIds  新的负责人ID列表
     * @param operName      操作人
     */
    private void syncTasksOnLeaderChange(ProjProject project, List<Long> newLeaderIds, String operName)
    {
        Long projectId = project.getId();
        // 查询当前项目已有任务的执行人
        List<Long> rawExisting = taskMapper.selectTaskUserIdsByProjectId(projectId);
        final List<Long> existingUserIds = (rawExisting != null) ? rawExisting : new ArrayList<>();

        // 新增的负责人 → 创建任务
        List<Long> toAdd = newLeaderIds.stream()
                .filter(uid -> !existingUserIds.contains(uid))
                .collect(Collectors.toList());
        if (!toAdd.isEmpty())
        {
            createTasksForLeaders(project, toAdd, operName);
        }

        // 移除的负责人 → 逻辑删除任务
        List<Long> toRemove = existingUserIds.stream()
                .filter(uid -> !newLeaderIds.contains(uid))
                .collect(Collectors.toList());
        if (!toRemove.isEmpty())
        {
            taskMapper.deleteTaskByProjectIdAndUserIds(projectId, toRemove);
        }
    }

    /**
     * 保存首笔付款（插入或更新 project_id = 当前项目且 payment_type = 'advance' 的记录）
     * 如果首笔付款金额为空，则删除已有的 advance 记录
     */
    private void saveFirstPayment(ProjProject project)
    {
        if (project.getFirstPaymentAmount() != null && project.getFirstPaymentAmount().compareTo(java.math.BigDecimal.ZERO) > 0)
        {
            ProjPayment payment = new ProjPayment();
            payment.setProjectId(project.getId());
            payment.setPaymentType("advance");
            payment.setAmount(project.getFirstPaymentAmount());
            payment.setPayTime(project.getFirstPaymentTime());
            payment.setPayUnit(project.getFirstPaymentUnit());
            payment.setPayMethod(project.getFirstPaymentMethod());
            payment.setCreateBy(project.getUpdateBy() != null ? project.getUpdateBy() : project.getCreateBy());
            payment.setRemark("签约首付款");
            paymentMapper.upsertPayment(payment);
        }
        else
        {
            // 金额为空或 ≤0 → 清空已有的首付款记录
            ProjPayment query = new ProjPayment();
            query.setProjectId(project.getId());
            query.setPaymentType("advance");
            List<ProjPayment> existing = paymentMapper.selectPaymentList(query);
            if (existing != null && !existing.isEmpty())
            {
                for (ProjPayment p : existing)
                {
                    paymentMapper.deletePaymentByIds(new Long[]{p.getId()});
                }
            }
        }
    }

    /**
     * 回填首笔付款数据到项目对象（编辑回显用）
     */
    private void fillFirstPayment(ProjProject project)
    {
        ProjPayment query = new ProjPayment();
        query.setProjectId(project.getId());
        query.setPaymentType("advance");
        List<ProjPayment> list = paymentMapper.selectPaymentList(query);
        if (list != null && !list.isEmpty())
        {
            ProjPayment p = list.get(0);
            project.setFirstPaymentAmount(p.getAmount());
            project.setFirstPaymentTime(p.getPayTime());
            project.setFirstPaymentUnit(p.getPayUnit());
            project.setFirstPaymentMethod(p.getPayMethod());
        }
    }

    @Override
    public List<Map<String, Object>> getStatusCounts()
    {
        return projectMapper.selectProjectStatusCounts();
    }

    @Override
    public List<String> getDistinctValues(String field)
    {
        // 字段白名单校验，防止 SQL 注入
        String[] allowedFields = {"client_unit", "engineering_project"};
        if (!Arrays.asList(allowedFields).contains(field))
        {
            throw new ServiceException("不支持的查询字段: " + field);
        }
        return projectMapper.selectDistinctValues(field);
    }

    /**
     * 项目列表可显隐列元数据：
     * 业务字段（含 JOIN 展示字段）→ 系统字段 → 动态字段（proj_field_def）
     * 物理表新增列时，未在固定清单中的列会自动追加到业务组末尾（默认隐藏）
     */
    @Override
    public List<Map<String, Object>> getListColumns()
    {
        List<Map<String, Object>> columns = new ArrayList<>();

        // ---- 业务字段（固定顺序，与用户确认的默认显示一致） ----
        addColumn(columns, "projectCode", "工程编号", "text", "business", true, "projectCode");
        addColumn(columns, "clientUnit", "委托单位", "text", "business", true, "clientUnit");
        addColumn(columns, "clientUnitNature", "委托单位性质", "dict", "business", true, "clientUnitNature");
        addColumn(columns, "contactName", "联系人", "text", "business", true, "contactName");
        addColumn(columns, "contactPhone", "联系电话", "text", "business", true, "contactPhone");
        addColumn(columns, "engineeringProject", "项目类别", "text", "business", true, "engineeringProject");
        addColumn(columns, "projectLocation", "工程地点", "text", "business", true, "projectLocation");
        addColumn(columns, "status", "状态", "dict", "business", true, "status");
        addColumn(columns, "closeTime", "办结日期", "date", "business", true, "closeTime");
        addColumn(columns, "projectNature", "项目性质", "dict", "business", true, "projectNature");
        addColumn(columns, "contractName", "合同", "text", "business", false, "contractName");
        // 合同金额 / 内外产值：列表 SQL 已通过 JOIN + lateral 聚合取到值，默认隐藏
        addColumn(columns, "contractNo", "合同编号", "text", "business", false, "contractNo");
        addColumn(columns, "contractAmount", "合同金额", "number", "business", false, "contractAmount");
        addColumn(columns, "internalOutput", "内部产值", "number", "business", false, "internalOutput");
        addColumn(columns, "externalOutput", "外部产值", "number", "business", false, "externalOutput");
        addColumn(columns, "leaderNames", "负责人", "text", "business", true, "leaderNames");
        addColumn(columns, "assignDate", "安排日期", "date", "business", true, "assignDate");
        addColumn(columns, "durationRequire", "工期要求", "duration", "business", true, "durationRequire");
        addColumn(columns, "totalDuration", "总时长", "total", "business", true, "totalDuration");
        addColumn(columns, "projectName", "项目名称", "text", "business", false, "projectName");
        addColumn(columns, "relatedProjectCode", "关联定线编号", "text", "business", true, "relatedProjectCode");
        addColumn(columns, "dataSource", "项目来源", "dict", "business", false, "dataSource");
        addColumn(columns, "remark", "备注", "text", "business", false, "remark");

        // ---- 物理表新增列自动发现（不在固定清单中的列 → 业务组末尾，默认隐藏） ----
        Set<String> known = new HashSet<>(Arrays.asList(
            "project_code", "client_unit", "client_unit_nature", "contact_name", "contact_phone", "engineering_project",
            "project_location", "status", "assign_date", "duration_require", "total_duration",
            "project_name", "remark", "id", "create_by", "create_time", "update_by", "update_time",
            "del_flag", "extra_data", "project_category_id", "contract_id", "related_project_id",
            "close_time", "data_source", "project_nature", "related_project_code_text"));
        List<Map<String, Object>> tableColumns = projectMapper.selectTableColumns("proj_project");
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
        List<ProjFieldDef> fieldDefs = fieldDefMapper.selectFieldDefByTableName("proj_project");
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

    @Override
    public List<ProjProject> getRelatedCandidates(String engineeringProject)
    {
        return projectMapper.selectRelatedCandidates(engineeringProject);
    }

    /**
     * 负责人下拉选项：在职项目经理 ∪ 项目表已出现过的负责人（含离职/影子用户）
     */
    @Override
    public List<SysUser> getLeaderOptions()
    {
        // LinkedHashMap 按 userId 去重：在职项目经理在前，历史负责人补充在后
        Map<Long, SysUser> byId = new java.util.LinkedHashMap<>();
        // 1. 在职项目经理（原下拉数据源）
        SysUser uq = new SysUser();
        uq.setStatus("0");
        uq.getParams().put("postName", "项目经理");
        for (SysUser u : userMapper.selectUserList(uq))
        {
            byId.putIfAbsent(u.getUserId(), u);
        }
        // 2. 项目表已出现过的全部负责人（含停用）
        for (SysUser u : leaderMapper.selectDistinctLeaders())
        {
            byId.putIfAbsent(u.getUserId(), u);
        }
        return new ArrayList<>(byId.values());
    }

    /**
     * 按姓名获取/创建负责人档案（影子用户）：精确匹配优先，匹配不到自动建档（停用、不可登录）
     */
    @Override
    public SysUser ensureLeaderByName(String name, String operName)
    {
        String nickName = name == null ? "" : name.trim();
        if (nickName.isEmpty())
        {
            throw new ServiceException("负责人姓名不能为空");
        }
        // 1. 昵称精确匹配已有用户（在职优先，含离职/影子用户）
        SysUser exist = leaderMapper.selectUserByNickName(nickName);
        if (exist != null)
        {
            return exist;
        }
        // 2. 自动创建影子用户：user_name 自动生成（ext_ 前缀 + 随机串，保证唯一）
        String userName = null;
        for (int i = 0; i < 5; i++)
        {
            String candidate = "ext_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            if (userMapper.checkUserNameUnique(candidate) == null)
            {
                userName = candidate;
                break;
            }
        }
        if (userName == null)
        {
            userName = "ext_" + System.currentTimeMillis();
        }
        SysUser u = new SysUser();
        u.setUserName(userName);
        u.setNickName(nickName);
        // 随机密码（BCrypt），账号为停用状态本身即不可登录，双重保险
        u.setPassword(SecurityUtils.encryptPassword(java.util.UUID.randomUUID().toString()));
        u.setStatus("1");
        u.setRemark("负责人档案（历史数据/手动添加，系统自动创建，不可登录）");
        u.setCreateBy(operName);
        userMapper.insertUser(u);
        return u;
    }
}
