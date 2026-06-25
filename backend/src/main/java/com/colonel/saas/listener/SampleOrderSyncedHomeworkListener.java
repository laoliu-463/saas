package com.colonel.saas.listener;

import com.colonel.saas.domain.order.application.OrderSampleHomeworkBridge;
import com.colonel.saas.event.OrderSyncedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * {@link OrderSyncedEvent} 异步入口，委派 {@link OrderSampleHomeworkBridge}（DDD-SAMPLE-004）。
 */
@Slf4j
@Component
public class SampleOrderSyncedHomeworkListener {

    private final OrderSampleHomeworkBridge orderSampleHomeworkBridge;

    public SampleOrderSyncedHomeworkListener(OrderSampleHomeworkBridge orderSampleHomeworkBridge) {
        this.orderSampleHomeworkBridge = orderSampleHomeworkBridge;
    }

    @Async
    @EventListener
    public void onOrderSynced(OrderSyncedEvent event) {
        if (event == null || !orderSampleHomeworkBridge.isEventDrivenHomeworkEnabled()) {
            return;
        }
        try {
            orderSampleHomeworkBridge.completeHomeworkForSyncedOrder(event);
        } catch (Exception ex) {
            log.warn("Sample homework event handling failed, orderId={}", event.orderId(), ex);
        }
    }
}
