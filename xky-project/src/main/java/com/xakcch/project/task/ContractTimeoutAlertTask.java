package com.xakcch.project.task;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.xakcch.project.mapper.ProjContractMapper;
import com.xakcch.project.mapper.ProjAlertLogMapper;

/**
 * 合同超时预警定时任务
 * <p>
 * 扫描规则：合同登记时间超过7天 且 完成日期为空 → 写入预警日志
 * <p>
 * 建议 cron：0 0 8 * * ?（每天上午8点执行）
 *
 * @author liuyonghui
 */
@Component("contractTimeoutAlertTask")
public class ContractTimeoutAlertTask
{
    private static final Logger log = LoggerFactory.getLogger(ContractTimeoutAlertTask.class);

    /** 默认预警规则ID（系统内置） */
    private static final Long DEFAULT_RULE_ID = -1L;

    @Autowired
    private ProjContractMapper contractMapper;

    @Autowired
    private ProjAlertLogMapper alertLogMapper;

    /**
     * 执行超时合同扫描
     */
    public void scanTimeoutContracts()
    {
        log.info("开始扫描合同超时预警...");
        try
        {
            List<Map<String, Object>> timeoutContracts = contractMapper.selectTimeoutContracts();
            if (timeoutContracts.isEmpty())
            {
                log.info("未发现超时合同");
                return;
            }

            int count = 0;
            for (Map<String, Object> row : timeoutContracts)
            {
                String contractNo = (String) row.get("contract_no");
                String contractName = (String) row.get("contract_name");
                java.util.Date entrustDate = (java.util.Date) row.get("entrust_date");

                String content = String.format(
                    "合同【%s - %s】登记时间 %tF，已超过7天，完成日期为空，请及时处理。",
                    contractNo, contractName, entrustDate);

                // 检查是否已存在未读的同类预警（幂等处理）
                long existing = alertLogMapper.countUnreadByContent(content);
                if (existing > 0) continue;

                alertLogMapper.insertAlertLog(DEFAULT_RULE_ID, content);
                count++;
            }
            log.info("合同超时预警扫描完成，新增预警 {} 条", count);
        }
        catch (Exception e)
        {
            log.error("合同超时预警扫描异常", e);
        }
    }
}
