package com.xakcch.project.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.xakcch.common.exception.ServiceException;
import com.xakcch.project.domain.ProjWorkload;
import com.xakcch.project.domain.vo.OutputDetailExportVo;
import com.xakcch.project.mapper.ProjWorkloadMapper;
import com.xakcch.project.service.IProjWorkloadService;

/**
 * 工作量 业务层实现
 *
 * @author liuyonghui
 */
@Service
public class ProjWorkloadServiceImpl implements IProjWorkloadService
{
    @Autowired
    private ProjWorkloadMapper workloadMapper;

    /**
     * 查询工作量列表
     */
    @Override
    public List<ProjWorkload> selectWorkloadList(ProjWorkload workload)
    {
        return workloadMapper.selectWorkloadList(workload);
    }

    /**
     * 根据ID查询
     */
    @Override
    public ProjWorkload selectWorkloadById(Long id)
    {
        return workloadMapper.selectWorkloadById(id);
    }

    /**
     * 新增工作量
     * 自动计算产值：内部产值 = 工作量 × 内部单价
     *               外部产值 = 工作量 × 外部单价
     */
    @Override
    public int insertWorkload(ProjWorkload workload)
    {
        // 默认单价来源
        if (workload.getPriceSource() == null || workload.getPriceSource().isEmpty())
        {
            workload.setPriceSource("dict");
        }
        // 自动计算产值
        calcOutput(workload);
        return workloadMapper.insertWorkload(workload);
    }

    /**
     * 修改工作量
     */
    @Override
    public int updateWorkload(ProjWorkload workload)
    {
        // 自动计算产值
        calcOutput(workload);
        workload.setUpdateBy(workload.getUpdateBy());
        return workloadMapper.updateWorkload(workload);
    }

    /**
     * 批量删除
     */
    @Override
    public int deleteWorkloadByIds(Long[] ids)
    {
        return workloadMapper.deleteWorkloadByIds(ids);
    }

    /**
     * 自动计算产值
     * 内部产值 = 工作量 × 内部单价
     * 外部产值 = 工作量 × 外部单价
     */
    private void calcOutput(ProjWorkload workload)
    {
        if (workload.getWorkload() != null && workload.getInternalPrice() != null)
        {
            workload.setInternalOutput(
                workload.getWorkload().multiply(workload.getInternalPrice())
                    .setScale(2, BigDecimal.ROUND_HALF_UP)
            );
        }
        if (workload.getWorkload() != null && workload.getExternalPrice() != null)
        {
            workload.setExternalOutput(
                workload.getWorkload().multiply(workload.getExternalPrice())
                    .setScale(2, BigDecimal.ROUND_HALF_UP)
            );
        }
    }

    // ============================================================
    // ===== 产值统计（费用结算页「产值统计」页签） =====
    // ============================================================

    /** 产值统计允许的分组维度（白名单，避免任意值进入 SQL 判断分支） */
    private static final Set<String> OUTPUT_GROUP_BY =
            new HashSet<>(Arrays.asList("none", "month", "quarter", "year", "clientUnit", "leader", "category"));

    /**
     * 产值统计：合计（内部产值 / 外部产值 / 涉及项目数）+ 分组明细
     *
     * <p>归属时间 = 项目办结时间（close_time）；内外产值不相加。</p>
     * <p>⚠️ 合计查询不参与 groupBy='leader' 的负责人展开，否则合计会被重复累计。</p>
     */
    @Override
    public Map<String, Object> selectOutputSummary(Map<String, Object> params)
    {
        Map<String, Object> result = new LinkedHashMap<>();

        String groupBy = str(params.get("groupBy"));
        if (!OUTPUT_GROUP_BY.contains(groupBy))
        {
            groupBy = "none";
        }
        params.put("groupBy", groupBy);

        // 合计（先去 leaderJoin，保证合计与分组无关）
        params.remove("leaderJoin");
        Map<String, Object> summary = workloadMapper.selectOutputSummary(params);
        result.put("summary", summary == null ? new LinkedHashMap<String, Object>() : summary);

        // 分组明细
        if ("none".equals(groupBy))
        {
            result.put("groups", new ArrayList<Map<String, Object>>());
        }
        else
        {
            if ("leader".equals(groupBy))
            {
                // 按负责人展开：一个项目多位负责人时，该项目产值在每位负责人下各计一次
                params.put("leaderJoin", "1");
            }
            result.put("groups", workloadMapper.selectOutputSummaryGroups(params));
        }

        result.put("dimension", "closeTime");
        result.put("groupBy", groupBy);
        return result;
    }

    /**
     * 产值明细（下钻）：与产值汇总同一口径与筛选条件（忽略分组维度）
     */
    @Override
    public List<Map<String, Object>> selectOutputDetail(Map<String, Object> params)
    {
        params.remove("groupBy");
        params.remove("leaderJoin");
        return workloadMapper.selectOutputDetail(params);
    }

    /**
     * 导出产值明细：与产值统计同一口径与筛选条件（不分页，忽略分组维度）
     */
    @Override
    public List<OutputDetailExportVo> selectOutputExportList(Map<String, Object> params)
    {
        List<Map<String, Object>> rows = selectOutputDetail(params);
        List<OutputDetailExportVo> result = new ArrayList<>();
        for (Map<String, Object> row : rows)
        {
            result.add(new OutputDetailExportVo(
                    str(row.get("projectCode")),
                    str(row.get("projectName")),
                    str(row.get("clientUnit")),
                    date(row.get("closeTime")),
                    num(row.get("internalOutput")),
                    num(row.get("externalOutput"))));
        }
        return result;
    }

    private String str(Object o)
    {
        return o == null ? "" : o.toString();
    }

    private BigDecimal num(Object o)
    {
        if (o == null)
        {
            return BigDecimal.ZERO;
        }
        if (o instanceof BigDecimal)
        {
            return (BigDecimal) o;
        }
        try
        {
            return new BigDecimal(o.toString());
        }
        catch (Exception e)
        {
            return BigDecimal.ZERO;
        }
    }

    /** PG DATE 列经 resultType=map 返回 java.sql.Date，统一转成 java.util.Date 供 Excel 导出 */
    private Date date(Object o)
    {
        if (o instanceof Date)
        {
            return new Date(((Date) o).getTime());
        }
        return null;
    }
}
