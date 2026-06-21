package com.colonel.saas.service;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.domain.user.policy.DataScopePolicy;
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
    private final DataScopePolicy dataScopePolicy;

    public PerformanceMetricsQueryService(
            JdbcTemplate jdbcTemplate,
            DataScopePolicy dataScopePolicy) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataScopePolicy = dataScopePolicy;
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

    public PerformanceAggregate aggregateRange(
            LocalDateTime startInclusive,
            LocalDateTime endExclusive,
            String timeField,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        return aggregateRange(startInclusive, endExclusive, timeField,
                null, null, null, userId, deptId, dataScope);
    }

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
        boolean estimateTrack = isEstimateTrack(timeField);
        String timeColumn = resolveTimeColumn(timeField);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("""
                FROM performance_records pr
                JOIN colonelsettlement_order co ON co.order_id = pr.order_id AND co.deleted = 0
                WHERE pr.is_valid = TRUE
                """);
        appendScope(where, args, userId, deptId, dataScope);
        appendBusinessLineFilter(where, args, businessLine, channelId, recruiterId);
        appendRangeFilter(where, args, timeColumn, startInclusive, endExclusive);

        String amountColumn = estimateTrack ? "pr.pay_amount" : "pr.settle_amount";
        String serviceFeeColumn = estimateTrack ? "pr.estimate_service_fee" : "pr.effective_service_fee";
        String techFeeColumn = estimateTrack ? "pr.estimate_tech_service_fee" : "pr.effective_tech_service_fee";
        String expenseColumn = estimateTrack ? "pr.estimate_service_fee_expense" : "pr.effective_service_fee_expense";
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
                    COALESCE(SUM(%s), 0) AS service_fee_expense_cent,
                    COALESCE(SUM(co.settle_second_colonel_commission), 0) AS talent_commission_cent,
                    COALESCE(SUM(%s), 0) AS service_profit_cent,
                    COALESCE(SUM(%s), 0) AS recruiter_commission_cent,
                    COALESCE(SUM(%s), 0) AS channel_commission_cent,
                    COALESCE(SUM(%s), 0) AS gross_profit_cent
                """.formatted(
                amountColumn,
                serviceFeeColumn,
                techFeeColumn,
                expenseColumn,
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
                asLong(row.get("service_fee_expense_cent")),
                asLong(row.get("talent_commission_cent")),
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
        return trendByDay(startInclusive, endExclusive, timeField,
                null, null, null, userId, deptId, dataScope);
    }

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
        boolean estimateTrack = isEstimateTrack(timeField);
        String timeColumn = resolveTimeColumn(timeField);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("""
                FROM performance_records pr
                JOIN colonelsettlement_order co ON co.order_id = pr.order_id AND co.deleted = 0
                WHERE pr.is_valid = TRUE
                """);
        appendScope(where, args, userId, deptId, dataScope);
        appendBusinessLineFilter(where, args, businessLine, channelId, recruiterId);
        appendRangeFilter(where, args, timeColumn, startInclusive, endExclusive);

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
        DataScopePolicy.Decision decision = dataScopePolicy.decide(userId, deptId, dataScope);
        switch (decision) {
            case FILTER_USER -> {
                where.append(" AND co.user_id = ?");
                args.add(userId);
            }
            case FILTER_DEPT -> {
                where.append(" AND co.dept_id = ?");
                args.add(deptId);
            }
            case NO_FILTER -> {
                // no filter
            }
        }
    }

    /**
     * 追加时间范围过滤条件，并在 settle 轨道时强制要求 settle_time 不为空。
     *
     * <p>用于聚合/趋势查询统一处理时间范围 + 已结算限定：
     * 任意一端为 null 时跳过该方向，时间范围在 settle 轨道上自动要求
     * {@code co.settle_time IS NOT NULL}，与原有 SQL 行为一致。</p>
     */
    private void appendRangeFilter(
            StringBuilder where,
            List<Object> args,
            String timeColumn,
            LocalDateTime startInclusive,
            LocalDateTime endExclusive) {
        if (startInclusive != null) {
            where.append(" AND co.").append(timeColumn).append(" >= ?");
            args.add(startInclusive);
        }
        if (endExclusive != null) {
            where.append(" AND co.").append(timeColumn).append(" < ?");
            args.add(endExclusive);
        }
        if ("settle_time".equals(timeColumn) && (startInclusive != null || endExclusive != null)) {
            where.append(" AND co.settle_time IS NOT NULL");
        }
    }

    /**
     * 追加业务线 + 渠道/招商 筛选条件。
     *
     * <p>业务线取值：
     * <ul>
     *   <li>CHANNEL：仅保留有 final_channel_user_id 的记录；可叠加 channelId 精确过滤</li>
     *   <li>RECRUITER：仅保留有 final_recruiter_user_id 的记录；可叠加 recruiterId 精确过滤</li>
     *   <li>其他值（含 null）：不追加业务线筛选</li>
     * </ul>
     *
     * <p>此过滤在数据范围过滤之后追加，确保业务线筛选与 dataScope 兼容叠加。</p>
     */
    private void appendBusinessLineFilter(
            StringBuilder where,
            List<Object> args,
            String businessLine,
            UUID channelId,
            UUID recruiterId) {
        if (businessLine == null) {
            return;
        }
        String normalized = businessLine.trim().toUpperCase(Locale.ROOT);
        if ("CHANNEL".equals(normalized)) {
            where.append(" AND pr.final_channel_user_id IS NOT NULL");
            if (channelId != null) {
                where.append(" AND pr.final_channel_user_id = ?");
                args.add(channelId);
            }
        } else if ("RECRUITER".equals(normalized)) {
            where.append(" AND pr.final_recruiter_user_id IS NOT NULL");
            if (recruiterId != null) {
                where.append(" AND pr.final_recruiter_user_id = ?");
                args.add(recruiterId);
            }
        }
        // 其他取值：忽略，避免非法值或注入
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
