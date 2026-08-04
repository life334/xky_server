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
import com.xakcch.project.domain.ProjPayment;
import com.xakcch.project.service.IProjPaymentService;

/**
 * 付款记录 信息操作处理
 *
 * @author liuyonghui
 */
@RestController
@RequestMapping("/project/payment")
public class ProjPaymentController extends BaseController
{
    @Autowired
    private IProjPaymentService paymentService;

    /**
     * 查询付款记录列表（分页）
     */
    @PreAuthorize("@ss.hasPermi('project:payment:list')")
    @GetMapping("/list")
    public TableDataInfo list(ProjPayment payment)
    {
        startPage();
        List<ProjPayment> list = paymentService.selectPaymentList(payment);
        return getDataTable(list);
    }

    /**
     * 导出付款记录
     */
    @PreAuthorize("@ss.hasPermi('project:payment:export')")
    @Log(title = "付款记录", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, ProjPayment payment)
    {
        List<ProjPayment> list = paymentService.selectPaymentList(payment);
        ExcelUtil<ProjPayment> util = new ExcelUtil<ProjPayment>(ProjPayment.class);
        util.exportExcel(response, list, "付款记录数据");
    }

    /**
     * 根据ID获取详细信息
     */
    @PreAuthorize("@ss.hasPermi('project:payment:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable Long id)
    {
        return success(paymentService.selectPaymentById(id));
    }

    /**
     * 新增付款记录
     */
    @PreAuthorize("@ss.hasPermi('project:payment:add')")
    @Log(title = "付款记录", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody ProjPayment payment)
    {
        payment.setCreateBy(getUsername());
        return toAjax(paymentService.insertPayment(payment));
    }

    /**
     * 修改付款记录
     */
    @PreAuthorize("@ss.hasPermi('project:payment:edit')")
    @Log(title = "付款记录", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody ProjPayment payment)
    {
        payment.setUpdateBy(getUsername());
        return toAjax(paymentService.updatePayment(payment));
    }

    /**
     * 删除付款记录
     */
    @PreAuthorize("@ss.hasPermi('project:payment:remove')")
    @Log(title = "付款记录", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(paymentService.deletePaymentByIds(ids));
    }

    /**
     * 项目收款总览列表（分页，按项目维度聚合：合同额、已收、未收、进度、状态）
     */
    @PreAuthorize("@ss.hasPermi('project:payment:list')")
    @GetMapping("/overview")
    public TableDataInfo overview(@RequestParam Map<String, Object> params)
    {
        startPage();
        List<Map<String, Object>> list = paymentService.selectPaymentOverviewList(params);
        return getDataTable(list);
    }

    /**
     * 项目收款总览 KPI 统计（总数/未付款/部分付款/已结清/应收合计/已收合计）
     */
    @PreAuthorize("@ss.hasPermi('project:payment:list')")
    @GetMapping("/overview/stats")
    public AjaxResult overviewStats()
    {
        Map<String, Object> stats = paymentService.selectPaymentOverviewStats();
        return success(stats);
    }
}
