package com.xakcch.web.controller.system;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xakcch.common.core.controller.BaseController;
import com.xakcch.common.core.domain.AjaxResult;
import com.xakcch.project.domain.ProjFieldDef;
import com.xakcch.project.mapper.ProjFieldDefMapper;
import com.xakcch.system.domain.SysRoleFieldAuth;
import com.xakcch.system.service.ISysRoleFieldAuthService;

/**
 * 角色字段权限 Controller
 * 
 * @author liuyonghui
 */
@RestController
@RequestMapping("/system/role")
public class SysRoleFieldAuthController extends BaseController
{
    @Autowired
    private ISysRoleFieldAuthService fieldAuthService;

    @Autowired
    private ProjFieldDefMapper projFieldDefMapper;

    /**
     * 获取指定角色的字段权限配置
     */
    @GetMapping("/{roleId}/fieldAuth")
    public AjaxResult getFieldAuth(@PathVariable Long roleId)
    {
        List<SysRoleFieldAuth> list = fieldAuthService.selectFieldAuthByRoleId(roleId);
        return success(list);
    }

    /**
     * 保存指定角色的字段权限配置（全量替换）
     */
    @PutMapping("/{roleId}/fieldAuth")
    public AjaxResult saveFieldAuth(@PathVariable Long roleId, @RequestBody List<SysRoleFieldAuth> list)
    {
        fieldAuthService.saveRoleFieldAuth(roleId, list);
        return success();
    }

    /**
     * 获取当前用户对指定表的字段权限 Map（前端动态渲染用）
     * 返回 Map<fieldKey, authType>，空 Map = 无限制（admin）
     */
    @GetMapping("/fieldAuth/my/{tableName}")
    public AjaxResult getMyFieldAuth(@PathVariable String tableName)
    {
        Map<String, String> authMap = fieldAuthService.getCurrentUserFieldAuthMap(tableName);
        return success(authMap);
    }

    /**
     * 获取字段权限配置页面所需的：所有表 + 字段元数据
     * 前端用此数据渲染配置界面
     */
    @GetMapping("/fieldAuth/schema")
    public AjaxResult getFieldAuthSchema()
    {
        List<Map<String, Object>> tables = buildTableSchema();
        return success(tables);
    }

    /**
     * 构建表字段元数据（固定字段 + 动态字段标记）
     */
    private List<Map<String, Object>> buildTableSchema()
    {
        List<Map<String, Object>> tables = new ArrayList<>();

        tables.add(buildTable("proj_project", "项目主表", new String[][]{
            {"project_code",       "工程编号"},
            {"project_name",       "项目名称"},
            {"engineering_project","工程项目"},
            {"project_category_id","项目类别"},
            {"client_unit",        "委托单位"},
            {"contact_name",       "联系人"},
            {"contact_phone",      "联系电话"},
            {"project_location",   "工程地点"},
            {"contract_id",        "关联合同"},
            {"status",             "项目状态"},
            {"remark",             "备注"},
        }));

        tables.add(buildTable("proj_contract", "合同表", new String[][]{
            {"contract_no",      "合同编号"},
            {"contract_name",    "合同名称"},
            {"client_unit",      "委托单位"},
            {"contact_name",     "联系人"},
            {"contact_phone",    "联系电话"},
            {"contract_type",    "合同类型"},
            {"contract_amount",  "合同金额"},
            {"sign_date",        "签署日期"},
            {"entrust_date",     "委托时间"},
            {"audit_date",       "审核日期"},
            {"return_date",      "用户返回日期"},
            {"finish_date",      "完成日期"},
            {"archive_date",     "归档日期"},
            {"archive_path",     "归档目录"},
            {"contract_period",  "合同期限"},
            {"payment_terms",    "支付条件"},
            {"remark",           "备注"},
        }));

        tables.add(buildTable("proj_task", "任务表", new String[][]{
            {"task_name",        "任务名称"},
            {"user_id",          "执行人"},
            {"assign_date",      "安排日期"},
            {"finish_date",      "完成日期"},
            {"duration_require", "工期要求"},
            {"total_duration",   "总时长"},
            {"status",           "任务状态"},
            {"remark",           "备注"},
        }));

        tables.add(buildTable("proj_workload", "工作量表", new String[][]{
            {"user_id",           "执行人"},
            {"category_id",       "项目类别"},
            {"internal_workload", "内部工作量"},
            {"external_workload", "外部工作量"},
            {"internal_price",    "内部单价"},
            {"external_price",    "外部单价"},
            {"internal_output",   "内部产值"},
            {"external_output",   "外部产值"},
            {"price_source",      "单价来源"},
            {"remark",            "备注"},
        }));

        tables.add(buildTable("proj_payment", "付款记录表", new String[][]{
            {"payment_type", "付款类型"},
            {"amount",       "金额"},
            {"pay_time",     "付款时间"},
            {"pay_unit",     "付款单位"},
            {"pay_method",   "付款方式"},
            {"remark",       "备注"},
        }));

        tables.add(buildTable("proj_material", "资料提交表", new String[][]{
            {"submit_time",   "提交时间"},
            {"contact_name",  "联系人"},
            {"contact_phone", "联系电话"},
            {"result_type",   "成果类型"},
            {"remark",        "备注"},
        }));

        tables.add(buildTable("proj_category", "项目类别表", new String[][]{
            {"name",            "类别名称"},
            {"parent_id",       "父级分类"},
            {"level",           "层级"},
            {"internal_price",  "内部单价"},
            {"external_price",  "外部单价"},
            {"sort_order",      "排序"},
            {"status",          "状态"},
            {"remark",          "备注"},
        }));

        return tables;
    }

    private Map<String, Object> buildTable(String tableName, String tableLabel, String[][] fields)
    {
        Map<String, Object> table = new HashMap<>();
        table.put("tableName", tableName);
        table.put("tableLabel", tableLabel);

        List<Map<String, String>> fieldList = new ArrayList<>();
        for (String[] f : fields)
        {
            Map<String, String> field = new HashMap<>();
            field.put("fieldKey", f[0]);
            field.put("fieldLabel", f[1]);
            field.put("source", "fixed"); // fixed=固定字段, dynamic=动态字段
            fieldList.add(field);
        }
        // 动态字段：从 proj_field_def 表查询实际定义，每个动态字段独立可配置权限
        if (!"proj_category".equals(tableName))
        {
            List<ProjFieldDef> dynamicFields = projFieldDefMapper.selectFieldDefByTableName(tableName);
            for (ProjFieldDef def : dynamicFields)
            {
                Map<String, String> dynamic = new HashMap<>();
                dynamic.put("fieldKey", def.getFieldKey());
                dynamic.put("fieldLabel", def.getFieldLabel());
                dynamic.put("source", "dynamic");
                fieldList.add(dynamic);
            }
        }

        table.put("fields", fieldList);
        return table;
    }
}
