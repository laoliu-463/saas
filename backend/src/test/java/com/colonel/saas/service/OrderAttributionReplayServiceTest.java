package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.domain.order.application.OrderAttributionRouter;
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
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderAttributionReplayServiceTest {

    @Mock
    private ColonelsettlementOrderMapper orderMapper;
    @Mock
    private OrderAttributionRouter orderAttributionRouter;
    @Mock
    private OrderSyncPersistenceService persistenceService;

    private OrderAttributionReplayService service;

    @BeforeEach
    void setUp() {
        service = new OrderAttributionReplayService(orderMapper, orderAttributionRouter, persistenceService);
    }

    @Test
    void replay_shouldUseOrderAttributionRouterAndPersistDualStatuses() {
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
        AttributionService.AttributionResult attributionResult =
                AttributionService.AttributionResult.attributed(
                        userId,
                        deptId,
                        userId,
                        null,
                        null,
                        "3543332",
                        colonelUserId,
                        AttributionService.REASON_COLONEL_ORDER_INFO);
        when(orderAttributionRouter.resolveAndApply(any(), any(), nullable(String.class)))
                .thenAnswer(invocation -> {
                    ColonelsettlementOrder routedOrder = invocation.getArgument(0);
                    routedOrder.setChannelUserId(userId);
                    routedOrder.setChannelDeptId(deptId);
                    routedOrder.setUserId(userId);
                    routedOrder.setDeptId(deptId);
                    routedOrder.setColonelUserId(colonelUserId);
                    routedOrder.setActivityId("3543332");
                    routedOrder.setAttributionStatus(AttributionService.STATUS_ATTRIBUTED);
                    routedOrder.setChannelAttributionStatus("CHANNEL_ATTRIBUTED");
                    routedOrder.setRecruiterAttributionStatus("RECRUITER_ATTRIBUTED");
                    return attributionResult;
                });
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
        verify(orderAttributionRouter).resolveAndApply(
                any(), sourceCaptor.capture(), nullable(String.class));
        assertThat(sourceCaptor.getValue().get("second_colonel_buyin_id")).isEqualTo("second-buyin");
        assertThat(sourceCaptor.getValue().get("second_colonel_activity_id")).isEqualTo("3543332");

        ArgumentCaptor<ColonelsettlementOrder> orderCaptor = ArgumentCaptor.forClass(ColonelsettlementOrder.class);
        verify(persistenceService).persistOrder(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getAttributionStatus()).isEqualTo(AttributionService.STATUS_ATTRIBUTED);
        assertThat(orderCaptor.getValue().getChannelAttributionStatus()).isEqualTo("CHANNEL_ATTRIBUTED");
        assertThat(orderCaptor.getValue().getRecruiterAttributionStatus()).isEqualTo("RECRUITER_ATTRIBUTED");
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
        when(orderAttributionRouter.resolveAndApply(any(), any(), nullable(String.class))).thenReturn(
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
        verify(orderAttributionRouter).resolveAndApply(any(), any(), nullable(String.class));
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

        when(orderAttributionRouter.resolveAndApply(any(), any(), nullable(String.class))).thenReturn(
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
