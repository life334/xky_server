package com.xakcch.project.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

/**
 * 首页驾驶舱 数据层
 *
 * @author liuyonghui
 */
public interface ProjDashboardMapper
{
    // ===== 项目KPI =====

    /** 在册项目总数 */
    public int countAllProjects();

    /** 指定日期范围内新增项目数 */
    public int countNewProjectsInRange(@Param("beginDate") String beginDate, @Param("endDate") String endDate);

    /** 指定日期范围内办结项目数 */
    public int countCompletedInRange(@Param("beginDate") String beginDate, @Param("endDate") String endDate);

    /** 进行中项目数 */
    public int countActiveProjects();

    // ===== 财务KPI =====

    /** 指定日期范围内到账总额（有到账流水即计入，退款负冲；不再看已废弃的 received_status） */
    public Map<String, Object> sumPeriodPayment(@Param("beginDate") String beginDate, @Param("endDate") String endDate);

    /** 本年累计到账 */
    public Map<String, Object> sumAnnualPayment();

    /** 指定日期范围内产值总额 */
    public Map<String, Object> sumPeriodOutput(@Param("beginDate") String beginDate, @Param("endDate") String endDate);

    /** 本年累计产值 */
    public Map<String, Object> sumAnnualOutput();

    /** 合同总额 + 已到账总额 */
    public Map<String, Object> contractPaymentSummary();

    /** 合同总数 */
    public int countContracts();

    // ===== 预警 =====

    /** 超期任务列表（展示用，LIMIT 10） */
    public List<Map<String, Object>> overdueTaskAlerts();

    /** 超期任务总数（计数用，不受列表 LIMIT 截断影响） */
    public int countOverdueTasks();

    /** 资料流转统计 */
    public List<Map<String, Object>> materialFlowStats();

    // ===== 图表数据 =====

    /** 产值与到账趋势（按月） */
    public List<Map<String, Object>> outputPaymentTrend(@Param("beginDate") String beginDate, @Param("endDate") String endDate);

    /** 项目类型产值分布 */
    public List<Map<String, Object>> categoryOutputDist(@Param("beginDate") String beginDate, @Param("endDate") String endDate);

    /** 产值累计趋势（按月） */
    public List<Map<String, Object>> outputCumulativeTrend(@Param("beginDate") String beginDate, @Param("endDate") String endDate);

    /** 项目动态趋势（按月 新增/办结） */
    public List<Map<String, Object>> projectDynamicTrend(@Param("beginDate") String beginDate, @Param("endDate") String endDate);

    /** 合同收款进度列表 */
    public List<Map<String, Object>> contractPaymentList();

    /** 项目产值排行 TOP10（累计外部产值，全周期） */
    public List<Map<String, Object>> projectOutputTop();

    // ============================================================
    // ===== v2（2026-09-27 契约）：summary/structure/trend/risk =====
    // 全部以 @Param("q") 传 ProjDashboardQuery，轻筛选（clientUnit/leaderId/categoryId）见 v2Filter 片段
    // ============================================================

    /** 合同额按签署日汇总：本年/上年全年/本月/上月 四值一查（clientUnit 筛选生效） */
    public Map<String, Object> contractAmountBySignDate(@Param("q") com.xakcch.project.domain.ProjDashboardQuery q);

    /** 项目期统计四值一查：本期新增/对比期新增/本期办结/对比期办结（新增按 assign_date，修正原 create_time 口径） */
    public Map<String, Object> periodProjectStats(@Param("q") com.xakcch.project.domain.ProjDashboardQuery q);

    /** 到账期统计双值一查：本期/对比期（pay_time 非空，refund 负冲，join 项目应用轻筛选） */
    public Map<String, Object> periodPaymentStats(@Param("q") com.xakcch.project.domain.ProjDashboardQuery q);

    /** 超期候选行（仅手动录入 data_source='manual' 且工期要求非空），日期算术在 Java 层（WorkdayUtils） */
    public List<Map<String, Object>> selectOverdueCandidates(@Param("q") com.xakcch.project.domain.ProjDashboardQuery q);

    /** 手动项目工期要求维护率统计：total/maintained 双值 */
    public Map<String, Object> manualDurationStats(@Param("q") com.xakcch.project.domain.ProjDashboardQuery q);

    /** 项目 KPI：总数/进行中 双值（轻筛选生效） */
    public Map<String, Object> projectKpiStats(@Param("q") com.xakcch.project.domain.ProjDashboardQuery q);

    /** 类别全量清单（id+name，桶映射用，一次查） */
    public List<Map<String, Object>> selectCategoryList();

    /** 类型桶统计：数量（assign 落周期）/办结数/内外产值（close 落周期）/合同额，GROUP BY category_id */
    public List<Map<String, Object>> bucketProjectStats(@Param("q") com.xakcch.project.domain.ProjDashboardQuery q);

    /** 负责人×类型矩阵：count/internalOutput/externalOutput，GROUP BY leader_id + category_id（多人项目允许重复计数） */
    public List<Map<String, Object>> leaderBucketMatrix(@Param("q") com.xakcch.project.domain.ProjDashboardQuery q);

    /** 负责人名单（user_id + nick_name，一次查，矩阵 Java 填名） */
    public List<Map<String, Object>> selectLeaderNames();

    /** 周期内 external_output 为空的 workload 行数（前端「未填」提示） */
    public int countExternalOutputMissing(@Param("q") com.xakcch.project.domain.ProjDashboardQuery q);

    /** 外部产值月增量+累计双轴（generate_series 补零月，轻筛选生效） */
    public List<Map<String, Object>> outputCumulativeTrendFiltered(@Param("q") com.xakcch.project.domain.ProjDashboardQuery q);

    /** 项目动态三系列：新增(assign_date)/办结(close_time)/到账金额，generate_series 补零月 */
    public List<Map<String, Object>> projectDynamicTrendFiltered(@Param("q") com.xakcch.project.domain.ProjDashboardQuery q);

    /** 欠款按年（办结年）：outputBase/received/debt（先项目级 GREATEST 防正负互抵） */
    public List<Map<String, Object>> debtStatsByYear(@Param("q") com.xakcch.project.domain.ProjDashboardQuery q);

    /** 欠款项目候选行（办结且欠款>0，全量，Java 取 top5+计数；带负责人名 string_agg） */
    public List<Map<String, Object>> debtProjectCandidates(@Param("q") com.xakcch.project.domain.ProjDashboardQuery q);

    /** 未关联合同候选行（仅手动录入；3 个工作日判定在 Java 层） */
    public List<Map<String, Object>> contractMissingCandidates(@Param("q") com.xakcch.project.domain.ProjDashboardQuery q);

    /**
     * 指令性任务卡：期间内办结的指令性项目的外部产值 + 项目数
     * （该部分外部产值不计入外部产值 / 应收，单列以便对账）
     */
    public Map<String, Object> mandateOutputStats(@Param("q") com.xakcch.project.domain.ProjDashboardQuery q);
}
