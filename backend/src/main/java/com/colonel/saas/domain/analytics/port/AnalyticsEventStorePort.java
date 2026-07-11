package com.colonel.saas.domain.analytics.port;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 事件聚合仓储 Port（DDD-ANALYTICS-001 Wave 2.2 补全）。
 *
 * <p>Port-Adapter 模式入口，封装分析域事件处理状态持久化能力。
 * 默认实现由 {@code com.colonel.saas.domain.analytics.infrastructure} 提供，
 * 本接口为 Application 层与基础设施之间的抽象边界。</p>
 */
public interface AnalyticsEventStorePort {

    /**
     * 标记事件为已处理。
     *
     * @param eventId 事件 ID
     * @param handlerType 处理类型
     */
    void markProcessed(String eventId, String handlerType);

    /**
     * 批量标记多个事件为已处理。
     *
     * @param eventIds 事件 ID 集合
     * @param handlerType 处理类型
     */
    void markProcessedBatch(Set<String> eventIds, String handlerType);

    /**
     * 查询某处理类型的所有已处理事件 ID。
     *
     * @param handlerType 处理类型
     * @return 已处理事件 ID 列表
     */
    List<String> findProcessedEventIds(String handlerType);

    /**
     * 查询某事件的处理快照。
     *
     * @param eventId 事件 ID
     * @return 快照（事件未处理时返回空 Map）
     */
    Map<String, Object> findSnapshot(String eventId);
}
