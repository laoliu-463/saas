package com.colonel.saas.service;

import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderSyncPersistenceService {

    private final ColonelsettlementOrderMapper orderMapper;
    private final PickSourceMappingService pickSourceMappingService;
    private final MerchantService merchantService;
    private final SampleLifecycleService sampleLifecycleService;

    public OrderSyncPersistenceService(
            ColonelsettlementOrderMapper orderMapper,
            PickSourceMappingService pickSourceMappingService,
            MerchantService merchantService,
            SampleLifecycleService sampleLifecycleService) {
        this.orderMapper = orderMapper;
        this.pickSourceMappingService = pickSourceMappingService;
        this.merchantService = merchantService;
        this.sampleLifecycleService = sampleLifecycleService;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean persistOrder(ColonelsettlementOrder order) {
        int effect = orderMapper.insertIgnoreByOrderId(order);
        if (effect <= 0) {
            return false;
        }
        pickSourceMappingService.ensureFromOrder(order);
        merchantService.findOrCreateByChannel(
                order.getChannelUserId() == null ? null : order.getChannelUserId().toString(),
                order
        );
        sampleLifecycleService.completePendingHomeworkByOrder(order);
        return true;
    }
}
