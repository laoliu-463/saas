package com.colonel.saas.domain.analytics.application;

import java.util.UUID;

/**
 * 分析聚合更新结果（DDD-ANALYTICS-001）。
 */
public record AggregationUpdateResult(
        UUID eventId,
        String eventType,
        AnalyticsHandlerType handlerType,
        boolean applied,
        boolean duplicateSkipped,
        String message) {

    public static AggregationUpdateResult applied(
            UUID eventId, String eventType, AnalyticsHandlerType handlerType) {
        return new AggregationUpdateResult(eventId, eventType, handlerType, true, false, null);
    }

    public static AggregationUpdateResult duplicate(UUID eventId, String eventType) {
        return new AggregationUpdateResult(eventId, eventType, null, false, true, "DUPLICATE_EVENT");
    }

    public static AggregationUpdateResult unsupported(UUID eventId, String eventType) {
        return new AggregationUpdateResult(eventId, eventType, null, false, false, "UNSUPPORTED_EVENT");
    }

    public static AggregationUpdateResult shadowDisabled(UUID eventId) {
        return new AggregationUpdateResult(eventId, null, null, false, false, "SHADOW_DISABLED");
    }
}
