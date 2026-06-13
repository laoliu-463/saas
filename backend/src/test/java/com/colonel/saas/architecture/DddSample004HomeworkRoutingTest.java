package com.colonel.saas.architecture;

import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.order.application.OrderAmountMappingRouter;
import com.colonel.saas.domain.order.event.InProcessOrderDomainEventPublisher;
import com.colonel.saas.domain.order.event.OrderDomainEventPublisher;
import com.colonel.saas.domain.order.event.OrderEventPayloadMapper;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.mapper.OrderSyncDedupClaimMapper;
import com.colonel.saas.service.MerchantService;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.service.OrderSyncPersistenceService;
import com.colonel.saas.service.PickSourceMappingService;
import com.colonel.saas.service.SampleLifecycleService;
import com.colonel.saas.service.MerchantService;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.mapper.OrderSyncDedupClaimMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DDD-SAMPLE-004：寄样交作业路由 — 开关 on 时持久化层跳过同步交作业。
 */
@ExtendWith(MockitoExtension.class)
class DddSample004HomeworkRoutingTest {

    @Mock
    private ColonelsettlementOrderMapper orderMapper;
    @Mock
    private OrderSyncDedupClaimMapper orderSyncDedupClaimMapper;
    @Mock
    private PickSourceMappingService pickSourceMappingService;
    @Mock
    private MerchantService merchantService;
    @Mock
    private SampleLifecycleService sampleLifecycleService;
    @Mock
    private OperationLogService operationLogService;
    @Mock
    private UserDomainFacade userDomainFacade;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private com.colonel.saas.domain.event.OutboxEventAppender outboxEventAppender;

    private DddRefactorProperties dddRefactorProperties;
    private OrderDomainEventPublisher orderDomainEventPublisher;
    private OrderSyncPersistenceService persistenceService;

    @BeforeEach
    void setUp() {
        dddRefactorProperties = new DddRefactorProperties();
        InProcessOrderDomainEventPublisher inProcessPublisher =
                new InProcessOrderDomainEventPublisher(eventPublisher);
        orderDomainEventPublisher = new OrderDomainEventPublisher(
                outboxEventAppender,
                eventPublisher,
                inProcessPublisher,
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()),
                dddRefactorProperties);
        persistenceService = new OrderSyncPersistenceService(
                orderMapper,
                orderSyncDedupClaimMapper,
                pickSourceMappingService,
                merchantService,
                sampleLifecycleService,
                operationLogService,
                userDomainFacade,
                new OrderAmountMappingRouter(dddRefactorProperties),
                orderDomainEventPublisher,
                new OrderEventPayloadMapper(),
                dddRefactorProperties);
    }

    @Test
    @DisplayName("sample-homework-event 关闭时 persistOrder 仍同步完成寄样作业")
    void persistOrder_legacyPath_stillCompletesHomeworkSynchronously() {
        ColonelsettlementOrder order = sampleOrder();
        when(orderSyncDedupClaimMapper.claim(order.getOrderId(), order.getId())).thenReturn(1);
        when(orderMapper.findByOrderId(order.getOrderId())).thenReturn(null);
        when(orderMapper.insertIgnoreByOrderId(order)).thenReturn(1);

        persistenceService.persistOrder(order);

        verify(sampleLifecycleService).completePendingHomeworkByOrder(order);
    }

    @Test
    @DisplayName("sample-homework-event 开启时 persistOrder 跳过同步寄样交作业")
    void persistOrder_eventDrivenPath_skipsSynchronouslyHomework() {
        dddRefactorProperties.setEnabled(true);
        dddRefactorProperties.getSampleHomeworkEvent().setEnabled(true);

        ColonelsettlementOrder order = sampleOrder();
        when(orderSyncDedupClaimMapper.claim(order.getOrderId(), order.getId())).thenReturn(1);
        when(orderMapper.findByOrderId(order.getOrderId())).thenReturn(null);
        when(orderMapper.insertIgnoreByOrderId(order)).thenReturn(1);

        persistenceService.persistOrder(order);

        verify(sampleLifecycleService, never()).completePendingHomeworkByOrder(order);
    }

    private static ColonelsettlementOrder sampleOrder() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setId(UUID.randomUUID());
        order.setOrderId("ORD-004");
        order.setChannelUserId(UUID.randomUUID());
        order.setUserId(order.getChannelUserId());
        order.setProductId("prod-1");
        return order;
    }
}
