package com.colonel.saas.listener;

import com.colonel.saas.domain.order.event.OrderRefundFactSyncedEvent;
import com.colonel.saas.domain.order.event.OrderAttributionReplayedEvent;
import com.colonel.saas.domain.order.facade.OrderReadFacade;
import com.colonel.saas.domain.performance.application.PerformanceCalculationApplicationService;
import com.colonel.saas.domain.performance.application.PerformanceCalculationExecutionService;
import com.colonel.saas.domain.performance.application.PerformanceRefundAdjustmentService;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.PerformanceRecord;
import com.colonel.saas.event.OrderSyncedEvent;
import com.colonel.saas.event.PerformanceCalculatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

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
    private final PerformanceCalculationExecutionService executionService;
    private final PerformanceRefundAdjustmentService refundAdjustmentService;

    public PerformanceRecordSyncListener(
            OrderReadFacade orderReadFacade,
            PerformanceCalculationApplicationService performanceCalculationApplicationService,
            ApplicationEventPublisher eventPublisher) {
        this(orderReadFacade, performanceCalculationApplicationService, eventPublisher, null, null);
    }

    @Autowired
    public PerformanceRecordSyncListener(
            OrderReadFacade orderReadFacade,
            PerformanceCalculationApplicationService performanceCalculationApplicationService,
            ApplicationEventPublisher eventPublisher,
            PerformanceCalculationExecutionService executionService,
            PerformanceRefundAdjustmentService refundAdjustmentService) {
        this.orderReadFacade = orderReadFacade;
        this.performanceCalculationApplicationService = performanceCalculationApplicationService;
        this.eventPublisher = eventPublisher;
        this.executionService = executionService;
        this.refundAdjustmentService = refundAdjustmentService;
    }

    @EventListener
    public void onOrderSynced(OrderSyncedEvent event) {
        if (event == null || event.orderId() == null) {
            return;
        }
        ColonelsettlementOrder order = orderReadFacade.findByOrderId(event.orderId());
        recalculateOrThrow(order, event.orderId(), "OrderSynced", event.orderVersion(),
                "OrderSynced:" + event.orderId() + ":" + event.orderVersion(), Map.of(), null);
    }

    @EventListener
    public void onOrderRefundFactSynced(OrderRefundFactSyncedEvent event) {
        if (event == null || event.orderId() == null) {
            return;
        }
        ColonelsettlementOrder order = orderReadFacade.findByOrderId(event.orderId());
        int refundVersion = refundVersion(event);
        recalculateOrThrow(order, event.orderId(), "OrderRefundFactSynced", refundVersion,
                "OrderRefundFactSynced:" + event.orderId() + ":" + refundEventIdentity(event),
                refundPayload(event),
                record -> {
                    if (refundAdjustmentService != null) {
                        refundAdjustmentService.recordRefund(record, event);
                    }
                });
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
        recalculateOrThrow(order, event.orderId(), "OrderAttributionReplayed", event.orderVersion(),
                "OrderAttributionReplayed:" + event.orderId() + ":" + event.orderVersion(), Map.of(), null);
    }

    private void recalculateOrThrow(
            ColonelsettlementOrder order,
            String orderId,
            String eventType,
            int eventVersion,
            String eventKey,
            Map<String, Object> eventPayload,
            Consumer<PerformanceRecord> postCalculation) {
        if (executionService != null && !executionService.start(
                eventKey, eventType, orderId, eventVersion, eventPayload)) {
            return;
        }
        try {
            if (order == null) {
                throw new IllegalStateException("Performance calculation order not found: " + orderId);
            }
            PerformanceRecord record = performanceCalculationApplicationService.upsertFromOrder(order);
            if (record != null) {
                if (postCalculation != null) {
                    postCalculation.accept(record);
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
            if (executionService != null) {
                executionService.markSucceeded(eventKey);
            }
        } catch (RuntimeException error) {
            if (executionService != null) {
                executionService.markFailed(eventKey, error);
            }
            throw error;
        }
    }

    private static int refundVersion(OrderRefundFactSyncedEvent event) {
        return refundEventIdentity(event).hashCode();
    }

    private static String refundEventIdentity(OrderRefundFactSyncedEvent event) {
        return event.refundId() == null || event.refundId().isBlank()
                ? String.valueOf(event.occurredAt())
                : event.refundId();
    }

    private static Map<String, Object> refundPayload(OrderRefundFactSyncedEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("refundId", event.refundId());
        payload.put("refundAmount", event.refundAmount());
        payload.put("previousStatus", event.previousStatus());
        payload.put("status", event.status());
        payload.put("flowPoint", event.flowPoint());
        payload.put("extraData", event.extraData());
        LocalDateTime occurredAt = event.occurredAt();
        payload.put("occurredAt", occurredAt == null ? null : occurredAt.toString());
        return payload;
    }

    private long nvl(Long value) {
        return value == null ? 0L : value;
    }
}
