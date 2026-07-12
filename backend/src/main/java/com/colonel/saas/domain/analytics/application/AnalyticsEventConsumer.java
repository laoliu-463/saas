package com.colonel.saas.domain.analytics.application;

import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.analytics.event.AnalyticsEventTypes;
import com.colonel.saas.domain.analytics.event.TalentClaimedEvent;
import com.colonel.saas.domain.analytics.infrastructure.ProcessedEventStore;
import com.colonel.saas.domain.order.event.OrderRefundFactSyncedEvent;
import com.colonel.saas.domain.product.event.ActivitySyncCompletedEvent;
import com.colonel.saas.domain.product.event.ProductHiddenEvent;
import com.colonel.saas.domain.product.event.ProductListedEvent;
import com.colonel.saas.domain.sample.event.SampleApprovedEvent;
import com.colonel.saas.domain.sample.event.SampleCompletedEvent;
import com.colonel.saas.domain.sample.event.SampleCreatedEvent;
import com.colonel.saas.domain.sample.event.SampleShippedEvent;
import com.colonel.saas.event.OrderSyncedEvent;
import com.colonel.saas.event.PerformanceCalculatedEvent;
import com.colonel.saas.event.PerformanceSummaryRefreshedEvent;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 分析事件消费入口（DDD-ANALYTICS-001 兼容层）。
 * <p>幂等消费领域事件并路由到 {@link AnalyticsAggregationService}；不替换 Dashboard 查询数据源。</p>
 */
@Service
public class AnalyticsEventConsumer {

    private final ProcessedEventStore processedEventStore;
    private final AnalyticsEventRouter eventRouter;
    private final DddRefactorProperties dddRefactorProperties;

    public AnalyticsEventConsumer(
            ProcessedEventStore processedEventStore,
            AnalyticsEventRouter eventRouter,
            DddRefactorProperties dddRefactorProperties) {
        this.processedEventStore = processedEventStore;
        this.eventRouter = eventRouter;
        this.dddRefactorProperties = dddRefactorProperties;
    }

    /**
     * Shadow 模式是否启用（需同时打开根开关与 analytics-shadow 子开关）。
     */
    public boolean isShadowEnabled() {
        return dddRefactorProperties.isEnabled()
                && dddRefactorProperties.getAnalyticsShadow().isEnabled();
    }

    /**
     * 幂等消费事件；重复 eventId 仅处理一次。
     */
    public AggregationUpdateResult consume(UUID eventId, String eventType, Object payload) {
        if (processedEventStore.isProcessed(eventId)) {
            return AggregationUpdateResult.duplicate(eventId, eventType);
        }
        AggregationUpdateResult result = eventRouter.route(eventId, eventType, payload);
        if (result.applied()) {
            processedEventStore.markProcessed(eventId, eventType);
        }
        return result;
    }

    /**
     * Shadow 监听器入口：开关关闭时跳过。
     */
    public AggregationUpdateResult consumeIfShadowEnabled(UUID eventId, String eventType, Object payload) {
        if (!isShadowEnabled()) {
            return AggregationUpdateResult.shadowDisabled(eventId);
        }
        return consume(eventId, eventType, payload);
    }

    public static UUID resolveEventId(OrderSyncedEvent event) {
        if (event.orderRowId() != null) {
            return event.orderRowId();
        }
        return UUID.nameUUIDFromBytes(
                ("OrderSynced:" + event.orderId()).getBytes(StandardCharsets.UTF_8));
    }

    public static UUID resolveEventId(OrderRefundFactSyncedEvent event) {
        if (event.orderRowId() != null) {
            return UUID.nameUUIDFromBytes(
                    ("OrderRefundFactSynced:" + event.orderId() + ":" + event.orderRowId())
                            .getBytes(StandardCharsets.UTF_8));
        }
        return UUID.nameUUIDFromBytes(
                ("OrderRefundFactSynced:" + event.orderId()).getBytes(StandardCharsets.UTF_8));
    }

    public static UUID resolveEventId(PerformanceCalculatedEvent event) {
        return UUID.nameUUIDFromBytes(
                ("PerformanceCalculated:" + event.orderId()).getBytes(StandardCharsets.UTF_8));
    }

    public static UUID resolveEventId(PerformanceSummaryRefreshedEvent event) {
        return event.eventId();
    }

    public static UUID resolveEventId(SampleCreatedEvent event) {
        return event.sampleRequestId();
    }

    public static UUID resolveEventId(SampleApprovedEvent event) {
        return event.sampleRequestId();
    }

    public static UUID resolveEventId(SampleShippedEvent event) {
        return event.sampleRequestId();
    }

    public static UUID resolveEventId(SampleCompletedEvent event) {
        return event.sampleRequestId();
    }

    public static UUID resolveEventId(ProductListedEvent event) {
        return event.eventId();
    }

    public static UUID resolveEventId(ProductHiddenEvent event) {
        return event.eventId();
    }

    public static UUID resolveEventId(ActivitySyncCompletedEvent event) {
        if (event.eventId() != null && !event.eventId().isBlank()) {
            try {
                return UUID.fromString(event.eventId());
            } catch (IllegalArgumentException ignore) {
                return UUID.nameUUIDFromBytes(event.eventId().getBytes(StandardCharsets.UTF_8));
            }
        }
        return UUID.nameUUIDFromBytes(
                ("ActivitySynced:" + event.activityId()).getBytes(StandardCharsets.UTF_8));
    }

    public static UUID resolveEventId(TalentClaimedEvent event) {
        return event.eventId() != null
                ? event.eventId()
                : event.claimId();
    }

    public static String eventTypeFor(OrderSyncedEvent ignored) {
        return AnalyticsEventTypes.ORDER_SYNCED;
    }

    public static String eventTypeFor(OrderRefundFactSyncedEvent ignored) {
        return AnalyticsEventTypes.ORDER_REFUND_FACT_SYNCED;
    }

    public static String eventTypeFor(PerformanceCalculatedEvent ignored) {
        return AnalyticsEventTypes.PERFORMANCE_CALCULATED;
    }

    public static String eventTypeFor(PerformanceSummaryRefreshedEvent ignored) {
        return AnalyticsEventTypes.PERFORMANCE_SUMMARY_REFRESHED;
    }

    public static String eventTypeFor(SampleCreatedEvent ignored) {
        return AnalyticsEventTypes.SAMPLE_SUBMITTED;
    }

    public static String eventTypeFor(SampleApprovedEvent ignored) {
        return AnalyticsEventTypes.SAMPLE_APPROVED;
    }

    public static String eventTypeFor(SampleShippedEvent ignored) {
        return AnalyticsEventTypes.SAMPLE_SHIPPED;
    }

    public static String eventTypeFor(SampleCompletedEvent ignored) {
        return AnalyticsEventTypes.SAMPLE_COMPLETED;
    }

    public static String eventTypeFor(ProductListedEvent ignored) {
        return AnalyticsEventTypes.PRODUCT_LISTED;
    }

    public static String eventTypeFor(ProductHiddenEvent ignored) {
        return AnalyticsEventTypes.PRODUCT_HIDDEN;
    }

    public static String eventTypeFor(ActivitySyncCompletedEvent ignored) {
        return AnalyticsEventTypes.ACTIVITY_SYNCED;
    }

    public static String eventTypeFor(TalentClaimedEvent ignored) {
        return AnalyticsEventTypes.TALENT_CLAIMED;
    }
}
