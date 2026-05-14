package com.colonel.saas.service;

import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.mapper.OrderSyncDedupClaimMapper;
import com.colonel.saas.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderSyncPersistenceService {

    private final ColonelsettlementOrderMapper orderMapper;
    private final OrderSyncDedupClaimMapper orderSyncDedupClaimMapper;
    private final PickSourceMappingService pickSourceMappingService;
    private final MerchantService merchantService;
    private final SampleLifecycleService sampleLifecycleService;
    private final SysUserMapper sysUserMapper;

    public OrderSyncPersistenceService(
            ColonelsettlementOrderMapper orderMapper,
            OrderSyncDedupClaimMapper orderSyncDedupClaimMapper,
            PickSourceMappingService pickSourceMappingService,
            MerchantService merchantService,
            SampleLifecycleService sampleLifecycleService,
            SysUserMapper sysUserMapper) {
        this.orderMapper = orderMapper;
        this.orderSyncDedupClaimMapper = orderSyncDedupClaimMapper;
        this.pickSourceMappingService = pickSourceMappingService;
        this.merchantService = merchantService;
        this.sampleLifecycleService = sampleLifecycleService;
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
            orderMapper.updateSyncedById(order);
            pickSourceMappingService.ensureFromOrder(order);
            merchantService.ensureMerchantFromOrder(order);
            sampleLifecycleService.completePendingHomeworkByOrder(order);
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
            orderMapper.updateSyncedById(order);
            pickSourceMappingService.ensureFromOrder(order);
            merchantService.ensureMerchantFromOrder(order);
            sampleLifecycleService.completePendingHomeworkByOrder(order);
            return false;
        }
        pickSourceMappingService.ensureFromOrder(order);
        merchantService.ensureMerchantFromOrder(order);
        sampleLifecycleService.completePendingHomeworkByOrder(order);
        return true;
    }
}
