package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import lombok.Data;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class DashboardService {

    public static final String DIAGNOSIS_MECHANISM_HIT_HISTORY_UNSAFE = "MECHANISM_HIT_HISTORY_UNSAFE";
    public static final String DIAGNOSIS_UPSTREAM_PRODUCT_UNCOVERED = "UPSTREAM_PRODUCT_UNCOVERED";
    public static final String DIAGNOSIS_CANNOT_AUTO_ATTRIBUTION = "CANNOT_AUTO_ATTRIBUTION";
    public static final String DIAGNOSIS_NATIVE_KEY_MISMATCH = "NATIVE_KEY_MISMATCH";
    public static final String DIAGNOSIS_AMBIGUOUS_MAPPING = "AMBIGUOUS_MAPPING";

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

    private static final int DEFAULT_BREAKDOWN_LIMIT = 20;

    private final ColonelsettlementOrderMapper orderMapper;
    private final JdbcTemplate jdbcTemplate;
    private final PerformanceMetricsQueryService performanceMetricsQueryService;

    public DashboardService(
            ColonelsettlementOrderMapper orderMapper,
            JdbcTemplate jdbcTemplate,
            PerformanceMetricsQueryService performanceMetricsQueryService) {
        this.orderMapper = orderMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.performanceMetricsQueryService = performanceMetricsQueryService;
    }

    public Summary getSummary(LocalDateTime startTime, LocalDateTime endTime, UUID userId, UUID deptId, DataScope dataScope) {
        boolean usePerformanceRecords = performanceMetricsQueryService.hasPerformanceRecords();
        Map<String, Object> totalMap = Map.of();
        if (!usePerformanceRecords) {
            QueryWrapper<ColonelsettlementOrder> totalWrapper = new QueryWrapper<ColonelsettlementOrder>()
                    .select("count(*) as orderCount", "sum(order_amount) as orderAmount", "sum(settle_colonel_commission) as serviceFee");
            applyRange(totalWrapper, startTime, endTime);
            applyScope(totalWrapper, userId, deptId, dataScope);
            totalMap = orderMapper.selectMaps(totalWrapper).stream()
                    .findFirst()
                    .orElse(Map.of());
        }

        QueryWrapper<ColonelsettlementOrder> attributedWrapper = new QueryWrapper<ColonelsettlementOrder>()
                .eq("attribution_status", AttributionService.STATUS_ATTRIBUTED);
        applyRange(attributedWrapper, startTime, endTime);
        applyScope(attributedWrapper, userId, deptId, dataScope);
        Long attributedCount = orderMapper.selectCount(attributedWrapper);

        QueryWrapper<ColonelsettlementOrder> unattributedWrapper = new QueryWrapper<ColonelsettlementOrder>()
                .eq("attribution_status", AttributionService.STATUS_UNATTRIBUTED);
        applyRange(unattributedWrapper, startTime, endTime);
        applyScope(unattributedWrapper, userId, deptId, dataScope);
        Long unattributedCount = orderMapper.selectCount(unattributedWrapper);

        List<PerformanceItem> channelPerformance;
        List<PerformanceItem> colonelPerformance;
        long orderCount;
        long orderAmount;
        long serviceFee;
        if (usePerformanceRecords) {
            PerformanceMetricsQueryService.DashboardPerformanceSummary performanceSummary =
                    performanceMetricsQueryService.aggregateDashboardSummary(startTime, endTime, userId, deptId, dataScope);
            orderCount = performanceSummary.orderCount();
            orderAmount = performanceSummary.orderAmountCent();
            serviceFee = performanceSummary.serviceFeeCent();
            channelPerformance = toPerformanceItems(performanceSummary.channelPerformance(), true);
            colonelPerformance = toPerformanceItems(performanceSummary.colonelPerformance(), false);
        } else {
            orderCount = asLong(totalMap.get("ordercount"));
            orderAmount = asLong(totalMap.get("orderamount"));
            serviceFee = asLong(totalMap.get("servicefee"));

            QueryWrapper<ColonelsettlementOrder> channelWrapper = new QueryWrapper<ColonelsettlementOrder>()
                    .select("channel_user_id as channelUserId", "channel_user_name as channelUserName", "count(*) as orderCount", "sum(order_amount) as orderAmount", "sum(settle_colonel_commission) as serviceFee")
                    .isNotNull("channel_user_id")
                    .eq("attribution_status", AttributionService.STATUS_ATTRIBUTED)
                    .groupBy("channel_user_id", "channel_user_name");
            applyRange(channelWrapper, startTime, endTime);
            applyScope(channelWrapper, userId, deptId, dataScope);
            channelPerformance = orderMapper.selectMaps(channelWrapper).stream()
                    .map(this::toPerformanceItem)
                    .sorted(Comparator.comparingLong(PerformanceItem::getOrderCount).reversed())
                    .limit(10)
                    .toList();

            QueryWrapper<ColonelsettlementOrder> colonelWrapper = new QueryWrapper<ColonelsettlementOrder>()
                    .select("colonel_user_id as colonelUserId", "colonel_user_name as colonelUserName", "count(*) as orderCount", "sum(order_amount) as orderAmount", "sum(settle_colonel_commission) as serviceFee")
                    .isNotNull("colonel_user_id")
                    .eq("attribution_status", AttributionService.STATUS_ATTRIBUTED)
                    .groupBy("colonel_user_id", "colonel_user_name");
            applyRange(colonelWrapper, startTime, endTime);
            applyScope(colonelWrapper, userId, deptId, dataScope);
            colonelPerformance = orderMapper.selectMaps(colonelWrapper).stream()
                    .map(this::toPerformanceItem)
                    .sorted(Comparator.comparingLong(PerformanceItem::getOrderCount).reversed())
                    .limit(10)
                    .toList();
        }

        QueryWrapper<ColonelsettlementOrder> reasonWrapper = new QueryWrapper<ColonelsettlementOrder>()
                .select("attribution_remark as reason", "count(*) as count")
                .eq("attribution_status", AttributionService.STATUS_UNATTRIBUTED)
                .isNotNull("attribution_remark")
                .groupBy("attribution_remark");
        applyRange(reasonWrapper, startTime, endTime);
        applyScope(reasonWrapper, userId, deptId, dataScope);
        List<ReasonCountItem> unattributedReasons = orderMapper.selectMaps(reasonWrapper).stream()
                .map(this::toReasonCountItem)
                .sorted(Comparator.comparingLong(ReasonCountItem::getCount).reversed())
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
        return summary;
    }

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

    public Summary getSummary() {
        return getSummary(null, null, null, null, null);
    }

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
        if (dataScope != null) {
            switch (dataScope) {
                case PERSONAL -> {
                    if (userId != null) {
                        clauses.add("co.user_id = ?");
                        args.add(userId);
                    }
                }
                case DEPT -> {
                    if (deptId != null) {
                        clauses.add("co.dept_id = ?");
                        args.add(deptId);
                    }
                }
                case ALL -> {
                }
            }
        }
        return new SqlContext(String.join(" AND ", clauses), args);
    }

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

    private PerformanceItem toPerformanceItem(Map<String, Object> map) {
        PerformanceItem item = new PerformanceItem();
        item.setChannelUserId(asString(map.get("channeluserid")));
        item.setChannelUserName(asString(map.get("channelusername")));
        item.setColonelUserId(asString(map.get("coloneluserid")));
        item.setColonelUserName(asString(map.get("colonelusername")));
        item.setOrderCount(asLong(map.get("ordercount")));
        item.setOrderAmount(asLong(map.get("orderamount")));
        item.setServiceFee(asLong(map.get("servicefee")));
        return item;
    }

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

    private ReasonCountItem toReasonCountItem(Map<String, Object> map) {
        ReasonCountItem item = new ReasonCountItem();
        item.setReason(asString(map.get("reason")));
        item.setCount(asLong(map.get("count")));
        item.setDrillDownQuery(new DrillDownQuery(null, null, AttributionService.STATUS_UNATTRIBUTED, item.getReason(), null, "settleTime"));
        return item;
    }

    private DiagnosticItem toDiagnosticItem(Map<String, Object> map) {
        DiagnosticItem item = new DiagnosticItem();
        item.setCategory(asString(readMapValue(map, "category")));
        item.setLabel(diagnosticLabel(item.getCategory()));
        item.setCount(asLong(readMapValue(map, "total_count")));
        item.setDrillDownQuery(new DrillDownQuery(null, null, null, null, item.getCategory(), "settleTime"));
        return item;
    }

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

    private long findDiagnosticCount(List<DiagnosticItem> diagnostics, String category) {
        return diagnostics.stream()
                .filter(item -> category.equals(item.getCategory()))
                .map(DiagnosticItem::getCount)
                .findFirst()
                .orElse(0L);
    }

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

    private void applyRange(QueryWrapper<ColonelsettlementOrder> wrapper, LocalDateTime startTime, LocalDateTime endTime) {
        if (wrapper == null) {
            return;
        }
        if (startTime != null) {
            wrapper.ge("settle_time", startTime);
        }
        if (endTime != null) {
            wrapper.le("settle_time", endTime);
        }
    }

    private void applyScope(QueryWrapper<ColonelsettlementOrder> wrapper, UUID userId, UUID deptId, DataScope dataScope) {
        if (wrapper == null || dataScope == null) {
            return;
        }
        switch (dataScope) {
            case PERSONAL -> {
                if (userId != null) {
                    wrapper.eq("user_id", userId);
                }
            }
            case DEPT -> {
                if (deptId != null) {
                    wrapper.eq("dept_id", deptId);
                }
            }
            case ALL -> {
            }
        }
    }

    private Object readMapValue(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        if (map.containsKey(key)) {
            return map.get(key);
        }
        return map.get(key.toLowerCase());
    }

    private long asLong(Object val) {
        if (val == null) {
            return 0L;
        }
        if (val instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private String asString(Object val) {
        return val == null ? null : String.valueOf(val);
    }

    private record SqlContext(String whereClause, List<Object> args) {
    }

    public record ActivityProductPage(long total, long page, long size, List<ActivityProductItem> records) {
    }

    @Data
    public static class Summary {
        private Long orderCount;
        private Long orderAmount;
        private Long serviceFee;
        private Long attributedOrderCount;
        private Long unattributedOrderCount;
        private Double attributionRate;
        private Long unsafeBecauseCreatedAfterOrderCount;
        private Long upstreamProductUncoveredCount;
        private Long cannotAutoAttributionCount;
        private Long nativeKeyMismatchCount;
        private Long ambiguousMappingCount;
        private List<PerformanceItem> channelPerformance;
        private List<PerformanceItem> colonelPerformance;
        private List<ReasonCountItem> unattributedReasons;
        private List<DiagnosticItem> diagnosticBreakdown;
        private List<ActivityProductItem> activityProductBreakdown;
    }

    @Data
    public static class PerformanceItem {
        private String channelUserId;
        private String channelUserName;
        private String colonelUserId;
        private String colonelUserName;
        private Long orderCount;
        private Long orderAmount;
        private Long serviceFee;
    }

    @Data
    public static class ReasonCountItem {
        private String reason;
        private Long count;
        private DrillDownQuery drillDownQuery;
    }

    @Data
    public static class DiagnosticItem {
        private String category;
        private String label;
        private Long count;
        private DrillDownQuery drillDownQuery;
    }

    @Data
    public static class ActivityProductItem {
        private String activityId;
        private String productId;
        private String productName;
        private String productCover;
        private String bizStatus;
        private String assigneeName;
        private Long orderCount;
        private Long orderAmount;
        private Long unattributedOrderCount;
        private Long mappingCount;
        private Long promotionLinkCount;
        private DrillDownQuery drillDownQuery;
    }

    public record DrillDownQuery(
            String activityId,
            String productId,
            String attributionStatus,
            String unattributedReason,
            String dashboardDiagnosis,
            String timeField) {
    }
}
