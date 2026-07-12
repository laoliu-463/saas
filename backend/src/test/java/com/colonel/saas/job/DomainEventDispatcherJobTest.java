package com.colonel.saas.job;

import com.colonel.saas.domain.event.ConfigChangedEventPayload;
import com.colonel.saas.domain.event.ConfigChangedEventRouter;
import com.colonel.saas.domain.event.DomainEventOutbox;
import com.colonel.saas.domain.event.DomainEventOutboxService;
import com.colonel.saas.domain.event.ProductDomainEventOutboxRouter;
import com.colonel.saas.domain.order.event.OrderDomainEventOutboxRouter;
import com.colonel.saas.domain.sample.event.SampleDomainEventOutboxRouter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DomainEventDispatcherJobTest {

    @Mock
    private DomainEventOutboxService domainEventOutboxService;
    @Mock
    private ConfigChangedEventRouter configChangedEventRouter;
    @Mock
    private ProductDomainEventOutboxRouter productDomainEventOutboxRouter;
    @Mock
    private SampleDomainEventOutboxRouter sampleDomainEventOutboxRouter;
    @Mock
    private OrderDomainEventOutboxRouter orderDomainEventOutboxRouter;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private DomainEventDispatcherJob job;

    @BeforeEach
    void setUp() {
        job = new DomainEventDispatcherJob(
                domainEventOutboxService,
                configChangedEventRouter,
                productDomainEventOutboxRouter,
                sampleDomainEventOutboxRouter,
                orderDomainEventOutboxRouter,
                objectMapper,
                false);
    }

    @Test
    @DisplayName("无待处理事件时不做任何操作")
    void dispatchPendingEvents_noPendingEvents_doesNothing() throws Exception {
        when(domainEventOutboxService.lockPendingEvents(anyInt(), anyInt())).thenReturn(List.of());

        job.dispatchPendingEvents();

        verify(domainEventOutboxService).lockPendingEvents(3, 20);
        verifyNoInteractionsWithRouters();
    }

    @Test
    @DisplayName("ConfigChanged 事件路由到 ConfigChangedEventRouter")
    void dispatchPendingEvents_configChanged_routesToConfigRouter() throws Exception {
        String payload = "{\"items\":[{\"configKey\":\"app.test\",\"group\":\"APP\",\"newValue\":\"v1\"}]}";
        DomainEventOutbox event = buildOutbox(ConfigChangedEventPayload.EVENT_TYPE, payload);

        when(domainEventOutboxService.lockPendingEvents(anyInt(), anyInt())).thenReturn(List.of(event));

        job.dispatchPendingEvents();

        verify(configChangedEventRouter).dispatch(any(ConfigChangedEventPayload.class));
        verify(domainEventOutboxService).markPublished(event.getEventId(), 0);
    }

    @Test
    @DisplayName("ConfigChanged 事件路由失败时标记失败并进入重试")
    void dispatchPendingEvents_configChangedDispatchFails_marksFailed() throws Exception {
        String payload = "{\"items\":[{\"configKey\":\"app.test\",\"group\":\"APP\",\"newValue\":\"v1\"}]}";
        DomainEventOutbox event = buildOutbox(ConfigChangedEventPayload.EVENT_TYPE, payload);

        when(domainEventOutboxService.lockPendingEvents(anyInt(), anyInt())).thenReturn(List.of(event));
        doThrow(new RuntimeException("config boom")).when(configChangedEventRouter)
                .dispatch(any(ConfigChangedEventPayload.class));

        job.dispatchPendingEvents();

        verify(domainEventOutboxService).markFailed(event.getEventId(), 1, "config boom", 3);
        verify(domainEventOutboxService, never()).markPublished(any(), anyInt());
    }

    @Test
    @DisplayName("Product 事件路由到 ProductDomainEventOutboxRouter")
    void dispatchPendingEvents_productEvent_routesToProductRouter() throws Exception {
        DomainEventOutbox event = buildOutbox("ProductCreated", "{\"productId\":\"abc\"}");

        when(domainEventOutboxService.lockPendingEvents(anyInt(), anyInt())).thenReturn(List.of(event));
        when(productDomainEventOutboxRouter.supports("ProductCreated")).thenReturn(true);

        job.dispatchPendingEvents();

        verify(productDomainEventOutboxRouter).dispatch(event);
        verify(domainEventOutboxService).markPublished(event.getEventId(), 0);
    }

    @Test
    @DisplayName("Sample 事件路由到 SampleDomainEventOutboxRouter")
    void dispatchPendingEvents_sampleEvent_routesToSampleRouter() throws Exception {
        DomainEventOutbox event = buildOutbox("SampleCreated", "{\"sampleRequestId\":\"123\"}");

        when(domainEventOutboxService.lockPendingEvents(anyInt(), anyInt())).thenReturn(List.of(event));
        when(productDomainEventOutboxRouter.supports("SampleCreated")).thenReturn(false);
        when(sampleDomainEventOutboxRouter.supports("SampleCreated")).thenReturn(true);

        job.dispatchPendingEvents();

        verify(sampleDomainEventOutboxRouter).dispatch(event);
        verify(domainEventOutboxService).markPublished(event.getEventId(), 0);
    }

    @Test
    @DisplayName("Order 事件路由到 OrderDomainEventOutboxRouter")
    void dispatchPendingEvents_orderEvent_routesToOrderRouter() throws Exception {
        DomainEventOutbox event = buildOutbox("OrderSynced", "{\"orderId\":\"ORD-1\"}");

        when(domainEventOutboxService.lockPendingEvents(anyInt(), anyInt())).thenReturn(List.of(event));
        when(productDomainEventOutboxRouter.supports("OrderSynced")).thenReturn(false);
        when(sampleDomainEventOutboxRouter.supports("OrderSynced")).thenReturn(false);
        when(orderDomainEventOutboxRouter.supports("OrderSynced")).thenReturn(true);

        job.dispatchPendingEvents();

        verify(orderDomainEventOutboxRouter).dispatch(event);
        verify(domainEventOutboxService).markPublished(event.getEventId(), 0);
    }

    @Test
    @DisplayName("派发失败时标记为失败并记录重试次数")
    void dispatchPendingEvents_dispatchFails_marksFailed() throws Exception {
        DomainEventOutbox event = buildOutbox("ProductCreated", "{}");
        event.setRetryCount(2);

        when(domainEventOutboxService.lockPendingEvents(anyInt(), anyInt())).thenReturn(List.of(event));
        when(productDomainEventOutboxRouter.supports("ProductCreated")).thenReturn(true);
        doThrow(new RuntimeException("boom")).when(productDomainEventOutboxRouter).dispatch(event);

        job.dispatchPendingEvents();

        verify(domainEventOutboxService).markFailed(event.getEventId(), 3, "boom", 3);
        verify(domainEventOutboxService, never()).markPublished(any(), anyInt());
    }

    @Test
    @DisplayName("首次失败时 retryCount 从 1 开始")
    void dispatchPendingEvents_firstFailure_retryCountStartsAtOne() throws Exception {
        DomainEventOutbox event = buildOutbox("SampleApproved", "{}");
        event.setRetryCount(null);

        when(domainEventOutboxService.lockPendingEvents(anyInt(), anyInt())).thenReturn(List.of(event));
        when(productDomainEventOutboxRouter.supports("SampleApproved")).thenReturn(false);
        when(sampleDomainEventOutboxRouter.supports("SampleApproved")).thenReturn(true);
        doThrow(new RuntimeException("oops")).when(sampleDomainEventOutboxRouter).dispatch(event);

        job.dispatchPendingEvents();

        verify(domainEventOutboxService).markFailed(event.getEventId(), 1, "oops", 3);
    }

    @Test
    @DisplayName("多个事件按顺序处理")
    void dispatchPendingEvents_multipleEvents_processesInOrder() throws Exception {
        DomainEventOutbox configEvent = buildOutbox(ConfigChangedEventPayload.EVENT_TYPE, "{\"items\":[]}");
        DomainEventOutbox sampleEvent = buildOutbox("SampleShipped", "{}");

        when(domainEventOutboxService.lockPendingEvents(anyInt(), anyInt()))
                .thenReturn(List.of(configEvent, sampleEvent));
        when(productDomainEventOutboxRouter.supports("SampleShipped")).thenReturn(false);
        when(sampleDomainEventOutboxRouter.supports("SampleShipped")).thenReturn(true);

        job.dispatchPendingEvents();

        InOrder order = inOrder(configChangedEventRouter, sampleDomainEventOutboxRouter, domainEventOutboxService);
        order.verify(configChangedEventRouter).dispatch(any());
        order.verify(domainEventOutboxService).markPublished(configEvent.getEventId(), 0);
        order.verify(sampleDomainEventOutboxRouter).dispatch(sampleEvent);
        order.verify(domainEventOutboxService).markPublished(sampleEvent.getEventId(), 0);
    }

    private DomainEventOutbox buildOutbox(String eventType, String payload) {
        DomainEventOutbox event = new DomainEventOutbox();
        event.setEventId(UUID.randomUUID());
        event.setEventType(eventType);
        event.setPayload(payload);
        event.setRetryCount(0);
        event.setMaxRetry(3);
        return event;
    }

    private void verifyNoInteractionsWithRouters() throws Exception {
        verify(configChangedEventRouter, never()).dispatch(any());
        verify(productDomainEventOutboxRouter, never()).dispatch(any());
        verify(sampleDomainEventOutboxRouter, never()).dispatch(any());
        verify(orderDomainEventOutboxRouter, never()).dispatch(any());
    }

    @Test
    @DisplayName("启用 dryRun 时不执行路由投递也不修改 Outbox 状态")
    void dispatchPendingEvents_dryRunEnabled_doesNotDispatchOrUpdateStatus() throws Exception {
        // 重新构造一个 dryRun=true 的 Job 实例
        DomainEventDispatcherJob dryRunJob = new DomainEventDispatcherJob(
                domainEventOutboxService,
                configChangedEventRouter,
                productDomainEventOutboxRouter,
                sampleDomainEventOutboxRouter,
                orderDomainEventOutboxRouter,
                objectMapper,
                true);

        DomainEventOutbox event = buildOutbox("ProductCreated", "{\"productId\":\"xyz\"}");
        when(domainEventOutboxService.lockPendingEvents(anyInt(), anyInt())).thenReturn(List.of(event));

        dryRunJob.dispatchPendingEvents();

        // Dry Run 模式下：不应调用任何路由器
        verifyNoInteractionsWithRouters();
        // 不应修改 Outbox 记录状态
        verify(domainEventOutboxService, never()).markPublished(any(), anyInt());
        verify(domainEventOutboxService, never()).markFailed(any(), anyInt(), anyString(), anyInt());
    }
}
