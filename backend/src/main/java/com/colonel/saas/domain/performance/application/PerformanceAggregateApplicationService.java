package com.colonel.saas.domain.performance.application;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.user.policy.DataScopeResolver;
import com.colonel.saas.service.PerformanceMetricsQueryService;
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
 * 业绩聚合应用服务（DDD-PERFORMANCE Slice 2 + Slice 3）。
 *
 * <p>从 {@code service.PerformanceMetricsQueryService} 迁移过来的核心聚合逻辑：
 * <ul>
 *   <li>{@link #aggregateRange} - 时间范围内业绩指标聚合（estimate / effective 双轨）</li>
 *   <li>{@link #trendByDay} - 按日趋势聚合（生成时间序列）</li>
 *   <li>{@link #aggregateDashboardSummary} - 看板汇总（聚合指标 + 渠道/团长 leaderboard）</li>
 * </ul>
 *
 * <p>本类是 performance 域读模型的独立入口。承接原 Service 的所有 private helper
 * （SQL 装配 / 数据范围策略 / 时间列解析 / 类型转换）。</p>
 *
 * <p>Slice 2：{@code aggregateRange} 下沉，Service 委派壳化。<br>
 * Slice 3：{@code trendByDay} + {@code aggregateDashboardSummary} 下沉，
 * 删除 Service 端 7 个 helper 副本（保留 {@code isEstimateTrack/resolveTimeColumn}
 * 给公开 API {@code resolveAmountTrackLabel}）。</p>
 *
 * <p>依赖：
 * <ul>
 *   <li>{@link JdbcTemplate} —— 业绩事实及调整流水聚合 SQL</li>
 *   <li>{@link DataScopeResolver} —— 用户域数据范围解析器（灰度切换）</li>
 *   <li>{@link DddRefactorProperties} —— DataScopePolicy 开关</li>
 * </ul>
 */
@Service
public class PerformanceAggregateApplicationService {

    private final JdbcTemplate jdbcTemplate;
    private final DataScopeResolver dataScopeResolver;
    private final DddRefactorProperties dddRefactorProperties;

    public PerformanceAggregateApplicationService(
            JdbcTemplate jdbcTemplate,
            DataScopeResolver dataScopeResolver,
            DddRefactorProperties dddRefactorProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataScopeResolver = dataScopeResolver;
        this.dddRefactorProperties = dddRefactorProperties;
    }

    /**
     * 复用 {@link PerformanceMetricsQueryService.PerformanceAggregate} 类型，
     * 保持 caller 端零修改（DataController / DashboardService /
     * DashboardShadowCompareService / DataApplicationService 仍按既有 record 类型引用）。
     * 后续切片可将 record 迁至独立 dto 包。
     */
    public PerformanceMetricsQueryService.PerformanceAggregate aggregateRange(
            LocalDateTime startInclusive,
            LocalDateTime endExclusive,
            String timeField,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        return aggregateRange(startInclusive, endExclusive, timeField,
                null, null, null, userId, deptId, dataScope);
    }

    public PerformanceMetricsQueryService.PerformanceAggregate aggregateRange(
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
        StringBuilder where = orderFactsPerformanceJoin();
        appendScope(where, args, userId, deptId, dataScope);
        appendBusinessLineFilter(where, args, businessLine, channelId, recruiterId);
        appendRangeFilter(where, args, timeColumn, startInclusive, endExclusive);

        String amountColumn = estimateTrack
                ? "pr.pay_amount + COALESCE(adj.delta_pay_amount, 0)"
                : "pr.settle_amount + COALESCE(adj.delta_settle_amount, 0)";
        String serviceFeeColumn = estimateTrack
                ? "pr.estimate_service_fee + COALESCE(adj.delta_estimate_service_fee, 0)"
                : "pr.effective_service_fee + COALESCE(adj.delta_effective_service_fee, 0)";
        String techFeeColumn = estimateTrack
                ? "pr.estimate_tech_service_fee + COALESCE(adj.delta_estimate_tech_service_fee, 0)"
                : "pr.effective_tech_service_fee + COALESCE(adj.delta_effective_tech_service_fee, 0)";
        String expenseColumn = estimateTrack
                ? "pr.estimate_service_fee_expense + COALESCE(adj.delta_estimate_service_fee_expense, 0)"
                : "pr.effective_service_fee_expense + COALESCE(adj.delta_effective_service_fee_expense, 0)";
        String talentCommissionColumn = "pr.talent_commission + COALESCE(adj.delta_talent_commission, 0)";
        String profitColumn = estimateTrack
                ? "pr.estimate_service_profit + COALESCE(adj.delta_estimate_service_profit, 0)"
                : "pr.effective_service_profit + COALESCE(adj.delta_effective_service_profit, 0)";
        String recruiterColumn = estimateTrack
                ? "pr.estimate_recruiter_commission + COALESCE(adj.delta_estimate_recruiter_commission, 0)"
                : "pr.effective_recruiter_commission + COALESCE(adj.delta_effective_recruiter_commission, 0)";
        String channelColumn = estimateTrack
                ? "pr.estimate_channel_commission + COALESCE(adj.delta_estimate_channel_commission, 0)"
                : "pr.effective_channel_commission + COALESCE(adj.delta_effective_channel_commission, 0)";
        String grossColumn = estimateTrack
                ? "pr.estimate_gross_profit + COALESCE(adj.delta_estimate_gross_profit, 0)"
                : "pr.effective_gross_profit + COALESCE(adj.delta_effective_gross_profit, 0)";

        String sql = """
                SELECT
                    COUNT(*) AS order_count,
                    COALESCE(SUM(%s), 0) AS order_amount_cent,
                    COALESCE(SUM(%s), 0) AS service_fee_income_cent,
                    COALESCE(SUM(%s), 0) AS tech_service_fee_cent,
                    COALESCE(SUM(%s), 0) AS service_fee_expense_cent,
                    COALESCE(SUM(%s), 0) AS talent_commission_cent,
                    COALESCE(SUM(%s), 0) AS service_profit_cent,
                    COALESCE(SUM(%s), 0) AS recruiter_commission_cent,
                    COALESCE(SUM(%s), 0) AS channel_commission_cent,
                    COALESCE(SUM(%s), 0) AS gross_profit_cent
                """.formatted(
                amountColumn,
                serviceFeeColumn,
                techFeeColumn,
                expenseColumn,
                talentCommissionColumn,
                profitColumn,
                recruiterColumn,
                channelColumn,
                grossColumn) + where;

        Map<String, Object> row = jdbcTemplate.queryForMap(sql, args.toArray());
        return new PerformanceMetricsQueryService.PerformanceAggregate(
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

    private boolean isEstimateTrack(String timeField) {
        return "order_create_time".equals(resolveTimeColumn(timeField));
    }

    private String resolveTimeColumn(String timeField) {
        if (!StringUtils.hasText(timeField)) {
            return "create_time";
        }
        String normalized = timeField.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "createtime", "create_time", "create" -> "order_create_time";
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
        if (!dddRefactorProperties.getDataScopePolicy().isEnabled()) {
            appendScopeLegacy(where, args, userId, deptId, dataScope);
            return;
        }
        appendScopeWithResolver(where, args, userId, deptId, dataScope);
    }

    private void appendScopeLegacy(
            StringBuilder where,
            List<Object> args,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        if (dataScope == DataScope.PERSONAL && userId != null) {
            where.append(" AND (pr.final_channel_user_id = ? OR pr.final_recruiter_user_id = ?)");
            args.add(userId);
            args.add(userId);
            return;
        }
        if (dataScope == DataScope.DEPT && deptId != null) {
            where.append(" AND (pr.final_channel_dept_id = ? OR pr.final_recruiter_dept_id = ?)");
            args.add(deptId);
            args.add(deptId);
        }
    }

    private StringBuilder orderFactsPerformanceJoin() {
        return new StringBuilder("""
                FROM performance_records pr
                LEFT JOIN colonelsettlement_order co ON co.order_id = pr.order_id AND co.deleted = 0
                LEFT JOIN (
                    SELECT order_id,
                           SUM(delta_pay_amount) AS delta_pay_amount,
                           SUM(delta_settle_amount) AS delta_settle_amount,
                           SUM(delta_estimate_service_fee) AS delta_estimate_service_fee,
                           SUM(delta_effective_service_fee) AS delta_effective_service_fee,
                           SUM(delta_estimate_tech_service_fee) AS delta_estimate_tech_service_fee,
                           SUM(delta_effective_tech_service_fee) AS delta_effective_tech_service_fee,
                           SUM(delta_estimate_service_fee_expense) AS delta_estimate_service_fee_expense,
                           SUM(delta_effective_service_fee_expense) AS delta_effective_service_fee_expense,
                           SUM(delta_talent_commission) AS delta_talent_commission,
                           SUM(delta_estimate_service_profit) AS delta_estimate_service_profit,
                           SUM(delta_effective_service_profit) AS delta_effective_service_profit,
                           SUM(delta_estimate_recruiter_commission) AS delta_estimate_recruiter_commission,
                           SUM(delta_effective_recruiter_commission) AS delta_effective_recruiter_commission,
                           SUM(delta_estimate_channel_commission) AS delta_estimate_channel_commission,
                           SUM(delta_effective_channel_commission) AS delta_effective_channel_commission,
                           SUM(delta_estimate_gross_profit) AS delta_estimate_gross_profit,
                           SUM(delta_effective_gross_profit) AS delta_effective_gross_profit
                    FROM performance_adjustment_ledger
                    GROUP BY order_id
                ) adj ON adj.order_id = pr.order_id
                WHERE pr.is_valid = TRUE
                """);
    }

    private void appendScopeWithResolver(
            StringBuilder where,
            List<Object> args,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        DataScopeResolver.ResolvedDataScope resolved = dataScopeResolver.resolve(userId, deptId, dataScope);
        if (resolved.filtersUser()) {
            where.append(" AND (pr.final_channel_user_id = ? OR pr.final_recruiter_user_id = ?)");
            args.add(userId);
            args.add(userId);
            return;
        }
        if (resolved.filtersDept()) {
            where.append(" AND (pr.final_channel_dept_id = ? OR pr.final_recruiter_dept_id = ?)");
            args.add(deptId);
            args.add(deptId);
        }
    }

    /**
     * 追加时间范围过滤条件，并在 settle 轨道时强制要求 settle_time 不为空。
     *
     * <p>用于聚合查询统一处理时间范围 + 已结算限定：任意一端为 null 时跳过该方向，
     * 时间范围在 settle 轨道上自动要求 {@code co.settle_time IS NOT NULL}，
     * 与原有 SQL 行为一致。</p>
     */
    private void appendRangeFilter(
            StringBuilder where,
            List<Object> args,
            String timeColumn,
            LocalDateTime startInclusive,
            LocalDateTime endExclusive) {
        if (startInclusive != null) {
            where.append(" AND pr.").append(timeColumn).append(" >= ?");
            args.add(startInclusive);
        }
        if (endExclusive != null) {
            where.append(" AND pr.").append(timeColumn).append(" < ?");
            args.add(endExclusive);
        }
        if ("settle_time".equals(timeColumn) && (startInclusive != null || endExclusive != null)) {
            where.append(" AND pr.settle_time IS NOT NULL");
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

    // ========================================================================
    // Slice 3：trendByDay + aggregateDashboardSummary 下沉（与 aggregateRange 同 Application）
    // ========================================================================

    public List<PerformanceMetricsQueryService.TrendPoint> trendByDay(
            LocalDateTime startInclusive,
            LocalDateTime endExclusive,
            String timeField,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        return trendByDay(startInclusive, endExclusive, timeField,
                null, null, null, userId, deptId, dataScope);
    }

    public List<PerformanceMetricsQueryService.TrendPoint> trendByDay(
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
        StringBuilder where = orderFactsPerformanceJoin();
        appendScope(where, args, userId, deptId, dataScope);
        appendBusinessLineFilter(where, args, businessLine, channelId, recruiterId);
        appendRangeFilter(where, args, timeColumn, startInclusive, endExclusive);

        String amountColumn = estimateTrack
                ? "pr.pay_amount + COALESCE(adj.delta_pay_amount, 0)"
                : "pr.settle_amount + COALESCE(adj.delta_settle_amount, 0)";
        String sql = """
                SELECT DATE(pr.%s) AS stat_date,
                       COUNT(*) AS order_count,
                       COALESCE(SUM(%s), 0) AS order_amount_cent
                """.formatted(timeColumn, amountColumn) + where + """
                 GROUP BY DATE(pr.%s)
                """.formatted(timeColumn);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args.toArray());
        Map<LocalDate, PerformanceMetricsQueryService.TrendPoint> mapped = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            LocalDate day = LocalDate.parse(String.valueOf(row.get("stat_date")));
            mapped.put(day, new PerformanceMetricsQueryService.TrendPoint(
                    day.toString(),
                    asLong(row.get("order_count")),
                    asLong(row.get("order_amount_cent"))));
        }

        List<PerformanceMetricsQueryService.TrendPoint> trend = new ArrayList<>();
        LocalDate cursor = startInclusive.toLocalDate();
        LocalDate endDate = endExclusive.toLocalDate().minusDays(1);
        while (!cursor.isAfter(endDate)) {
            PerformanceMetricsQueryService.TrendPoint point = mapped.get(cursor);
            trend.add(point == null
                    ? new PerformanceMetricsQueryService.TrendPoint(cursor.toString(), 0L, 0L)
                    : point);
            cursor = cursor.plusDays(1);
        }
        return trend;
    }

    public PerformanceMetricsQueryService.DashboardPerformanceSummary aggregateDashboardSummary(
            LocalDateTime startInclusive,
            LocalDateTime endInclusive,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = orderFactsPerformanceJoin();
        appendScope(where, args, userId, deptId, dataScope);
        appendSettleTimeRangeInclusive(where, args, startInclusive, endInclusive);

        String totalsSql = """
                SELECT
                    COUNT(*) AS order_count,
                    COALESCE(SUM(pr.settle_amount + COALESCE(adj.delta_settle_amount, 0)), 0) AS order_amount_cent,
                    COALESCE(SUM(pr.effective_service_profit
                                 + COALESCE(adj.delta_effective_service_profit, 0)), 0) AS service_fee_cent
                """ + where;
        Map<String, Object> totals = jdbcTemplate.queryForMap(totalsSql, args.toArray());

        List<PerformanceMetricsQueryService.PerformanceLeaderboardItem> channelPerformance = queryLeaderboard(
                where.toString(),
                new ArrayList<>(args),
                "pr.final_channel_user_id",
                "co.channel_user_name",
                "pr.final_channel_user_id IS NOT NULL");
        List<PerformanceMetricsQueryService.PerformanceLeaderboardItem> colonelPerformance = queryLeaderboard(
                where.toString(),
                new ArrayList<>(args),
                "pr.final_recruiter_user_id",
                "co.colonel_user_name",
                "pr.final_recruiter_user_id IS NOT NULL");

        return new PerformanceMetricsQueryService.DashboardPerformanceSummary(
                asLong(totals.get("order_count")),
                asLong(totals.get("order_amount_cent")),
                asLong(totals.get("service_fee_cent")),
                channelPerformance,
                colonelPerformance);
    }

    /**
     * 追加 settle_time 时间范围过滤（包含两端）。
     *
     * <p>{@code aggregateDashboardSummary} 专用，与 {@link #appendRangeFilter} 的
     * {@code [start, end)} 半开区间不同。</p>
     */
    private void appendSettleTimeRangeInclusive(
            StringBuilder where,
            List<Object> args,
            LocalDateTime startInclusive,
            LocalDateTime endInclusive) {
        if (startInclusive != null) {
            where.append(" AND pr.settle_time >= ?");
            args.add(startInclusive);
        }
        if (endInclusive != null) {
            where.append(" AND pr.settle_time <= ?");
            args.add(endInclusive);
        }
        if (startInclusive != null || endInclusive != null) {
            where.append(" AND pr.settle_time IS NOT NULL");
        }
    }

    /**
     * 查询 leaderboard（按 user_id 分组的业绩排名），供
     * {@code aggregateDashboardSummary} 中渠道/团长 leaderboard 共用。
     */
    private List<PerformanceMetricsQueryService.PerformanceLeaderboardItem> queryLeaderboard(
            String baseWhere,
            List<Object> args,
            String userIdColumn,
            String userNameColumn,
            String extraFilter) {
        String sql = """
                SELECT %s::text AS user_id,
                       MAX(%s) AS user_name,
                       COUNT(*) AS order_count,
                       COALESCE(SUM(pr.settle_amount + COALESCE(adj.delta_settle_amount, 0)), 0) AS order_amount_cent,
                       COALESCE(SUM(pr.effective_service_profit
                                    + COALESCE(adj.delta_effective_service_profit, 0)), 0) AS service_fee_cent
                """.formatted(userIdColumn, userNameColumn) + baseWhere
                + " AND " + extraFilter
                + " GROUP BY " + userIdColumn
                + " ORDER BY order_count DESC, order_amount_cent DESC"
                + " LIMIT 10";
        return jdbcTemplate.queryForList(sql, args.toArray()).stream()
                .map(row -> new PerformanceMetricsQueryService.PerformanceLeaderboardItem(
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
}
