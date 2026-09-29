package com.xakcch.project.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.xakcch.project.domain.ProjMandateRule;

/**
 * 指令性任务规则 数据层
 *
 * @author liuyonghui
 */
public interface ProjMandateRuleMapper
{
    /**
     * 查询规则列表
     *
     * @param rule 查询条件（keyword 模糊、enabled 精确）
     * @return 规则集合
     */
    public List<ProjMandateRule> selectMandateRuleList(ProjMandateRule rule);

    /**
     * 查询全部「启用」规则的关键词（自动打标用；带缓存）
     *
     * @return 关键词列表
     */
    public List<String> selectEnabledKeywords();

    /**
     * 校验关键词是否已存在（同一关键词不重复）
     *
     * @param keyword 关键词
     * @param excludeId 需排除的规则ID（新增时传 null）
     * @return 命中的规则ID（无则 null）
     */
    public Long checkKeywordUnique(@Param("keyword") String keyword, @Param("excludeId") Long excludeId);

    /**
     * 新增规则
     */
    public int insertMandateRule(ProjMandateRule rule);

    /**
     * 修改规则
     */
    public int updateMandateRule(ProjMandateRule rule);

    /**
     * 删除规则（逻辑删除）
     */
    public int deleteMandateRuleById(@Param("id") Long id, @Param("updateBy") String updateBy);

    /**
     * 批量删除规则（逻辑删除）
     */
    public int deleteMandateRuleByIds(@Param("ids") Long[] ids, @Param("updateBy") String updateBy);
}
