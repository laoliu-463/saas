package com.colonel.saas.domain.analytics.port;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 分析指标聚合 Port（DDD-ANALYTICS-001 Wave 2.2 补全）。
 *
 * <p>Port-Adapter 模式入口，封装分析指标聚合的查询与更新能力。
 * 实现由 Infrastructure 层提供（如 MyBatis Mapper、Redis 缓存、ClickHouse 等）。</p>
 */
public interface AnalyticsAggregationPort {

    /**
     * 按时间窗口聚合指标。
     *
     * @param metricName 指标名（如 GMV / 订单数 / 业绩汇总）
     * @param granularity 粒度（DAY / HOUR / WEEK）
     * @param startTime 起始时间
     * @param endTime 结束时间
     * @return 时间序列聚合数据（key=时间字符串，value=指标值）
     */
    Map<String, Double> aggregateByTimeWindow(
            String metricName,
            Granularity granularity,
            LocalDateTime startTime,
            LocalDateTime endTime);

    /**
     * 粒度枚举。
     */
    enum Granularity {
        HOUR,
        DAY,
        WEEK,
        MONTH
    }
}
