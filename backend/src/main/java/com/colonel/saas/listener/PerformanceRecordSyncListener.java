package com.colonel.saas.listener;

import com.colonel.saas.domain.order.event.OrderRefundFactSyncedEvent;
import com.colonel.saas.domain.order.event.OrderAttributionReplayedEvent;
import com.colonel.saas.domain.order.facade.OrderReadFacade;
import com.colonel.saas.domain.performance.application.PerformanceCalculationApplicationService;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.PerformanceRecord;
import com.colonel.saas.event.OrderSyncedEvent;
import com.colonel.saas.event.PerformanceCalculatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 业绩记录同步事件监听器。
 * <p>
 * 监听订单同步完成事件（{@link OrderSyncedEvent}），异步执行业绩计算并发布
 * 业绩计算完成事件。该监听器是订单域到业绩域的关键桥梁，实现了订单同步后的
 * 自动业绩归集。
 * </p>
 */
@Slf4j
@Component
public class PerformanceRecordSyncListener {

    private final OrderReadFacade orderReadFacade;
    private final PerformanceCalculationApplicationService performanceCalculationApplicationService;
    private final ApplicationEventPublisher eventPublisher;

    public PerformanceRecordSyncListener(
            OrderReadFacade orderReadFacade,
            PerformanceCalculationApplicationService performanceCalculationApplicationService,
            ApplicationEventPublisher eventPublisher) {
        this.orderReadFacade = orderReadFacade;
        this.performanceCalculationApplicationService = performanceCalculationApplicationService;
        this.eventPublisher = eventPublisher;
    }

    @Async
    @EventListener
    public void onOrderSynced(OrderSyncedEvent event) {
        if (event == null || event.orderId() == null) {
            return;
        }
        try {
            ColonelsettlementOrder order = orderReadFacade.findByOrderId(event.orderId());
            recalculateOrThrow(order, event.orderId());
        } catch (Exception ex) {
            log.warn("Performance calculation failed, orderId={}", event.orderId(), ex);
        }
    }

    @Async
    @EventListener
    public void onOrderRefundFactSynced(OrderRefundFactSyncedEvent event) {
        if (event == null || event.orderId() == null) {
            return;
        }
        recalculate(event.orderId());
    }

    /**
     * 受控归因更正由 Outbox dispatcher 同步投递；异常必须向上抛出，
     * 使既有 FAILED/retry/DEAD 机制能够可靠处理。
     */
    @EventListener
    public void onOrderAttributionReplayed(OrderAttributionReplayedEvent event) {
        if (event == null || event.orderId() == null) {
            return;
        }
        ColonelsettlementOrder order = orderReadFacade.findByOrderId(event.orderId());
        recalculateOrThrow(order, event.orderId());
    }

    private void recalculate(String orderId) {
        try {
            ColonelsettlementOrder order = orderReadFacade.findByOrderId(orderId);
            recalculateOrThrow(order, orderId);
        } catch (Exception ex) {
            log.warn("Performance calculation failed, orderId={}", orderId, ex);
        }
    }

    private void recalculateOrThrow(ColonelsettlementOrder order, String orderId) {
        if (order == null) {
            throw new IllegalStateException("Performance calculation order not found: " + orderId);
        }
        PerformanceRecord record = performanceCalculationApplicationService.upsertFromOrder(order);
        if (record == null) {
            return;
        }
        eventPublisher.publishEvent(new PerformanceCalculatedEvent(
                record.getOrderId(),
                record.getFinalChannelUserId(),
                record.getFinalRecruiterUserId(),
                nvl(record.getEstimateRecruiterCommission()),
                nvl(record.getEffectiveRecruiterCommission()),
                nvl(record.getEstimateChannelCommission()),
                nvl(record.getEffectiveChannelCommission()),
                nvl(record.getEstimateGrossProfit()),
                nvl(record.getEffectiveGrossProfit()),
                Boolean.TRUE.equals(record.getReversed()) ? "REVERSAL" : "NORMAL",
                Boolean.TRUE.equals(record.getReversed())));
    }

    private long nvl(Long value) {
        return value == null ? 0L : value;
    }
}
