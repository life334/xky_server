package com.xakcch.project.service.impl;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.xakcch.common.exception.ServiceException;
import com.xakcch.common.utils.SecurityUtils;
import com.xakcch.project.domain.ProjMandateRule;
import com.xakcch.project.mapper.ProjMandateRuleMapper;
import com.xakcch.project.mapper.ProjProjectMapper;
import com.xakcch.project.service.IProjMandateService;
import com.xakcch.system.domain.SysConfig;
import com.xakcch.system.service.ISysConfigService;

/**
 * 指令性任务 服务实现
 *
 * @author liuyonghui
 */
@Service
public class ProjMandateServiceImpl implements IProjMandateService
{
    /** 新建 / 导入项目时是否按规则自动打标的开关（sys_config 键） */
    private static final String CONFIG_AUTO_MATCH = "proj.mandate.autoMatch";

    /** 关键词缓存有效期：30s（导入 2000 行只需 1 次查询，同时避免规则改了长时间不生效） */
    private static final long KEYWORD_CACHE_MS = 30_000L;

    @Autowired
    private ProjMandateRuleMapper ruleMapper;

    @Autowired
    private ProjProjectMapper projectMapper;

    @Autowired
    private ISysConfigService configService;

    private volatile List<String> keywordCache;

    private volatile long keywordCacheAt;

    @Override
    public List<ProjMandateRule> selectRuleList(ProjMandateRule rule)
    {
        return ruleMapper.selectMandateRuleList(rule);
    }

    @Override
    public int insertRule(ProjMandateRule rule)
    {
        if (rule.getKeyword() == null || rule.getKeyword().trim().isEmpty())
        {
            throw new ServiceException("委托单位关键词不能为空");
        }
        rule.setKeyword(rule.getKeyword().trim());
        Long exist = ruleMapper.checkKeywordUnique(rule.getKeyword(), null);
        if (exist != null)
        {
            throw new ServiceException("关键词「" + rule.getKeyword() + "」已存在");
        }
        if (rule.getEnabled() == null || rule.getEnabled().isEmpty())
        {
            rule.setEnabled("0");
        }
        rule.setCreateBy(currentUser());
        int rows = ruleMapper.insertMandateRule(rule);
        invalidateKeywordCache();
        return rows;
    }

    @Override
    public int updateRule(ProjMandateRule rule)
    {
        if (rule.getId() == null)
        {
            throw new ServiceException("规则ID不能为空");
        }
        if (rule.getKeyword() != null && !rule.getKeyword().trim().isEmpty())
        {
            rule.setKeyword(rule.getKeyword().trim());
            Long exist = ruleMapper.checkKeywordUnique(rule.getKeyword(), rule.getId());
            if (exist != null)
            {
                throw new ServiceException("关键词「" + rule.getKeyword() + "」已存在");
            }
        }
        rule.setUpdateBy(currentUser());
        int rows = ruleMapper.updateMandateRule(rule);
        invalidateKeywordCache();
        return rows;
    }

    @Override
    public int deleteRuleByIds(Long[] ids)
    {
        if (ids == null || ids.length == 0)
        {
            throw new ServiceException("请先选择规则");
        }
        int rows = ruleMapper.deleteMandateRuleByIds(ids, currentUser());
        invalidateKeywordCache();
        return rows;
    }

    @Override
    public Map<String, Object> preview()
    {
        List<String> keywords = enabledKeywords();
        Map<String, Object> stats = projectMapper.previewMandateRule(keywords);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("keywords", keywords);
        result.put("matchedCount", stats == null ? 0 : stats.get("matchedCount"));
        result.put("alreadyCount", stats == null ? 0 : stats.get("alreadyCount"));
        result.put("autoMatch", isAutoMatchEnabled());
        return result;
    }

    @Override
    public int applyToExisting()
    {
        List<String> keywords = enabledKeywords();
        List<Long> ids = projectMapper.selectIdsByClientUnitKeywords(keywords, true);
        if (ids == null || ids.isEmpty())
        {
            return 0;
        }
        return projectMapper.updateProjectNatureByIds(ids, NATURE_MANDATE, currentUser());
    }

    @Override
    public int setNature(List<Long> ids, String nature)
    {
        if (ids == null || ids.isEmpty())
        {
            throw new ServiceException("请先选择项目");
        }
        if (!NATURE_MANDATE.equals(nature) && !NATURE_NORMAL.equals(nature))
        {
            throw new ServiceException("项目性质取值非法：" + nature);
        }
        return projectMapper.updateProjectNatureByIds(ids, nature, currentUser());
    }

    @Override
    public boolean isAutoMatchEnabled()
    {
        String v = configService.selectConfigByKey(CONFIG_AUTO_MATCH);
        // 未配置时默认为开（规则本身即开关：停用某条规则即对它不再生效）
        if (v == null || v.trim().isEmpty())
        {
            return true;
        }
        String s = v.trim();
        return "true".equalsIgnoreCase(s) || "1".equals(s) || "Y".equalsIgnoreCase(s);
    }

    @Override
    public int setAutoMatchEnabled(boolean enabled)
    {
        SysConfig probe = new SysConfig();
        probe.setConfigKey(CONFIG_AUTO_MATCH);
        List<SysConfig> list = configService.selectConfigList(probe);
        if (list == null || list.isEmpty())
        {
            SysConfig cfg = new SysConfig();
            cfg.setConfigName("指令性任务自动打标");
            cfg.setConfigKey(CONFIG_AUTO_MATCH);
            cfg.setConfigValue(String.valueOf(enabled));
            cfg.setConfigType("Y");
            cfg.setRemark("新建 / 导入项目时按 proj_mandate_rule 关键词自动判定项目性质");
            cfg.setCreateBy(currentUser());
            return configService.insertConfig(cfg);
        }
        SysConfig cfg = list.get(0);
        cfg.setConfigValue(String.valueOf(enabled));
        cfg.setUpdateBy(currentUser());
        return configService.updateConfig(cfg);
    }

    @Override
    public String resolveNature(String clientUnit)
    {
        if (clientUnit == null || clientUnit.trim().isEmpty() || !isAutoMatchEnabled())
        {
            return NATURE_NORMAL;
        }
        String cu = clientUnit.trim();
        for (String kw : enabledKeywords())
        {
            if (kw != null && !kw.isEmpty() && cu.contains(kw))
            {
                return NATURE_MANDATE;
            }
        }
        return NATURE_NORMAL;
    }

    /** 启用中的关键词（带 30s 缓存，避免导入时逐行查库） */
    private List<String> enabledKeywords()
    {
        long now = System.currentTimeMillis();
        List<String> cached = keywordCache;
        if (cached != null && (now - keywordCacheAt) < KEYWORD_CACHE_MS)
        {
            return cached;
        }
        List<String> fresh = ruleMapper.selectEnabledKeywords();
        keywordCache = fresh == null ? Collections.<String>emptyList() : fresh;
        keywordCacheAt = now;
        return keywordCache;
    }

    private void invalidateKeywordCache()
    {
        keywordCache = null;
        keywordCacheAt = 0L;
    }

    private String currentUser()
    {
        try
        {
            return SecurityUtils.getUsername();
        }
        catch (Exception e)
        {
            return "system";
        }
    }
}
