package com.colonel.saas.domain.product.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 活动商品同步完成。
 */
public record ActivitySyncCompletedEvent(
        String eventId,
        String activityId,
        String activityName,
        String syncType,
        int createdCount,
        int updatedCount,
        int skippedCount,
        String syncStatus,
        UUID operatorId,
        LocalDateTime occurredAt,
        String traceId) {
}
