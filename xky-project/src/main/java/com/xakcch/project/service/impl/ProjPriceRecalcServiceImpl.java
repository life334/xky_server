package com.xakcch.project.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.xakcch.project.domain.ProjCategoryBilling;
import com.xakcch.project.domain.ProjContractPrice;
import com.xakcch.project.domain.ProjProject;
import com.xakcch.project.domain.ProjWorkload;
import com.xakcch.project.mapper.ProjCategoryBillingMapper;
import com.xakcch.project.mapper.ProjContractPriceMapper;
import com.xakcch.project.mapper.ProjProjectMapper;
import com.xakcch.project.mapper.ProjWorkloadMapper;
import com.xakcch.project.service.IProjPriceRecalcService;

/**
 * 工作量单价重算服务实现
 *
 * @author liuyonghui
 */
@Service
public class ProjPriceRecalcServiceImpl implements IProjPriceRecalcService
{
    private static final Logger log = LoggerFactory.getLogger(ProjPriceRecalcServiceImpl.class);

    /** 单价来源：合同价 */
    private static final String SRC_CONTRACT = "contract";
    /** 单价来源：字典默认价 */
    private static final String SRC_DICT = "dict";
    /** 单价来源：导入推导价 */
    private static final String SRC_IMPORTED = "imported";
    /** 手动覆盖（单价 / 起步量通用） */
    private static final String SRC_MANUAL = "manual";

    @Autowired
    private ProjProjectMapper projectMapper;

    @Autowired
    private ProjWorkloadMapper workloadMapper;

    @Autowired
    private ProjContractPriceMapper contractPriceMapper;

    @Autowired
    private ProjCategoryBillingMapper billingMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int recalcExternalPrices(Long projectId, List<Long> billingIds, boolean forceReset)
    {
        if (projectId == null)
        {
            return 0;
        }
        ProjProject project = projectMapper.selectProjectById(projectId);
        if (project == null)
        {
            return 0;
        }

        List<ProjWorkload> workloads = workloadMapper.selectWorkloadsByProjectId(projectId);
        if (workloads == null || workloads.isEmpty())
        {
            return 0;
        }

        // 1. 合同索引： key = categoryId + "|" + billingId  ->  price / minQuantity
        Map<String, BigDecimal> contractPriceMap = new HashMap<>();
        Map<String, BigDecimal> contractMinMap = new HashMap<>();
        Long contractId = project.getContractId();
        if (contractId != null)
        {
            List<ProjContractPrice> cps = contractPriceMapper.selectPriceListByContractId(contractId);
            if (cps != null)
            {
                for (ProjContractPrice cp : cps)
                {
                    if (cp.getCategoryId() == null || cp.getBillingId() == null)
                    {
                        continue;
                    }
                    String key = cp.getCategoryId() + "|" + cp.getBillingId();
                    if (cp.getPrice() != null)
                    {
                        contractPriceMap.put(key, cp.getPrice());
                    }
                    if (cp.getContractMinQuantity() != null)
                    {
                        contractMinMap.put(key, cp.getContractMinQuantity());
                    }
                }
            }
        }

        // 2. 字典价索引： key = categoryId + "|" + billingType + "|" + billingCategory  ->  billing
        //    同时提供 (categoryId|billingId) -> billing，用于按 billingId 反查其 type/category（重算过滤需要）
        Map<String, ProjCategoryBilling> dictPriceMap = new HashMap<>();
        Map<Long, ProjCategoryBilling> billingById = new HashMap<>();
        Set<Long> categoryIds = new HashSet<>();
        for (ProjWorkload w : workloads)
        {
            if (w.getCategoryId() != null)
            {
                categoryIds.add(w.getCategoryId());
            }
        }
        if (!categoryIds.isEmpty())
        {
            List<ProjCategoryBilling> billings = billingMapper.selectBillingByCategoryIds(new ArrayList<>(categoryIds));
            if (billings != null)
            {
                for (ProjCategoryBilling b : billings)
                {
                    if (b.getCategoryId() == null)
                    {
                        continue;
                    }
                    dictPriceMap.put(dictKey(b.getCategoryId(), b.getBillingType(), b.getBillingCategory()), b);
                    if (b.getId() != null)
                    {
                        billingById.put(b.getId(), b);
                    }
                }
            }
        }

        boolean isImportProject = "import".equals(project.getDataSource());
        boolean filterByBilling = billingIds != null && !billingIds.isEmpty();
        Set<Long> billingIdSet = filterByBilling ? new HashSet<>(billingIds) : null;

        int updated = 0;
        for (ProjWorkload w : workloads)
        {
            // 只重算外部工作量
            if (!"external".equals(w.getBillingType()))
            {
                continue;
            }
            // 触发点二：只重算被改动的计费方式对应的行
            // 工作量表无 billing_id，需通过 (categoryId, billingType, billingCategory) 反查 billingId
            if (filterByBilling)
            {
                ProjCategoryBilling matched = dictPriceMap.get(dictKey(w.getCategoryId(), w.getBillingType(), w.getBillingCategory()));
                Long wBillingId = (matched != null) ? matched.getId() : null;
                if (wBillingId == null || !billingIdSet.contains(wBillingId))
                {
                    continue;
                }
            }

            // 现值（兜底基准）
            BigDecimal currentPrice = w.getUnitPrice() != null ? w.getUnitPrice() : w.getExternalPrice();

            // 解析目标单价
            ResolveResult rr = resolvePrice(project, w, contractPriceMap, dictPriceMap, isImportProject, currentPrice, forceReset);
            if (rr.price == null)
            {
                continue;
            }

            // 解析目标起步量（与单价同优先级：① 手改 ② 合同 ③ 字典）
            MinResolve mr = resolveMinQuantity(project, w, contractMinMap, dictPriceMap, forceReset);

            // 无变化则跳过（单价、起步量及其来源都相同才跳过）
            boolean priceSame = w.getUnitPrice() != null && w.getUnitPrice().compareTo(rr.price) == 0;
            boolean sourceSame = rr.source.equals(w.getPriceSource());
            boolean minSame = bigDecimalEquals(w.getMinQuantity(), mr.minQuantity)
                && java.util.Objects.equals(mr.source, w.getMinQuantitySource());
            if (priceSame && sourceSame && minSame)
            {
                continue;
            }

            // 产值 = 计费数量 × 单价；计费数量 = max(工作量, 起步量)
            BigDecimal output = null;
            if (w.getWorkload() != null)
            {
                BigDecimal effQty = w.getWorkload();
                if (mr.minQuantity != null && mr.minQuantity.signum() > 0 && effQty.signum() > 0
                        && effQty.compareTo(mr.minQuantity) < 0)
                {
                    // 起步量 = 最低计费数量：工作量低于起步量时按起步量计费，达到/超过则按实际
                    // （与前端 calcRow 一致：effQty = max(工作量, 起步量)）
                    effQty = mr.minQuantity;
                }
                output = rr.price.multiply(effQty).setScale(2, RoundingMode.HALF_UP);
            }

            // 备份导入推导价：重算为合同价/字典价时，把导入推导价写入 extra_data.origin_price 以便还原
            String extraData = null;
            if (isImportProject)
            {
                BigDecimal origin = readOriginPrice(w.getExtraData());
                if (origin == null && currentPrice != null)
                {
                    origin = currentPrice;
                }
                if (origin != null)
                {
                    JSONObject extra = new JSONObject();
                    extra.put("origin_price", origin);
                    extraData = extra.toJSONString();
                }
            }

            workloadMapper.updatePriceById(w.getId(), rr.price, output, rr.source, mr.minQuantity, mr.source, extraData);
            updated++;
        }

        if (updated > 0)
        {
            log.info("单价重算完成：projectId={}, billingIds={}, forceReset={}, updated={}", projectId, billingIds, forceReset, updated);
        }
        return updated;
    }

    /**
     * 解析目标单价
     * 优先级：① 手改价（manual，保持不动）② 合同价 ③ 兜底（导入项目→导入推导价→字典价；手动项目→字典价）
     */
    private ResolveResult resolvePrice(ProjProject project, ProjWorkload w,
                                       Map<String, BigDecimal> contractPriceMap,
                                       Map<String, ProjCategoryBilling> dictPriceMap,
                                       boolean isImportProject,
                                       BigDecimal currentPrice,
                                       boolean forceReset)
    {
        String categoryId = w.getCategoryId() == null ? null : String.valueOf(w.getCategoryId());

        // ① 用户手改价：非强制重置场景下不覆盖（合同关联变化时 forceReset=true，历史手改价作废、按原则重算）
        if (!forceReset && "manual".equals(w.getPriceSource()))
        {
            return new ResolveResult(currentPrice, "manual");
        }

        // ② 合同价
        if (project.getContractId() != null && categoryId != null)
        {
            ProjCategoryBilling matched = dictPriceMap.get(dictKey(w.getCategoryId(), w.getBillingType(), w.getBillingCategory()));
            if (matched != null && matched.getId() != null)
            {
                BigDecimal cp = contractPriceMap.get(categoryId + "|" + matched.getId());
                if (cp != null)
                {
                    return new ResolveResult(cp, SRC_CONTRACT);
                }
            }
        }

        // ③ 兜底
        if (isImportProject)
        {
            BigDecimal origin = readOriginPrice(w.getExtraData());
            if (origin != null)
            {
                return new ResolveResult(origin, SRC_IMPORTED);
            }
        }
        ProjCategoryBilling matched = dictPriceMap.get(dictKey(w.getCategoryId(), w.getBillingType(), w.getBillingCategory()));
        if (matched != null && matched.getUnitPrice() != null)
        {
            return new ResolveResult(matched.getUnitPrice(), SRC_DICT);
        }
        // 字典价也没有：保持原值
        return new ResolveResult(currentPrice, w.getPriceSource() != null ? w.getPriceSource() : SRC_DICT);
    }

    /**
     * 解析目标起步量（优先级与单价一致）：
     * ① 手改起步量（manual，保持不动；forceReset=true 时作废重算）
     * ② 合同起步量（项目关联合同且该计费方式配置了合同起步量）
     * ③ 字典起步量（proj_category_billing.min_quantity）
     * 三者皆无 → 保持原值
     */
    private MinResolve resolveMinQuantity(ProjProject project, ProjWorkload w,
                                          Map<String, BigDecimal> contractMinMap,
                                          Map<String, ProjCategoryBilling> dictPriceMap,
                                          boolean forceReset)
    {
        BigDecimal current = w.getMinQuantity();
        String currentSource = w.getMinQuantitySource();

        // ① 手改起步量：非强制重置场景下不覆盖
        if (!forceReset && SRC_MANUAL.equals(currentSource))
        {
            return new MinResolve(current, SRC_MANUAL);
        }

        ProjCategoryBilling matched = dictPriceMap.get(dictKey(w.getCategoryId(), w.getBillingType(), w.getBillingCategory()));

        // ② 合同起步量
        if (project.getContractId() != null && w.getCategoryId() != null && matched != null && matched.getId() != null)
        {
            BigDecimal cm = contractMinMap.get(w.getCategoryId() + "|" + matched.getId());
            if (cm != null)
            {
                return new MinResolve(cm, SRC_CONTRACT);
            }
        }

        // ③ 字典起步量
        if (matched != null && matched.getMinQuantity() != null)
        {
            return new MinResolve(matched.getMinQuantity(), SRC_DICT);
        }

        // 无起步量来源：保持原值
        return new MinResolve(current, currentSource != null ? currentSource : SRC_DICT);
    }

    /** 读取 extra_data.origin_price（导入推导价备份） */
    private BigDecimal readOriginPrice(String extraData)
    {
        if (extraData == null || extraData.isEmpty())
        {
            return null;
        }
        try
        {
            JSONObject obj = JSON.parseObject(extraData);
            if (obj == null || !obj.containsKey("origin_price"))
            {
                return null;
            }
            return obj.getBigDecimal("origin_price");
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private String dictKey(Long categoryId, String billingType, String billingCategory)
    {
        return (categoryId == null ? "" : categoryId) + "|"
            + (billingType == null ? "" : billingType) + "|"
            + (billingCategory == null ? "" : billingCategory);
    }

    /** 单价解析结果 */
    private static class ResolveResult
    {
        final BigDecimal price;
        final String source;

        ResolveResult(BigDecimal price, String source)
        {
            this.price = price;
            this.source = source;
        }
    }

    /** 起步量解析结果 */
    private static class MinResolve
    {
        final BigDecimal minQuantity;
        final String source;

        MinResolve(BigDecimal minQuantity, String source)
        {
            this.minQuantity = minQuantity;
            this.source = source;
        }
    }

    /** BigDecimal 相等判断（含 null 语义：都为空视为相等；按值比较而非标度） */
    private boolean bigDecimalEquals(BigDecimal a, BigDecimal b)
    {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.compareTo(b) == 0;
    }
}
