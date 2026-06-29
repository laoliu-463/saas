package com.colonel.saas.service;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.order.facade.OrderReadFacade;
import com.colonel.saas.domain.user.policy.DataScopePolicy;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 仪表盘数据聚合服务。
 *
 * <p>职责：为前端仪表盘提供订单总览、渠道/招商员业绩排名、未归因原因分布、
 * 诊断分类统计、活动-商品维度下钻等多维聚合查询能力。
 *
 * <p>数据源策略（双通道）：
 * <ul>
 *   <li>当 {@link PerformanceMetricsQueryService#hasPerformanceRecords()} 为 true 时，
 *       优先从 performance_records 汇总表读取（更准确，含完整提成计算结果）</li>
 *   <li>否则回退到 colonelsettlement_order 原始订单表做实时聚合</li>
 * </ul>
 *
 * <p>诊断分类体系：针对未归因订单，通过 CASE 表达式 SQL 进行多维度诊断，
 * 将未归因订单分为 5 大类：机制命中但历史不可回填、上游商品未覆盖、
 * 无法自动归因、native key 不一致、ambiguous 多用户冲突。
 *
 * <p>数据范围过滤：支持 PERSONAL（个人）/ DEPT（部门）/ ALL（全局）三种数据范围，
 * 通过 {@link DataScope} 枚举控制查询范围。
 *
 * <p>依赖服务/仓储：
 * <ul>
 *   <li>{@link OrderReadFacade} —— 订单事实只读聚合</li>
 *   <li>{@link JdbcTemplate} —— 复杂诊断 SQL 和活动-商品下钻 SQL 执行</li>
 *   <li>{@link PerformanceMetricsQueryService} —— 业绩汇总表数据源</li>
 *   <li>{@link AttributionService} —— 归因状态常量</li>
 * </ul>
 */
@Service
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    /** 诊断分类：机制命中但历史订单创建时间晚于映射时间，无法安全回填 */
    public static final String DIAGNOSIS_MECHANISM_HIT_HISTORY_UNSAFE = "MECHANISM_HIT_HISTORY_UNSAFE";
    /** 诊断分类：上游活动商品列表未覆盖该订单商品 */
    public static final String DIAGNOSIS_UPSTREAM_PRODUCT_UNCOVERED = "UPSTREAM_PRODUCT_UNCOVERED";
    /** 诊断分类：无法通过任何规则自动归因 */
    public static final String DIAGNOSIS_CANNOT_AUTO_ATTRIBUTION = "CANNOT_AUTO_ATTRIBUTION";
    /** 诊断分类：pick_source_mapping 中的 native key 与订单不一致 */
    public static final String DIAGNOSIS_NATIVE_KEY_MISMATCH = "NATIVE_KEY_MISMATCH";
    /** 诊断分类：存在多用户冲突映射（ambiguous） */
    public static final String DIAGNOSIS_AMBIGUOUS_MAPPING = "AMBIGUOUS_MAPPING";

    /** 允许的诊断筛选分类集合（用于接口参数校验） */
    private static final Set<String> ALLOWED_DIAGNOSIS_FILTER_CATEGORIES = Set.of(
            DIAGNOSIS_MECHANISM_HIT_HISTORY_UNSAFE,
            DIAGNOSIS_UPSTREAM_PRODUCT_UNCOVERED,
            DIAGNOSIS_CANNOT_AUTO_ATTRIBUTION,
            DIAGNOSIS_NATIVE_KEY_MISMATCH,
            DIAGNOSIS_AMBIGUOUS_MAPPING,
            "ATTRIBUTED",
            "MISSING_ACTIVITY_ID",
            "MISSING_PRODUCT_ID"
    );

    /** 活动-商品下钻默认每页条数 */
    private static final int DEFAULT_BREAKDOWN_LIMIT = 20;

    /** 订单事实只读门面，用于 Dashboard 简单聚合和范围过滤 */
    private final OrderReadFacade orderReadFacade;
    /** 原始 JDBC 模板，用于执行复杂诊断 SQL 和活动-商品下钻 SQL */
    private final JdbcTemplate jdbcTemplate;
    /** 业绩指标查询服务，用于判断是否可用汇总表以及从汇总表读取聚合数据 */
    private final PerformanceMetricsQueryService performanceMetricsQueryService;
    /** 数据范围策略，由用户域统一解释 PERSONAL / DEPT / ALL 可见性范围 */
    private final DataScopePolicy dataScopePolicy;
    /** DDD 重构灰度开关，默认关闭时保持 Legacy 查询路径 */
    private final DddRefactorProperties dddRefactorProperties;

    /** 影子对账服务（可选），开关开启时在后台执行新路径对比并输出 diff 日志 */
    @Autowired(required = false)
    private DashboardShadowCompareService shadowCompareService;

    /**
     * 构造函数，通过依赖注入初始化服务。
     *
     * @param orderReadFacade            订单事实只读门面（简单聚合查询）
     * @param jdbcTemplate               JDBC 模板（复杂诊断 SQL 和活动-商品下钻 SQL）
     * @param performanceMetricsQueryService 业绩指标查询服务（判断汇总表可用性）
     */
    public DashboardService(
            OrderReadFacade orderReadFacade,
            JdbcTemplate jdbcTemplate,
            PerformanceMetricsQueryService performanceMetricsQueryService,
            DataScopePolicy dataScopePolicy,
            DddRefactorProperties dddRefactorProperties) {
        this.orderReadFacade = orderReadFacade;
        this.jdbcTemplate = jdbcTemplate;
        this.performanceMetricsQueryService = performanceMetricsQueryService;
        this.dataScopePolicy = dataScopePolicy;
        this.dddRefactorProperties = dddRefactorProperties;
    }

    /**
     * 获取仪表盘汇总数据。
     *
     * <p>聚合内容包括：
     * <ol>
     *   <li>订单总览：订单数、订单金额、服务费、已归因/未归因数量和归因率</li>
     *   <li>渠道员 TOP10 业绩排名（按订单数降序）</li>
     *   <li>招商员 TOP10 业绩排名（按订单数降序）</li>
     *   <li>未归因订单的原因分布统计</li>
     *   <li>未归因订单的诊断分类统计（5 大诊断类别）</li>
     *   <li>活动-商品维度下钻列表（第一页，默认 20 条）</li>
     * </ol>
     *
     * <p>数据源切换：若业绩汇总表已有数据（{@code hasPerformanceRecords=true}），
     * 从汇总表读取业绩排名数据，否则从原始订单表实时聚合。
     *
     * @param startTime  结算开始时间（可选，null 表示不限制）
     * @param endTime    结算结束时间（可选，null 表示不限制）
     * @param userId     当前用户ID（PERSONAL 范围时使用）
     * @param deptId     部门ID（DEPT 范围时使用）
     * @param dataScope  数据范围过滤（PERSONAL / DEPT / ALL）
     * @return 仪表盘汇总对象
     */
    public Summary getSummary(LocalDateTime startTime, LocalDateTime endTime, UUID userId, UUID deptId, DataScope dataScope) {
        boolean usePerformanceRecords = performanceMetricsQueryService.hasPerformanceRecords();
        OrderReadFacade.OrderVisibility orderVisibility = buildOrderVisibility(userId, deptId, dataScope);
        OrderReadFacade.DashboardAttributionSummary attributionSummary =
                orderReadFacade.getDashboardAttributionSummary(startTime, endTime, orderVisibility);
        Long attributedCount = attributionSummary.attributedOrderCount();
        Long unattributedCount = attributionSummary.unattributedOrderCount();

        List<PerformanceItem> channelPerformance;
        List<PerformanceItem> colonelPerformance;
        long orderCount;
        long orderAmount;
        long serviceFee;
        long settledOrderCount = 0L;
        PerformanceMetricsQueryService.DashboardPerformanceSummary performanceSummary = null;
        if (usePerformanceRecords) {
            performanceSummary = performanceMetricsQueryService.aggregateDashboardSummary(startTime, endTime, userId, deptId, dataScope);
            orderCount = performanceSummary.orderCount();
            orderAmount = performanceSummary.orderAmountCent();
            serviceFee = performanceSummary.serviceFeeCent();
            settledOrderCount = orderCount;
            channelPerformance = toPerformanceItems(performanceSummary.channelPerformance(), true);
            colonelPerformance = toPerformanceItems(performanceSummary.colonelPerformance(), false);
        } else {
            OrderReadFacade.DashboardFallbackSummary fallbackSummary =
                    orderReadFacade.getDashboardFallbackSummary(startTime, endTime, orderVisibility);
            orderCount = fallbackSummary.orderCount();
            orderAmount = fallbackSummary.orderAmountCent();
            serviceFee = fallbackSummary.serviceFeeCent();
            channelPerformance = toDashboardPerformanceItems(fallbackSummary.channelPerformance(), true);
            colonelPerformance = toDashboardPerformanceItems(fallbackSummary.colonelPerformance(), false);
        }

        List<ReasonCountItem> unattributedReasons = attributionSummary.unattributedReasons().stream()
                .map(this::toReasonCountItem)
                .toList();

        List<DiagnosticItem> diagnostics = loadDiagnostics(startTime, endTime, userId, deptId, dataScope);
        List<ActivityProductItem> activityProductBreakdown = loadActivityProductBreakdown(
                startTime,
                endTime,
                userId,
                deptId,
                dataScope,
                1,
                DEFAULT_BREAKDOWN_LIMIT
        ).records();

        Summary summary = new Summary();
        summary.setOrderCount(orderCount);
        summary.setOrderAmount(orderAmount);
        summary.setServiceFee(serviceFee);
        summary.setAttributedOrderCount(attributedCount);
        summary.setUnattributedOrderCount(unattributedCount);
        summary.setAttributionRate(orderCount == 0
                ? 0D
                : attributedCount.doubleValue() / (double) orderCount);
        summary.setChannelPerformance(channelPerformance);
        summary.setColonelPerformance(colonelPerformance);
        summary.setUnattributedReasons(unattributedReasons);
        summary.setDiagnosticBreakdown(diagnostics);
        summary.setUnsafeBecauseCreatedAfterOrderCount(findDiagnosticCount(diagnostics, DIAGNOSIS_MECHANISM_HIT_HISTORY_UNSAFE));
        summary.setUpstreamProductUncoveredCount(findDiagnosticCount(diagnostics, DIAGNOSIS_UPSTREAM_PRODUCT_UNCOVERED));
        summary.setCannotAutoAttributionCount(findDiagnosticCount(diagnostics, DIAGNOSIS_CANNOT_AUTO_ATTRIBUTION));
        summary.setNativeKeyMismatchCount(findDiagnosticCount(diagnostics, DIAGNOSIS_NATIVE_KEY_MISMATCH));
        summary.setAmbiguousMappingCount(findDiagnosticCount(diagnostics, DIAGNOSIS_AMBIGUOUS_MAPPING));
        summary.setActivityProductBreakdown(activityProductBreakdown);

        summary.setSettledOrderCount(settledOrderCount);
        summary.setSnapshotAt(LocalDateTime.now());
        summary.setSettlementReason(deriveSettlementReason(settledOrderCount, orderCount));
        summary.setSettleTimeRange(formatRange(startTime, endTime));

        if (usePerformanceRecords && orderCount > 0 && settledOrderCount == 0L) {
            log.warn(
                    "Dashboard summary has zero settled orders in range: orderCount={}, settledOrderCount={}, range={}",
                    orderCount, settledOrderCount, formatRange(startTime, endTime));
        }

        // DDD-ANALYTICS-002: shadow compare (不影响响应)
        if (shadowCompareService != null && shadowCompareService.isEnabled()) {
            shadowCompareService.compare(summary, startTime, endTime, userId, deptId, dataScope);
        }

        return summary;
    }

    /**
     * 分页查询活动-商品维度下钻数据。
     * 返回每个活动-商品组合的订单数、金额、未归因订单数、映射数、推广链接数等详情。
     *
     * @param startTime  结算开始时间（可选）
     * @param endTime    结算结束时间（可选）
     * @param userId     当前用户ID
     * @param deptId     部门ID
     * @param dataScope  数据范围过滤
     * @param page       页码
     * @param size       每页大小
     * @return 分页的活动-商品下钻结果
     */
    public ActivityProductPage getActivityProductBreakdown(
            LocalDateTime startTime,
            LocalDateTime endTime,
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            long page,
            long size) {
        return loadActivityProductBreakdown(startTime, endTime, userId, deptId, dataScope, page, size);
    }

    /**
     * 获取仪表盘汇总数据（无筛选条件版本）。
     * 使用全量数据、无时间范围和数据范围限制。
     *
     * @return 仪表盘汇总对象
     */
    public Summary getSummary() {
        return getSummary(null, null, null, null, null);
    }

    /**
     * 加载诊断分类统计。
     * 通过 CTE + CASE 表达式 SQL 对未归因订单进行多维度诊断分类，
     * 统计每个诊断类别的订单数量，按数量降序排列。
     *
     * @param startTime  结算开始时间
     * @param endTime    结算结束时间
     * @param userId     当前用户ID
     * @param deptId     部门ID
     * @param dataScope  数据范围过滤
     * @return 诊断分类统计列表（按数量降序）
     */
    private List<DiagnosticItem> loadDiagnostics(
            LocalDateTime startTime,
            LocalDateTime endTime,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        SqlContext context = buildFilteredOrdersContext(startTime, endTime, userId, deptId, dataScope);
        String sql = diagnosticSql(context.whereClause());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, context.args().toArray());
        return rows.stream()
                .map(this::toDiagnosticItem)
                .sorted(Comparator.comparingLong(DiagnosticItem::getCount).reversed())
                .toList();
    }

    /**
     * 加载活动-商品维度下钻分页数据。
     * 先统计活动-商品组合总数，再按分页参数查询具体数据。
     * 每条记录包含订单数、订单金额、未归因订单数、映射数、推广链接数、
     * 商品名称/封面、业务状态、负责人等详细信息。
     *
     * @param startTime  结算开始时间
     * @param endTime    结算结束时间
     * @param userId     当前用户ID
     * @param deptId     部门ID
     * @param dataScope  数据范围过滤
     * @param page       页码
     * @param size       每页大小
     * @return 分页的活动-商品下钻结果
     */
    private ActivityProductPage loadActivityProductBreakdown(
            LocalDateTime startTime,
            LocalDateTime endTime,
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            long page,
            long size) {
        SqlContext context = buildFilteredOrdersContext(startTime, endTime, userId, deptId, dataScope);
        long safePage = Math.max(page, 1L);
        long safeSize = Math.max(size, 1L);
        long offset = (safePage - 1) * safeSize;

        String countSql = activityProductCountSql(context.whereClause());
        Map<String, Object> totalRow = jdbcTemplate.queryForList(countSql, context.args().toArray()).stream()
                .findFirst()
                .orElse(Map.of("total_count", 0L));
        long total = asLong(totalRow.get("total_count"));

        List<Object> breakdownArgs = new ArrayList<>(context.args());
        breakdownArgs.add(safeSize);
        breakdownArgs.add(offset);
        String breakdownSql = activityProductBreakdownSql(context.whereClause());
        List<ActivityProductItem> records = jdbcTemplate.queryForList(breakdownSql, breakdownArgs.toArray()).stream()
                .map(this::toActivityProductItem)
                .toList();
        return new ActivityProductPage(total, safePage, safeSize, records);
    }

    /**
     * 构建带过滤条件的 SQL 上下文。
     * 根据时间范围和数据范围生成 WHERE 子句及参数列表，供后续诊断 SQL 和下钻 SQL 复用。
     * 基础条件为 {@code co.deleted = 0}，其余条件按需追加。
     *
     * @param startTime  结算开始时间（可选）
     * @param endTime    结算结束时间（可选）
     * @param userId     当前用户ID（PERSONAL 范围时使用）
     * @param deptId     部门ID（DEPT 范围时使用）
     * @param dataScope  数据范围过滤
     * @return SQL 上下文，包含 WHERE 子句字符串和绑定参数列表
     */
    private SqlContext buildFilteredOrdersContext(
            LocalDateTime startTime,
            LocalDateTime endTime,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        List<String> clauses = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        clauses.add("co.deleted = 0");
        if (startTime != null) {
            clauses.add("co.settle_time >= ?");
            args.add(startTime);
        }
        if (endTime != null) {
            clauses.add("co.settle_time <= ?");
            args.add(endTime);
        }
        appendScopeClause(clauses, args, userId, deptId, dataScope);
        return new SqlContext(String.join(" AND ", clauses), args);
    }

    private void appendScopeClause(
            List<String> clauses,
            List<Object> args,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        if (dataScope == null) {
            return;
        }
        if (!dddRefactorProperties.getDataScopePolicy().isEnabled()) {
            appendScopeClauseLegacy(clauses, args, userId, deptId, dataScope);
            return;
        }
        appendScopeClauseWithPolicy(clauses, args, userId, deptId, dataScope);
    }

    private void appendScopeClauseLegacy(
            List<String> clauses,
            List<Object> args,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        if (dataScope == DataScope.PERSONAL && userId != null) {
            clauses.add("co.user_id = ?");
            args.add(userId);
            return;
        }
        if (dataScope == DataScope.DEPT && deptId != null) {
            clauses.add("co.dept_id = ?");
            args.add(deptId);
        }
    }

    private void appendScopeClauseWithPolicy(
            List<String> clauses,
            List<Object> args,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        DataScopePolicy.Decision decision = dataScopePolicy.decide(userId, deptId, dataScope);
        switch (decision) {
            case FILTER_USER -> {
                clauses.add("co.user_id = ?");
                args.add(userId);
            }
            case FILTER_DEPT -> {
                clauses.add("co.dept_id = ?");
                args.add(deptId);
            }
            case NO_FILTER -> {
                // no filter
            }
        }
    }

    /**
     * 构建诊断分类统计 SQL。
     * 使用 CTE 结构：先过滤出未归因订单，再通过 CASE 表达式进行诊断分类，
     * 最后按类别分组统计数量。仅返回 5 种主要诊断类别。
     *
     * @param whereClause WHERE 子句（由 buildFilteredOrdersContext 生成）
     * @return 完整的诊断统计 SQL
     */
    private String diagnosticSql(String whereClause) {
        return """
                WITH filtered_orders AS (
                    SELECT co.order_id,
                           co.product_id,
                           co.colonel_activity_id AS activity_id,
                           co.second_colonel_activity_id,
                           co.colonel_buyin_id,
                           co.attribution_status,
                           co.attribution_remark,
                           co.create_time
                    FROM colonelsettlement_order co
                    WHERE %s
                ),
                diagnosed AS (
                    SELECT fo.order_id,
                           %s AS diagnostic_category
                    FROM filtered_orders fo
                    WHERE COALESCE(fo.attribution_status, 'UNATTRIBUTED') <> 'ATTRIBUTED'
                )
                SELECT diagnostic_category AS category, COUNT(*) AS total_count
                FROM diagnosed
                WHERE diagnostic_category IN ('%s', '%s', '%s', '%s', '%s')
                GROUP BY diagnostic_category
                """.formatted(
                whereClause,
                diagnosisCategoryCaseSql("fo."),
                DIAGNOSIS_MECHANISM_HIT_HISTORY_UNSAFE,
                DIAGNOSIS_UPSTREAM_PRODUCT_UNCOVERED,
                DIAGNOSIS_CANNOT_AUTO_ATTRIBUTION,
                DIAGNOSIS_NATIVE_KEY_MISMATCH,
                DIAGNOSIS_AMBIGUOUS_MAPPING
        );
    }

    /**
     * 标准化诊断分类名称。
     * 将旧的诊断标签映射到新的标准分类常量，并校验是否在允许的分类集合中。
     * 例如 "UNSAFE_BECAUSE_CREATED_AFTER_ORDER" 会被映射为 "MECHANISM_HIT_HISTORY_UNSAFE"。
     *
     * @param diagnosis 原始诊断分类名称
     * @return 标准化后的分类名称；不在允许集合中或为空时返回 null
     */
    public static String normalizeDiagnosisCategory(String diagnosis) {
        if (!StringUtils.hasText(diagnosis)) {
            return null;
        }
        String trimmed = diagnosis.trim();
        String normalized = switch (trimmed) {
            case "UNSAFE_BECAUSE_CREATED_AFTER_ORDER" -> DIAGNOSIS_MECHANISM_HIT_HISTORY_UNSAFE;
            default -> trimmed;
        };
        return ALLOWED_DIAGNOSIS_FILTER_CATEGORIES.contains(normalized) ? normalized : null;
    }

    /**
     * 构建诊断分类 CASE 表达式 SQL（带列名前缀版本）。
     * 自动为所有列名添加表别名前缀，用于 JOIN 场景下避免列名歧义。
     *
     * @param prefix 列名前缀（如 "fo."、"co." 等）
     * @return 完整的 CASE 表达式 SQL 片段
     */
    public static String diagnosisCategoryCaseSql(String prefix) {
        String safePrefix = prefix == null ? "" : prefix;
        return diagnosisCategoryCaseSql(
                safePrefix + "activity_id",
                safePrefix + "second_colonel_activity_id",
                safePrefix + "product_id",
                safePrefix + "create_time",
                safePrefix + "colonel_buyin_id",
                safePrefix + "attribution_status",
                safePrefix + "attribution_remark"
        );
    }

    /**
     * 构建诊断分类 CASE 表达式 SQL（完整参数版本）。
     *
     * <p>诊断逻辑按优先级依次判定：
     * <ol>
     *   <li>已归因（ATTRIBUTED）-> 直接标记</li>
     *   <li>归因备注为 COLONEL_MAPPING_AMBIGUOUS -> AMBIGUOUS_MAPPING</li>
     *   <li>活动ID和二团长活动ID均为空 -> MISSING_ACTIVITY_ID</li>
     *   <li>商品ID为空 -> MISSING_PRODUCT_ID</li>
     *   <li>商品快照表和运营状态表均无记录 -> UPSTREAM_PRODUCT_UNCOVERED</li>
     *   <li>活动-商品组合有多条映射 -> AMBIGUOUS_MAPPING</li>
     *   <li>映射创建时间晚于订单时间 -> MECHANISM_HIT_HISTORY_UNSAFE</li>
     *   <li>精确映射为 0 但有其他 buyin 的映射 -> NATIVE_KEY_MISMATCH</li>
     *   <li>以上均不满足 -> CANNOT_AUTO_ATTRIBUTION</li>
     * </ol>
     *
     * @param activity     活动ID列名
     * @param secondActivity 二团长活动ID列名
     * @param product      商品ID列名
     * @param createTime   订单创建时间列名
     * @param buyin        团长 buyin ID 列名
     * @param attrStatus   归因状态列名
     * @param attrRemark   归因备注列名
     * @return 完整的 CASE 表达式 SQL 片段
     */
    public static String diagnosisCategoryCaseSql(
            String activity,
            String secondActivity,
            String product,
            String createTime,
            String buyin,
            String attrStatus,
            String attrRemark) {
        String exactNativeCount = """
                (
                    SELECT COUNT(*)
                    FROM pick_source_mapping psm
                    WHERE psm.deleted = 0
                      AND psm.status = 1
                      AND psm.source_type = 'NATIVE'
                      AND psm.activity_id = %s
                      AND psm.product_id = %s
                      AND psm.colonel_buyin_id = CAST(%s AS varchar)
                )
                """.formatted(activity, product, buyin);
        String activityProductCount = """
                (
                    SELECT COUNT(*)
                    FROM pick_source_mapping psm
                    WHERE psm.deleted = 0
                      AND psm.status = 1
                      AND psm.source_type = 'NATIVE'
                      AND psm.activity_id = %s
                      AND psm.product_id = %s
                )
                """.formatted(activity, product);
        return """
                CASE
                    WHEN COALESCE(%s, 'UNATTRIBUTED') = 'ATTRIBUTED' THEN 'ATTRIBUTED'
                    WHEN %s = 'COLONEL_MAPPING_AMBIGUOUS' THEN '%s'
                    WHEN (%s IS NULL OR BTRIM(%s) = '')
                         AND (%s IS NULL OR BTRIM(%s) = '') THEN 'MISSING_ACTIVITY_ID'
                    WHEN %s IS NULL OR BTRIM(%s) = '' THEN 'MISSING_PRODUCT_ID'
                    WHEN NOT EXISTS (
                         SELECT 1
                         FROM product_snapshot ps
                         WHERE ps.deleted = 0
                           AND ps.activity_id = %s
                           AND ps.product_id = %s
                    ) AND NOT EXISTS (
                         SELECT 1
                         FROM product_operation_state pos
                         WHERE pos.deleted = 0
                           AND pos.activity_id = %s
                           AND pos.product_id = %s
                    ) THEN '%s'
                    WHEN %s > 1 THEN '%s'
                    WHEN %s = 1
                    AND EXISTS (
                         SELECT 1
                         FROM pick_source_mapping psm
                         WHERE psm.deleted = 0
                           AND psm.status = 1
                           AND psm.source_type = 'NATIVE'
                           AND psm.activity_id = %s
                           AND psm.product_id = %s
                           AND psm.colonel_buyin_id = CAST(%s AS varchar)
                           AND psm.create_time > %s
                    ) THEN '%s'
                    WHEN %s = 0
                    AND %s = 1
                    AND EXISTS (
                         SELECT 1
                         FROM pick_source_mapping psm
                         WHERE psm.deleted = 0
                           AND psm.status = 1
                           AND psm.source_type = 'NATIVE'
                           AND psm.activity_id = %s
                           AND psm.product_id = %s
                           AND psm.colonel_buyin_id <> CAST(%s AS varchar)
                    ) THEN '%s'
                    ELSE '%s'
                END
                """.formatted(
                attrStatus,
                attrRemark, DIAGNOSIS_AMBIGUOUS_MAPPING,
                activity, activity, secondActivity, secondActivity,
                product, product,
                activity, product, activity, product, DIAGNOSIS_UPSTREAM_PRODUCT_UNCOVERED,
                activityProductCount, DIAGNOSIS_AMBIGUOUS_MAPPING,
                exactNativeCount, activity, product, buyin, createTime, DIAGNOSIS_MECHANISM_HIT_HISTORY_UNSAFE,
                exactNativeCount, activityProductCount, activity, product, buyin, DIAGNOSIS_NATIVE_KEY_MISMATCH,
                DIAGNOSIS_CANNOT_AUTO_ATTRIBUTION
        );
    }

    /**
     * 构建活动-商品组合总数统计 SQL。
     * 过滤条件：未删除、活动ID和商品ID均非空。
     *
     * @param whereClause WHERE 子句
     * @return 总数统计 SQL
     */
    private String activityProductCountSql(String whereClause) {
        return """
                SELECT COUNT(*) AS total_count
                FROM (
                    SELECT co.colonel_activity_id, co.product_id
                    FROM colonelsettlement_order co
                    WHERE %s
                      AND co.colonel_activity_id IS NOT NULL
                      AND BTRIM(co.colonel_activity_id) <> ''
                      AND co.product_id IS NOT NULL
                      AND BTRIM(co.product_id) <> ''
                    GROUP BY co.colonel_activity_id, co.product_id
                ) grouped
                """.formatted(whereClause);
    }

    /**
     * 构建活动-商品维度下钻分页 SQL。
     * 关联 product_snapshot（商品名称/封面）、product_operation_state（业务状态/负责人）等表，
     * 聚合订单数、金额、未归因订单数、映射数和推广链接数等指标。
     * 按订单数降序、金额降序排列，支持分页。
     *
     * @param whereClause WHERE 子句
     * @return 下钻分页 SQL（末尾含 LIMIT ? OFFSET ?）
     */
    private String activityProductBreakdownSql(String whereClause) {
        return """
                SELECT co.colonel_activity_id AS activity_id,
                       co.product_id,
                       COALESCE(MAX(NULLIF(ps.title, '')), MAX(NULLIF(co.product_name, '')), MAX(NULLIF(co.product_title, '')), co.product_id) AS product_name,
                       MAX(ps.cover) AS product_cover,
                       MAX(pos.biz_status) AS biz_status,
                       MAX(su.real_name) AS assignee_name,
                       COUNT(*) AS order_count,
                       COALESCE(SUM(co.order_amount), 0) AS order_amount,
                       COALESCE(SUM(CASE WHEN COALESCE(co.attribution_status, 'UNATTRIBUTED') = 'UNATTRIBUTED' THEN 1 ELSE 0 END), 0) AS unattributed_order_count,
                       COALESCE((
                           SELECT COUNT(DISTINCT psm.id)
                           FROM pick_source_mapping psm
                           WHERE psm.deleted = 0
                             AND psm.status = 1
                             AND psm.activity_id = co.colonel_activity_id
                             AND psm.product_id = co.product_id
                       ), 0) AS mapping_count,
                       COALESCE((
                           SELECT COUNT(DISTINCT pl.id)
                           FROM promotion_link pl
                           WHERE pl.deleted = 0
                             AND pl.activity_id = co.colonel_activity_id
                             AND pl.product_id = co.product_id
                       ), 0) AS promotion_link_count
                FROM colonelsettlement_order co
                LEFT JOIN product_snapshot ps
                  ON ps.deleted = 0
                 AND ps.activity_id = co.colonel_activity_id
                 AND ps.product_id = co.product_id
                LEFT JOIN product_operation_state pos
                  ON pos.deleted = 0
                 AND pos.activity_id = co.colonel_activity_id
                 AND pos.product_id = co.product_id
                LEFT JOIN sys_user su
                  ON su.id = pos.assignee_id
                 AND su.deleted = 0
                WHERE %s
                  AND co.colonel_activity_id IS NOT NULL
                  AND BTRIM(co.colonel_activity_id) <> ''
                  AND co.product_id IS NOT NULL
                  AND BTRIM(co.product_id) <> ''
                GROUP BY co.colonel_activity_id, co.product_id
                ORDER BY COUNT(*) DESC, COALESCE(SUM(co.order_amount), 0) DESC, co.colonel_activity_id, co.product_id
                LIMIT ? OFFSET ?
                """.formatted(whereClause);
    }

    /**
     * 将业绩汇总服务返回的排行榜列表转换为 PerformanceItem 列表。
     * 根据 channel 参数决定映射渠道员或招商员字段。
     *
     * @param items    业绩排行榜项列表
     * @param channel  true 为渠道员排行，false 为招商员排行
     * @return 业绩排名项列表
     */
    private List<PerformanceItem> toPerformanceItems(
            List<PerformanceMetricsQueryService.PerformanceLeaderboardItem> items,
            boolean channel) {
        return items.stream()
                .map(item -> {
                    PerformanceItem performanceItem = new PerformanceItem();
                    if (channel) {
                        performanceItem.setChannelUserId(item.userId());
                        performanceItem.setChannelUserName(item.userName());
                    } else {
                        performanceItem.setColonelUserId(item.userId());
                        performanceItem.setColonelUserName(item.userName());
                    }
                    performanceItem.setOrderCount(item.orderCount());
                    performanceItem.setOrderAmount(item.orderAmountCent());
                    performanceItem.setServiceFee(item.serviceFeeCent());
                    return performanceItem;
                })
                .toList();
    }

    private List<PerformanceItem> toDashboardPerformanceItems(
            List<OrderReadFacade.DashboardPerformanceItem> items,
            boolean channel) {
        return items.stream()
                .map(item -> {
                    PerformanceItem performanceItem = new PerformanceItem();
                    if (channel) {
                        performanceItem.setChannelUserId(item.userId());
                        performanceItem.setChannelUserName(item.userName());
                    } else {
                        performanceItem.setColonelUserId(item.userId());
                        performanceItem.setColonelUserName(item.userName());
                    }
                    performanceItem.setOrderCount(item.orderCount());
                    performanceItem.setOrderAmount(item.orderAmountCent());
                    performanceItem.setServiceFee(item.serviceFeeCent());
                    return performanceItem;
                })
                .toList();
    }

    /**
     * 将未归因原因聚合查询结果 Map 转换为 ReasonCountItem。
     * 同时生成对应的下钻查询对象，方便前端跳转到具体未归因订单列表。
     *
     * @param reasonCount 订单域返回的未归因原因计数
     * @return 未归因原因统计项（含下钻查询）
     */
    private ReasonCountItem toReasonCountItem(OrderReadFacade.DashboardReasonCount reasonCount) {
        ReasonCountItem item = new ReasonCountItem();
        item.setReason(reasonCount.reason());
        item.setCount(reasonCount.count());
        item.setDrillDownQuery(new DrillDownQuery(null, null, AttributionService.STATUS_UNATTRIBUTED, item.getReason(), null, "settleTime"));
        return item;
    }

    /**
     * 将诊断分类统计查询结果 Map 转换为 DiagnosticItem。
     * 通过 diagnosticLabel 方法将分类代码转换为中文标签。
     *
     * @param map 查询结果行
     * @return 诊断分类统计项（含下钻查询和中文标签）
     */
    private DiagnosticItem toDiagnosticItem(Map<String, Object> map) {
        DiagnosticItem item = new DiagnosticItem();
        item.setCategory(asString(readMapValue(map, "category")));
        item.setLabel(diagnosticLabel(item.getCategory()));
        item.setCount(asLong(readMapValue(map, "total_count")));
        item.setDrillDownQuery(new DrillDownQuery(null, null, null, null, item.getCategory(), "settleTime"));
        return item;
    }

    /**
     * 将活动-商品下钻查询结果 Map 转换为 ActivityProductItem。
     * 映射字段包括：活动ID、商品ID、商品名称/封面、业务状态、负责人、
     * 订单数、金额、未归因订单数、映射数、推广链接数，并生成下钻查询。
     *
     * @param map 查询结果行
     * @return 活动-商品下钻项（含下钻查询）
     */
    private ActivityProductItem toActivityProductItem(Map<String, Object> map) {
        ActivityProductItem item = new ActivityProductItem();
        item.setActivityId(asString(readMapValue(map, "activity_id")));
        item.setProductId(asString(readMapValue(map, "product_id")));
        item.setProductName(asString(readMapValue(map, "product_name")));
        item.setProductCover(asString(readMapValue(map, "product_cover")));
        item.setBizStatus(asString(readMapValue(map, "biz_status")));
        item.setAssigneeName(asString(readMapValue(map, "assignee_name")));
        item.setOrderCount(asLong(readMapValue(map, "order_count")));
        item.setOrderAmount(asLong(readMapValue(map, "order_amount")));
        item.setUnattributedOrderCount(asLong(readMapValue(map, "unattributed_order_count")));
        item.setMappingCount(asLong(readMapValue(map, "mapping_count")));
        item.setPromotionLinkCount(asLong(readMapValue(map, "promotion_link_count")));
        item.setDrillDownQuery(new DrillDownQuery(item.getActivityId(), item.getProductId(), null, null, null, "settleTime"));
        return item;
    }

    /**
     * 从诊断列表中查找指定分类的数量。
     *
     * @param diagnostics 诊断分类统计列表
     * @param category    目标分类代码
     * @return 匹配分类的数量；未找到时返回 0
     */
    private long findDiagnosticCount(List<DiagnosticItem> diagnostics, String category) {
        return diagnostics.stream()
                .filter(item -> category.equals(item.getCategory()))
                .map(DiagnosticItem::getCount)
                .findFirst()
                .orElse(0L);
    }

    /**
     * 将诊断分类代码转换为中文标签。
     *
     * @param category 诊断分类代码
     * @return 中文标签；未知分类返回原始代码
     */
    private String diagnosticLabel(String category) {
        return switch (category) {
            case DIAGNOSIS_MECHANISM_HIT_HISTORY_UNSAFE -> "机制命中但历史不可回填";
            case DIAGNOSIS_UPSTREAM_PRODUCT_UNCOVERED -> "上游活动商品列表未覆盖";
            case DIAGNOSIS_CANNOT_AUTO_ATTRIBUTION -> "无法自动归因";
            case DIAGNOSIS_NATIVE_KEY_MISMATCH -> "native key 不一致";
            case DIAGNOSIS_AMBIGUOUS_MAPPING -> "ambiguous 多用户冲突";
            default -> category;
        };
    }

    private OrderReadFacade.OrderVisibility buildOrderVisibility(UUID userId, UUID deptId, DataScope dataScope) {
        if (dataScope == null) {
            return OrderReadFacade.OrderVisibility.all();
        }
        if (!dddRefactorProperties.getDataScopePolicy().isEnabled()) {
            return buildOrderVisibilityLegacy(userId, deptId, dataScope);
        }
        DataScopePolicy.Decision decision = dataScopePolicy.decide(userId, deptId, dataScope);
        return switch (decision) {
            case FILTER_USER -> OrderReadFacade.OrderVisibility.user(userId);
            case FILTER_DEPT -> OrderReadFacade.OrderVisibility.dept(deptId);
            case NO_FILTER -> dataScopePolicy.requiresFilter(dataScope)
                    ? OrderReadFacade.OrderVisibility.none()
                    : OrderReadFacade.OrderVisibility.all();
        };
    }

    private OrderReadFacade.OrderVisibility buildOrderVisibilityLegacy(UUID userId, UUID deptId, DataScope dataScope) {
        if (dataScope == DataScope.PERSONAL && userId != null) {
            return OrderReadFacade.OrderVisibility.user(userId);
        }
        if (dataScope == DataScope.DEPT && deptId != null) {
            return OrderReadFacade.OrderVisibility.dept(deptId);
        }
        if (requiresRestrictedContext(dataScope)) {
            return OrderReadFacade.OrderVisibility.none();
        }
        return OrderReadFacade.OrderVisibility.all();
    }

    private boolean requiresRestrictedContext(DataScope dataScope) {
        return dataScope == DataScope.PERSONAL || dataScope == DataScope.DEPT;
    }

    /**
     * 安全读取 Map 中的值（兼容大小写 key）。
     * 先尝试精确匹配 key，未命中时尝试小写 key，兼容不同数据库驱动返回的列名大小写差异。
     *
     * @param map 数据行
     * @param key 列名
     * @return 对应值；map 或 key 为 null 时返回 null
     */
    private Object readMapValue(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        if (map.containsKey(key)) {
            return map.get(key);
        }
        return map.get(key.toLowerCase());
    }

    /**
     * 安全地将 Object 转换为 long。
     * 支持 Number 类型转换，null 或其他类型返回 0。
     *
     * @param val 待转换对象
     * @return long 值；null 或非 Number 类型时返回 0
     */
    private long asLong(Object val) {
        if (val == null) {
            return 0L;
        }
        if (val instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    /**
     * 安全地将 Object 转换为 String。
     *
     * @param val 待转换对象
     * @return 字符串值；null 时返回 null
     */
    private String asString(Object val) {
        return val == null ? null : String.valueOf(val);
    }

    /**
     * SQL 上下文，封装 WHERE 子句和绑定参数。
     * 用于在构建过滤条件后复用于多种不同类型的查询（诊断 SQL、下钻 SQL 等）。
     *
     * @param whereClause WHERE 子句字符串（不含 WHERE 关键字）
     * @param args        绑定参数列表（与 ? 占位符一一对应）
     */
    private record SqlContext(String whereClause, List<Object> args) {
    }

    /**
     * 活动-商品下钻分页结果。
     *
     * @param total   活动-商品组合总数
     * @param page    当前页码
     * @param size    每页大小
     * @param records 当前页数据列表
     */
    public record ActivityProductPage(long total, long page, long size, List<ActivityProductItem> records) {
    }

    /**
     * 仪表盘汇总数据。
     * 包含订单总览指标、业绩排名、未归因原因分布、诊断分类统计和活动-商品下钻列表。
     */
    @Data
    public static class Summary {
        /** 订单总数 */
        private Long orderCount;
        /** 订单金额（单位：分） */
        private Long orderAmount;
        /** 服务费/佣金（单位：分） */
        private Long serviceFee;
        /** 已归因订单数 */
        private Long attributedOrderCount;
        /** 未归因订单数 */
        private Long unattributedOrderCount;
        /** 归因率（0.0 ~ 1.0） */
        private Double attributionRate;
        /** 诊断分类：机制命中但历史订单创建时间晚于映射时间 */
        private Long unsafeBecauseCreatedAfterOrderCount;
        /** 诊断分类：上游活动商品未覆盖 */
        private Long upstreamProductUncoveredCount;
        /** 诊断分类：无法自动归因 */
        private Long cannotAutoAttributionCount;
        /** 诊断分类：native key 不一致 */
        private Long nativeKeyMismatchCount;
        /** 诊断分类：多用户冲突映射 */
        private Long ambiguousMappingCount;
        /** 渠道员 TOP10 业绩排名 */
        private List<PerformanceItem> channelPerformance;
        /** 招商员 TOP10 业绩排名 */
        private List<PerformanceItem> colonelPerformance;
        /** 未归因订单原因分布 */
        private List<ReasonCountItem> unattributedReasons;
        /** 未归因订单诊断分类统计 */
        private List<DiagnosticItem> diagnosticBreakdown;
        /** 活动-商品维度下钻列表 */
        private List<ActivityProductItem> activityProductBreakdown;
        /** 已结算订单数（performance_records.settle_time IS NOT NULL 的订单数；空时为 0） */
        private Long settledOrderCount;
        /** 指标快照生成时间（服务端 LocalDateTime.now()） */
        private LocalDateTime snapshotAt;
        /** 结算原因说明（基于已结算订单数 / 总订单数 比例给出业务侧可读解释） */
        private String settlementReason;
        /** 结算时间范围（与传入 startTime/endTime 同步，用于前端展示口径） */
        private String settleTimeRange;
    }

    /**
     * 业绩排名项。
     * 用于渠道员和招商员的业绩排行展示，包含订单数、订单金额和服务费。
     */
    @Data
    public static class PerformanceItem {
        /** 渠道员用户 ID（渠道员排名时非空） */
        private String channelUserId;
        /** 渠道员姓名 */
        private String channelUserName;
        /** 招商员用户 ID（招商员排名时非空） */
        private String colonelUserId;
        /** 招商员姓名 */
        private String colonelUserName;
        /** 订单数 */
        private Long orderCount;
        /** 订单金额（单位：分） */
        private Long orderAmount;
        /** 服务费/佣金（单位：分） */
        private Long serviceFee;
    }

    /**
     * 未归因原因统计项。
     * 包含归因失败原因、订单数量及对应的下钻查询（用于跳转到具体未归因订单列表）。
     */
    @Data
    public static class ReasonCountItem {
        /** 未归因原因标识 */
        private String reason;
        /** 匹配该原因的订单数 */
        private Long count;
        /** 下钻查询参数（用于跳转到具体未归因订单列表） */
        private DrillDownQuery drillDownQuery;
    }

    /**
     * 诊断分类统计项。
     * 包含诊断分类代码、中文标签、订单数量及对应的下钻查询。
     */
    @Data
    public static class DiagnosticItem {
        /** 诊断分类代码（如 MECHANISM_HIT_HISTORY_UNSAFE） */
        private String category;
        /** 诊断分类中文标签 */
        private String label;
        /** 匹配该分类的订单数 */
        private Long count;
        /** 下钻查询参数 */
        private DrillDownQuery drillDownQuery;
    }

    /**
     * 活动-商品维度下钻项。
     * 包含商品基本信息（名称、封面、业务状态、负责人）、订单聚合指标（订单数、金额、
     * 未归因数）、映射和推广链接数量，以及对应的下钻查询。
     */
    @Data
    public static class ActivityProductItem {
        /** 活动 ID */
        private String activityId;
        /** 商品 ID */
        private String productId;
        /** 商品名称 */
        private String productName;
        /** 商品封面图 URL */
        private String productCover;
        /** 商品业务状态（如"在售""已下架"） */
        private String bizStatus;
        /** 负责人姓名 */
        private String assigneeName;
        /** 订单数 */
        private Long orderCount;
        /** 订单金额（单位：分） */
        private Long orderAmount;
        /** 未归因订单数 */
        private Long unattributedOrderCount;
        /** pick_source_mapping 映射记录数 */
        private Long mappingCount;
        /** 推广链接数 */
        private Long promotionLinkCount;
        /** 下钻查询参数 */
        private DrillDownQuery drillDownQuery;
    }

    /**
     * 下钻查询参数，封装从仪表盘跳转到订单列表时需要的筛选条件。
     *
     * @param activityId           活动ID筛选
     * @param productId            商品ID筛选
     * @param attributionStatus    归因状态筛选
     * @param unattributedReason   未归因原因筛选
     * @param dashboardDiagnosis   诊断分类筛选
     * @param timeField            排序时间字段
     */
    public record DrillDownQuery(
            String activityId,
            String productId,
            String attributionStatus,
            String unattributedReason,
            String dashboardDiagnosis,
            String timeField) {
    }

    private static final DateTimeFormatter RANGE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 构造面向业务侧可读的结算说明文案。
     *
     * <p>当 {@code total == 0} 时返回"暂无订单数据"；
     * 当 {@code settled == 0} 但有订单时，说明上游尚未回传结算样本；
     * 否则展示已结算订单数与占比，便于业务侧快速判断结算健康度。
     */
    private String deriveSettlementReason(long settled, long total) {
        if (total <= 0L) {
            return "暂无订单数据";
        }
        if (settled <= 0L) {
            return "上游尚未回传结算样本，当前仅展示下单/同步侧指标";
        }
        long ratio = Math.round((double) settled * 10000L / (double) total) / 100L;
        return "已结算订单 " + settled + " / " + total + "（" + ratio + "%）";
    }

    /**
     * 将传入的起止时间格式化为统一的时间范围字符串，供前端展示口径。
     *
     * <p>任意一端为 null 时跳过对应侧；两端均为 null 时返回"未指定时间范围"。
     * 该字符串只用于展示，不参与缓存键生成（缓存键由 controller 层计算）。
     */
    private String formatRange(LocalDateTime start, LocalDateTime end) {
        if (start == null && end == null) {
            return "未指定时间范围";
        }
        if (start == null) {
            return "截至 " + RANGE_FORMATTER.format(end);
        }
        if (end == null) {
            return "开始于 " + RANGE_FORMATTER.format(start);
        }
        return RANGE_FORMATTER.format(start) + " ~ " + RANGE_FORMATTER.format(end);
    }
}
