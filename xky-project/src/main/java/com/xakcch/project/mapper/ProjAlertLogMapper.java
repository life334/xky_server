package com.xakcch.project.mapper;

/**
 * 预警日志 数据层
 *
 * @author liuyonghui
 */
public interface ProjAlertLogMapper
{
    /**
     * 插入预警日志
     *
     * @param ruleId  规则ID
     * @param content 预警内容
     * @return 结果
     */
    int insertAlertLog(Long ruleId, String content);

    /**
     * 统计未读的同类预警数量（按内容去重）
     *
     * @param content 预警内容
     * @return 数量
     */
    long countUnreadByContent(String content);

    /**
     * 查询未读预警总数
     *
     * @return 未读数量
     */
    long countUnread();

    /**
     * 查询未读预警列表
     *
     * @param limit 限制条数
     * @return 预警列表
     */
    java.util.List<java.util.Map<String, Object>> selectUnreadList(int limit);
}
