package com.xakcch.project.service.impl;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.xakcch.common.exception.ServiceException;
import com.xakcch.project.domain.ProjWorkload;
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
     * 自动计算产值：内部产值 = 内部工作量 × 内部单价
     *               外部产值 = 外部工作量 × 外部单价
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
     * 内部产值 = 内部工作量 × 内部单价
     * 外部产值 = 外部工作量 × 外部单价
     */
    private void calcOutput(ProjWorkload workload)
    {
        if (workload.getInternalWorkload() != null && workload.getInternalPrice() != null)
        {
            workload.setInternalOutput(
                workload.getInternalWorkload().multiply(workload.getInternalPrice())
                    .setScale(2, BigDecimal.ROUND_HALF_UP)
            );
        }
        if (workload.getExternalWorkload() != null && workload.getExternalPrice() != null)
        {
            workload.setExternalOutput(
                workload.getExternalWorkload().multiply(workload.getExternalPrice())
                    .setScale(2, BigDecimal.ROUND_HALF_UP)
            );
        }
    }
}
