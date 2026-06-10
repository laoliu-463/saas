package com.colonel.saas.domain.analytics.event;

/**
 * 分析模块消费的事件类型标识（DDD-ANALYTICS-001）。
 */
public final class AnalyticsEventTypes {

    public static final String ORDER_SYNCED = "OrderSyncedEvent";
    public static final String PERFORMANCE_CALCULATED = "PerformanceCalculatedEvent";
    /** 寄样提交：当前映射 {@link com.colonel.saas.domain.sample.event.SampleCreatedEvent} */
    public static final String SAMPLE_SUBMITTED = "SampleSubmittedEvent";
    public static final String SAMPLE_APPROVED = "SampleApprovedEvent";
    public static final String SAMPLE_SHIPPED = "SampleShippedEvent";
    public static final String SAMPLE_COMPLETED = "SampleCompletedEvent";
    public static final String PRODUCT_LISTED = "ProductListedEvent";
    public static final String PRODUCT_HIDDEN = "ProductHiddenEvent";
    public static final String ACTIVITY_SYNCED = "ActivitySyncedEvent";
    public static final String TALENT_CLAIMED = "TalentClaimedEvent";

    private AnalyticsEventTypes() {
    }
}
