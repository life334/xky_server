package com.xakcch.project.service;

import java.util.List;
import java.util.Map;
import com.xakcch.project.domain.ProjCollectionLog;
import com.xakcch.project.domain.vo.CollectionExportVo;

/**
 * 回款管理 服务层
 *
 * @author liuyonghui
 */
public interface IProjCollectionService
{
    /**
     * 回款台账列表（已办结+未结清，项目维度）
     */
    public List<Map<String, Object>> selectCollectionList(Map<String, Object> params);

    /**
     * 客户聚合视图
     */
    public List<Map<String, Object>> selectClientCollectionList(Map<String, Object> params);

    /**
     * 统计卡
     */
    public Map<String, Object> selectCollectionStats();

    /**
     * 待结算提醒列表
     */
    public List<Map<String, Object>> selectUnsettledList(Map<String, Object> params);

    /**
     * 导出催款清单（按客户分组）
     */
    public List<CollectionExportVo> selectExportList(Map<String, Object> params);

    /**
     * 按项目查询催收记录
     */
    public List<ProjCollectionLog> selectLogListByProjectId(Long projectId);

    /**
     * 新增催收记录
     */
    public int insertLog(ProjCollectionLog log);

    /**
     * 批量删除催收记录
     */
    public int deleteLogByIds(Long[] ids, String updateBy);
}
