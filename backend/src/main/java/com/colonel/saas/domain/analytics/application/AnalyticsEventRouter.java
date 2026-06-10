package com.colonel.saas.domain.analytics.application;

import com.colonel.saas.domain.analytics.event.AnalyticsEventTypes;
import com.colonel.saas.domain.analytics.event.TalentClaimedEvent;
import com.colonel.saas.domain.product.event.ActivitySyncCompletedEvent;
import com.colonel.saas.domain.product.event.ProductHiddenEvent;
import com.colonel.saas.domain.product.event.ProductListedEvent;
import com.colonel.saas.domain.sample.event.SampleApprovedEvent;
import com.colonel.saas.domain.sample.event.SampleCompletedEvent;
import com.colonel.saas.domain.sample.event.SampleCreatedEvent;
import com.colonel.saas.domain.sample.event.SampleShippedEvent;
import com.colonel.saas.event.OrderSyncedEvent;
import com.colonel.saas.event.PerformanceCalculatedEvent;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 分析事件路由器（DDD-ANALYTICS-001）。
 */
@Component
public class AnalyticsEventRouter {

    private final AnalyticsAggregationService aggregationService;

    public AnalyticsEventRouter(AnalyticsAggregationService aggregationService) {
        this.aggregationService = aggregationService;
    }

    public AggregationUpdateResult route(UUID eventId, String eventType, Object payload) {
        if (eventId == null || eventType == null || payload == null) {
            return AggregationUpdateResult.unsupported(eventId, eventType);
        }
        return switch (eventType) {
            case AnalyticsEventTypes.ORDER_SYNCED -> {
                if (payload instanceof OrderSyncedEvent event) {
                    yield aggregationService.applyOrderEstimateSummary(eventId, eventType, event);
                }
                yield AggregationUpdateResult.unsupported(eventId, eventType);
            }
            case AnalyticsEventTypes.PERFORMANCE_CALCULATED -> {
                if (payload instanceof PerformanceCalculatedEvent event) {
                    yield aggregationService.applyPerformanceSummary(eventId, eventType, event);
                }
                yield AggregationUpdateResult.unsupported(eventId, eventType);
            }
            case AnalyticsEventTypes.SAMPLE_SUBMITTED -> {
                if (payload instanceof SampleCreatedEvent event) {
                    yield aggregationService.applySampleSummary(eventId, eventType, event);
                }
                yield AggregationUpdateResult.unsupported(eventId, eventType);
            }
            case AnalyticsEventTypes.SAMPLE_APPROVED -> {
                if (payload instanceof SampleApprovedEvent event) {
                    yield aggregationService.applySampleSummary(eventId, eventType, event);
                }
                yield AggregationUpdateResult.unsupported(eventId, eventType);
            }
            case AnalyticsEventTypes.SAMPLE_SHIPPED -> {
                if (payload instanceof SampleShippedEvent event) {
                    yield aggregationService.applySampleSummary(eventId, eventType, event);
                }
                yield AggregationUpdateResult.unsupported(eventId, eventType);
            }
            case AnalyticsEventTypes.SAMPLE_COMPLETED -> {
                if (payload instanceof SampleCompletedEvent event) {
                    yield aggregationService.applySampleSummary(eventId, eventType, event);
                }
                yield AggregationUpdateResult.unsupported(eventId, eventType);
            }
            case AnalyticsEventTypes.PRODUCT_LISTED -> {
                if (payload instanceof ProductListedEvent event) {
                    yield aggregationService.applyProductSnapshot(eventId, eventType, event);
                }
                yield AggregationUpdateResult.unsupported(eventId, eventType);
            }
            case AnalyticsEventTypes.PRODUCT_HIDDEN -> {
                if (payload instanceof ProductHiddenEvent event) {
                    yield aggregationService.applyProductSnapshot(eventId, eventType, event);
                }
                yield AggregationUpdateResult.unsupported(eventId, eventType);
            }
            case AnalyticsEventTypes.ACTIVITY_SYNCED -> {
                if (payload instanceof ActivitySyncCompletedEvent event) {
                    yield aggregationService.applyActivitySnapshot(eventId, eventType, event);
                }
                yield AggregationUpdateResult.unsupported(eventId, eventType);
            }
            case AnalyticsEventTypes.TALENT_CLAIMED -> {
                if (payload instanceof TalentClaimedEvent event) {
                    yield aggregationService.applyTalentSnapshot(eventId, eventType, event);
                }
                yield AggregationUpdateResult.unsupported(eventId, eventType);
            }
            default -> AggregationUpdateResult.unsupported(eventId, eventType);
        };
    }
}
