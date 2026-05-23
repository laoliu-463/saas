package com.colonel.saas.service;

import com.colonel.saas.entity.ColonelsettlementOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CommissionService {

    private static final Logger log = LoggerFactory.getLogger(CommissionService.class);

    private static final BigDecimal DEFAULT_RATIO = new BigDecimal("0.15");
    private static final String KEY_BIZ_RATIO = "commission.business_default_ratio";
    private static final String KEY_CHANNEL_RATIO = "commission.channel_default_ratio";
    private static final String KEY_BIZ_ACTIVITY_RATIO_PREFIX = "commission.business_activity_ratio.";
    private static final String KEY_CHANNEL_ACTIVITY_RATIO_PREFIX = "commission.channel_activity_ratio.";

    private final JdbcTemplate jdbcTemplate;
    private final CommissionRuleService commissionRuleService;
    private final PerformanceCalculationService performanceCalculationService;

    public CommissionService(JdbcTemplate jdbcTemplate,
                             CommissionRuleService commissionRuleService,
                             @Lazy PerformanceCalculationService performanceCalculationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.commissionRuleService = commissionRuleService;
        this.performanceCalculationService = performanceCalculationService;
    }

    public CommissionSummary calculate(List<ColonelsettlementOrder> orders) {
        return calculateByActivityBuckets(toActivityBuckets(filterCommissionEligible(orders)));
    }

    /**
     * 批量补全订单业绩（Y-08）：计算双轨提成。
     * 取消/失效订单返回 reversed=true（不写 performance_records）。
     */
    public List<OrderCommissionItem> batchFillCommission(List<ColonelsettlementOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return List.of();
        }
        List<OrderCommissionItem> items = new ArrayList<>();
        for (ColonelsettlementOrder order : orders) {
            if (order == null || !StringUtils.hasText(order.getOrderId())) {
                continue;
            }
            if (!OrderCommissionPolicy.countsTowardCommission(order.getOrderStatus())) {
                items.add(OrderCommissionItem.reversed(order.getOrderId()));
                continue;
            }
            CommissionSummary summary = calculate(List.of(order));
            items.add(OrderCommissionItem.of(order.getOrderId(), summary));
        }
        return items;
    }

    /**
     * 批量补全订单业绩（Y-08）：计算并持久化到 performance_records。
     * 取消/失效订单写入 is_reversed=true（冲正）。
     * 返回每笔订单的写入结果。
     */
    public List<OrderCommissionItem> batchUpsertPerformanceRecords(List<ColonelsettlementOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return List.of();
        }
        List<OrderCommissionItem> items = new ArrayList<>();
        for (ColonelsettlementOrder order : orders) {
            if (order == null || !StringUtils.hasText(order.getOrderId())) {
                continue;
            }
            if (!OrderCommissionPolicy.countsTowardCommission(order.getOrderStatus())) {
                items.add(OrderCommissionItem.reversed(order.getOrderId()));
                continue;
            }
            try {
                performanceCalculationService.upsertFromOrder(order);
                CommissionSummary summary = calculate(List.of(order));
                items.add(OrderCommissionItem.of(order.getOrderId(), summary));
            } catch (Exception ex) {
                log.warn("Failed to upsert performance record for orderId={}: {}",
                        order.getOrderId(), ex.getMessage());
                items.add(OrderCommissionItem.reversed(order.getOrderId()));
            }
        }
        return items;
    }

    /**
     * 单轨提成计算（业绩域双轨公式之一）。
     */
    public CommissionSummary calculateTrack(
            long serviceFeeIncome,
            long techServiceFee,
            long talentCommission,
            String activityId) {
        return calculateTrack(serviceFeeIncome, techServiceFee, talentCommission, activityId, null, null, null);
    }

    public CommissionSummary calculateTrack(
            long serviceFeeIncome,
            long techServiceFee,
            long talentCommission,
            String activityId,
            String productId,
            UUID recruiterUserId,
            LocalDateTime effectiveAt) {
        String normalizedActivityId = normalizeActivityId(activityId);
        ActivityCommissionBucket bucket = new ActivityCommissionBucket(
                normalizedActivityId,
                normalizeId(productId),
                recruiterUserId,
                Math.max(serviceFeeIncome, 0L),
                Math.max(techServiceFee, 0L),
                Math.max(talentCommission, 0L));
        return calculateByActivityBuckets(List.of(bucket), effectiveAt);
    }

    private List<ColonelsettlementOrder> filterCommissionEligible(List<ColonelsettlementOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return List.of();
        }
        return orders.stream()
                .filter(order -> order != null && OrderCommissionPolicy.countsTowardCommission(order.getOrderStatus()))
                .toList();
    }

    private List<ActivityCommissionBucket> toActivityBuckets(List<ColonelsettlementOrder> orders) {
        Map<String, ActivityCommissionBucket> grouped = new LinkedHashMap<>();
        if (orders == null || orders.isEmpty()) {
            grouped.put("", new ActivityCommissionBucket("", null, null, 0L, 0L, 0L));
            return List.copyOf(grouped.values());
        }
        for (ColonelsettlementOrder order : orders) {
            String key = bucketKey(order);
            ActivityCommissionBucket existing = grouped.get(key);
            long serviceFee = nvl(order.getSettleColonelCommission());
            long techFee = nvl(order.getSettleColonelTechServiceFee());
            long talentFee = nvl(order.getSettleSecondColonelCommission());
            if (existing == null) {
                grouped.put(key, new ActivityCommissionBucket(
                        normalizeActivityId(order.getActivityId()),
                        normalizeId(order.getProductId()),
                        resolveRecruiterUserId(order),
                        serviceFee,
                        techFee,
                        talentFee));
            } else {
                grouped.put(key, new ActivityCommissionBucket(
                        existing.activityId(),
                        existing.productId(),
                        existing.recruiterUserId(),
                        existing.serviceFeeIncome() + serviceFee,
                        existing.techServiceFee() + techFee,
                        existing.talentCommission() + talentFee));
            }
        }
        return List.copyOf(grouped.values());
    }

    public CommissionSummary calculateByActivityBuckets(List<ActivityCommissionBucket> buckets) {
        return calculateByActivityBuckets(buckets, null);
    }

    public CommissionSummary calculateByActivityBuckets(List<ActivityCommissionBucket> buckets, LocalDateTime effectiveAt) {
        long serviceFeeIncome = sumBuckets(buckets, ActivityCommissionBucket::serviceFeeIncome);
        long techServiceFee = sumBuckets(buckets, ActivityCommissionBucket::techServiceFee);
        long talentCommission = sumBuckets(buckets, ActivityCommissionBucket::talentCommission);

        // Y-03/Y-04 fix: 提成基数（serviceFeeNet）= 服务费收入 − 技术服务费，不含达人佣金
        // 达人佣金(talentCommission)来自抖店结算，不从团长毛利中再扣一次
        long serviceFeeNet = Math.max(serviceFeeIncome - techServiceFee, 0L);
        BigDecimal defaultBizRatio = loadRatio(KEY_BIZ_RATIO);
        BigDecimal defaultChannelRatio = loadRatio(KEY_CHANNEL_RATIO);

        long bizCommission = 0L;
        long channelCommission = 0L;
        BigDecimal lastBizRatio = defaultBizRatio;
        BigDecimal lastChannelRatio = defaultChannelRatio;
        for (ActivityCommissionBucket bucket : normalizeBuckets(buckets)) {
            // Y-04 fix: 活动级毛利基数 = 该活动收入 − 技术费（不含达人佣金），再减两笔提成
            long activityServiceFeeNet = Math.max(
                    bucket.serviceFeeIncome() - bucket.techServiceFee(),
                    0L);
            BigDecimal activityBizRatio = resolveBizRatio(bucket, defaultBizRatio, effectiveAt);
            BigDecimal activityChannelRatio = resolveChannelRatio(bucket, defaultChannelRatio, effectiveAt);
            lastBizRatio = activityBizRatio;
            lastChannelRatio = activityChannelRatio;
            bizCommission += multiplyCent(activityServiceFeeNet, activityBizRatio);
            channelCommission += multiplyCent(activityServiceFeeNet, activityChannelRatio);
        }
        // Y-04: 毛利 = 提成基数 − 两笔提成（服务费净收益 − 招商提成 − 渠道提成）
        long grossProfit = Math.max(serviceFeeNet - bizCommission - channelCommission, 0L);

        return new CommissionSummary(
                serviceFeeIncome,
                techServiceFee,
                talentCommission,
                serviceFeeNet,
                bizCommission,
                channelCommission,
                grossProfit,
                lastBizRatio,
                lastChannelRatio);
    }

    private BigDecimal resolveBizRatio(
            ActivityCommissionBucket bucket,
            BigDecimal defaultRatio,
            LocalDateTime effectiveAt) {
        BigDecimal ruleRatio = resolveRuleRatio(
                CommissionRuleService.TYPE_RECRUITER,
                bucket,
                effectiveAt);
        if (ruleRatio != null) {
            return ruleRatio;
        }
        return loadRatio(KEY_BIZ_ACTIVITY_RATIO_PREFIX, bucket.activityId(), defaultRatio);
    }

    private BigDecimal resolveChannelRatio(
            ActivityCommissionBucket bucket,
            BigDecimal defaultRatio,
            LocalDateTime effectiveAt) {
        BigDecimal ruleRatio = resolveRuleRatio(
                CommissionRuleService.TYPE_CHANNEL,
                bucket,
                effectiveAt);
        if (ruleRatio != null) {
            return ruleRatio;
        }
        return loadRatio(KEY_CHANNEL_ACTIVITY_RATIO_PREFIX, bucket.activityId(), defaultRatio);
    }

    private BigDecimal resolveRuleRatio(
            String commissionType,
            ActivityCommissionBucket bucket,
            LocalDateTime effectiveAt) {
        try {
            return commissionRuleService.resolveRatio(
                    commissionType,
                    new CommissionRuleService.CommissionResolutionContext(
                            bucket.activityId(),
                            bucket.productId(),
                            bucket.recruiterUserId()),
                    effectiveAt);
        } catch (Exception ex) {
            log.warn("Failed to resolve commission rule ratio, fallback to legacy config", ex);
            return null;
        }
    }

    private String bucketKey(ColonelsettlementOrder order) {
        return normalizeActivityId(order == null ? null : order.getActivityId())
                + "|"
                + normalizeId(order == null ? null : order.getProductId())
                + "|"
                + (order == null || resolveRecruiterUserId(order) == null
                ? ""
                : resolveRecruiterUserId(order).toString());
    }

    private UUID resolveRecruiterUserId(ColonelsettlementOrder order) {
        if (order == null) {
            return null;
        }
        return order.getColonelUserId() != null ? order.getColonelUserId() : order.getUserId();
    }

    private BigDecimal loadRatio(String overridePrefix, String activityId, BigDecimal defaultRatio) {
        if (activityId != null && !activityId.isBlank()) {
            BigDecimal override = queryRatio(overridePrefix + activityId);
            if (override != null) {
                return override;
            }
        }
        return defaultRatio;
    }

    private BigDecimal loadRatio(String key) {
        BigDecimal ratio = queryRatio(key);
        return ratio == null ? DEFAULT_RATIO : ratio;
    }

    private BigDecimal queryRatio(String key) {
        try {
            String sql = "SELECT config_value FROM system_config WHERE config_key = ? AND deleted = 0 LIMIT 1";
            String value = jdbcTemplate.query(sql, rs -> rs.next() ? rs.getString(1) : null, key);
            if (value == null || value.isBlank()) {
                return null;
            }
            BigDecimal ratio = new BigDecimal(value.trim());
            if (ratio.compareTo(BigDecimal.ZERO) < 0) {
                return null;
            }
            return ratio;
        } catch (Exception ex) {
            log.warn("Failed to load commission ratio config, fallback to default: {}", key, ex);
            return null;
        }
    }

    private String normalizeActivityId(String activityId) {
        return activityId == null ? "" : activityId.trim();
    }

    private String normalizeId(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private long nvl(Long value) {
        return value == null ? 0L : value;
    }

    private List<ActivityCommissionBucket> normalizeBuckets(List<ActivityCommissionBucket> buckets) {
        if (buckets == null || buckets.isEmpty()) {
            return List.of(new ActivityCommissionBucket("", null, null, 0L, 0L, 0L));
        }
        return buckets;
    }

    private long sumBuckets(List<ActivityCommissionBucket> buckets, java.util.function.ToLongFunction<ActivityCommissionBucket> getter) {
        if (buckets == null || buckets.isEmpty()) {
            return 0L;
        }
        long result = 0L;
        for (ActivityCommissionBucket bucket : buckets) {
            result += getter.applyAsLong(bucket);
        }
        return result;
    }

    private long multiplyCent(long amount, BigDecimal ratio) {
        if (amount <= 0L) {
            return 0L;
        }
        return BigDecimal.valueOf(amount)
                .multiply(ratio)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }

    public record CommissionSummary(
            long serviceFeeIncome,
            long techServiceFee,
            long talentCommission,
            long serviceFeeNet,
            long bizCommission,
            long channelCommission,
            long grossProfit,
            BigDecimal bizRatio,
            BigDecimal channelRatio) {
    }

    public record ActivityCommissionBucket(
            String activityId,
            String productId,
            UUID recruiterUserId,
            long serviceFeeIncome,
            long techServiceFee,
            long talentCommission) {
    }

    public record OrderCommissionItem(
            String orderId,
            boolean reversed,
            CommissionSummary commission) {

        public static OrderCommissionItem of(String orderId, CommissionSummary commission) {
            return new OrderCommissionItem(orderId, false, commission);
        }

        public static OrderCommissionItem reversed(String orderId) {
            return new OrderCommissionItem(orderId, true, null);
        }
    }
}
