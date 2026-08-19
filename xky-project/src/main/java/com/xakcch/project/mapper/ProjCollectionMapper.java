package com.xakcch.project.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.xakcch.project.domain.ProjCollectionLog;

/**
 * 回款管理 数据层
 *
 * @author liuyonghui
 */
public interface ProjCollectionMapper
{
    /**
     * 回款台账列表（已办结+未结清，项目维度一行一项目）
     */
    public List<Map<String, Object>> selectCollectionList(Map<String, Object> params);

    /**
     * 客户聚合视图（同一客户多个欠款项目聚合一行）
     */
    public List<Map<String, Object>> selectClientCollectionList(Map<String, Object> params);

    /**
     * 统计卡（待回款项目数/总额、超账期数、本月已回款、上月已回款）
     */
    public Map<String, Object> selectCollectionStats();

    /**
     * 待结算提醒列表（已办结但无外部产值的项目）
     */
    public List<Map<String, Object>> selectUnsettledList(Map<String, Object> params);

    /**
     * 按项目查询催收记录（时间倒序）
     */
    public List<ProjCollectionLog> selectLogListByProjectId(Long projectId);

    /**
     * 新增催收记录
     */
    public int insertLog(ProjCollectionLog log);

    /**
     * 批量删除催收记录（逻辑删除）
     */
    public int deleteLogByIds(@Param("ids") Long[] ids, @Param("updateBy") String updateBy);
}
