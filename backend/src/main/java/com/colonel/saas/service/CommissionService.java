package com.colonel.saas.service;

import com.colonel.saas.domain.config.facade.ConfigDomainFacade;
import com.colonel.saas.domain.performance.policy.PerformanceMoneyPolicy;
import com.colonel.saas.entity.ColonelsettlementOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 提成计算服务。
 *
 * <p>职责：基于订单数据计算双轨提成（招商提成 + 渠道提成），支持按活动维度分桶聚合计算，
 * 并提供批量业绩补全和持久化能力。
 *
 * <p>计算公式（调用方按轨道传入扣减额）：
 * <ul>
 *   <li>服务费收益（serviceFeeNet）= 服务费收入 - 调用方传入的技术服务费扣减额</li>
 *   <li>招商提成 = 服务费收益 x 招商提成比例（按活动分桶计算后求和）</li>
 *   <li>渠道提成 = 服务费收益 x 渠道提成比例（按活动分桶计算后求和）</li>
 *   <li>毛利 = 服务费收益 - 招商提成 - 渠道提成</li>
 * </ul>
 *
 * <p>提成比例解析优先级链：规则库（{@link CommissionRuleService}）-> 配置表活动级覆盖 -> 配置表全局默认。
 *
 * <p>依赖服务/仓储：
 * <ul>
 *   <li>{@link ConfigDomainFacade} —— 全局默认提成比例（DDD-CONFIG-003）</li>
 *   <li>{@link CommissionRuleService} —— 提成规则优先级解析</li>
 *   <li>{@link PerformanceCalculationService} —— 业绩记录写入</li>
 *   <li>{@link OrderCommissionPolicy} —— 订单状态判定（是否计入提成）</li>
 * </ul>
 */
@Service
public class CommissionService {

    private static final Logger log = LoggerFactory.getLogger(CommissionService.class);

    /** 默认提成比例（兜底值，15%） */
    private static final BigDecimal DEFAULT_RATIO = new BigDecimal("0.15");
    /** 配置键：招商提成全局默认比例 */
    private static final String KEY_BIZ_RATIO = "commission.business_default_ratio";
    /** 配置键：渠道提成全局默认比例 */
    private static final String KEY_CHANNEL_RATIO = "commission.channel_default_ratio";
    /** 配置键前缀：活动级招商提成比例覆盖 */
    private static final String KEY_BIZ_ACTIVITY_RATIO_PREFIX = "commission.business_activity_ratio.";
    /** 配置键前缀：活动级渠道提成比例覆盖 */
    private static final String KEY_CHANNEL_ACTIVITY_RATIO_PREFIX = "commission.channel_activity_ratio.";

    private final ConfigDomainFacade configDomainFacade;
    private final CommissionRuleService commissionRuleService;
    private final PerformanceCalculationService performanceCalculationService;

    public CommissionService(ConfigDomainFacade configDomainFacade,
                             CommissionRuleService commissionRuleService,
                             @Lazy PerformanceCalculationService performanceCalculationService) {
        this.configDomainFacade = configDomainFacade;
        this.commissionRuleService = commissionRuleService;
        this.performanceCalculationService = performanceCalculationService;
    }

    /**
     * 计算订单列表的提成汇总。
     * 先筛选有效订单，再按活动维度分桶，最后执行按活动分桶的提成计算。
     *
     * @param orders 订单列表
     * @return 提成汇总结果
     */
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
     * 直接接受金额参数进行计算，不从订单实体读取。
     *
     * @param serviceFeeIncome 服务费收入（分）
     * @param techServiceFee   技术服务费（分）
     * @param talentCommission 达人佣金（分）
     * @param activityId       活动ID
     * @return 提成计算结果
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
        return calculateTrack(serviceFeeIncome, techServiceFee, 0L, talentCommission, activityId, productId, recruiterUserId, effectiveAt);
    }

    /**
     * 单轨提成计算（含服务费支出参数版本）。
     *
     * @param serviceFeeIncome   服务费收入（分）
     * @param techServiceFee     技术服务费（分）
     * @param serviceFeeExpense  服务费支出（分），独立外部成本
     * @param talentCommission   达人佣金（分）
     * @param activityId         活动ID
     * @param productId          商品ID
     * @param recruiterUserId    招商员用户ID
     * @param effectiveAt        生效时间
     * @return 提成计算结果
     */
    /**
     * 统一服务费收益公式（分）：收入 − 技术服务费 − 服务费支出。
     * 看板 / 汇总 API 必须与卡片展示共用此公式，避免混用 DB profit 汇总与订单 income 汇总。
     */
    public static long serviceFeeNetCent(long serviceFeeIncome, long techServiceFee, long serviceFeeExpense) {
        return PerformanceMoneyPolicy.serviceFeeNetCent(serviceFeeIncome, techServiceFee, serviceFeeExpense);
    }

    public CommissionSummary calculateTrack(
            long serviceFeeIncome,
            long techServiceFee,
            long serviceFeeExpense,
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
                Math.max(serviceFeeExpense, 0L),
                Math.max(talentCommission, 0L));
        return calculateByActivityBuckets(List.of(bucket), effectiveAt);
    }

    /**
     * 过滤出可计入提成的有效订单。
     * 由 {@link OrderCommissionPolicy#countsTowardCommission} 判定订单状态是否有效。
     */
    private List<ColonelsettlementOrder> filterCommissionEligible(List<ColonelsettlementOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return List.of();
        }
        return orders.stream()
                .filter(order -> order != null && OrderCommissionPolicy.countsTowardCommission(order.getOrderStatus()))
                .toList();
    }

    /**
     * 将订单列表按活动ID+商品ID+招商员ID 聚合为活动提成桶。
     * 同一桶内的金额进行累加，用于后续按活动维度计算提成比例。
     */
    private List<ActivityCommissionBucket> toActivityBuckets(List<ColonelsettlementOrder> orders) {
        Map<String, ActivityCommissionBucket> grouped = new LinkedHashMap<>();
        if (orders == null || orders.isEmpty()) {
            grouped.put("", new ActivityCommissionBucket("", null, null, 0L, 0L, 0L, 0L));
            return List.copyOf(grouped.values());
        }
        for (ColonelsettlementOrder order : orders) {
            String key = bucketKey(order);
            ActivityCommissionBucket existing = grouped.get(key);
            long serviceFee = nvl(order.getSettleColonelCommission());
            long techFee = nvl(order.getSettleColonelTechServiceFee());
            long talentFee = nvl(order.getSettleSecondColonelCommission());
            long expense = nvl(order.getEstimateServiceFeeExpense());
            if (existing == null) {
                grouped.put(key, new ActivityCommissionBucket(
                        normalizeActivityId(order.getActivityId()),
                        normalizeId(order.getProductId()),
                        resolveRecruiterUserId(order),
                        serviceFee,
                        techFee,
                        expense,
                        talentFee));
            } else {
                grouped.put(key, new ActivityCommissionBucket(
                        existing.activityId(),
                        existing.productId(),
                        existing.recruiterUserId(),
                        existing.serviceFeeIncome() + serviceFee,
                        existing.techServiceFee() + techFee,
                        existing.serviceFeeExpense() + expense,
                        existing.talentCommission() + talentFee));
            }
        }
        return List.copyOf(grouped.values());
    }

    public CommissionSummary calculateByActivityBuckets(List<ActivityCommissionBucket> buckets) {
        return calculateByActivityBuckets(buckets, null);
    }

    /**
     * 按活动维度分桶计算双轨提成。
     *
     * <p>计算流程：
     * <ol>
     *   <li>汇总所有桶的金额：服务费收入、技术服务费、达人佣金</li>
     *   <li>计算提成基数 = 服务费收入 - 调用方传入的技术服务费扣减额（即服务费收益）</li>
     *   <li>逐桶解析活动级提成比例（招商/渠道），计算各活动的提成金额</li>
     *   <li>毛利 = 提成基数 - 招商提成 - 渠道提成</li>
     * </ol>
     *
     * @param buckets     活动提成桶列表
     * @param effectiveAt 生效时间（用于提成规则有效期过滤），null 时使用当前时间
     * @return 提成计算汇总
     */
    public CommissionSummary calculateByActivityBuckets(List<ActivityCommissionBucket> buckets, LocalDateTime effectiveAt) {
        BigDecimal defaultBizRatio = loadRatio(KEY_BIZ_RATIO);
        BigDecimal defaultChannelRatio = loadRatio(KEY_CHANNEL_RATIO);

        List<PerformanceMoneyPolicy.BucketInput> inputs = new ArrayList<>();
        for (ActivityCommissionBucket bucket : normalizeBuckets(buckets)) {
            BigDecimal activityBizRatio = resolveBizRatio(bucket, defaultBizRatio, effectiveAt);
            BigDecimal activityChannelRatio = resolveChannelRatio(bucket, defaultChannelRatio, effectiveAt);
            inputs.add(new PerformanceMoneyPolicy.BucketInput(
                    bucket.serviceFeeIncome(),
                    bucket.techServiceFee(),
                    bucket.serviceFeeExpense(),
                    bucket.talentCommission(),
                    activityBizRatio,
                    activityChannelRatio));
        }

        PerformanceMoneyPolicy.MoneyResult result = PerformanceMoneyPolicy.calculate(inputs);
        return new CommissionSummary(
                result.serviceFeeIncome(),
                result.techServiceFee(),
                result.serviceFeeExpense(),
                result.talentCommission(),
                result.serviceFeeNet(),
                result.bizCommission(),
                result.channelCommission(),
                result.grossProfit(),
                result.lastBizRatio(),
                result.lastChannelRatio());
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
            String value = configDomainFacade.getConfig(key);
            if (!StringUtils.hasText(value)) {
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
            return List.of(new ActivityCommissionBucket("", null, null, 0L, 0L, 0L, 0L));
        }
        return buckets;
    }

    public record CommissionSummary(
            long serviceFeeIncome,
            long techServiceFee,
            long serviceFeeExpense,
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
            long serviceFeeExpense,
            long talentCommission) {
        /** 向后兼容构造函数（无 expense） */
        public ActivityCommissionBucket(
                String activityId,
                String productId,
                UUID recruiterUserId,
                long serviceFeeIncome,
                long techServiceFee,
                long talentCommission) {
            this(activityId, productId, recruiterUserId, serviceFeeIncome, techServiceFee, 0L, talentCommission);
        }
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
