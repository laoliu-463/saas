package com.colonel.saas.domain.order.application;

import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.sample.facade.SampleHomeworkFacade;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.event.OrderSyncedEvent;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.service.OperationLogService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * 订单同步后驱动寄样交作业（DDD-SAMPLE-004 订单域编排入口）。
 */
@Service
public class OrderSampleHomeworkBridge {

    private final DddRefactorProperties dddRefactorProperties;
    private final ColonelsettlementOrderMapper orderMapper;
    private final SampleHomeworkFacade sampleHomeworkFacade;
    private final OperationLogService operationLogService;

    public OrderSampleHomeworkBridge(
            DddRefactorProperties dddRefactorProperties,
            ColonelsettlementOrderMapper orderMapper,
            SampleHomeworkFacade sampleHomeworkFacade,
            OperationLogService operationLogService) {
        this.dddRefactorProperties = dddRefactorProperties;
        this.orderMapper = orderMapper;
        this.sampleHomeworkFacade = sampleHomeworkFacade;
        this.operationLogService = operationLogService;
    }

    public boolean isEventDrivenHomeworkEnabled() {
        return dddRefactorProperties.isEnabled()
                && dddRefactorProperties.getSampleHomeworkEvent().isEnabled();
    }

    public void completeHomeworkForSyncedOrder(OrderSyncedEvent event) {
        if (!isEventDrivenHomeworkEnabled() || event == null) {
            return;
        }
        ColonelsettlementOrder order = resolveOrder(event);
        if (order == null) {
            return;
        }
        int completed = sampleHomeworkFacade.completePendingHomeworkByOrder(order);
        if (completed > 0) {
            recordAttributionFollowUp(order);
        }
    }

    ColonelsettlementOrder resolveOrder(OrderSyncedEvent event) {
        UUID rowId = event.orderRowId();
        if (rowId != null) {
            ColonelsettlementOrder byId = orderMapper.selectById(rowId);
            if (byId != null) {
                return byId;
            }
        }
        if (StringUtils.hasText(event.orderId())) {
            return orderMapper.findByOrderId(event.orderId());
        }
        return null;
    }

    private void recordAttributionFollowUp(ColonelsettlementOrder order) {
        operationLogService.recordSystemAction(
                order.getUserId() != null ? order.getUserId() : order.getChannelUserId(),
                "订单归因",
                "完成寄样作业",
                "POST",
                "order",
                order.getOrderId(),
                resolveTargetName(order),
                "订单归因副作用: completePendingHomeworkByOrder");
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
