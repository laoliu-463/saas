package com.colonel.saas.listener;

import com.colonel.saas.domain.order.event.OrderRefundFactSyncedEvent;
import com.colonel.saas.domain.order.application.OrderAttributionRouter;
import com.colonel.saas.domain.order.facade.OrderReadFacade;
import com.colonel.saas.domain.performance.application.PerformanceCalculationApplicationService;
import com.colonel.saas.domain.product.event.ProductOwnerChangedEvent;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.PerformanceRecord;
import com.colonel.saas.event.OrderSyncedEvent;
import com.colonel.saas.event.PerformanceCalculatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

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
    private final OrderAttributionRouter orderAttributionRouter;
    private final PerformanceCalculationApplicationService performanceCalculationApplicationService;
    private final ApplicationEventPublisher eventPublisher;

    public PerformanceRecordSyncListener(
            OrderReadFacade orderReadFacade,
            OrderAttributionRouter orderAttributionRouter,
            PerformanceCalculationApplicationService performanceCalculationApplicationService,
            ApplicationEventPublisher eventPublisher) {
        this.orderReadFacade = orderReadFacade;
        this.orderAttributionRouter = orderAttributionRouter;
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
            recalculate(order, event.orderId());
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
     * 商品负责人变更后，重算该活动商品下仍未结算的订单。
     *
     * <p>商品负责人是业绩域的默认招商归属来源。负责人变更只影响未结算订单，
     * 已结算订单保留历史归属，避免改写已结算业绩。</p>
     */
    @Async
    @EventListener
    public void onProductOwnerChanged(ProductOwnerChangedEvent event) {
        if (event == null || event.activityId() == null || event.productId() == null) {
            return;
        }
        try {
            List<ColonelsettlementOrder> orders = orderReadFacade.findUnsettledOrdersByActivityAndProduct(
                    event.activityId(), event.productId());
            if (orders == null) {
                return;
            }
            for (ColonelsettlementOrder order : orders) {
                if (order == null || order.getOrderId() == null) {
                    continue;
                }
                try {
                    // 商品负责人变更后，订单中保存的是旧默认归属快照；未结算订单必须按当前商品负责人重新解析，
                    // 再交给业绩域计算最终归属。已结算订单已在查询层排除。
                    orderAttributionRouter.resolveAndApply(
                            order, order.getExtraData(), order.getTalentName());
                    recalculate(order, order.getOrderId());
                } catch (Exception ex) {
                    log.warn("Performance recalculation failed after product owner change, orderId={}",
                            order.getOrderId(), ex);
                }
            }
        } catch (Exception ex) {
            log.warn("Performance recalculation lookup failed after product owner change, activityId={}, productId={}",
                    event.activityId(), event.productId(), ex);
        }
    }

    private void recalculate(String orderId) {
        try {
            ColonelsettlementOrder order = orderReadFacade.findByOrderId(orderId);
            recalculate(order, orderId);
        } catch (Exception ex) {
            log.warn("Performance calculation failed, orderId={}", orderId, ex);
        }
    }

    private void recalculate(ColonelsettlementOrder order, String orderId) {
        if (order == null) {
            log.warn("Performance calculation skipped, order not found: {}", orderId);
            return;
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
