package com.xakcch.project.service;

import java.util.List;
import java.util.Map;
import com.xakcch.project.domain.ProjCollectionLog;
import com.xakcch.project.domain.vo.CollectionExportVo;
import com.xakcch.project.domain.vo.PaymentDetailExportVo;

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
     * 到账明细（下钻：与到账汇总同一驱动与筛选条件）
     */
    public List<Map<String, Object>> selectReceivedDetail(Map<String, Object> params);

    /**
     * 到账统计：合计（到账金额 / 退款金额 / 净额 / 笔数）+ 分组明细
     *
     * @param params 查询条件（dimension / begin / end / groupBy / keyword / clientUnit /
     *               projectCategoryId / paymentType / payUnit / payMethod / leaderId）
     * @return { summary: {...}, groups: [...] }
     */
    public Map<String, Object> selectPaymentSummary(Map<String, Object> params);

    /**
     * 导出到账明细（与到账汇总同一口径与筛选条件，不分页）
     */
    public List<PaymentDetailExportVo> selectPaymentExportList(Map<String, Object> params);

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
