package com.colonel.saas.service;

import com.colonel.saas.domain.order.application.OrderAttributionReplayService;
import com.colonel.saas.domain.order.infrastructure.OrderSyncPersistenceService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderAttributionReplayServiceTest {

    @Mock
    private ColonelsettlementOrderMapper orderMapper;
    @Mock
    private AttributionService attributionService;
    @Mock
    private OrderSyncPersistenceService persistenceService;

    private OrderAttributionReplayService service;

    @BeforeEach
    void setUp() {
        service = new OrderAttributionReplayService(orderMapper, attributionService, persistenceService);
    }

    @Test
    void replay_shouldNormalizeNestedColonelInfoBeforeResolvingAttribution() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setId(UUID.randomUUID());
        order.setOrderId("o-1");
        order.setProductId("p-1");
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        order.setDeleted(0);
        order.setExtraData(Map.of(
                "colonel_order_info_second", Map.of(
                        "colonel_buyin_id", "second-buyin",
                        "activity_id", "3543332"
                )
        ));
        when(orderMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(order));

        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        UUID colonelUserId = UUID.randomUUID();
        when(attributionService.resolveAttribution(any(), any())).thenReturn(
                AttributionService.AttributionResult.attributed(
                        userId,
                        deptId,
                        userId,
                        null,
                        null,
                        "3543332",
                        colonelUserId,
                        AttributionService.REASON_COLONEL_ORDER_INFO
                )
        );
        when(persistenceService.getUserName(userId)).thenReturn("渠道A");
        when(persistenceService.getUserName(colonelUserId)).thenReturn("团长A");

        OrderAttributionReplayService.ReplayResult result =
                service.replay(null, AttributionService.REASON_COLONEL_MAPPING_NOT_FOUND, 20, false);

        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.attributed()).isEqualTo(1);
        assertThat(result.unattributed()).isEqualTo(0);
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.safeToUpdate()).isEqualTo(0);

        ArgumentCaptor<Map<String, Object>> sourceCaptor = ArgumentCaptor.forClass(Map.class);
        verify(attributionService).resolveAttribution(any(), sourceCaptor.capture());
        assertThat(sourceCaptor.getValue().get("second_colonel_buyin_id")).isEqualTo("second-buyin");
        assertThat(sourceCaptor.getValue().get("second_colonel_activity_id")).isEqualTo("3543332");

        ArgumentCaptor<ColonelsettlementOrder> orderCaptor = ArgumentCaptor.forClass(ColonelsettlementOrder.class);
        verify(persistenceService).persistOrder(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getAttributionStatus()).isEqualTo(AttributionService.STATUS_ATTRIBUTED);
        assertThat(orderCaptor.getValue().getActivityId()).isEqualTo("3543332");
        assertThat(orderCaptor.getValue().getChannelUserName()).isEqualTo("渠道A");
        assertThat(orderCaptor.getValue().getColonelUserName()).isEqualTo("团长A");
    }

    @Test
    void replay_shouldSupportDryRunWithoutPersisting() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setOrderId("o-2");
        order.setDeleted(0);
        order.setUpdateTime(LocalDateTime.now());
        when(orderMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(order));
        when(attributionService.resolveAttribution(any(), any())).thenReturn(
                AttributionService.AttributionResult.unattributed(
                        null,
                        null,
                        null,
                        null,
                        AttributionService.REASON_COLONEL_MAPPING_NOT_FOUND
                )
        );

        OrderAttributionReplayService.ReplayResult result = service.replay(List.of("o-2"), null, null, true);

        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.updated()).isEqualTo(0);
        assertThat(result.stillUnattributed()).isEqualTo(1);
        verify(attributionService).resolveAttribution(any(), any());
    }

    @Test
    void replay_shouldReportUnsafeWhenNativeMappingCreatedAfterOrder() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setOrderId("o-3");
        order.setProductId("3816127512791089531");
        order.setActivityId("3859423");
        order.setCreateTime(LocalDateTime.of(2026, 5, 10, 1, 4, 11));
        order.setUpdateTime(LocalDateTime.now());
        order.setDeleted(0);
        when(orderMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(order));

        when(attributionService.resolveAttribution(any(), any())).thenReturn(
                AttributionService.AttributionResult.attributed(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        null,
                        null,
                        "3859423",
                        null,
                        AttributionService.REASON_COLONEL_ORDER_INFO,
                        new AttributionService.NativeMappingTrace(
                                true,
                                true,
                                false,
                                true,
                                LocalDateTime.of(2026, 5, 10, 6, 41, 19)
                        )
                )
        );

        OrderAttributionReplayService.ReplayResult result = service.replay(List.of("o-3"), null, null, true);

        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.nativeKeyMatched()).isEqualTo(1);
        assertThat(result.safeToUpdate()).isEqualTo(0);
        assertThat(result.unsafeBecauseCreatedAfterOrder()).isEqualTo(1);
        assertThat(result.colonelBuyinIdMismatch()).isEqualTo(1);
        assertThat(result.updated()).isEqualTo(0);
    }
}
