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
import com.xakcch.project.domain.vo.CollectionExportVo;
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
     * 统计卡（待回款项目数/总额、超账期数、本月已回款、上月已回款）
     */
    @GetMapping("/stats")
    public AjaxResult stats()
    {
        return success(collectionService.selectCollectionStats());
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
     * 导出催款清单（按客户分组的欠款明细）
     */
    @PreAuthorize("@ss.hasPermi('project:collection:export')")
    @Log(title = "回款管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, @RequestParam Map<String, Object> params)
    {
        List<CollectionExportVo> list = collectionService.selectExportList(params);
        ExcelUtil<CollectionExportVo> util = new ExcelUtil<CollectionExportVo>(CollectionExportVo.class);
        util.exportExcel(response, list, "催款清单");
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
