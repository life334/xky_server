package com.xakcch.web.controller.project;

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
import com.xakcch.common.utils.poi.ExcelUtil;
import com.xakcch.project.domain.ProjContract;
import com.xakcch.project.mapper.ProjProjectMapper;
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
    @PreAuthorize("@ss.hasPermi('project:contract:list')")
    @GetMapping("/list")
    public TableDataInfo list(ProjContract contract)
    {
        startPage();
        List<ProjContract> list = contractService.selectContractList(contract);
        return getDataTable(list);
    }

    /**
     * 导出合同列表
     */
    @PreAuthorize("@ss.hasPermi('project:contract:export')")
    @Log(title = "合同管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, ProjContract contract)
    {
        List<ProjContract> list = contractService.selectContractList(contract);
        ExcelUtil<ProjContract> util = new ExcelUtil<ProjContract>(ProjContract.class);
        util.exportExcel(response, list, "合同信息数据");
    }

    /**
     * 统计各状态下的合同数量（状态胶囊导航用）
     */
    @PreAuthorize("@ss.hasPermi('project:contract:list')")
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
    @PreAuthorize("@ss.hasPermi('project:contract:list')")
    @GetMapping("/distinctValues")
    public AjaxResult distinctValues(@RequestParam String field)
    {
        List<String> list = contractService.getDistinctValues(field);
        return success(list);
    }

    /**
     * 根据合同ID获取详细信息
     */
    @PreAuthorize("@ss.hasPermi('project:contract:query')")
    @GetMapping(value = "/{id}")
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
    @DeleteMapping("/{ids}")
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
    @PreAuthorize("@ss.hasPermi('project:contract:query')")
    @GetMapping("/{id}/projects")
    public AjaxResult projectsByContract(@PathVariable Long id)
    {
        List<Map<String, Object>> list = projectMapper.selectProjectsByContractId(id);
        return success(list);
    }
}
