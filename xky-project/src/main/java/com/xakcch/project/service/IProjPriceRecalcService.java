package com.xakcch.project.service;

import java.util.List;

/**
 * 工作量单价重算服务
 *
 * <p>重算原则（外部工作量单价优先级）：
 * <ol>
 *   <li>用户手改价（price_source='manual'）——重算时不覆盖（仅"合同关联变化"的全量重算会先恢复再按原则重算）</li>
 *   <li>合同价——项目已关联合同，且合同中该计费方式(billingId)已配置单价</li>
 *   <li>兜底价：
 *       <ul>
 *         <li>导入项目（data_source='import'）→ 导入推导价（extra_data.origin_price），无则字典价</li>
 *         <li>手动项目（data_source='manual'）→ 字典价（proj_category_billing.unit_price）</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <p>触发时机：
 * <ul>
 *   <li>项目关联合同发生变化（0→A / A→B / A→0）→ 全量重算该项目所有外部工作量行</li>
 *   <li>合同单价被改动 → 仅重算被改动的计费方式(billingId)对应的外部工作量行</li>
 * </ul>
 *
 * @author liuyonghui
 */
public interface IProjPriceRecalcService
{
    /**
     * 重算指定项目的外部工作量单价
     *
     * @param projectId 项目ID
     * @param billingIds 需要重算的计费方式ID集合；为 null 或空表示全量重算（该项目所有外部行）
     * @param forceReset 是否强制重置（忽略手改价）：
     *                   true  = 合同关联发生变化（0→A / A→B / A→0）时，先恢复所有单价再按原则重算（含手改价）
     *                   false = 合同单价改动时，仅重算对应计费方式的行，手改价(manual)保持不动
     * @return 实际更新的行数
     */
    int recalcExternalPrices(Long projectId, List<Long> billingIds, boolean forceReset);
}
