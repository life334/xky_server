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
import com.xakcch.project.domain.ProjCollectionLog;
import com.xakcch.project.domain.vo.PaymentDetailExportVo;
import com.xakcch.common.utils.DateUtils;
import com.xakcch.common.utils.file.FileUtils;
import com.xakcch.project.report.DynamicColumnExcelExporter;
import com.xakcch.project.service.IProjCollectionService;

/**
 * 回款管理 信息操作处理
 *
 * @author liuyonghui
 */
@RestController
@RequestMapping("/project/collection")
public class ProjCollectionController extends BaseController
{
    @Autowired
    private IProjCollectionService collectionService;

    /**
     * 回款台账列表（已办结+未结清，项目维度，分页）
     */
    @GetMapping("/list")
    public TableDataInfo list(@RequestParam Map<String, Object> params)
    {
        startPage();
        List<Map<String, Object>> list = collectionService.selectCollectionList(params);
        return getDataTable(list);
    }

    /**
     * 客户聚合视图（分页）
     */
    @GetMapping("/clientList")
    public TableDataInfo clientList(@RequestParam Map<String, Object> params)
    {
        startPage();
        List<Map<String, Object>> list = collectionService.selectClientCollectionList(params);
        return getDataTable(list);
    }

    /**
     * 统计卡（待回款项目数/总额、超账期预警、待结算提醒、窗口到账额）
     * ⚠️ 口径与 /list 同理联动：入参即列表查询条件的同一套参数
     */
    @GetMapping("/stats")
    public AjaxResult stats(@RequestParam Map<String, Object> params)
    {
        return success(collectionService.selectCollectionStats(params));
    }

    /**
     * 待结算提醒列表（已办结但无外部产值，分页）
     */
    @GetMapping("/unsettledList")
    public TableDataInfo unsettledList(@RequestParam Map<String, Object> params)
    {
        startPage();
        List<Map<String, Object>> list = collectionService.selectUnsettledList(params);
        return getDataTable(list);
    }

    /**
     * 到账明细（下钻：与到账汇总同一驱动与筛选条件，分页）
     */
    @GetMapping("/receivedDetail")
    public TableDataInfo receivedDetail(@RequestParam Map<String, Object> params)
    {
        startPage();
        List<Map<String, Object>> list = collectionService.selectReceivedDetail(params);
        return getDataTable(list);
    }

    /**
     * 到账统计（合计 + 分组明细，不分页）
     *
     * dimension=payTime（按到账时间，默认）| closeTime（按办结时间）
     * groupBy=none（默认）| month | quarter | year | clientUnit | leader | paymentType | category
     */
    @GetMapping("/paymentSummary")
    public AjaxResult paymentSummary(@RequestParam Map<String, Object> params)
    {
        return success(collectionService.selectPaymentSummary(params));
    }

    /**
     * 导出到账明细（与到账统计同一口径与筛选条件；忽略分组维度，导出全部命中明细）
     */
    @PreAuthorize("@ss.hasPermi('project:collection:export')")
    @Log(title = "回款管理", businessType = BusinessType.EXPORT)
    @PostMapping("/paymentExport")
    public void paymentExport(HttpServletResponse response, @RequestParam Map<String, Object> params)
    {
        List<PaymentDetailExportVo> list = collectionService.selectPaymentExportList(params);
        ExcelUtil<PaymentDetailExportVo> util = new ExcelUtil<PaymentDetailExportVo>(PaymentDetailExportVo.class);
        util.exportExcel(response, list, "到账明细");
    }

    /**
     * 催款清单导出字段池（导出弹窗「可选字段」的唯一出处）
     *
     * <p>每项含 {@code key / label / type / defaultVisible / width}；
     * 后端以此为准做表头与取值，未知 key 一律忽略。</p>
     */
    @PreAuthorize("@ss.hasPermi('project:collection:export')")
    @GetMapping("/exportColumns")
    public AjaxResult exportColumns()
    {
        return success(collectionService.getExportColumns());
    }

    /**
     * 导出催款清单（可选字段）
     *
     * <p>form 参数：
     * <ul>
     *   <li>筛选条件：与列表页 {@code /list} 完全同一套（keyword / clientUnit / collectStatus /
     *       账龄 / 办结时间 / 到账时间 / 项目类别 / 负责人…），不含分页</li>
     *   <li>{@code fields} 列 key 逗号串，按导出顺序；为空时回退为「默认可见列」（改造前的 11 列）</li>
     * </ul>
     *
     * <p>排序：按客户全称分组、组内账龄降序（催款时同单位的欠款项目挨在一起）。
     * ⚠️ 字段清单以 {@code getExportColumns()} 为唯一出处，未知 key 一律忽略
     *    （既防越权导出内部字段，也防参数拼错导出空列）。</p>
     */
    @PreAuthorize("@ss.hasPermi('project:collection:export')")
    @Log(title = "回款管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response,
                       @RequestParam Map<String, Object> params,
                       @RequestParam(required = false) String fields) throws IOException
    {
        Map<String, Object> data = collectionService.exportCollection(params, splitFields(fields));

        @SuppressWarnings("unchecked")
        List<DynamicColumnExcelExporter.Column> headers =
                (List<DynamicColumnExcelExporter.Column>) data.get("headers");
        @SuppressWarnings("unchecked")
        List<List<Object>> rows = (List<List<Object>>) data.get("rows");

        String fileName = "催款清单_" + DateUtils.parseDateToStr("yyyyMMddHHmmss", new java.util.Date()) + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        FileUtils.setAttachmentResponseHeader(response, fileName);
        // 不输出标题行：第 1 行即表头，导出的文件可直接用于筛选 / 透视
        DynamicColumnExcelExporter.export(response.getOutputStream(), headers, rows);
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
     * 按项目查询催收记录（时间倒序）
     */
    @GetMapping("/log/{projectId}")
    public AjaxResult logList(@PathVariable Long projectId)
    {
        return success(collectionService.selectLogListByProjectId(projectId));
    }

    /**
     * 新增催收记录
     */
    @PreAuthorize("@ss.hasPermi('project:collection:log')")
    @Log(title = "回款催收记录", businessType = BusinessType.INSERT)
    @PostMapping("/log")
    public AjaxResult addLog(@Validated @RequestBody ProjCollectionLog log)
    {
        log.setCreateBy(getUsername());
        return toAjax(collectionService.insertLog(log));
    }

    /**
     * 删除催收记录
     */
    @PreAuthorize("@ss.hasPermi('project:collection:log')")
    @Log(title = "回款催收记录", businessType = BusinessType.DELETE)
    @DeleteMapping("/log/{ids}")
    public AjaxResult removeLog(@PathVariable Long[] ids)
    {
        return toAjax(collectionService.deleteLogByIds(ids, getUsername()));
    }
}
