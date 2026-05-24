package com.colonel.saas.service;

import com.colonel.saas.dto.performance.PerformanceSummaryQuery;
import com.colonel.saas.dto.performance.PerformanceSummaryResponse;
import com.colonel.saas.dto.performance.PerformanceTrackSummaryDTO;
import com.colonel.saas.service.performance.PerformanceAccessContext;
import com.colonel.saas.service.performance.PerformanceAccessScope;
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
 * 业绩指标卡片双轨汇总：cohort 时间（pay/settle）与 estimate/effective 金额轨道分离。
 */
@Service
public class PerformanceSummaryService {

    private static final String BASE_FROM = """
            FROM performance_records pr
            LEFT JOIN colonelsettlement_order co ON co.order_id = pr.order_id AND co.deleted = 0
            """;

    private final JdbcTemplate jdbcTemplate;

    public PerformanceSummaryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PerformanceSummaryResponse getSummary(PerformanceSummaryQuery query, PerformanceAccessContext context) {
        PerformanceSummaryQuery safeQuery = query == null ? new PerformanceSummaryQuery() : query;
        PerformanceAccessScope.assertFilterAllowed(
                safeQuery.getChannelId(),
                safeQuery.getRecruiterId(),
                context);

        PerformanceSummaryResponse response = new PerformanceSummaryResponse();
        response.setEstimate(aggregateEstimate(safeQuery, context));
        response.setEffective(aggregateEffective(safeQuery, context));
        return response;
    }

    public PerformanceTrackSummaryDTO aggregateEstimate(PerformanceSummaryQuery query, PerformanceAccessContext context) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = buildScopeCondition(query, context, args);
        String sql = """
                SELECT
                    COUNT(*) AS order_count,
                    COALESCE(SUM(pr.pay_amount), 0) AS order_amount,
                    COALESCE(SUM(pr.estimate_service_fee), 0) AS service_fee_income,
                    COALESCE(SUM(pr.estimate_tech_service_fee), 0) AS tech_service_fee,
                    COALESCE(SUM(pr.estimate_service_profit), 0) AS service_fee_profit,
                    COALESCE(SUM(pr.estimate_recruiter_commission), 0) AS recruiter_commission,
                    COALESCE(SUM(pr.estimate_channel_commission), 0) AS channel_commission,
                    COALESCE(SUM(pr.estimate_gross_profit), 0) AS gross_profit
                """ + BASE_FROM + where;
        return mapTrackSummary(jdbcTemplate.queryForMap(sql, args.toArray()));
    }

    public PerformanceTrackSummaryDTO aggregateEffective(PerformanceSummaryQuery query, PerformanceAccessContext context) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = buildScopeCondition(query, context, args);
        where.append(" AND (pr.settle_time IS NOT NULL OR pr.effective_service_fee > 0)");
        String sql = """
                SELECT
                    COUNT(*) AS order_count,
                    COALESCE(SUM(pr.settle_amount), 0) AS order_amount,
                    COALESCE(SUM(pr.effective_service_fee), 0) AS service_fee_income,
                    COALESCE(SUM(pr.effective_tech_service_fee), 0) AS tech_service_fee,
                    COALESCE(SUM(pr.effective_service_profit), 0) AS service_fee_profit,
                    COALESCE(SUM(pr.effective_recruiter_commission), 0) AS recruiter_commission,
                    COALESCE(SUM(pr.effective_channel_commission), 0) AS channel_commission,
                    COALESCE(SUM(pr.effective_gross_profit), 0) AS gross_profit
                """ + BASE_FROM + where;
        return mapTrackSummary(jdbcTemplate.queryForMap(sql, args.toArray()));
    }

    StringBuilder buildScopeCondition(
            PerformanceSummaryQuery query,
            PerformanceAccessContext context,
            List<Object> args) {
        StringBuilder where = new StringBuilder(" WHERE pr.is_valid = TRUE ");
        PerformanceAccessScope.appendScopeCondition(where, args, context, "pr");
        appendSummaryFilters(where, args, query);
        appendCohortTimeFilter(where, args, query);
        return where;
    }

    private void appendSummaryFilters(StringBuilder where, List<Object> args, PerformanceSummaryQuery query) {
        if (query.getChannelId() != null) {
            where.append(" AND pr.final_channel_user_id = ?");
            args.add(query.getChannelId());
        }
        if (query.getRecruiterId() != null) {
            where.append(" AND pr.final_recruiter_user_id = ?");
            args.add(query.getRecruiterId());
        }
        if (StringUtils.hasText(query.getActivityId())) {
            where.append(" AND pr.activity_id = ?");
            args.add(query.getActivityId().trim());
        }
        if (StringUtils.hasText(query.getProductId())) {
            where.append(" AND pr.product_id = ?");
            args.add(query.getProductId().trim());
        }
        if (query.getPartnerId() != null) {
            where.append(" AND pr.partner_id = ?");
            args.add(query.getPartnerId());
        }
        if (query.getTalentId() != null) {
            where.append(" AND pr.talent_id = ?");
            args.add(query.getTalentId());
        }
        if (StringUtils.hasText(query.getOrderStatus())) {
            where.append(" AND pr.order_status = ?");
            args.add(toOrderStatusCode(query.getOrderStatus()));
        }
    }

    private void appendCohortTimeFilter(StringBuilder where, List<Object> args, PerformanceSummaryQuery query) {
        String cohortColumn = resolveCohortColumn(query.getTimeFilterType());
        if (query.getTimeStart() != null) {
            where.append(" AND ").append(cohortColumn).append(" >= ?");
            args.add(query.getTimeStart());
        }
        if (query.getTimeEnd() != null) {
            where.append(" AND ").append(cohortColumn).append(" < ?");
            args.add(query.getTimeEnd());
        }
        if ("settle".equalsIgnoreCase(query.getTimeFilterType())
                && (query.getTimeStart() != null || query.getTimeEnd() != null)) {
            where.append(" AND pr.settle_time IS NOT NULL");
        }
    }

    private String resolveCohortColumn(String timeFilterType) {
        if ("settle".equalsIgnoreCase(timeFilterType)) {
            return "pr.settle_time";
        }
        return "COALESCE(pr.order_create_time, co.create_time)";
    }

    private PerformanceTrackSummaryDTO mapTrackSummary(Map<String, Object> row) {
        PerformanceTrackSummaryDTO track = new PerformanceTrackSummaryDTO();
        long recruiter = asLong(row.get("recruiter_commission"));
        long channel = asLong(row.get("channel_commission"));
        track.setOrderCount(asLong(row.get("order_count")));
        track.setOrderAmount(asLong(row.get("order_amount")));
        track.setServiceFeeIncome(asLong(row.get("service_fee_income")));
        track.setTechServiceFee(asLong(row.get("tech_service_fee")));
        track.setServiceFeeProfit(asLong(row.get("service_fee_profit")));
        track.setRecruiterCommission(recruiter);
        track.setChannelCommission(channel);
        track.setServiceFeeExpense(recruiter + channel);
        track.setGrossProfit(asLong(row.get("gross_profit")));
        return track;
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

    private Integer toOrderStatusCode(String status) {
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "ORDERED" -> 1;
            case "SHIPPED" -> 2;
            case "FINISHED" -> 3;
            case "CANCELLED" -> 4;
            default -> null;
        };
    }
}
