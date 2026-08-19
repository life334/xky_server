package com.xakcch.project.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.xakcch.project.domain.ProjCollectionLog;
import com.xakcch.project.domain.vo.CollectionExportVo;
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
