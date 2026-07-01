package com.colonel.saas.service;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.domain.performance.application.PerformanceAggregateApplicationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 从订单事实表聚合看板双轨指标，并左关联 performance_records 补充业绩字段。
 */
@Service
public class PerformanceMetricsQueryService {

    private final JdbcTemplate jdbcTemplate;
    private final PerformanceAggregateApplicationService aggregateApplicationService;

    public PerformanceMetricsQueryService(
            JdbcTemplate jdbcTemplate,
            PerformanceAggregateApplicationService aggregateApplicationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.aggregateApplicationService = aggregateApplicationService;
    }

    public record PerformanceAggregate(
            long orderCount,
            long orderAmountCent,
            long serviceFeeIncomeCent,
            long techServiceFeeCent,
            long serviceFeeExpenseCent,
            long talentCommissionCent,
            long serviceProfitCent,
            long recruiterCommissionCent,
            long channelCommissionCent,
            long grossProfitCent) {
    }

    public record TrendPoint(String date, long orderCount, long orderAmountCent) {
    }

    public record PerformanceLeaderboardItem(
            String userId,
            String userName,
            long orderCount,
            long orderAmountCent,
            long serviceFeeCent) {
    }

    public record DashboardPerformanceSummary(
            long orderCount,
            long orderAmountCent,
            long serviceFeeCent,
            List<PerformanceLeaderboardItem> channelPerformance,
            List<PerformanceLeaderboardItem> colonelPerformance) {
    }

    public boolean hasPerformanceRecords() {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM performance_records LIMIT 1)",
                Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    /**
     * 委派到 {@link PerformanceAggregateApplicationService#aggregateRange}（DDD-PERFORMANCE Slice 2）。
     *
     * <p>完整 SQL 装配逻辑已迁出至 application 层；本 service 仅保留 thin shell 委派，
     * 保持 caller 端零修改（DataController / DashboardService /
     * DashboardShadowCompareService / DataApplicationService 仍按既有签名调用）。</p>
     */
    public PerformanceAggregate aggregateRange(
            LocalDateTime startInclusive,
            LocalDateTime endExclusive,
            String timeField,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        return aggregateApplicationService.aggregateRange(
                startInclusive, endExclusive, timeField, userId, deptId, dataScope);
    }

    /**
     * 委派到 {@link PerformanceAggregateApplicationService#aggregateRange}（DDD-PERFORMANCE Slice 2）。
     */
    public PerformanceAggregate aggregateRange(
            LocalDateTime startInclusive,
            LocalDateTime endExclusive,
            String timeField,
            String businessLine,
            UUID channelId,
            UUID recruiterId,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        return aggregateApplicationService.aggregateRange(
                startInclusive, endExclusive, timeField,
                businessLine, channelId, recruiterId, userId, deptId, dataScope);
    }

    public List<TrendPoint> trendByDay(
            LocalDateTime startInclusive,
            LocalDateTime endExclusive,
            String timeField,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        return aggregateApplicationService.trendByDay(
                startInclusive, endExclusive, timeField, userId, deptId, dataScope);
    }

    /**
     * 委派到 {@link PerformanceAggregateApplicationService#trendByDay}（DDD-PERFORMANCE Slice 3）。
     */
    public List<TrendPoint> trendByDay(
            LocalDateTime startInclusive,
            LocalDateTime endExclusive,
            String timeField,
            String businessLine,
            UUID channelId,
            UUID recruiterId,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        return aggregateApplicationService.trendByDay(
                startInclusive, endExclusive, timeField,
                businessLine, channelId, recruiterId, userId, deptId, dataScope);
    }

    /**
     * 委派到 {@link PerformanceAggregateApplicationService#aggregateDashboardSummary}（DDD-PERFORMANCE Slice 3）。
     */
    public DashboardPerformanceSummary aggregateDashboardSummary(
            LocalDateTime startInclusive,
            LocalDateTime endInclusive,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        return aggregateApplicationService.aggregateDashboardSummary(
                startInclusive, endInclusive, userId, deptId, dataScope);
    }

    public String resolveAmountTrackLabel(String timeField) {
        return isEstimateTrack(timeField) ? "estimate" : "effective";
    }

    /**
     * 保留 isEstimateTrack / resolveTimeColumn：公开 API
     * {@link #resolveAmountTrackLabel} 仍依赖这两个 helper。
     * 其他 SQL 装配/数据范围策略 helper 已全部下沉至
     * {@link PerformanceAggregateApplicationService}（DDD-PERFORMANCE Slice 3）。
     */
    private boolean isEstimateTrack(String timeField) {
        return "create_time".equals(resolveTimeColumn(timeField));
    }

    private String resolveTimeColumn(String timeField) {
        if (!StringUtils.hasText(timeField)) {
            return "create_time";
        }
        String normalized = timeField.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "createtime", "create_time", "create" -> "create_time";
            case "settletime", "settle_time", "settle" -> "settle_time";
            default -> "create_time";
        };
    }
}
