package com.xakcch.web.controller.project;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.xakcch.common.annotation.Log;
import com.xakcch.common.core.controller.BaseController;
import com.xakcch.common.core.domain.AjaxResult;
import com.xakcch.common.core.page.TableDataInfo;
import com.xakcch.common.enums.BusinessType;
import com.xakcch.common.exception.ServiceException;
import com.xakcch.common.utils.DateUtils;
import com.xakcch.common.utils.file.FileUtils;
import com.xakcch.project.domain.ProjContract;
import com.xakcch.project.mapper.ProjProjectMapper;
import com.xakcch.project.report.DynamicColumnExcelExporter;
import com.xakcch.project.service.IProjContractService;

/**
 * 合同表 信息操作处理
 *
 * @author liuyonghui
 */
@RestController
@RequestMapping("/project/contract")
public class ProjContractController extends BaseController
{
    @Autowired
    private IProjContractService contractService;

    @Autowired
    private ProjProjectMapper projectMapper;

    /**
     * 查询合同列表（分页）
     */
    @GetMapping("/list")
    public TableDataInfo list(ProjContract contract)
    {
        startPage();
        List<ProjContract> list = contractService.selectContractList(contract);
        return getDataTable(list);
    }

    /**
     * 导出合同列表（可选字段）
     *
     * <p>form 参数：
     * <ul>
     *   <li>{@code fields} 列 key 逗号串，按导出顺序；为空时回退为「默认可见列」</li>
     *   <li>{@code scope}  {@code filter}（默认，全部筛选结果）/ {@code selected}（仅已勾选的行）</li>
     *   <li>{@code ids}    {@code scope=selected} 时的合同 id 逗号串</li>
     * </ul>
     *
     * <p>与列表页共用同一套筛选条件。附件 / 付款进度两列不参与导出（见
     * {@code ProjContractServiceImpl#EXPORT_ACCESSORS}）；动态字段（项目类型、测绘地址等）
     * 从 {@code extra_data} 解析取值。</p>
     *
     * <p>⚠️ 字段清单以 {@code getListColumns()} 为唯一出处，未知 key 一律忽略
     *    （既防越权导出内部字段，也防参数拼错导致空列）。</p>
     */
    @PreAuthorize("@ss.hasPermi('project:contract:export')")
    @Log(title = "合同管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, ProjContract contract,
                       @RequestParam(required = false) String fields,
                       @RequestParam(required = false) String scope,
                       @RequestParam(required = false) String ids) throws IOException
    {
        List<Long> idScope = null;
        if ("selected".equals(scope))
        {
            idScope = toIdList(ids);
            if (idScope.isEmpty())
            {
                throw new ServiceException("请先勾选要导出的合同");
            }
        }

        List<String> fieldKeys = splitFields(fields);
        if (fieldKeys.isEmpty())
        {
            // 未指定字段 → 回退为默认可见列（与首次进入页面的表格一致），并剔除不可导出的渲染型列
            for (Map<String, Object> c : contractService.getListColumns())
            {
                if (Boolean.TRUE.equals(c.get("defaultVisible"))
                        && !isNonExportable(String.valueOf(c.get("key"))))
                {
                    fieldKeys.add(String.valueOf(c.get("key")));
                }
            }
        }

        Map<String, Object> data = contractService.exportContracts(contract, fieldKeys, idScope);

        @SuppressWarnings("unchecked")
        List<DynamicColumnExcelExporter.Column> headers =
                (List<DynamicColumnExcelExporter.Column>) data.get("headers");
        @SuppressWarnings("unchecked")
        List<List<Object>> rows = (List<List<Object>>) data.get("rows");

        String fileName = "合同列表_" + DateUtils.parseDateToStr("yyyyMMddHHmmss", new java.util.Date()) + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        FileUtils.setAttachmentResponseHeader(response, fileName);
        // 不输出标题行：第 1 行即表头，导出的文件可直接用于筛选 / 透视 / 二次导入
        DynamicColumnExcelExporter.export(response.getOutputStream(), headers, rows);
    }

    /** 逗号串 id → Long 列表（「仅导出已勾选的行」用） */
    private List<Long> toIdList(String ids)
    {
        List<Long> list = new ArrayList<>();
        if (ids == null || ids.trim().isEmpty())
        {
            return list;
        }
        for (String s : ids.split(","))
        {
            if (s == null || s.trim().isEmpty())
            {
                continue;
            }
            try
            {
                list.add(Long.valueOf(s.trim()));
            }
            catch (NumberFormatException e)
            {
                throw new ServiceException("合同ID格式非法：" + s);
            }
        }
        return list;
    }

    /** 附件 / 付款进度：列表页是按钮与进度条，导出成文本无意义 */
    private boolean isNonExportable(String key)
    {
        return "attachment".equals(key) || "paymentProgress".equals(key);
    }

    /** 逗号串 → 字段 key 列表 */
    private List<String> splitFields(String fields)
    {
        List<String> list = new ArrayList<>();
        if (fields == null || fields.trim().isEmpty())
        {
            return list;
        }
        for (String s : fields.split(","))
        {
            if (s == null || s.trim().isEmpty())
            {
                continue;
            }
            list.add(s.trim());
        }
        return list;
    }

    /**
     * 统计各状态下的合同数量（状态胶囊导航用）
     */
    @GetMapping("/statusCounts")
    public AjaxResult statusCounts()
    {
        List<Map<String, Object>> list = contractService.getStatusCounts();
        return success(list);
    }

    /**
     * 查询字段去重值列表（高级筛选下拉选项用）
     * 支持字段：clientUnit（委托单位）、contactName（联系人）、contractType（合同类型）
     */
    @GetMapping("/distinctValues")
    public AjaxResult distinctValues(@RequestParam String field)
    {
        List<String> list = contractService.getDistinctValues(field);
        return success(list);
    }

    /**
     * 查询合同列表可显隐列的元数据（显隐列面板 + 表格动态渲染用）
     * 物理字段从 information_schema 动态读取，另含 JOIN/计算展示字段、系统字段、动态字段
     */
    @GetMapping("/columns")
    public AjaxResult columns()
    {
        return success(contractService.getListColumns());
    }

    /**
     * 根据合同ID获取详细信息
     */
    @GetMapping(value = "/{id:[0-9]+}")
    public AjaxResult getInfo(@PathVariable Long id)
    {
        return success(contractService.selectContractById(id));
    }

    /**
     * 新增合同
     */
    @PreAuthorize("@ss.hasPermi('project:contract:add')")
    @Log(title = "合同管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody ProjContract contract)
    {
        if (!contractService.checkContractNoUnique(contract))
        {
            return error("新增合同'" + contract.getContractName() + "'失败，合同编号已存在");
        }
        contract.setCreateBy(getUsername());
        contractService.insertContract(contract);
        // 返回新创建的合同ID，供前端紧接着保存合同单价使用
        return AjaxResult.success(contract.getId());
    }

    /**
     * 修改合同
     */
    @PreAuthorize("@ss.hasPermi('project:contract:edit')")
    @Log(title = "合同管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody ProjContract contract)
    {
        if (!contractService.checkContractNoUnique(contract))
        {
            return error("修改合同'" + contract.getContractName() + "'失败，合同编号已存在");
        }
        contract.setUpdateBy(getUsername());
        return toAjax(contractService.updateContract(contract));
    }

    /**
     * 删除合同
     */
    @PreAuthorize("@ss.hasPermi('project:contract:remove')")
    @Log(title = "合同管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids:[0-9,]+}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(contractService.deleteContractByIds(ids));
    }

    /**
     * 变更合同状态
     */
    @PreAuthorize("@ss.hasPermi('project:contract:edit')")
    @Log(title = "合同管理", businessType = BusinessType.UPDATE)
    @PutMapping("/changeStatus/{id}/{status}")
    public AjaxResult changeStatus(@PathVariable Long id, @PathVariable String status)
    {
        return toAjax(contractService.changeContractStatus(id, status));
    }

    /**
     * 查询合同关联的项目列表
     */
    @GetMapping("/{id}/projects")
    public AjaxResult projectsByContract(@PathVariable Long id)
    {
        List<Map<String, Object>> list = projectMapper.selectProjectsByContractId(id);
        return success(list);
    }
}
