package com.colonel.saas.domain.analytics.infrastructure;

import java.util.UUID;

/**
 * 已处理事件存储（幂等去重，DDD-ANALYTICS-001）。
 * <p>当前默认内存实现；后续 Outbox / processed_events 表阶段可替换为持久化实现。</p>
 */
public interface ProcessedEventStore {

    boolean isProcessed(UUID eventId);

    void markProcessed(UUID eventId, String eventType);
}
