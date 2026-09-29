package com.xakcch.project.service;

import java.util.List;
import java.util.Map;
import com.xakcch.project.domain.ProjMandateRule;

/**
 * 指令性任务 服务层
 *
 * <p>指令性任务 = 委托单位命中规则关键词的项目。其「外部产值（= 应收账款）」不计入
 * 应收 / 全量外部产值，改道到独立的「指令性任务」指标；内部产值照常计算。</p>
 *
 * @author liuyonghui
 */
public interface IProjMandateService
{
    /** 项目性质：市场性任务 */
    String NATURE_NORMAL = "normal";

    /** 项目性质：指令性任务 */
    String NATURE_MANDATE = "mandate";

    /**
     * 查询规则列表
     */
    List<ProjMandateRule> selectRuleList(ProjMandateRule rule);

    /**
     * 新增规则（关键词唯一校验）
     */
    int insertRule(ProjMandateRule rule);

    /**
     * 修改规则（关键词唯一校验）
     */
    int updateRule(ProjMandateRule rule);

    /**
     * 批量删除规则（逻辑删除）
     */
    int deleteRuleByIds(Long[] ids);

    /**
     * 规则预览：启用中的关键词 + 命中项目数 + 其中已是指令性任务的数量
     *
     * @return { keywords: [...], matchedCount: N, alreadyCount: M }
     */
    Map<String, Object> preview();

    /**
     * 一键回填存量：把命中规则、且当前不是指令性任务的项目批量打标
     *
     * @return 更新条数
     */
    int applyToExisting();

    /**
     * 批量设置指定项目的项目性质
     *
     * @param ids    项目 id 列表
     * @param nature normal / mandate
     * @return 更新条数
     */
    int setNature(List<Long> ids, String nature);

    /**
     * 新建 / 导入项目时是否按规则自动打标（sys_config: proj.mandate.autoMatch）
     */
    boolean isAutoMatchEnabled();

    /**
     * 设置自动打标开关
     */
    int setAutoMatchEnabled(boolean enabled);

    /**
     * 按规则判定某个委托单位对应的项目性质（关键词「包含匹配」，兼容名称变体）
     *
     * @param clientUnit 委托单位
     * @return mandate / normal
     */
    String resolveNature(String clientUnit);
}
