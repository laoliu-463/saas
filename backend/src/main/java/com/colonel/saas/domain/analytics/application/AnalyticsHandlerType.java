package com.colonel.saas.domain.analytics.application;

/**
 * 分析聚合处理器类型（DDD-ANALYTICS-001）。
 */
public enum AnalyticsHandlerType {
    /** 订单同步后的预估汇总（兼容层，不写 Dashboard 表） */
    ORDER_ESTIMATE_SUMMARY,
    /** 业绩计算完成后的业绩汇总 */
    PERFORMANCE_SUMMARY,
    /** 寄样生命周期汇总 */
    SAMPLE_SUMMARY,
    /** 商品库快照 */
    PRODUCT_SNAPSHOT,
    /** 活动同步快照 */
    ACTIVITY_SNAPSHOT,
    /** 达人认领快照（可选） */
    TALENT_SNAPSHOT
}
