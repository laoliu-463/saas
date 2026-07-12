package com.colonel.saas.domain.analytics.application;

import com.colonel.saas.domain.analytics.event.TalentClaimedEvent;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 分析聚合服务（DDD-ANALYTICS-001 兼容层）。
 * <p>当前仅记录 handler 调用，不写汇总表、不重算业绩、不修改业务域数据。</p>
 */
@Slf4j
@Service
public class AnalyticsAggregationService {

    private final AtomicInteger orderEstimateInvocations = new AtomicInteger();
    private final AtomicInteger performanceSummaryInvocations = new AtomicInteger();
    private final AtomicInteger sampleSummaryInvocations = new AtomicInteger();
    private final AtomicInteger productSnapshotInvocations = new AtomicInteger();
    private final AtomicInteger activitySnapshotInvocations = new AtomicInteger();
    private final AtomicInteger talentSnapshotInvocations = new AtomicInteger();

    public AggregationUpdateResult applyOrderEstimateSummary(
            UUID eventId, String eventType, OrderSyncedEvent event) {
        orderEstimateInvocations.incrementAndGet();
        log.debug("Analytics shadow: order estimate summary, eventId={}, orderId={}", eventId, event.orderId());
        return AggregationUpdateResult.applied(eventId, eventType, AnalyticsHandlerType.ORDER_ESTIMATE_SUMMARY);
    }

    public AggregationUpdateResult applyPerformanceSummary(
            UUID eventId, String eventType, PerformanceCalculatedEvent event) {
        performanceSummaryInvocations.incrementAndGet();
        log.debug("Analytics shadow: performance summary, eventId={}, orderId={}", eventId, event.orderId());
        return AggregationUpdateResult.applied(eventId, eventType, AnalyticsHandlerType.PERFORMANCE_SUMMARY);
    }

    public AggregationUpdateResult applyOrderRefundFact(
            UUID eventId, String eventType, OrderRefundFactSyncedEvent event) {
        performanceSummaryInvocations.incrementAndGet();
        log.debug("Analytics shadow: order refund fact, eventId={}, orderId={}", eventId, event.orderId());
        return AggregationUpdateResult.applied(eventId, eventType, AnalyticsHandlerType.PERFORMANCE_SUMMARY);
    }

    public AggregationUpdateResult applyPerformanceSummaryRefresh(
            UUID eventId, String eventType, PerformanceSummaryRefreshedEvent event) {
        performanceSummaryInvocations.incrementAndGet();
        log.debug("Analytics shadow: performance summary refresh, eventId={}, summaryId={}",
                eventId, event.summaryId());
        return AggregationUpdateResult.applied(eventId, eventType, AnalyticsHandlerType.PERFORMANCE_SUMMARY);
    }

    public AggregationUpdateResult applySampleSummary(UUID eventId, String eventType, Object event) {
        sampleSummaryInvocations.incrementAndGet();
        log.debug("Analytics shadow: sample summary, eventId={}, eventType={}", eventId, eventType);
        return AggregationUpdateResult.applied(eventId, eventType, AnalyticsHandlerType.SAMPLE_SUMMARY);
    }

    public AggregationUpdateResult applyProductSnapshot(UUID eventId, String eventType, Object event) {
        productSnapshotInvocations.incrementAndGet();
        log.debug("Analytics shadow: product snapshot, eventId={}, eventType={}", eventId, eventType);
        return AggregationUpdateResult.applied(eventId, eventType, AnalyticsHandlerType.PRODUCT_SNAPSHOT);
    }

    public AggregationUpdateResult applyActivitySnapshot(
            UUID eventId, String eventType, ActivitySyncCompletedEvent event) {
        activitySnapshotInvocations.incrementAndGet();
        log.debug("Analytics shadow: activity snapshot, eventId={}, activityId={}", eventId, event.activityId());
        return AggregationUpdateResult.applied(eventId, eventType, AnalyticsHandlerType.ACTIVITY_SNAPSHOT);
    }

    public AggregationUpdateResult applyTalentSnapshot(UUID eventId, String eventType, TalentClaimedEvent event) {
        talentSnapshotInvocations.incrementAndGet();
        log.debug("Analytics shadow: talent snapshot, eventId={}, talentUid={}", eventId, event.talentUid());
        return AggregationUpdateResult.applied(eventId, eventType, AnalyticsHandlerType.TALENT_SNAPSHOT);
    }

    int orderEstimateInvocationCount() {
        return orderEstimateInvocations.get();
    }

    int performanceSummaryInvocationCount() {
        return performanceSummaryInvocations.get();
    }

    int sampleSummaryInvocationCount() {
        return sampleSummaryInvocations.get();
    }

    int productSnapshotInvocationCount() {
        return productSnapshotInvocations.get();
    }

    void resetInvocationCounters() {
        orderEstimateInvocations.set(0);
        performanceSummaryInvocations.set(0);
        sampleSummaryInvocations.set(0);
        productSnapshotInvocations.set(0);
        activitySnapshotInvocations.set(0);
        talentSnapshotInvocations.set(0);
    }
}
