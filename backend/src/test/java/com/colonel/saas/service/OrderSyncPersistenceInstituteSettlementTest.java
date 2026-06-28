package com.colonel.saas.service;

import com.colonel.saas.domain.order.infrastructure.OrderSyncPersistenceService;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.order.application.OrderAmountMappingRouter;
import com.colonel.saas.domain.order.event.InProcessOrderDomainEventPublisher;
import com.colonel.saas.domain.order.event.OrderDomainEventPublisher;
import com.colonel.saas.domain.order.event.OrderEventPayloadMapper;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.event.OrderSyncedEvent;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.mapper.OrderSyncDedupClaimMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderSyncPersistenceInstituteSettlementTest {

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

    private OrderDomainEventPublisher orderDomainEventPublisher;
    private OrderSyncPersistenceService service;

    @BeforeEach
    void setUp() {
        lenient().when(orderMapper.updateSyncedById(any(ColonelsettlementOrder.class))).thenReturn(1);
        DddRefactorProperties properties = new DddRefactorProperties();
        orderDomainEventPublisher = new InProcessOrderDomainEventPublisher(
                outboxEventAppender,
                eventPublisher,
                new ObjectMapper().registerModule(new JavaTimeModule()),
                properties);
        service = new OrderSyncPersistenceService(
                orderMapper,
                orderSyncDedupClaimMapper,
                pickSourceMappingService,
                merchantService,
                sampleLifecycleService,
                operationLogService,
                userDomainFacade,
                new OrderAmountMappingRouter(properties),
                orderDomainEventPublisher,
                new OrderEventPayloadMapper(),
                properties
        );
    }

    @Test
    void persistOrder_shouldProtectExistingSettlementTrackAndPublishEventFor1603Settlement() {
        ColonelsettlementOrder incoming = makeOrder("ORDER-1603-PERSIST");
        incoming.setSyncSource(OrderSyncPersistenceService.SYNC_SOURCE_INSTITUTE_SETTLEMENT);
        incoming.setSettleAmount(0L);
        incoming.setEffectiveServiceFee(0L);
        incoming.setEffectiveTechServiceFee(0L);
        incoming.setSettleTime(null);
        incoming.setFlowPoint("REFUND");

        ColonelsettlementOrder existing = makeOrder("ORDER-1603-PERSIST");
        existing.setId(UUID.randomUUID());
        existing.setCreateTime(LocalDateTime.of(2026, 6, 1, 10, 0));
        existing.setSettleAmount(2480L);
        existing.setEffectiveServiceFee(50L);
        existing.setEffectiveTechServiceFee(6L);
        existing.setSettleTime(LocalDateTime.of(2026, 6, 5, 10, 0));
        existing.setFlowPoint("SETTLE");

        when(orderSyncDedupClaimMapper.claim(incoming.getOrderId(), incoming.getId())).thenReturn(1);
        when(orderMapper.findByOrderId(incoming.getOrderId())).thenReturn(existing);

        boolean created = service.persistOrder(incoming);

        assertThat(created).isFalse();
        ArgumentCaptor<ColonelsettlementOrder> orderCaptor = ArgumentCaptor.forClass(ColonelsettlementOrder.class);
        verify(orderMapper).updateSyncedById(orderCaptor.capture());
        ColonelsettlementOrder updated = orderCaptor.getValue();
        assertThat(updated.getSettleAmount()).isEqualTo(2480L);
        assertThat(updated.getEffectiveServiceFee()).isEqualTo(50L);
        assertThat(updated.getEffectiveTechServiceFee()).isEqualTo(6L);
        assertThat(updated.getSettleTime()).isEqualTo(LocalDateTime.of(2026, 6, 5, 10, 0));
        assertThat(updated.getFlowPoint()).isEqualTo("REFUND");

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(OrderSyncedEvent.class);
        assertThat(((OrderSyncedEvent) eventCaptor.getValue()).orderId()).isEqualTo("ORDER-1603-PERSIST");
    }

    @Test
    void persistOrder_shouldClearStaleTechFeeWhen1603ReturnsExplicitZeroSettlementTechFee() {
        ColonelsettlementOrder incoming = makeOrder("ORDER-1603-ZERO-TECH");
        incoming.setSyncSource(OrderSyncPersistenceService.SYNC_SOURCE_INSTITUTE_SETTLEMENT);
        incoming.setSettleAmount(240L);
        incoming.setEffectiveServiceFee(2L);
        incoming.setEffectiveTechServiceFee(0L);
        incoming.setSettleColonelTechServiceFee(null);
        incoming.setExtraData(Map.of(
                "colonel_order_info", Map.of(
                        "real_commission", 2L,
                        "tech_service_fee", 2L,
                        "settled_tech_service_fee", 0L
                )
        ));

        ColonelsettlementOrder existing = makeOrder("ORDER-1603-ZERO-TECH");
        existing.setId(UUID.randomUUID());
        existing.setCreateTime(LocalDateTime.of(2026, 6, 1, 10, 0));
        existing.setSettleAmount(240L);
        existing.setEffectiveServiceFee(2L);
        existing.setEffectiveTechServiceFee(2L);
        existing.setSettleColonelTechServiceFee(2L);

        when(orderSyncDedupClaimMapper.claim(incoming.getOrderId(), incoming.getId())).thenReturn(1);
        when(orderMapper.findByOrderId(incoming.getOrderId())).thenReturn(existing);

        service.persistOrder(incoming);

        ArgumentCaptor<ColonelsettlementOrder> orderCaptor = ArgumentCaptor.forClass(ColonelsettlementOrder.class);
        verify(orderMapper).updateSyncedById(orderCaptor.capture());
        ColonelsettlementOrder updated = orderCaptor.getValue();
        assertThat(updated.getEffectiveTechServiceFee()).isZero();
        assertThat(updated.getSettleColonelTechServiceFee()).isNull();
    }

    private ColonelsettlementOrder makeOrder(String orderId) {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setId(UUID.randomUUID());
        order.setOrderId(orderId);
        order.setPickSource("usr_ABC12345_1712000000");
        order.setShopId(1L);
        order.setShopName("Test Shop");
        return order;
    }
}
