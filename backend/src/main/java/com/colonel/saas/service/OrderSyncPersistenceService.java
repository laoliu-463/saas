package com.colonel.saas.service;

import com.colonel.saas.common.exception.OptimisticLockSupport;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.mapper.OrderSyncDedupClaimMapper;
import com.colonel.saas.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class OrderSyncPersistenceService {

    private final ColonelsettlementOrderMapper orderMapper;
    private final OrderSyncDedupClaimMapper orderSyncDedupClaimMapper;
    private final PickSourceMappingService pickSourceMappingService;
    private final MerchantService merchantService;
    private final SampleLifecycleService sampleLifecycleService;
    private final OperationLogService operationLogService;
    private final SysUserMapper sysUserMapper;

    public OrderSyncPersistenceService(
            ColonelsettlementOrderMapper orderMapper,
            OrderSyncDedupClaimMapper orderSyncDedupClaimMapper,
            PickSourceMappingService pickSourceMappingService,
            MerchantService merchantService,
            SampleLifecycleService sampleLifecycleService,
            OperationLogService operationLogService,
            SysUserMapper sysUserMapper) {
        this.orderMapper = orderMapper;
        this.orderSyncDedupClaimMapper = orderSyncDedupClaimMapper;
        this.pickSourceMappingService = pickSourceMappingService;
        this.merchantService = merchantService;
        this.sampleLifecycleService = sampleLifecycleService;
        this.operationLogService = operationLogService;
        this.sysUserMapper = sysUserMapper;
    }

    public SysUser getUser(java.util.UUID id) {
        return sysUserMapper.selectById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean persistOrder(ColonelsettlementOrder order) {
        int claimEffect = orderSyncDedupClaimMapper.claim(order.getOrderId(), order.getId());
        ColonelsettlementOrder existing = orderMapper.findByOrderId(order.getOrderId());
        if (existing != null) {
            orderSyncDedupClaimMapper.bindOrderRow(order.getOrderId(), existing.getId());
            order.setId(existing.getId());
            order.setCreateTime(existing.getCreateTime());
            order.setVersion(existing.getVersion());
            OptimisticLockSupport.requireUpdated(orderMapper.updateSyncedById(order));
            runAttributionFollowUps(order);
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
            order.setId(existing.getId());
            order.setCreateTime(existing.getCreateTime());
            order.setVersion(existing.getVersion());
            OptimisticLockSupport.requireUpdated(orderMapper.updateSyncedById(order));
            runAttributionFollowUps(order);
            return false;
        }
        runAttributionFollowUps(order);
        return true;
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
