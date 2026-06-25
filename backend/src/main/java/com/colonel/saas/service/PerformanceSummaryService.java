package com.colonel.saas.service;

import com.colonel.saas.dto.performance.PerformanceSummaryQuery;
import com.colonel.saas.dto.performance.PerformanceSummaryResponse;
import com.colonel.saas.dto.performance.PerformanceTrackSummaryDTO;
import com.colonel.saas.domain.performance.policy.PerformanceAccessContext;
import com.colonel.saas.domain.performance.policy.PerformanceAccessScope;
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
 * 业绩指标卡片双轨汇总服务。
 * <p>
 * 负责从订单事实表（{@code colonelsettlement_order}）聚合出看板卡片所需的汇总指标，
 * 并左关联业绩明细表（{@code performance_records}）补充提成归属字段，
 * 支持 cohort 时间维度（下单时间 / 结算时间）与金额轨道维度（预估 / 实收）的双轨分离查询。
 * </p>
 *
 * <h3>职责列表</h3>
 * <ul>
 *   <li>提供业绩指标卡片的全局汇总（预估轨道 + 实收轨道）</li>
 *   <li>支持按 cohort 时间类型（pay / settle）筛选统计口径</li>
 *   <li>支持多维筛选条件：渠道、招募官、活动、商品、合伙人、达人、订单状态</li>
 *   <li>结合数据权限上下文（{@link PerformanceAccessScope}）进行行级过滤</li>
 *   <li>将数据库行映射为 {@link PerformanceTrackSummaryDTO} 统一输出</li>
 * </ul>
 *
 * <h3>架构角色</h3>
 * <p>
 * 属于业绩域（Performance Domain）的查询服务层，只读不写。
 * 上层由 {@code PerformanceController} 调用，不直接暴露给前端。
 * </p>
 *
 * @see PerformanceAccessScope 数据权限范围
 * @see PerformanceSummaryQuery 查询参数
 */
@Service
public class PerformanceSummaryService {

    /** 多表连接基础 FROM 子句：订单事实表左关联业绩明细表 */
    private static final String BASE_FROM = """
            FROM colonelsettlement_order co
            LEFT JOIN performance_records pr ON pr.order_id = co.order_id
            """;

    /** Spring JDBC 模板，用于原生 SQL 查询 */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造注入。
     *
     * @param jdbcTemplate Spring JDBC 模板
     */
    public PerformanceSummaryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 获取业绩指标卡片汇总数据（同时包含预估轨道和实收轨道）。
     * <p>
     * 处理流程：
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
                context);

        // 第三步：分别聚合双轨指标，组装响应
        PerformanceSummaryResponse response = new PerformanceSummaryResponse();
        response.setEstimate(aggregateEstimate(safeQuery, context));
        response.setEffective(aggregateEffective(safeQuery, context));
        return response;
    }

    /**
     * 聚合预估轨道指标（基于下单时间 cohort + order_amount 金额维度）。
     * <p>
     * 处理流程：
     * <ol>
     *   <li>构建数据权限 + 查询条件的 WHERE 子句</li>
     *   <li>拼接预估轨道 SQL（SUM 聚合 order_amount、estimate_service_fee 等订单字段）</li>
     *   <li>执行查询并映射为 {@link PerformanceTrackSummaryDTO}</li>
     * </ol>
     *
     * @param query   业绩汇总查询参数
     * @param context 数据权限上下文
     * @return 预估轨道汇总指标
     */
    public PerformanceTrackSummaryDTO aggregateEstimate(PerformanceSummaryQuery query, PerformanceAccessContext context) {
        // 第一步：构建 SQL 参数列表和 WHERE 条件
        List<Object> args = new ArrayList<>();
        StringBuilder where = buildScopeCondition(query, context, args);
        // 第二步：拼接预估轨道聚合 SQL（使用 estimate_* 系列金额字段）
        String sql = """
                SELECT
                    COUNT(*) AS order_count,
                    COALESCE(SUM(co.order_amount), 0) AS order_amount,
                    COALESCE(SUM(co.estimate_service_fee), 0) AS service_fee_income,
                    COALESCE(SUM(co.estimate_tech_service_fee), 0) AS tech_service_fee,
                    COALESCE(SUM(co.estimate_service_fee_expense), 0) AS service_fee_expense,
                    COALESCE(SUM(pr.estimate_service_profit), 0) AS service_fee_profit,
                    COALESCE(SUM(pr.estimate_recruiter_commission), 0) AS recruiter_commission,
                    COALESCE(SUM(pr.estimate_channel_commission), 0) AS channel_commission,
                    COALESCE(SUM(pr.estimate_gross_profit), 0) AS gross_profit
                """ + BASE_FROM + where;
        // 第三步：执行查询并映射为 DTO
        return mapTrackSummary(jdbcTemplate.queryForMap(sql, args.toArray()), true);
    }

    /**
     * 聚合实收轨道指标（基于结算时间 cohort + settle_amount 金额维度）。
     * <p>
     * 与预估轨道的区别：
     * <ul>
     *   <li>仅统计已结算的记录（settle_time IS NOT NULL 或 effective_service_fee > 0）</li>
     *   <li>金额列使用 effective_* 系列字段而非 estimate_* 系列</li>
     * </ul>
     *
     * @param query   业绩汇总查询参数
     * @param context 数据权限上下文
     * @return 实收轨道汇总指标
     */
    public PerformanceTrackSummaryDTO aggregateEffective(PerformanceSummaryQuery query, PerformanceAccessContext context) {
        // 第一步：构建 SQL 参数列表和 WHERE 条件
        List<Object> args = new ArrayList<>();
        StringBuilder where = buildScopeCondition(query, context, args);
        // 第二步：追加实收轨道过滤条件——仅统计已结算订单事实
        where.append(" AND (co.settle_time IS NOT NULL OR co.effective_service_fee > 0)");
        // 第三步：拼接实收轨道聚合 SQL（使用 effective_* 系列订单字段）
        String sql = """
                SELECT
                    COUNT(*) AS order_count,
                    COALESCE(SUM(co.settle_amount), 0) AS order_amount,
                    COALESCE(SUM(co.effective_service_fee), 0) AS service_fee_income,
                    COALESCE(SUM(co.effective_tech_service_fee), 0) AS tech_service_fee,
                    COALESCE(SUM(co.effective_service_fee_expense), 0) AS service_fee_expense,
                    COALESCE(SUM(pr.effective_service_profit), 0) AS service_fee_profit,
                    COALESCE(SUM(pr.effective_recruiter_commission), 0) AS recruiter_commission,
                    COALESCE(SUM(pr.effective_channel_commission), 0) AS channel_commission,
                    COALESCE(SUM(pr.effective_gross_profit), 0) AS gross_profit
                """ + BASE_FROM + where;
        // 第四步：执行查询并映射为 DTO
        return mapTrackSummary(jdbcTemplate.queryForMap(sql, args.toArray()), false);
    }

    /**
     * 构建业绩汇总查询的作用域 WHERE 条件。
     * <p>
     * 处理流程：
     * <ol>
     *   <li>以 {@code co.deleted = 0} 为基准条件</li>
     *   <li>追加数据权限范围条件（{@link PerformanceAccessScope}）</li>
     *   <li>追加用户指定的筛选条件（渠道、招募官、活动等）</li>
     *   <li>追加 cohort 时间区间筛选</li>
     * </ol>
     *
     * @param query   业绩汇总查询参数
     * @param context 数据权限上下文
     * @param args    SQL 参数列表（方法会追加参数值）
     * @return 构建好的 WHERE 子句
     */
    StringBuilder buildScopeCondition(
            PerformanceSummaryQuery query,
            PerformanceAccessContext context,
            List<Object> args) {
        // 第一步：以订单事实未删除为基准条件
        StringBuilder where = new StringBuilder(" WHERE co.deleted = 0 ");
        // 第二步：追加数据权限范围条件（个人/部门/全部）
        PerformanceAccessScope.appendScopeCondition(where, args, context, "pr");
        // 第三步：追加业务筛选条件（渠道、招募官、活动等）
        appendSummaryFilters(where, args, query);
        // 第四步：追加 cohort 时间区间条件
        appendCohortTimeFilter(where, args, query);
        return where;
    }

    /**
     * 追加汇总查询的业务筛选条件。
     * <p>
     * 支持的筛选维度：渠道 ID、招募官 ID、活动 ID、商品 ID、合伙人 ID、达人 ID、订单状态。
     * 每个参数仅在非空时追加对应 AND 条件。
     *
     * @param where SQL WHERE 子句构建器
     * @param args  SQL 参数列表
     * @param query 查询参数
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
            where.append(" AND co.colonel_activity_id = ?");
            args.add(query.getActivityId().trim());
        }
        if (StringUtils.hasText(query.getProductId())) {
            where.append(" AND co.product_id = ?");
            args.add(query.getProductId().trim());
        }
        if (query.getPartnerId() != null) {
            where.append(" AND pr.partner_id = ?");
            args.add(query.getPartnerId());
        }
        if (query.getTalentId() != null) {
            where.append(" AND co.talent_id = ?");
            args.add(query.getTalentId());
        }
        if (StringUtils.hasText(query.getOrderStatus())) {
            where.append(" AND co.order_status = ?");
            args.add(toOrderStatusCode(query.getOrderStatus()));
        }
    }

    /**
     * 追加 cohort 时间区间筛选。
     * <p>
     * 处理流程：
     * <ol>
     *   <li>根据 timeFilterType 解析 cohort 时间列（settle → co.settle_time，其他 → COALESCE(order_create_time, create_time)）</li>
     *   <li>追加时间起止范围条件</li>
     *   <li>当使用 settle cohort 且指定了时间范围时，额外要求 settle_time IS NOT NULL</li>
     * </ol>
     *
     * @param where SQL WHERE 子句构建器
     * @param args  SQL 参数列表
     * @param query 查询参数
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
            where.append(" AND co.settle_time IS NOT NULL");
        }
    }

    /**
     * 根据时间筛选类型解析对应的 cohort 时间列名。
     *
     * @param timeFilterType 时间筛选类型："settle" 表示结算时间，其他值默认为下单时间
     * @return SQL 时间列表达式
     */
    private String resolveCohortColumn(String timeFilterType) {
        if ("settle".equalsIgnoreCase(timeFilterType)) {
            return "co.settle_time";
        }
        return "COALESCE(co.order_create_time, co.create_time)";
    }

    /**
     * 将数据库聚合结果行映射为 {@link PerformanceTrackSummaryDTO}。
     * <p>
     * 处理流程：
     * <ol>
     *   <li>从结果行提取各聚合字段（order_count、order_amount 等）</li>
     *   <li>按预估 / 结算轨分别反推服务费支出，结算轨不重复扣技术服务费</li>
     *   <li>封装为 DTO 返回</li>
     * </ol>
     *
     * @param row 数据库查询结果行（key-value 映射）
     * @return 轨道汇总指标 DTO
     */
    private PerformanceTrackSummaryDTO mapTrackSummary(Map<String, Object> row, boolean estimateTrack) {
        PerformanceTrackSummaryDTO track = new PerformanceTrackSummaryDTO();
        long serviceFeeIncome = asLong(row.get("service_fee_income"));
        long techServiceFee = asLong(row.get("tech_service_fee"));
        long serviceFeeExpense = asLong(row.get("service_fee_expense"));
        long serviceProfit = CommissionService.serviceFeeNetCent(
                serviceFeeIncome,
                techServiceFee,
                serviceFeeExpense,
                estimateTrack);
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
        track.setGrossProfit(Math.max(serviceProfit - recruiter - channel, 0L));
        return track;
    }

    /**
     * 安全地将数据库返回值转换为 long 类型。
     * <p>
     * 支持 Number 类型直接转换，null 值返回 0，其他类型尝试字符串解析后返回 0（解析失败时）。
     *
     * @param value 数据库返回的原始值
     * @return 转换后的 long 值，无法转换时返回 0
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
     * <p>
     * 映射关系：ORDERED→1, SHIPPED→2, FINISHED→3, CANCELLED→4，其他值返回 null。
     *
     * @param status 订单状态字符串（大小写不敏感）
     * @return 状态码，无法识别时返回 null
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
