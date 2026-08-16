package com.xakcch.project.service.impl;

import java.time.LocalDate;
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
import org.springframework.transaction.annotation.Transactional;
import com.xakcch.common.exception.ServiceException;
import com.xakcch.common.utils.SecurityUtils;
import com.xakcch.common.utils.WorkdayUtils;
import com.xakcch.project.domain.ProjCategory;
import com.xakcch.project.domain.ProjFieldDef;
import com.xakcch.project.domain.ProjMaterial;
import com.xakcch.project.domain.ProjPayment;
import com.xakcch.project.domain.ProjProject;
import com.xakcch.project.domain.ProjTask;
import com.xakcch.project.mapper.ProjCategoryMapper;
import com.xakcch.project.mapper.ProjFieldDefMapper;
import com.xakcch.project.mapper.ProjLeaderMapper;
import com.xakcch.project.mapper.ProjMaterialMapper;
import com.xakcch.project.mapper.ProjPaymentMapper;
import com.xakcch.project.mapper.ProjProjectMapper;
import com.xakcch.project.mapper.ProjTaskMapper;
import com.xakcch.project.service.IProjProjectService;
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
    /** 状态流转规则：key=当前状态, value=允许流转到的目标状态列表 */
    private static final Map<String, List<String>> STATUS_FLOW = new HashMap<>();
    static
    {
        STATUS_FLOW.put("ongoing",  Arrays.asList("closed"));
        STATUS_FLOW.put("closed",   Arrays.asList("archived"));
        STATUS_FLOW.put("archived", Arrays.asList());
    }

    @Autowired
    private ProjProjectMapper projectMapper;

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
    private ProjFieldDefMapper fieldDefMapper;

    @Autowired
    private ISysWorkdayCalendarService workdayCalendarService;

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
        return projectMapper.selectProjectList(project);
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

        return rows;
    }

    /**
     * 删除项目（含负责人关联清理）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteProjectById(Long id)
    {
        leaderMapper.deleteLeadersByProjectId(id);
        return projectMapper.deleteProjectById(id);
    }

    /**
     * 批量删除项目（含负责人关联清理）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteProjectByIds(Long[] ids)
    {
        for (Long id : ids)
        {
            leaderMapper.deleteLeadersByProjectId(id);
        }
        return projectMapper.deleteProjectByIds(ids);
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
        return rows;
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
        }
        return projectMapper.updateProjectStatus(id, targetStatus);
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
     * 导入项目数据（Excel文件解析后批量插入）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String importProject(List<ProjProject> projectList, Boolean isUpdateSupport, String operName)
    {
        if (projectList == null || projectList.isEmpty())
        {
            throw new ServiceException("导入数据不能为空");
        }
        int successNum = 0;
        int failureNum = 0;
        StringBuilder successMsg = new StringBuilder();
        StringBuilder failureMsg = new StringBuilder();
        for (ProjProject project : projectList)
        {
            try
            {
                // 校验工程编号
                if (project.getProjectCode() == null || project.getProjectCode().isEmpty())
                {
                    failureNum++;
                    failureMsg.append("<br/>工程编号为空，跳过");
                    continue;
                }
                // 检查重复
                ProjProject existing = projectMapper.checkProjectCodeUnique(project);
                if (existing != null)
                {
                    if (isUpdateSupport)
                    {
                        project.setId(existing.getId());
                        project.setUpdateBy(operName);
                        if (project.getImportRemark() != null)
                        {
                            project.setRemark(project.getImportRemark());
                        }
                        resolveCategoryAndLeaders(project);
                        updateProject(project);
                        // 更新已有任务的日期/时长（Excel 导入的分配日期/验收日期/总时长覆盖到所有关联任务）
                        updateImportedTaskFields(project.getId(), project);
                        successNum++;
                        successMsg.append("<br/>更新成功：" + project.getProjectCode());
                    }
                    else
                    {
                        failureNum++;
                        failureMsg.append("<br/>工程编号已存在：" + project.getProjectCode());
                    }
                    continue;
                }
                // 新增
                project.setStatus("ongoing");
                project.setCreateBy(operName);
                // 备注：从 Excel 导入字段写入 BaseEntity.remark
                if (project.getImportRemark() != null)
                {
                    project.setRemark(project.getImportRemark());
                }
                resolveCategoryAndLeaders(project);
                recalcTotalDuration(project);
                projectMapper.insertProject(project);                  // 1. 插入项目
                Long[] leaderIds = project.getLeaderIds();
                if (leaderIds != null && leaderIds.length > 0)
                {
                    leaderMapper.insertProjectLeaders(project.getId(), leaderIds, operName);  // 2. 负责人关联
                    createImportedTask(project, operName);                                   // 3. 任务（含日期/时长）
                }
                successNum++;
                successMsg.append("<br/>新增成功：" + project.getProjectCode());
            }
            catch (Exception e)
            {
                failureNum++;
                failureMsg.append("<br/>" + project.getProjectCode() + " 导入失败：" + e.getMessage());
            }
        }
        if (failureNum > 0)
        {
            failureMsg.insert(0, "导入失败！共 " + failureNum + " 条数据格式不正确");
            throw new ServiceException(failureMsg.toString());
        }
        successMsg.insert(0, "导入成功！共 " + successNum + " 条");
        return successMsg.toString();
    }

    /**
     * 批量新增项目（区域粘贴）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchInsertProject(List<ProjProject> projectList, String operName)
    {
        if (projectList == null || projectList.isEmpty())
        {
            throw new ServiceException("批量数据不能为空");
        }
        int count = 0;
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
            count++;
        }
        return count;
    }

    /**
     * 解析类别名称→ID、负责人姓名→用户ID
     */
    private void resolveCategoryAndLeaders(ProjProject project)
    {
        // 类别名称 → ID
        if (project.getCategoryName() != null && !project.getCategoryName().isEmpty())
        {
            ProjCategory category = categoryMapper.selectCategoryByName(project.getCategoryName());
            if (category != null)
            {
                project.setProjectCategoryId(category.getId());
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
     * Excel 导入时创建任务（含分配日期/验收日期/总时长）
     * 每个负责人创建一条任务记录
     *
     * @param project  项目对象（含 leaderIds 和导入的日期/时长字段）
     * @param operName 操作人
     */
    private void createImportedTask(ProjProject project, String operName)
    {
        Long[] leaderIds = project.getLeaderIds();
        if (leaderIds == null || leaderIds.length == 0) return;

        String taskName = (project.getProjectName() != null && !project.getProjectName().isEmpty())
                ? project.getProjectName() : "项目任务";
        for (Long userId : leaderIds)
        {
            ProjTask task = new ProjTask();
            task.setProjectId(project.getId());
            task.setUserId(userId);
            task.setTaskName(taskName);
            task.setAssignDate(project.getImportTaskAssignDate());
            task.setActualFinishDate(project.getImportTaskFinishDate());
            task.setTotalDuration(project.getImportTaskDuration());
            task.setStatus("ongoing");
            task.setCreateBy(operName);
            taskMapper.insertTask(task);
        }
    }

    /**
     * Excel 更新导入时，用导入的日期/时长覆盖项目下所有已有任务
     * 只更新非 null 字段，避免误清空
     *
     * @param projectId 项目ID
     * @param project   含导入日期/时长的项目对象
     */
    private void updateImportedTaskFields(Long projectId, ProjProject project)
    {
        if (project.getImportTaskAssignDate() == null
                && project.getImportTaskFinishDate() == null
                && project.getImportTaskDuration() == null)
        {
            return; // 没有导入日期信息，跳过
        }
        ProjTask query = new ProjTask();
        query.setProjectId(projectId);
        List<ProjTask> tasks = taskMapper.selectTaskList(query);
        if (tasks == null || tasks.isEmpty()) return;

        for (ProjTask task : tasks)
        {
            boolean changed = false;
            if (project.getImportTaskAssignDate() != null)
            {
                task.setAssignDate(project.getImportTaskAssignDate());
                changed = true;
            }
            if (project.getImportTaskFinishDate() != null)
            {
                task.setActualFinishDate(project.getImportTaskFinishDate());
                changed = true;
            }
            if (project.getImportTaskDuration() != null)
            {
                task.setTotalDuration(project.getImportTaskDuration());
                changed = true;
            }
            if (changed)
            {
                taskMapper.updateTask(task);
            }
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
        addColumn(columns, "contactName", "联系人", "text", "business", true, "contactName");
        addColumn(columns, "contactPhone", "联系电话", "text", "business", true, "contactPhone");
        addColumn(columns, "engineeringProject", "工程项目", "text", "business", true, "engineeringProject");
        addColumn(columns, "projectLocation", "工程地点", "text", "business", true, "projectLocation");
        addColumn(columns, "status", "状态", "dict", "business", true, "status");
        addColumn(columns, "categoryName", "项目类别", "text", "business", false, "categoryName");
        addColumn(columns, "contractName", "合同", "text", "business", false, "contractName");
        addColumn(columns, "leaderNames", "负责人", "text", "business", true, "leaderNames");
        addColumn(columns, "assignDate", "安排日期", "date", "business", true, "assignDate");
        addColumn(columns, "durationRequire", "工期要求", "duration", "business", true, "durationRequire");
        addColumn(columns, "totalDuration", "总时长", "total", "business", true, "totalDuration");
        addColumn(columns, "projectName", "项目名称", "text", "business", false, "projectName");
        addColumn(columns, "relatedProjectCode", "关联工程编号", "text", "business", true, "relatedProjectCode");
        addColumn(columns, "remark", "备注", "text", "business", false, "remark");

        // ---- 物理表新增列自动发现（不在固定清单中的列 → 业务组末尾，默认隐藏） ----
        Set<String> known = new HashSet<>(Arrays.asList(
            "project_code", "client_unit", "contact_name", "contact_phone", "engineering_project",
            "project_location", "status", "assign_date", "duration_require", "total_duration",
            "project_name", "remark", "id", "create_by", "create_time", "update_by", "update_time",
            "del_flag", "extra_data", "project_category_id", "contract_id", "related_project_id"));
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
}
