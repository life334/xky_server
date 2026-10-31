package com.xakcch.project.service;

import java.util.List;
import java.util.Map;
import com.xakcch.project.domain.ProjCollectionLog;
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
     * 统计卡：待回款项目数/总额、超账期预警、待结算提醒、当前窗口/对比窗口到账额
     * ⚠️ 口径随列表筛选联动，params 与 selectCollectionList 同一套条件
     */
    public Map<String, Object> selectCollectionStats(Map<String, Object> params);

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
     * 催款清单导出字段池（导出弹窗「可选字段」的唯一出处）
     *
     * @return 每项含 key / label / type / defaultVisible / width
     */
    public List<Map<String, Object>> getExportColumns();

    /**
     * 导出催款清单（可选字段）：与列表页同筛选条件，按 fields 顺序输出表头 + 行数据
     *
     * @param params 列表筛选条件（同一套参数，忽略分页）
     * @param fields 导出字段 key 列表；为空 / 全部未知时回退为默认可见列
     * @return {@code headers}（DynamicColumnExcelExporter.Column 列表）与 {@code rows}
     */
    public Map<String, Object> exportCollection(Map<String, Object> params, List<String> fields);

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
