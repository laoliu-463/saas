package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.domain.order.application.OrderDefaultAttributionResolver;
import com.colonel.saas.domain.order.policy.OrderAttributionInput;
import com.colonel.saas.domain.order.policy.OrderDefaultAttributionPolicy;
import com.colonel.saas.domain.order.policy.OrderDefaultAttributionResult;
import com.colonel.saas.domain.order.policy.OrderLinkAttributionResolution;
import com.colonel.saas.domain.order.policy.OrderLinkAttributionResolution.Status;
import com.colonel.saas.domain.performance.application.PerformanceCalculationApplicationService;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 历史订单归属回放服务。
 *
 * <p>实时同步和历史回放都必须经 {@link OrderDefaultAttributionResolver} 解析，
 * 这样两条链路共享同一套链接拥有者、活动招商回退和映射时间安全规则。</p>
 */
@Service
public class OrderAttributionReplayService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final ColonelsettlementOrderMapper orderMapper;
    private final OrderDefaultAttributionResolver defaultAttributionResolver;
    private final OrderSyncPersistenceService persistenceService;
    private final PerformanceCalculationApplicationService performanceCalculationApplicationService;

    public OrderAttributionReplayService(
            ColonelsettlementOrderMapper orderMapper,
            OrderDefaultAttributionResolver defaultAttributionResolver,
            OrderSyncPersistenceService persistenceService,
            PerformanceCalculationApplicationService performanceCalculationApplicationService) {
        this.orderMapper = orderMapper;
        this.defaultAttributionResolver = defaultAttributionResolver;
        this.persistenceService = persistenceService;
        this.performanceCalculationApplicationService = performanceCalculationApplicationService;
    }

    /**
     * 逐笔重放订单归属。dry-run 永不写订单或业绩；apply 时订单写入先于业绩 upsert。
     */
    @Transactional(rollbackFor = Exception.class)
    public ReplayResult replay(List<String> orderIds, String reason, Integer limit, boolean dryRun) {
        List<ColonelsettlementOrder> orders = loadOrders(orderIds, reason, limit);
        int scanned = 0;
        int attributed = 0;
        int unattributed = 0;
        int updated = 0;
        int nativeKeyMatched = 0;
        int safeToUpdate = 0;
        int unsafeBecauseCreatedAfterOrder = 0;
        int colonelBuyinIdMismatch = 0;
        int ambiguousMapping = 0;
        int stillUnattributed = 0;
        List<ReplayDecision> decisions = new ArrayList<>();

        for (ColonelsettlementOrder order : orders) {
            scanned++;
            Map<String, Object> normalizedSource = AttributionSourceNormalizer.normalize(order.getExtraData());
            OrderDefaultAttributionResult result = defaultAttributionResolver.resolve(order, normalizedSource);
            OrderLinkAttributionResolution linkResolution = result.linkResolution();
            OrderAttributionInput input = OrderAttributionInput.from(order, normalizedSource);

            if (linkResolution != null && linkResolution.nativeKeyMatched()) {
                nativeKeyMatched++;
            }
            if (linkResolution != null && linkResolution.colonelBuyinIdMismatch()) {
                colonelBuyinIdMismatch++;
            }
            if (linkResolution != null && linkResolution.status() == Status.AMBIGUOUS) {
                ambiguousMapping++;
            }

            boolean isAttributed = AttributionService.STATUS_ATTRIBUTED.equals(result.attributionStatus());
            if (isAttributed) {
                attributed++;
            } else {
                unattributed++;
                stillUnattributed++;
            }

            boolean safe = isSafeForHistoricalUpdate(input.businessTime(), result);
            if (safe) {
                safeToUpdate++;
            } else if (isMappingCreatedAfterBusinessTime(input.businessTime(), linkResolution)) {
                unsafeBecauseCreatedAfterOrder++;
            }

            boolean changed = wouldChange(order, result);
            if (decisions.size() < MAX_LIMIT) {
                decisions.add(new ReplayDecision(
                        order.getOrderId(),
                        result.defaultChannelUserId(),
                        result.defaultRecruiterId(),
                        result.channelAttributionSource(),
                        result.recruiterAttributionSource(),
                        result.attributionRemark(),
                        safe,
                        changed));
            }
            if (dryRun || !safe) {
                continue;
            }

            OrderDefaultAttributionPolicy.applyToOrder(order, result, order.getTalentName());
            order.setUpdateTime(LocalDateTime.now());
            fillUserNames(order);
            persistenceService.persistOrder(order);
            performanceCalculationApplicationService.upsertFromOrder(order);
            updated++;
        }

        return new ReplayResult(
                scanned,
                attributed,
                unattributed,
                updated,
                dryRun,
                nativeKeyMatched,
                safeToUpdate,
                unsafeBecauseCreatedAfterOrder,
                colonelBuyinIdMismatch,
                ambiguousMapping,
                stillUnattributed,
                decisions);
    }

    private List<ColonelsettlementOrder> loadOrders(List<String> orderIds, String reason, Integer limit) {
        LambdaQueryWrapper<ColonelsettlementOrder> wrapper = new LambdaQueryWrapper<ColonelsettlementOrder>()
                .eq(ColonelsettlementOrder::getDeleted, 0);
        if (orderIds != null && !orderIds.isEmpty()) {
            wrapper.in(ColonelsettlementOrder::getOrderId, orderIds);
        } else {
            wrapper.eq(ColonelsettlementOrder::getAttributionStatus, AttributionService.STATUS_UNATTRIBUTED);
            if (StringUtils.hasText(reason)) {
                wrapper.eq(ColonelsettlementOrder::getAttributionRemark, reason.trim());
            }
            wrapper.last("limit " + normalizeLimit(limit));
        }
        wrapper.orderByDesc(ColonelsettlementOrder::getUpdateTime)
                .orderByDesc(ColonelsettlementOrder::getCreateTime);
        return orderMapper.selectList(wrapper).stream().limit(MAX_LIMIT).toList();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private boolean isSafeForHistoricalUpdate(
            LocalDateTime businessTime,
            OrderDefaultAttributionResult result) {
        if (result == null || !AttributionService.STATUS_ATTRIBUTED.equals(result.attributionStatus())) {
            return false;
        }
        OrderLinkAttributionResolution resolution = result.linkResolution();
        if (resolution == null || resolution.mappingCreatedAt() == null) {
            return resolution == null || !resolution.nativeKeyMatched();
        }
        if (businessTime == null) {
            return false;
        }
        return !resolution.mappingCreatedAt().isAfter(businessTime);
    }

    private boolean isMappingCreatedAfterBusinessTime(
            LocalDateTime businessTime,
            OrderLinkAttributionResolution resolution) {
        return businessTime != null
                && resolution != null
                && resolution.mappingCreatedAt() != null
                && resolution.mappingCreatedAt().isAfter(businessTime);
    }

    private boolean wouldChange(ColonelsettlementOrder order, OrderDefaultAttributionResult result) {
        return !Objects.equals(order.getChannelUserId(), result.defaultChannelUserId())
                || !Objects.equals(order.getChannelDeptId(), result.channelDeptId())
                || !Objects.equals(order.getColonelUserId(), result.defaultRecruiterId())
                || !Objects.equals(order.getTalentId(), result.talentId())
                || !Objects.equals(order.getActivityId(), result.activityId())
                || !Objects.equals(order.getChannelAttributionSource(), result.channelAttributionSource())
                || !Objects.equals(order.getRecruiterAttributionSource(), result.recruiterAttributionSource())
                || !Objects.equals(order.getAttributionStatus(), result.attributionStatus())
                || !Objects.equals(order.getAttributionRemark(), result.attributionRemark());
    }

    private void fillUserNames(ColonelsettlementOrder order) {
        order.setChannelUserName(resolveUserName(order.getChannelUserId()));
        order.setColonelUserName(resolveUserName(order.getColonelUserId()));
    }

    private String resolveUserName(UUID userId) {
        return userId == null ? null : persistenceService.getUserName(userId);
    }

    /** 单笔回放的可审计决策，最多返回 {@value MAX_LIMIT} 条。 */
    public record ReplayDecision(
            String orderId,
            UUID channelUserId,
            UUID recruiterUserId,
            String channelSource,
            String recruiterSource,
            String mappingReason,
            boolean safe,
            boolean changed) {
    }

    /** 回放统计及逐笔决策证据。 */
    public record ReplayResult(
            int scanned,
            int attributed,
            int unattributed,
            int updated,
            boolean dryRun,
            int nativeKeyMatched,
            int safeToUpdate,
            int unsafeBecauseCreatedAfterOrder,
            int colonelBuyinIdMismatch,
            int ambiguousMapping,
            int stillUnattributed,
            List<ReplayDecision> decisions) {

        public ReplayResult {
            decisions = decisions == null ? List.of() : List.copyOf(decisions);
        }

        /** 兼容旧控制器与测试中的统计结果构造。 */
        public ReplayResult(
                int scanned,
                int attributed,
                int unattributed,
                int updated,
                boolean dryRun,
                int nativeKeyMatched,
                int safeToUpdate,
                int unsafeBecauseCreatedAfterOrder,
                int colonelBuyinIdMismatch,
                int ambiguousMapping,
                int stillUnattributed) {
            this(scanned, attributed, unattributed, updated, dryRun, nativeKeyMatched, safeToUpdate,
                    unsafeBecauseCreatedAfterOrder, colonelBuyinIdMismatch, ambiguousMapping,
                    stillUnattributed, List.of());
        }
    }
}
