package com.colonel.saas.domain.performance.application;

import com.colonel.saas.domain.performance.policy.PerformanceAccessContext;
import com.colonel.saas.domain.performance.policy.PerformanceAccessScope;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionChecker;
import com.colonel.saas.dto.performance.PerformanceSummaryQuery;
import com.colonel.saas.dto.performance.PerformanceSummaryResponse;
import com.colonel.saas.dto.performance.PerformanceTrackSummaryDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 业绩汇总应用服务（DDD-PERFORMANCE Slice 4）。
 *
 * <p>从 {@code service.PerformanceSummaryService} 整体迁移过来的卡片汇总逻辑：
 * <ul>
 *   <li>{@link #getSummary} - 全局卡片汇总（双轨：预估 + 实收）</li>
 *   <li>{@link #aggregateEstimate} - 预估轨道（基于下单时间 + order_amount）</li>
 *   <li>{@link #aggregateEffective} - 实收轨道（基于结算时间 + settle_amount）</li>
 * </ul>
 *
 * <p>本类是 performance 域读模型的另一独立入口。承接原 Service 的所有 private helper
 * （SQL 装配 / 数据权限范围 / 类型转换 / 订单状态码映射）。</p>
 *
 * <p>与 {@link PerformanceAggregateApplicationService} 关系：
 * <ul>
 *   <li>Aggregate 类负责"单维度指标聚合"（range/趋势/看板卡片）</li>
 *   <li>Summary 类负责"全局卡片双轨汇总"（estimate + effective 双轨成对返回）</li>
 * </ul>
 *
 * <p>依赖：
 * <ul>
 *   <li>{@link JdbcTemplate} —— 业绩事实及调整流水聚合 SQL</li>
 *   <li>{@link PerformanceAccessScope} —— 数据权限范围 + cohort 时间列解析</li>
 * </ul>
 */
@Service
public class PerformanceSummaryApplicationService {

    /**
     * 业绩读模型以 performance_records 为唯一事实根，并合并退款/冲正调整流水。
     * 订单表只在计算阶段提供输入快照，不能在查询阶段重新决定业绩金额或归属。
     */
    private static final String BASE_FROM = """
            FROM performance_records pr
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
            """;

    private static final String VALID_PERFORMANCE_CONDITION =
            " AND pr.is_valid = TRUE"
                    + " AND pr.is_reversed = FALSE";

    private final JdbcTemplate jdbcTemplate;
    private final CurrentUserPermissionChecker currentUserPermissionChecker;

    public PerformanceSummaryApplicationService(
            JdbcTemplate jdbcTemplate,
            CurrentUserPermissionChecker currentUserPermissionChecker) {
        this.jdbcTemplate = jdbcTemplate;
        this.currentUserPermissionChecker = currentUserPermissionChecker;
    }

    /**
     * 获取业绩指标卡片汇总数据（同时包含预估轨道和实收轨道）。
     *
     * <p>处理流程：
     * <ol>
     *   <li>对查询参数做空值保护（null 时创建默认空对象）</li>
     *   <li>通过 {@link PerformanceAccessScope#assertFilterAllowed} 校验数据权限是否允许指定的筛选条件</li>
     *   <li>调用 {@link #aggregateEstimate} 聚合预估轨道指标</li>
     *   <li>调用 {@link #aggregateEffective} 聚合实收轨道指标</li>
     *   <li>封装双轨结果并返回</li>
     * </ol>
     *
     * @param query   业绩汇总查询参数（可为 null，null 时使用默认值）
     * @param context 数据权限上下文（含当前用户 ID、部门 ID、数据范围）
     * @return 双轨汇总响应（预估 + 实收）
     */
    public PerformanceSummaryResponse getSummary(PerformanceSummaryQuery query, PerformanceAccessContext context) {
        // 第一步：空值保护，null 参数替换为默认空查询对象
        PerformanceSummaryQuery safeQuery = query == null ? new PerformanceSummaryQuery() : query;
        // 第二步：校验当前数据权限是否允许指定的渠道/招募官筛选
        PerformanceAccessScope.assertFilterAllowed(
                safeQuery.getChannelId(),
                safeQuery.getRecruiterId(),
                context,
                currentUserPermissionChecker);

        // 第三步：分别聚合双轨指标，组装响应
        PerformanceSummaryResponse response = new PerformanceSummaryResponse();
        response.setEstimate(aggregateEstimate(safeQuery, context));
        response.setEffective(aggregateEffective(safeQuery, context));
        return response;
    }

    /**
     * 聚合预估轨道指标（基于下单时间 cohort + order_amount 金额维度）。
     */
    public PerformanceTrackSummaryDTO aggregateEstimate(PerformanceSummaryQuery query, PerformanceAccessContext context) {
        // 第一步：构建 SQL 参数列表和 WHERE 条件
        List<Object> args = new ArrayList<>();
        StringBuilder where = buildScopeCondition(query, context, args);
        // 第二步：拼接预估轨道聚合 SQL（使用 estimate_* 系列金额字段）
        String sql = """
                SELECT
                    COUNT(*) AS order_count,
                    COALESCE(SUM(pr.pay_amount + COALESCE(adj.delta_pay_amount, 0)), 0) AS order_amount,
                    COALESCE(SUM(pr.estimate_service_fee + COALESCE(adj.delta_estimate_service_fee, 0)), 0) AS service_fee_income,
                    COALESCE(SUM(pr.estimate_tech_service_fee + COALESCE(adj.delta_estimate_tech_service_fee, 0)), 0) AS tech_service_fee,
                    COALESCE(SUM(pr.estimate_service_fee_expense + COALESCE(adj.delta_estimate_service_fee_expense, 0)), 0) AS service_fee_expense,
                    COALESCE(SUM(pr.estimate_service_profit + COALESCE(adj.delta_estimate_service_profit, 0)), 0) AS service_fee_profit,
                    COALESCE(SUM(pr.estimate_recruiter_commission + COALESCE(adj.delta_estimate_recruiter_commission, 0)), 0) AS recruiter_commission,
                    COALESCE(SUM(pr.estimate_channel_commission + COALESCE(adj.delta_estimate_channel_commission, 0)), 0) AS channel_commission,
                    COALESCE(SUM(pr.estimate_gross_profit + COALESCE(adj.delta_estimate_gross_profit, 0)), 0) AS gross_profit
                """ + BASE_FROM + where;
        // 第三步：执行查询并映射为 DTO
        return mapTrackSummary(jdbcTemplate.queryForMap(sql, args.toArray()), true);
    }

    /**
     * 聚合实收轨道指标（基于结算时间 cohort + settle_amount 金额维度）。
     *
     * <p>与预估轨道的区别：
     * <ul>
     *   <li>仅统计已结算的记录（settle_time IS NOT NULL 或 effective_service_fee > 0）</li>
     *   <li>金额列使用 effective_* 系列字段而非 estimate_* 系列</li>
     * </ul>
     */
    public PerformanceTrackSummaryDTO aggregateEffective(PerformanceSummaryQuery query, PerformanceAccessContext context) {
        // 第一步：构建 SQL 参数列表和 WHERE 条件
        List<Object> args = new ArrayList<>();
        StringBuilder where = buildScopeCondition(query, context, args);
        // 第二步：追加实收轨道过滤条件——仅统计已结算业绩记录
        where.append(" AND (pr.settle_time IS NOT NULL OR pr.effective_service_fee > 0)");
        // 第三步：拼接实收轨道聚合 SQL（使用 effective_* 系列业绩快照字段）
        String sql = """
                SELECT
                    COUNT(*) AS order_count,
                    COALESCE(SUM(pr.settle_amount + COALESCE(adj.delta_settle_amount, 0)), 0) AS order_amount,
                    COALESCE(SUM(pr.effective_service_fee + COALESCE(adj.delta_effective_service_fee, 0)), 0) AS service_fee_income,
                    COALESCE(SUM(pr.effective_tech_service_fee + COALESCE(adj.delta_effective_tech_service_fee, 0)), 0) AS tech_service_fee,
                    COALESCE(SUM(pr.effective_service_fee_expense + COALESCE(adj.delta_effective_service_fee_expense, 0)), 0) AS service_fee_expense,
                    COALESCE(SUM(pr.effective_service_profit + COALESCE(adj.delta_effective_service_profit, 0)), 0) AS service_fee_profit,
                    COALESCE(SUM(pr.effective_recruiter_commission + COALESCE(adj.delta_effective_recruiter_commission, 0)), 0) AS recruiter_commission,
                    COALESCE(SUM(pr.effective_channel_commission + COALESCE(adj.delta_effective_channel_commission, 0)), 0) AS channel_commission,
                    COALESCE(SUM(pr.effective_gross_profit + COALESCE(adj.delta_effective_gross_profit, 0)), 0) AS gross_profit
                """ + BASE_FROM + where;
        // 第四步：执行查询并映射为 DTO
        return mapTrackSummary(jdbcTemplate.queryForMap(sql, args.toArray()), false);
    }

    /**
     * 构建业绩汇总查询的作用域 WHERE 条件。
     */
    StringBuilder buildScopeCondition(
            PerformanceSummaryQuery query,
            PerformanceAccessContext context,
            List<Object> args) {
        // 第一步：以已计算业绩事实为基准条件
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        where.append(VALID_PERFORMANCE_CONDITION);
        // 第二步：追加数据权限范围条件（个人/部门/全部）
        PerformanceAccessScope.appendScopeCondition(where, args, context, "pr", currentUserPermissionChecker);
        // 第三步：追加业务筛选条件（渠道、招募官、活动等）
        appendSummaryFilters(where, args, query);
        // 第四步：追加 cohort 时间区间条件
        appendCohortTimeFilter(where, args, query);
        return where;
    }

    /**
     * 追加汇总查询的业务筛选条件。
     */
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

    /**
     * 追加 cohort 时间区间筛选。
     */
    private void appendCohortTimeFilter(StringBuilder where, List<Object> args, PerformanceSummaryQuery query) {
        // 第一步：解析 cohort 时间列名
        String cohortColumn = resolveCohortColumn(query.getTimeFilterType());
        // 第二步：追加时间起止范围条件
        if (query.getTimeStart() != null) {
            where.append(" AND ").append(cohortColumn).append(" >= ?");
            args.add(query.getTimeStart());
        }
        if (query.getTimeEnd() != null) {
            where.append(" AND ").append(cohortColumn).append(" < ?");
            args.add(query.getTimeEnd());
        }
        // 第三步：当使用结算时间 cohort 且有时间范围时，排除未结算记录
        if ("settle".equalsIgnoreCase(query.getTimeFilterType())
                && (query.getTimeStart() != null || query.getTimeEnd() != null)) {
            where.append(" AND pr.settle_time IS NOT NULL");
        }
    }

    /**
     * 根据时间筛选类型解析对应的 cohort 时间列名。
     */
    private String resolveCohortColumn(String timeFilterType) {
        if ("settle".equalsIgnoreCase(timeFilterType)) {
            return "pr.settle_time";
        }
        return "pr.order_create_time";
    }

    /**
     * 将数据库聚合结果行映射为 {@link PerformanceTrackSummaryDTO}。
     */
    private PerformanceTrackSummaryDTO mapTrackSummary(Map<String, Object> row, boolean estimateTrack) {
        PerformanceTrackSummaryDTO track = new PerformanceTrackSummaryDTO();
        long serviceFeeIncome = asLong(row.get("service_fee_income"));
        long techServiceFee = asLong(row.get("tech_service_fee"));
        long serviceFeeExpense = asLong(row.get("service_fee_expense"));
        long serviceProfit = asLong(row.get("service_fee_profit"));
        long recruiter = asLong(row.get("recruiter_commission"));
        long channel = asLong(row.get("channel_commission"));
        track.setOrderCount(asLong(row.get("order_count")));
        track.setOrderAmount(asLong(row.get("order_amount")));
        track.setServiceFeeIncome(serviceFeeIncome);
        track.setTechServiceFee(techServiceFee);
        track.setServiceFeeExpense(serviceFeeExpense);
        track.setServiceFeeProfit(serviceProfit);
        track.setRecruiterCommission(recruiter);
        track.setChannelCommission(channel);
        track.setGrossProfit(asLong(row.get("gross_profit")));
        return track;
    }

    /**
     * 安全地将数据库返回值转换为 long 类型。
     */
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

    /**
     * 将订单状态字符串映射为状态码。
     */
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
