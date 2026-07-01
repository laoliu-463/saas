package com.colonel.saas.domain.performance.application;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.user.policy.DataScopePolicy;
import com.colonel.saas.service.PerformanceMetricsQueryService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 业绩聚合应用服务（DDD-PERFORMANCE Slice 2）。
 *
 * <p>从 {@code service.PerformanceMetricsQueryService} 迁移过来的核心聚合逻辑：
 * <ul>
 *   <li>{@link #aggregateRange} - 时间范围内业绩指标聚合（estimate / effective 双轨）</li>
 * </ul>
 *
 * <p>本类是 performance 域读模型的独立入口。承接原 Service 的所有 private helper
 * （SQL 装配 / 数据范围策略 / 时间列解析）。{@code trendByDay} 和
 * {@code aggregateDashboardSummary} 仍由原 Service 持有，下一切片（Slice 3+）处理。</p>
 *
 * <p>依赖：
 * <ul>
 *   <li>{@link JdbcTemplate} —— 多表 JOIN 原始 SQL</li>
 *   <li>{@link DataScopePolicy} —— 数据范围策略（灰度切换）</li>
 *   <li>{@link DddRefactorProperties} —— DataScopePolicy 开关</li>
 * </ul>
 */
@Service
public class PerformanceAggregateApplicationService {

    private final JdbcTemplate jdbcTemplate;
    private final DataScopePolicy dataScopePolicy;
    private final DddRefactorProperties dddRefactorProperties;

    public PerformanceAggregateApplicationService(
            JdbcTemplate jdbcTemplate,
            DataScopePolicy dataScopePolicy,
            DddRefactorProperties dddRefactorProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataScopePolicy = dataScopePolicy;
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

        String amountColumn = estimateTrack ? "co.order_amount" : "co.settle_amount";
        String serviceFeeColumn = estimateTrack ? "co.estimate_service_fee" : "co.effective_service_fee";
        String techFeeColumn = estimateTrack ? "co.estimate_tech_service_fee" : "co.effective_tech_service_fee";
        String expenseColumn = estimateTrack ? "co.estimate_service_fee_expense" : "co.effective_service_fee_expense";
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
        if (!dddRefactorProperties.getDataScopePolicy().isEnabled()) {
            appendScopeLegacy(where, args, userId, deptId, dataScope);
            return;
        }
        appendScopeWithPolicy(where, args, userId, deptId, dataScope);
    }

    private void appendScopeLegacy(
            StringBuilder where,
            List<Object> args,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        if (dataScope == DataScope.PERSONAL && userId != null) {
            where.append(" AND co.user_id = ?");
            args.add(userId);
            return;
        }
        if (dataScope == DataScope.DEPT && deptId != null) {
            where.append(" AND co.dept_id = ?");
            args.add(deptId);
        }
    }

    private StringBuilder orderFactsPerformanceJoin() {
        return new StringBuilder("""
                FROM colonelsettlement_order co
                LEFT JOIN performance_records pr ON pr.order_id = co.order_id
                WHERE co.deleted = 0
                """);
    }

    private void appendScopeWithPolicy(
            StringBuilder where,
            List<Object> args,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
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