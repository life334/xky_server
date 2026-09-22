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
import com.xakcch.project.domain.ProjCollectionLog;
import com.xakcch.project.domain.vo.CollectionExportVo;
import com.xakcch.project.domain.vo.PaymentDetailExportVo;
import com.xakcch.project.mapper.ProjCollectionMapper;
import com.xakcch.project.service.IProjCollectionService;

/**
 * 回款管理 服务实现
 *
 * @author liuyonghui
 */
@Service
public class ProjCollectionServiceImpl implements IProjCollectionService
{
    /** 到账统计允许的分组维度（白名单，避免任意值进入 SQL 判断分支） */
    private static final Set<String> SUMMARY_GROUP_BY =
            new HashSet<>(Arrays.asList("none", "month", "quarter", "year", "clientUnit", "leader", "paymentType", "category"));

    @Autowired
    private ProjCollectionMapper collectionMapper;

    @Override
    public List<Map<String, Object>> selectCollectionList(Map<String, Object> params)
    {
        return collectionMapper.selectCollectionList(params);
    }

    @Override
    public List<Map<String, Object>> selectClientCollectionList(Map<String, Object> params)
    {
        return collectionMapper.selectClientCollectionList(params);
    }

    @Override
    public Map<String, Object> selectCollectionStats()
    {
        return collectionMapper.selectCollectionStats();
    }

    @Override
    public List<Map<String, Object>> selectUnsettledList(Map<String, Object> params)
    {
        return collectionMapper.selectUnsettledList(params);
    }

    @Override
    public List<Map<String, Object>> selectReceivedDetail(Map<String, Object> params)
    {
        return collectionMapper.selectReceivedDetail(params);
    }

    /**
     * 到账统计：合计 + 分组明细
     *
     * 口径：只要有到账流水（del_flag='0'）即计入，退款负冲，不看 received_status（已废弃列）。
     * dimension=closeTime 时按办结时间归属且不限制到账时间（取这批办结项目的累计到账）。
     * ⚠️ 合计查询不参与 groupBy='leader' 的负责人展开，否则合计会被重复累计。
     */
    @Override
    public Map<String, Object> selectPaymentSummary(Map<String, Object> params)
    {
        Map<String, Object> result = new LinkedHashMap<>();

        // 口径归一
        String dimension = "closeTime".equals(str(params.get("dimension"))) ? "closeTime" : "payTime";
        params.put("dimension", dimension);

        String groupBy = str(params.get("groupBy"));
        if (!SUMMARY_GROUP_BY.contains(groupBy))
        {
            groupBy = "none";
        }
        params.put("groupBy", groupBy);

        // 合计（先去 leaderJoin，保证合计与分组无关）
        params.remove("leaderJoin");
        Map<String, Object> summary = collectionMapper.selectPaymentSummary(params);
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
                // 按负责人展开：一个项目多位负责人时，同一笔到账在每位负责人下各计一次
                params.put("leaderJoin", "1");
            }
            result.put("groups", collectionMapper.selectPaymentSummaryGroups(params));
        }

        result.put("dimension", dimension);
        result.put("groupBy", groupBy);
        return result;
    }

    /**
     * 导出到账明细：与到账汇总同一口径与筛选条件（不分页，忽略分组维度）
     */
    @Override
    public List<PaymentDetailExportVo> selectPaymentExportList(Map<String, Object> params)
    {
        params.remove("groupBy");
        params.remove("leaderJoin");
        List<Map<String, Object>> rows = collectionMapper.selectReceivedDetail(params);
        List<PaymentDetailExportVo> result = new ArrayList<>();
        for (Map<String, Object> row : rows)
        {
            result.add(new PaymentDetailExportVo(
                    str(row.get("projectCode")),
                    str(row.get("projectName")),
                    str(row.get("clientUnit")),
                    paymentTypeText(str(row.get("paymentType"))),
                    num(row.get("amount")),
                    date(row.get("payTime")),
                    date(row.get("closeTime")),
                    str(row.get("payUnit")),
                    str(row.get("payMethod")),
                    str(row.get("remark"))));
        }
        return result;
    }

    /**
     * 导出催款清单：SQL 已按账龄/客户排序，此处按客户全称重排（同客户相邻）并转换催收状态文案
     */
    @Override
    public List<CollectionExportVo> selectExportList(Map<String, Object> params)
    {
        List<Map<String, Object>> rows = collectionMapper.selectCollectionList(params);
        // 按客户全称分组排序（同客户记录相邻），客户内保持账龄降序
        rows.sort((a, b) -> {
            int cmp = str(a.get("clientUnit")).compareTo(str(b.get("clientUnit")));
            if (cmp != 0)
            {
                return cmp;
            }
            return num(b.get("debtMonths")).compareTo(num(a.get("debtMonths")));
        });
        List<CollectionExportVo> result = new ArrayList<>();
        for (Map<String, Object> row : rows)
        {
            result.add(new CollectionExportVo(
                    str(row.get("clientUnit")),
                    str(row.get("projectCode")),
                    str(row.get("projectName")),
                    str(row.get("engineeringProject")),
                    date(row.get("closeTime")),
                    num(row.get("receivable")),
                    num(row.get("received")),
                    num(row.get("unpaidAmount")),
                    num(row.get("debtMonths")),
                    date(row.get("lastCollectTime")),
                    collectStatusText(str(row.get("collectStatus")))));
        }
        return result;
    }

    @Override
    public List<ProjCollectionLog> selectLogListByProjectId(Long projectId)
    {
        return collectionMapper.selectLogListByProjectId(projectId);
    }

    @Override
    public int insertLog(ProjCollectionLog log)
    {
        return collectionMapper.insertLog(log);
    }

    @Override
    public int deleteLogByIds(Long[] ids, String updateBy)
    {
        return collectionMapper.deleteLogByIds(ids, updateBy);
    }

    /** 催收状态 → 中文文案 */
    private String collectStatusText(String status)
    {
        if ("overdue".equals(status))
        {
            return "超期未催";
        }
        if ("calling".equals(status))
        {
            return "催收中";
        }
        return "从未催收";
    }

    /** 付款类型 → 中文文案（字典 proj_payment_type：advance/final/refund） */
    private String paymentTypeText(String type)
    {
        if ("advance".equals(type))
        {
            return "预付款";
        }
        if ("final".equals(type))
        {
            return "尾款";
        }
        if ("refund".equals(type))
        {
            return "退款";
        }
        return str(type);
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

    private Date date(Object o)
    {
        if (o instanceof Date)
        {
            return (Date) o;
        }
        return null;
    }
}
