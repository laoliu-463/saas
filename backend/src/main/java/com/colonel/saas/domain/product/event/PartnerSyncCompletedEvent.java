package com.colonel.saas.domain.product.event;

import java.time.LocalDateTime;

/**
 * 团长合作方主数据同步完成。
 */
public record PartnerSyncCompletedEvent(
        String eventId,
        String partnerId,
        String partnerName,
        String partnerType,
        String source,
        String syncStatus,
        boolean created,
        boolean updated,
        LocalDateTime occurredAt,
        String traceId) {
}
