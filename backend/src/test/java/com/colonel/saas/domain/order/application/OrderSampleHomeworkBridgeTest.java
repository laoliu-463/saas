package com.colonel.saas.domain.order.application;

import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.event.OrderSyncedEvent;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.service.SampleLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderSampleHomeworkBridgeTest {

    @Mock
    private ColonelsettlementOrderMapper orderMapper;
    @Mock
    private SampleLifecycleService sampleLifecycleService;
    @Mock
    private OperationLogService operationLogService;

    private DddRefactorProperties dddRefactorProperties;
    private OrderSampleHomeworkBridge bridge;

    @BeforeEach
    void setUp() {
        dddRefactorProperties = new DddRefactorProperties();
        bridge = new OrderSampleHomeworkBridge(
                dddRefactorProperties,
                orderMapper,
                sampleLifecycleService,
                operationLogService);
    }

    @Test
    void completeHomework_whenSwitchOff_shouldDoNothing() {
        bridge.completeHomeworkForSyncedOrder(sampleEvent());

        verifyNoInteractions(orderMapper, sampleLifecycleService, operationLogService);
    }

    @Test
    void completeHomework_whenSwitchOn_shouldCompleteHomeworkAndLog() {
        dddRefactorProperties.setEnabled(true);
        dddRefactorProperties.getSampleHomeworkEvent().setEnabled(true);

        OrderSyncedEvent event = sampleEvent();
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setId(event.orderRowId());
        order.setOrderId(event.orderId());
        order.setUserId(UUID.randomUUID());
        order.setProductName("测试商品");
        when(orderMapper.selectById(event.orderRowId())).thenReturn(order);

        bridge.completeHomeworkForSyncedOrder(event);

        verify(sampleLifecycleService).completePendingHomeworkByOrder(order);
        verify(operationLogService).recordSystemAction(
                eq(order.getUserId()),
                eq("订单归因"),
                eq("完成寄样作业"),
                eq("POST"),
                eq("order"),
                eq(order.getOrderId()),
                eq("测试商品"),
                eq("订单归因副作用: completePendingHomeworkByOrder"));
    }

    @Test
    void completeHomework_whenOrderMissing_shouldSkipHomework() {
        dddRefactorProperties.setEnabled(true);
        dddRefactorProperties.getSampleHomeworkEvent().setEnabled(true);
        when(orderMapper.selectById(any())).thenReturn(null);
        when(orderMapper.findByOrderId(any())).thenReturn(null);

        bridge.completeHomeworkForSyncedOrder(sampleEvent());

        verify(sampleLifecycleService, never()).completePendingHomeworkByOrder(any());
        verifyNoInteractions(operationLogService);
    }

    private static OrderSyncedEvent sampleEvent() {
        return new OrderSyncedEvent(
                "ORD-SAMPLE-004",
                UUID.randomUUID(),
                true,
                "ATTRIBUTED",
                100L,
                100L,
                80L,
                10L,
                8L,
                2L,
                1L,
                5L,
                1L,
                0L,
                1,
                LocalDateTime.now(),
                "talent-1",
                Map.of("author_id", "talent-1", "product_id", "prod-1"));
    }
}
