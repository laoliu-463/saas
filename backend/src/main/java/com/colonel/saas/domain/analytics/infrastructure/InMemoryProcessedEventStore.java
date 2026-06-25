package com.colonel.saas.domain.analytics.infrastructure;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版已处理事件存储（DDD-ANALYTICS-001 兼容层，仅用于幂等测试与 Shadow 验证）。
 */
@Component
public class InMemoryProcessedEventStore implements ProcessedEventStore {

    private final ConcurrentHashMap<UUID, String> processed = new ConcurrentHashMap<>();

    @Override
    public boolean isProcessed(UUID eventId) {
        return eventId != null && processed.containsKey(eventId);
    }

    @Override
    public void markProcessed(UUID eventId, String eventType) {
        if (eventId == null) {
            return;
        }
        processed.putIfAbsent(eventId, eventType == null ? "" : eventType);
    }

}
