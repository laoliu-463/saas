package com.colonel.saas.listener;

import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.PerformanceRecord;
import com.colonel.saas.event.OrderSyncedEvent;
import com.colonel.saas.event.PerformanceCalculatedEvent;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.service.PerformanceCalculationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PerformanceRecordSyncListener {

    private final ColonelsettlementOrderMapper orderMapper;
    private final PerformanceCalculationService performanceCalculationService;
    private final ApplicationEventPublisher eventPublisher;

    public PerformanceRecordSyncListener(
            ColonelsettlementOrderMapper orderMapper,
            PerformanceCalculationService performanceCalculationService,
            ApplicationEventPublisher eventPublisher) {
        this.orderMapper = orderMapper;
        this.performanceCalculationService = performanceCalculationService;
        this.eventPublisher = eventPublisher;
    }

    @Async
    @EventListener
    public void onOrderSynced(OrderSyncedEvent event) {
        if (event == null || event.orderId() == null) {
            return;
        }
        try {
            ColonelsettlementOrder order = orderMapper.findByOrderId(event.orderId());
            if (order == null) {
                log.warn("Performance calculation skipped, order not found: {}", event.orderId());
                return;
            }
            PerformanceRecord record = performanceCalculationService.upsertFromOrder(order);
            if (record == null) {
                return;
            }
            eventPublisher.publishEvent(new PerformanceCalculatedEvent(
                    record.getOrderId(),
                    nvl(record.getEstimateGrossProfit()),
                    nvl(record.getEffectiveGrossProfit()),
                    Boolean.TRUE.equals(record.getReversed())));
        } catch (Exception ex) {
            log.warn("Performance calculation failed, orderId={}", event.orderId(), ex);
        }
    }

    private long nvl(Long value) {
        return value == null ? 0L : value;
    }
}
