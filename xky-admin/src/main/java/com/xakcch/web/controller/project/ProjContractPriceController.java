package com.xakcch.web.controller.project;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.xakcch.common.core.controller.BaseController;
import com.xakcch.common.core.domain.AjaxResult;
import com.xakcch.project.domain.ProjContractPrice;
import com.xakcch.project.service.IProjContractPriceService;

/**
 * 合同单价管理
 *
 * @author liuyonghui
 */
@RestController
@RequestMapping("/project/contract/price")
public class ProjContractPriceController extends BaseController
{
    @Autowired
    private IProjContractPriceService priceService;

    /**
     * 获取指定合同的全部类别树 + 已填单价
     */
    @PreAuthorize("@ss.hasPermi('project:contract:price:list')")
    @GetMapping("/list")
    public AjaxResult list(@RequestParam Long contractId)
    {
        List<ProjContractPrice> list = priceService.getCategoryTreeWithPrice(contractId);
        return success(list);
    }

    /**
     * 批量保存合同单价
     */
    @PreAuthorize("@ss.hasPermi('project:contract:price:save')")
    @PutMapping
    public AjaxResult save(@RequestBody List<ProjContractPrice> priceList)
    {
        if (priceList == null || priceList.isEmpty())
        {
            return error("单价列表不能为空");
        }
        Long contractId = priceList.get(0).getContractId();
        if (contractId == null)
        {
            return error("合同ID不能为空");
        }
        priceService.savePrices(contractId, priceList);
        return success();
    }
}
