package com.xakcch.project.mapper;

import java.util.List;
import com.xakcch.project.domain.ProjMaterialFlow;
import org.apache.ibatis.annotations.Param;

/**
 * 资料流转记录 数据层
 *
 * @author liuyonghui
 */
public interface ProjMaterialFlowMapper
{
    /**
     * 根据资料ID查询流转记录列表（含操作人和担保人姓名）
     */
    public List<ProjMaterialFlow> selectFlowListByMaterialId(@Param("materialId") Long materialId);

    /**
     * 新增流转记录
     */
    public int insertFlow(ProjMaterialFlow flow);
}
