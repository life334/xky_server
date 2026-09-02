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

    /**
     * 逻辑删除某项目下所有资料的流转记录（删除项目时级联清理）
     *
     * @param projectId 项目ID
     * @return 结果
     */
    public int deleteFlowsByProjectId(Long projectId);

    /**
     * 逻辑删除指定项目ID数组下所有资料的流转记录（批量删除项目时级联清理）
     *
     * @param projectIds 项目ID数组
     * @return 结果
     */
    public int deleteFlowsByProjectIds(@Param("projectIds") Long[] projectIds);
}
