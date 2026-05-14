package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderAttributionReplayService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final ColonelsettlementOrderMapper orderMapper;
    private final AttributionService attributionService;
    private final OrderSyncPersistenceService persistenceService;

    public OrderAttributionReplayService(
            ColonelsettlementOrderMapper orderMapper,
            AttributionService attributionService,
            OrderSyncPersistenceService persistenceService) {
        this.orderMapper = orderMapper;
        this.attributionService = attributionService;
        this.persistenceService = persistenceService;
    }

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

        for (ColonelsettlementOrder order : orders) {
            scanned++;
            Map<String, Object> normalizedSource = AttributionSourceNormalizer.normalize(order.getExtraData());
            AttributionService.AttributionResult result = attributionService.resolveAttribution(order, normalizedSource);
            AttributionService.NativeMappingTrace nativeTrace = result.nativeTrace();
            if (nativeTrace != null && nativeTrace.nativeKeyMatched()) {
                nativeKeyMatched++;
            }
            if (nativeTrace != null && nativeTrace.colonelBuyinIdMismatch()) {
                colonelBuyinIdMismatch++;
            }
            if (nativeTrace != null && nativeTrace.ambiguousMapping()) {
                ambiguousMapping++;
            }
            if (AttributionService.STATUS_ATTRIBUTED.equals(result.attributionStatus())) {
                attributed++;
            } else {
                unattributed++;
                stillUnattributed++;
            }
            boolean safeForHistoricalUpdate = isSafeForHistoricalUpdate(order, result);
            if (nativeTrace != null && nativeTrace.nativeKeyMatched() && AttributionService.STATUS_ATTRIBUTED.equals(result.attributionStatus())) {
                if (safeForHistoricalUpdate) {
                    safeToUpdate++;
                } else if (nativeTrace.mappingCreatedAt() != null && order.getCreateTime() != null
                        && nativeTrace.mappingCreatedAt().isAfter(order.getCreateTime())) {
                    unsafeBecauseCreatedAfterOrder++;
                }
            }
            if (dryRun) {
                continue;
            }
            if (!safeForHistoricalUpdate) {
                continue;
            }
            applyAttribution(order, result);
            persistenceService.persistOrder(order);
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
                stillUnattributed
        );
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
        return orderMapper.selectList(wrapper);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private void applyAttribution(ColonelsettlementOrder order, AttributionService.AttributionResult result) {
        order.setChannelUserId(result.channelUserId());
        order.setChannelDeptId(result.deptId());
        order.setUserId(result.userId());
        order.setDeptId(result.deptId());
        order.setColonelUserId(result.colonelUserId());
        order.setTalentId(result.talentId());
        if (StringUtils.hasText(result.activityId())) {
            order.setActivityId(result.activityId());
        }
        order.setAttributionStatus(result.attributionStatus());
        order.setAttributionRemark(result.attributionRemark());
        order.setUpdateTime(LocalDateTime.now());
        fillUserNames(order);
    }

    private void fillUserNames(ColonelsettlementOrder order) {
        order.setChannelUserName(resolveUserName(order.getChannelUserId()));
        order.setColonelUserName(resolveUserName(order.getColonelUserId()));
    }

    private String resolveUserName(UUID userId) {
        if (userId == null) {
            return null;
        }
        SysUser user = persistenceService.getUser(userId);
        return user == null ? null : user.getRealName();
    }

    private boolean isSafeForHistoricalUpdate(ColonelsettlementOrder order, AttributionService.AttributionResult result) {
        if (!AttributionService.STATUS_ATTRIBUTED.equals(result.attributionStatus())) {
            return false;
        }
        AttributionService.NativeMappingTrace nativeTrace = result.nativeTrace();
        if (nativeTrace == null || !nativeTrace.nativeKeyMatched()) {
            return true;
        }
        if (nativeTrace.mappingCreatedAt() == null || order.getCreateTime() == null) {
            return false;
        }
        return !nativeTrace.mappingCreatedAt().isAfter(order.getCreateTime());
    }

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
            int stillUnattributed) {
    }
}
