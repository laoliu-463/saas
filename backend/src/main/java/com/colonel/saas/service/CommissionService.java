package com.colonel.saas.service;

import com.colonel.saas.entity.ColonelsettlementOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CommissionService {

    private static final Logger log = LoggerFactory.getLogger(CommissionService.class);

    private static final BigDecimal DEFAULT_RATIO = new BigDecimal("0.15");
    private static final String KEY_BIZ_RATIO = "commission.business_default_ratio";
    private static final String KEY_CHANNEL_RATIO = "commission.channel_default_ratio";
    private static final String KEY_BIZ_ACTIVITY_RATIO_PREFIX = "commission.business_activity_ratio.";
    private static final String KEY_CHANNEL_ACTIVITY_RATIO_PREFIX = "commission.channel_activity_ratio.";

    private final JdbcTemplate jdbcTemplate;

    public CommissionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CommissionSummary calculate(List<ColonelsettlementOrder> orders) {
        List<ActivityCommissionBucket> buckets = new ArrayList<>();
        for (Map.Entry<String, List<ColonelsettlementOrder>> entry : groupByActivity(orders).entrySet()) {
            buckets.add(new ActivityCommissionBucket(
                    entry.getKey(),
                    sum(entry.getValue(), ColonelsettlementOrder::getSettleColonelCommission),
                    sum(entry.getValue(), ColonelsettlementOrder::getSettleColonelTechServiceFee),
                    sum(entry.getValue(), ColonelsettlementOrder::getSettleSecondColonelCommission)
            ));
        }
        return calculateByActivityBuckets(buckets);
    }

    public CommissionSummary calculateByActivityBuckets(List<ActivityCommissionBucket> buckets) {
        long serviceFeeIncome = sumBuckets(buckets, ActivityCommissionBucket::serviceFeeIncome);
        long techServiceFee = sumBuckets(buckets, ActivityCommissionBucket::techServiceFee);
        long talentCommission = sumBuckets(buckets, ActivityCommissionBucket::talentCommission);

        long serviceFeeNet = Math.max(serviceFeeIncome - techServiceFee - talentCommission, 0L);
        BigDecimal bizRatio = loadRatio(KEY_BIZ_RATIO);
        BigDecimal channelRatio = loadRatio(KEY_CHANNEL_RATIO);

        long bizCommission = 0L;
        long channelCommission = 0L;
        for (ActivityCommissionBucket bucket : normalizeBuckets(buckets)) {
            long activityServiceFeeNet = Math.max(bucket.serviceFeeIncome() - bucket.techServiceFee() - bucket.talentCommission(), 0L);
            BigDecimal activityBizRatio = loadRatio(KEY_BIZ_ACTIVITY_RATIO_PREFIX, bucket.activityId(), bizRatio);
            BigDecimal activityChannelRatio = loadRatio(KEY_CHANNEL_ACTIVITY_RATIO_PREFIX, bucket.activityId(), channelRatio);
            bizCommission += multiplyCent(activityServiceFeeNet, activityBizRatio);
            channelCommission += multiplyCent(activityServiceFeeNet, activityChannelRatio);
        }
        long grossProfit = Math.max(serviceFeeNet - bizCommission - channelCommission, 0L);

        return new CommissionSummary(
                serviceFeeIncome,
                techServiceFee,
                talentCommission,
                serviceFeeNet,
                bizCommission,
                channelCommission,
                grossProfit,
                bizRatio,
                channelRatio
        );
    }

    private Map<String, List<ColonelsettlementOrder>> groupByActivity(List<ColonelsettlementOrder> orders) {
        Map<String, List<ColonelsettlementOrder>> grouped = new LinkedHashMap<>();
        if (orders == null || orders.isEmpty()) {
            grouped.put("", List.of());
            return grouped;
        }
        for (ColonelsettlementOrder order : orders) {
            String activityId = normalizeActivityId(order == null ? null : order.getActivityId());
            grouped.computeIfAbsent(activityId, key -> new java.util.ArrayList<>()).add(order);
        }
        return grouped;
    }

    private List<ActivityCommissionBucket> normalizeBuckets(List<ActivityCommissionBucket> buckets) {
        if (buckets == null || buckets.isEmpty()) {
            return List.of(new ActivityCommissionBucket("", 0L, 0L, 0L));
        }
        return buckets;
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
            log.error("Failed to load commission ratio config: {}", key, ex);
            throw new IllegalStateException("Failed to load commission ratio config: " + key, ex);
        }
    }

    private String normalizeActivityId(String activityId) {
        return activityId == null ? "" : activityId.trim();
    }

    private long sum(List<ColonelsettlementOrder> rows, java.util.function.Function<ColonelsettlementOrder, Long> getter) {
        if (rows == null || rows.isEmpty()) {
            return 0L;
        }
        long result = 0L;
        for (ColonelsettlementOrder row : rows) {
            Long value = getter.apply(row);
            result += value == null ? 0L : value;
        }
        return result;
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
            BigDecimal channelRatio
    ) {
    }

    public record ActivityCommissionBucket(
            String activityId,
            long serviceFeeIncome,
            long techServiceFee,
            long talentCommission
    ) {
    }
}
