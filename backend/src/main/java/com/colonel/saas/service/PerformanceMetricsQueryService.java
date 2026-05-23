package com.colonel.saas.service;

import com.colonel.saas.common.enums.DataScope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 从 performance_records 聚合看板双轨指标（业绩域 V1.1）。
 */
@Service
public class PerformanceMetricsQueryService {

    private final JdbcTemplate jdbcTemplate;

    public PerformanceMetricsQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record PerformanceAggregate(
            long orderCount,
            long orderAmountCent,
            long serviceFeeIncomeCent,
            long techServiceFeeCent,
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
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM performance_records",
                Long.class);
        return count != null && count > 0;
    }

    public PerformanceAggregate aggregateRange(
            LocalDateTime startInclusive,
            LocalDateTime endExclusive,
            String timeField,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        boolean estimateTrack = isEstimateTrack(timeField);
        String timeColumn = resolveTimeColumn(timeField);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("""
                FROM performance_records pr
                JOIN colonelsettlement_order co ON co.order_id = pr.order_id AND co.deleted = 0
                WHERE pr.is_valid = TRUE
                """);
        appendScope(where, args, userId, deptId, dataScope);
        where.append(" AND co.").append(timeColumn).append(" >= ? AND co.").append(timeColumn).append(" < ?");
        args.add(startInclusive);
        args.add(endExclusive);
        if ("settle_time".equals(timeColumn)) {
            where.append(" AND co.settle_time IS NOT NULL");
        }

        String amountColumn = estimateTrack ? "pr.pay_amount" : "pr.settle_amount";
        String serviceFeeColumn = estimateTrack ? "pr.estimate_service_fee" : "pr.effective_service_fee";
        String techFeeColumn = estimateTrack ? "pr.estimate_tech_service_fee" : "pr.effective_tech_service_fee";
        String profitColumn = estimateTrack ? "pr.estimate_service_profit" : "pr.effective_service_profit";
        String recruiterColumn = estimateTrack ? "pr.estimate_recruiter_commission" : "pr.effective_recruiter_commission";
        String channelColumn = estimateTrack ? "pr.estimate_channel_commission" : "pr.effective_channel_commission";
        String grossColumn = estimateTrack ? "pr.estimate_gross_profit" : "pr.effective_gross_profit";

        String sql = """
                SELECT
                    COUNT(*) AS order_count,
                    COALESCE(SUM(%s), 0) AS order_amount_cent,
                    COALESCE(SUM(%s), 0) AS service_fee_income_cent,
                    COALESCE(SUM(%s), 0) AS tech_service_fee_cent,
                    COALESCE(SUM(%s), 0) AS service_profit_cent,
                    COALESCE(SUM(%s), 0) AS recruiter_commission_cent,
                    COALESCE(SUM(%s), 0) AS channel_commission_cent,
                    COALESCE(SUM(%s), 0) AS gross_profit_cent
                """.formatted(
                amountColumn,
                serviceFeeColumn,
                techFeeColumn,
                profitColumn,
                recruiterColumn,
                channelColumn,
                grossColumn) + where;

        Map<String, Object> row = jdbcTemplate.queryForMap(sql, args.toArray());
        return new PerformanceAggregate(
                asLong(row.get("order_count")),
                asLong(row.get("order_amount_cent")),
                asLong(row.get("service_fee_income_cent")),
                asLong(row.get("tech_service_fee_cent")),
                asLong(row.get("service_profit_cent")),
                asLong(row.get("recruiter_commission_cent")),
                asLong(row.get("channel_commission_cent")),
                asLong(row.get("gross_profit_cent")));
    }

    public List<TrendPoint> trendByDay(
            LocalDateTime startInclusive,
            LocalDateTime endExclusive,
            String timeField,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        boolean estimateTrack = isEstimateTrack(timeField);
        String timeColumn = resolveTimeColumn(timeField);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("""
                FROM performance_records pr
                JOIN colonelsettlement_order co ON co.order_id = pr.order_id AND co.deleted = 0
                WHERE pr.is_valid = TRUE
                """);
        appendScope(where, args, userId, deptId, dataScope);
        where.append(" AND co.").append(timeColumn).append(" >= ? AND co.").append(timeColumn).append(" < ?");
        args.add(startInclusive);
        args.add(endExclusive);
        if ("settle_time".equals(timeColumn)) {
            where.append(" AND co.settle_time IS NOT NULL");
        }

        String amountColumn = estimateTrack ? "pr.pay_amount" : "pr.settle_amount";
        String sql = """
                SELECT DATE(co.%s) AS stat_date,
                       COUNT(*) AS order_count,
                       COALESCE(SUM(%s), 0) AS order_amount_cent
                """.formatted(timeColumn, amountColumn) + where + """
                 GROUP BY DATE(co.%s)
                """.formatted(timeColumn);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args.toArray());
        Map<LocalDate, TrendPoint> mapped = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            LocalDate day = LocalDate.parse(String.valueOf(row.get("stat_date")));
            mapped.put(day, new TrendPoint(
                    day.toString(),
                    asLong(row.get("order_count")),
                    asLong(row.get("order_amount_cent"))));
        }

        List<TrendPoint> trend = new ArrayList<>();
        LocalDate cursor = startInclusive.toLocalDate();
        LocalDate endDate = endExclusive.toLocalDate().minusDays(1);
        while (!cursor.isAfter(endDate)) {
            TrendPoint point = mapped.get(cursor);
            trend.add(point == null
                    ? new TrendPoint(cursor.toString(), 0L, 0L)
                    : point);
            cursor = cursor.plusDays(1);
        }
        return trend;
    }

    public DashboardPerformanceSummary aggregateDashboardSummary(
            LocalDateTime startInclusive,
            LocalDateTime endInclusive,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("""
                FROM performance_records pr
                JOIN colonelsettlement_order co ON co.order_id = pr.order_id AND co.deleted = 0
                WHERE pr.is_valid = TRUE
                """);
        appendScope(where, args, userId, deptId, dataScope);
        appendSettleTimeRangeInclusive(where, args, startInclusive, endInclusive);

        String totalsSql = """
                SELECT
                    COUNT(*) AS order_count,
                    COALESCE(SUM(pr.settle_amount), 0) AS order_amount_cent,
                    COALESCE(SUM(pr.effective_service_fee), 0) AS service_fee_cent
                """ + where;
        Map<String, Object> totals = jdbcTemplate.queryForMap(totalsSql, args.toArray());

        List<PerformanceLeaderboardItem> channelPerformance = queryLeaderboard(
                where.toString(),
                new ArrayList<>(args),
                "pr.final_channel_user_id",
                "co.channel_user_name",
                "co.attribution_status = 'ATTRIBUTED' AND pr.final_channel_user_id IS NOT NULL");
        List<PerformanceLeaderboardItem> colonelPerformance = queryLeaderboard(
                where.toString(),
                new ArrayList<>(args),
                "pr.final_recruiter_user_id",
                "co.colonel_user_name",
                "co.attribution_status = 'ATTRIBUTED' AND pr.final_recruiter_user_id IS NOT NULL");

        return new DashboardPerformanceSummary(
                asLong(totals.get("order_count")),
                asLong(totals.get("order_amount_cent")),
                asLong(totals.get("service_fee_cent")),
                channelPerformance,
                colonelPerformance);
    }

    public String resolveAmountTrackLabel(String timeField) {
        return isEstimateTrack(timeField) ? "estimate" : "effective";
    }

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

    private void appendScope(
            StringBuilder where,
            List<Object> args,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        if (dataScope == null) {
            return;
        }
        switch (dataScope) {
            case PERSONAL -> {
                if (userId != null) {
                    where.append(" AND co.user_id = ?");
                    args.add(userId);
                }
            }
            case DEPT -> {
                if (deptId != null) {
                    where.append(" AND co.dept_id = ?");
                    args.add(deptId);
                }
            }
            case ALL -> {
                // no filter
            }
        }
    }

    private void appendSettleTimeRangeInclusive(
            StringBuilder where,
            List<Object> args,
            LocalDateTime startInclusive,
            LocalDateTime endInclusive) {
        if (startInclusive != null) {
            where.append(" AND co.settle_time >= ?");
            args.add(startInclusive);
        }
        if (endInclusive != null) {
            where.append(" AND co.settle_time <= ?");
            args.add(endInclusive);
        }
        if (startInclusive != null || endInclusive != null) {
            where.append(" AND co.settle_time IS NOT NULL");
        }
    }

    private List<PerformanceLeaderboardItem> queryLeaderboard(
            String baseWhere,
            List<Object> args,
            String userIdColumn,
            String userNameColumn,
            String extraFilter) {
        String sql = """
                SELECT %s::text AS user_id,
                       MAX(%s) AS user_name,
                       COUNT(*) AS order_count,
                       COALESCE(SUM(pr.settle_amount), 0) AS order_amount_cent,
                       COALESCE(SUM(pr.effective_service_fee), 0) AS service_fee_cent
                """.formatted(userIdColumn, userNameColumn) + baseWhere
                + " AND " + extraFilter
                + " GROUP BY " + userIdColumn
                + " ORDER BY order_count DESC, order_amount_cent DESC"
                + " LIMIT 10";
        return jdbcTemplate.queryForList(sql, args.toArray()).stream()
                .map(row -> new PerformanceLeaderboardItem(
                        asString(row.get("user_id")),
                        asString(row.get("user_name")),
                        asLong(row.get("order_count")),
                        asLong(row.get("order_amount_cent")),
                        asLong(row.get("service_fee_cent"))))
                .toList();
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }
}
