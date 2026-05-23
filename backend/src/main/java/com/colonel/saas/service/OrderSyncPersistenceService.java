package com.colonel.saas.service;

import com.colonel.saas.common.exception.OptimisticLockSupport;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.event.OrderSyncedEvent;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.mapper.OrderSyncDedupClaimMapper;
import com.colonel.saas.mapper.SysUserMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderSyncPersistenceService {

    private final ColonelsettlementOrderMapper orderMapper;
    private final OrderSyncDedupClaimMapper orderSyncDedupClaimMapper;
    private final PickSourceMappingService pickSourceMappingService;
    private final MerchantService merchantService;
    private final SampleLifecycleService sampleLifecycleService;
    private final OperationLogService operationLogService;
    private final SysUserMapper sysUserMapper;
    private final ApplicationEventPublisher eventPublisher;

    public OrderSyncPersistenceService(
            ColonelsettlementOrderMapper orderMapper,
            OrderSyncDedupClaimMapper orderSyncDedupClaimMapper,
            PickSourceMappingService pickSourceMappingService,
            MerchantService merchantService,
            SampleLifecycleService sampleLifecycleService,
            OperationLogService operationLogService,
            SysUserMapper sysUserMapper,
            ApplicationEventPublisher eventPublisher) {
        this.orderMapper = orderMapper;
        this.orderSyncDedupClaimMapper = orderSyncDedupClaimMapper;
        this.pickSourceMappingService = pickSourceMappingService;
        this.merchantService = merchantService;
        this.sampleLifecycleService = sampleLifecycleService;
        this.operationLogService = operationLogService;
        this.sysUserMapper = sysUserMapper;
        this.eventPublisher = eventPublisher;
    }

    public SysUser getUser(UUID id) {
        return sysUserMapper.selectById(id);
    }

    public Map<UUID, SysUser> loadUsersByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<UUID> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return sysUserMapper.selectBatchIds(distinct).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(SysUser::getId, Function.identity(), (left, right) -> left));
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean persistOrder(ColonelsettlementOrder order) {
        int claimEffect = orderSyncDedupClaimMapper.claim(order.getOrderId(), order.getId());
        ColonelsettlementOrder existing = orderMapper.findByOrderId(order.getOrderId());
        if (existing != null) {
            orderSyncDedupClaimMapper.bindOrderRow(order.getOrderId(), existing.getId());
            OrderDualTrackAmountResolver.mergeEstimateSnapshot(existing, order);
            order.setId(existing.getId());
            order.setCreateTime(existing.getCreateTime());
            order.setVersion(existing.getVersion());
            OptimisticLockSupport.requireUpdated(orderMapper.updateSyncedById(order));
            runAttributionFollowUps(order);
            publishOrderSynced(order, false);
            return false;
        }
        if (claimEffect <= 0) {
            return false;
        }
        int effect = orderMapper.insertIgnoreByOrderId(order);
        if (effect <= 0) {
            existing = orderMapper.findByOrderId(order.getOrderId());
            if (existing == null) {
                return false;
            }
            orderSyncDedupClaimMapper.bindOrderRow(order.getOrderId(), existing.getId());
            OrderDualTrackAmountResolver.mergeEstimateSnapshot(existing, order);
            order.setId(existing.getId());
            order.setCreateTime(existing.getCreateTime());
            order.setVersion(existing.getVersion());
            OptimisticLockSupport.requireUpdated(orderMapper.updateSyncedById(order));
            runAttributionFollowUps(order);
            publishOrderSynced(order, false);
            return false;
        }
        runAttributionFollowUps(order);
        publishOrderSynced(order, true);
        return true;
    }

    private void publishOrderSynced(ColonelsettlementOrder order, boolean newlyInserted) {
        if (order == null || eventPublisher == null) {
            return;
        }
        eventPublisher.publishEvent(new OrderSyncedEvent(
                order.getOrderId(),
                order.getId(),
                newlyInserted,
                order.getAttributionStatus(),
                order.getOrderAmount() == null ? 0L : order.getOrderAmount(),
                order.getOrderAmount() == null ? 0L : order.getOrderAmount(),
                order.getSettleAmount() == null ? 0L : order.getSettleAmount(),
                order.getEstimateServiceFee() == null ? 0L : order.getEstimateServiceFee(),
                order.getEffectiveServiceFee() == null ? 0L : order.getEffectiveServiceFee(),
                order.getEstimateTechServiceFee() == null ? 0L : order.getEstimateTechServiceFee(),
                order.getEffectiveTechServiceFee() == null ? 0L : order.getEffectiveTechServiceFee(),
                order.getSettleColonelCommission() == null ? 0L : order.getSettleColonelCommission(),
                order.getSettleColonelTechServiceFee() == null ? 0L : order.getSettleColonelTechServiceFee(),
                order.getSettleSecondColonelCommission() == null ? 0L : order.getSettleSecondColonelCommission(),
                order.getOrderStatus(),
                resolveTalentUid(order.getExtraData()),
                order.getExtraData()));
    }

    private String resolveTalentUid(Map<String, Object> extraData) {
        if (extraData == null || extraData.isEmpty()) {
            return null;
        }
        for (String key : List.of("author_id", "talent_uid", "talentUid", "authorId", "talent_id")) {
            Object value = extraData.get(key);
            if (value != null && StringUtils.hasText(value.toString())) {
                return value.toString().trim();
            }
        }
        return null;
    }

    private void runAttributionFollowUps(ColonelsettlementOrder order) {
        pickSourceMappingService.ensureFromOrder(order);
        recordAttributionFollowUp(order, "补齐推广映射", "ensureFromOrder");

        merchantService.ensureMerchantFromOrder(order);
        recordAttributionFollowUp(order, "沉淀商家", "ensureMerchantFromOrder");

        sampleLifecycleService.completePendingHomeworkByOrder(order);
        recordAttributionFollowUp(order, "完成寄样作业", "completePendingHomeworkByOrder");
    }

    private void recordAttributionFollowUp(ColonelsettlementOrder order, String action, String followUpName) {
        if (order == null) {
            return;
        }
        operationLogService.recordSystemAction(
                order.getUserId() != null ? order.getUserId() : order.getChannelUserId(),
                "订单归因",
                action,
                "POST",
                "order",
                order.getOrderId(),
                resolveTargetName(order),
                "订单归因副作用: " + followUpName);
    }

    private String resolveTargetName(ColonelsettlementOrder order) {
        if (StringUtils.hasText(order.getProductName())) {
            return order.getProductName();
        }
        if (StringUtils.hasText(order.getProductTitle())) {
            return order.getProductTitle();
        }
        return order.getOrderId();
    }
}
