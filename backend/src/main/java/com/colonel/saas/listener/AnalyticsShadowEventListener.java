package com.colonel.saas.listener;

import com.colonel.saas.domain.analytics.application.AggregationUpdateResult;
import com.colonel.saas.domain.analytics.application.AnalyticsEventConsumer;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 分析 Shadow 事件监听器（DDD-ANALYTICS-001）。
 * <p>仅在 {@code ddd.refactor.analytics-shadow.enabled} 开启时消费事件；不替换现有 Dashboard 监听器。</p>
 */
@Slf4j
@Component
public class AnalyticsShadowEventListener {

    private final AnalyticsEventConsumer analyticsEventConsumer;

    public AnalyticsShadowEventListener(AnalyticsEventConsumer analyticsEventConsumer) {
        this.analyticsEventConsumer = analyticsEventConsumer;
    }

    @Async
    @EventListener
    public void onOrderSynced(OrderSyncedEvent event) {
        consume(event, AnalyticsEventConsumer.resolveEventId(event), AnalyticsEventConsumer.eventTypeFor(event));
    }

    @Async
    @EventListener
    public void onPerformanceCalculated(PerformanceCalculatedEvent event) {
        consume(event, AnalyticsEventConsumer.resolveEventId(event), AnalyticsEventConsumer.eventTypeFor(event));
    }

    @Async
    @EventListener
    public void onSampleSubmitted(SampleCreatedEvent event) {
        consume(event, AnalyticsEventConsumer.resolveEventId(event), AnalyticsEventConsumer.eventTypeFor(event));
    }

    @Async
    @EventListener
    public void onSampleApproved(SampleApprovedEvent event) {
        consume(event, AnalyticsEventConsumer.resolveEventId(event), AnalyticsEventConsumer.eventTypeFor(event));
    }

    @Async
    @EventListener
    public void onSampleShipped(SampleShippedEvent event) {
        consume(event, AnalyticsEventConsumer.resolveEventId(event), AnalyticsEventConsumer.eventTypeFor(event));
    }

    @Async
    @EventListener
    public void onSampleCompleted(SampleCompletedEvent event) {
        consume(event, AnalyticsEventConsumer.resolveEventId(event), AnalyticsEventConsumer.eventTypeFor(event));
    }

    @Async
    @EventListener
    public void onProductListed(ProductListedEvent event) {
        consume(event, AnalyticsEventConsumer.resolveEventId(event), AnalyticsEventConsumer.eventTypeFor(event));
    }

    @Async
    @EventListener
    public void onProductHidden(ProductHiddenEvent event) {
        consume(event, AnalyticsEventConsumer.resolveEventId(event), AnalyticsEventConsumer.eventTypeFor(event));
    }

    @Async
    @EventListener
    public void onActivitySynced(ActivitySyncCompletedEvent event) {
        consume(event, AnalyticsEventConsumer.resolveEventId(event), AnalyticsEventConsumer.eventTypeFor(event));
    }

    @Async
    @EventListener
    public void onTalentClaimed(TalentClaimedEvent event) {
        consume(event, AnalyticsEventConsumer.resolveEventId(event), AnalyticsEventConsumer.eventTypeFor(event));
    }

    private void consume(Object payload, java.util.UUID eventId, String eventType) {
        AggregationUpdateResult result = analyticsEventConsumer.consumeIfShadowEnabled(eventId, eventType, payload);
        if (result.applied()) {
            log.debug("Analytics shadow consumed {}, handler={}", eventType, result.handlerType());
        }
    }
}
