package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.domain.order.application.OrderDefaultAttributionResolver;
import com.colonel.saas.domain.order.policy.OrderDefaultAttributionResult;
import com.colonel.saas.domain.order.policy.OrderLinkAttributionResolution;
import com.colonel.saas.domain.order.policy.OrderLinkAttributionResolution.Status;
import com.colonel.saas.domain.performance.application.PerformanceCalculationApplicationService;
import com.colonel.saas.domain.shared.attribution.AttributionOwnerType;
import com.colonel.saas.domain.shared.attribution.AttributionSource;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderAttributionReplayServiceTest {

    @Mock
    private ColonelsettlementOrderMapper orderMapper;
    @Mock
    private OrderDefaultAttributionResolver defaultAttributionResolver;
    @Mock
    private OrderSyncPersistenceService persistenceService;
    @Mock
    private PerformanceCalculationApplicationService performanceService;

    private OrderAttributionReplayService service;

    @BeforeEach
    void setUp() {
        service = new OrderAttributionReplayService(
                orderMapper,
                defaultAttributionResolver,
                persistenceService,
                performanceService);
    }

    @Test
    void replayShouldUseDefaultResolverThenPersistOrderAndUpsertPerformance() {
        LocalDateTime businessTime = LocalDateTime.of(2026, 7, 16, 14, 6, 24);
        ColonelsettlementOrder order = order("o-1", businessTime);
        order.setExtraData(Map.of(
                "colonel_order_info_second", Map.of(
                        "colonel_buyin_id", "second-buyin",
                        "activity_id", "3543332"
                )));
        when(orderMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(order));

        UUID channelUserId = UUID.randomUUID();
        UUID recruiterUserId = UUID.randomUUID();
        UUID channelDeptId = UUID.randomUUID();
        when(defaultAttributionResolver.resolve(eq(order), anyMap())).thenReturn(attributed(
                channelUserId,
                channelDeptId,
                recruiterUserId,
                new OrderLinkAttributionResolution(
                        Status.UNIQUE, recruiterUserId, null, AttributionOwnerType.RECRUITER,
                        AttributionSource.PICK_SOURCE, "UNIQUE_LINK_OWNER", true, false,
                        businessTime.minusDays(1))));
        when(persistenceService.getUserName(channelUserId)).thenReturn("渠道A");
        when(persistenceService.getUserName(recruiterUserId)).thenReturn("招商A");

        OrderAttributionReplayService.ReplayResult result =
                service.replay(List.of(order.getOrderId()), "verified role-aware correction", 1, false);

        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.attributed()).isEqualTo(1);
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.decisions()).singleElement()
                .extracting(
                        OrderAttributionReplayService.ReplayDecision::safe,
                        OrderAttributionReplayService.ReplayDecision::recruiterUserId,
                        OrderAttributionReplayService.ReplayDecision::recruiterSource)
                .containsExactly(true, recruiterUserId, AttributionSource.PICK_SOURCE);

        ArgumentCaptor<Map<String, Object>> sourceCaptor = ArgumentCaptor.forClass(Map.class);
        verify(defaultAttributionResolver).resolve(eq(order), sourceCaptor.capture());
        assertThat(sourceCaptor.getValue().get("second_colonel_buyin_id")).isEqualTo("second-buyin");
        assertThat(sourceCaptor.getValue().get("second_colonel_activity_id")).isEqualTo("3543332");

        InOrder writes = inOrder(persistenceService, performanceService);
        writes.verify(persistenceService).persistOrder(order);
        writes.verify(performanceService).upsertFromOrder(order);
        assertThat(order.getAttributionStatus()).isEqualTo(AttributionService.STATUS_ATTRIBUTED);
        assertThat(order.getRecruiterAttributionSource()).isEqualTo(AttributionSource.PICK_SOURCE);
        assertThat(order.getChannelUserName()).isEqualTo("渠道A");
        assertThat(order.getColonelUserName()).isEqualTo("招商A");
    }

    @Test
    void replayDryRunShouldNotWriteOrderOrPerformance() {
        ColonelsettlementOrder order = order("o-2", LocalDateTime.of(2026, 7, 16, 14, 6, 24));
        when(orderMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(order));
        when(defaultAttributionResolver.resolve(eq(order), anyMap())).thenReturn(unattributed());

        OrderAttributionReplayService.ReplayResult result =
                service.replay(List.of(order.getOrderId()), null, null, true);

        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.updated()).isZero();
        assertThat(result.stillUnattributed()).isEqualTo(1);
        verify(persistenceService, never()).persistOrder(any());
        verify(performanceService, never()).upsertFromOrder(any());
    }

    @Test
    void replayShouldSkipMappingCreatedAfterOrderBusinessTime() {
        LocalDateTime businessTime = LocalDateTime.of(2026, 5, 10, 1, 4, 11);
        ColonelsettlementOrder order = order("o-3", businessTime);
        when(orderMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(order));
        UUID recruiterUserId = UUID.randomUUID();
        when(defaultAttributionResolver.resolve(eq(order), anyMap())).thenReturn(attributed(
                null,
                null,
                recruiterUserId,
                new OrderLinkAttributionResolution(
                        Status.UNIQUE, recruiterUserId, null, AttributionOwnerType.RECRUITER,
                        AttributionSource.NATIVE_UNIQUE_LINK_OWNER, "UNIQUE_LINK_OWNER", true, true,
                        businessTime.plusHours(5))));

        OrderAttributionReplayService.ReplayResult result =
                service.replay(List.of(order.getOrderId()), null, null, true);

        assertThat(result.nativeKeyMatched()).isEqualTo(1);
        assertThat(result.safeToUpdate()).isZero();
        assertThat(result.unsafeBecauseCreatedAfterOrder()).isEqualTo(1);
        assertThat(result.colonelBuyinIdMismatch()).isEqualTo(1);
        assertThat(result.updated()).isZero();
        assertThat(result.decisions()).singleElement()
                .extracting(OrderAttributionReplayService.ReplayDecision::safe)
                .isEqualTo(false);
    }

    private ColonelsettlementOrder order(String orderId, LocalDateTime businessTime) {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setId(UUID.randomUUID());
        order.setOrderId(orderId);
        order.setProductId("p-1");
        order.setActivityId("3543332");
        order.setPayTime(businessTime);
        order.setCreateTime(businessTime.plusMinutes(1));
        order.setUpdateTime(businessTime.plusMinutes(1));
        order.setDeleted(0);
        return order;
    }

    private OrderDefaultAttributionResult attributed(
            UUID channelUserId,
            UUID channelDeptId,
            UUID recruiterUserId,
            OrderLinkAttributionResolution resolution) {
        return OrderDefaultAttributionResult.attributed(
                channelUserId,
                channelDeptId,
                recruiterUserId,
                channelUserId == null ? AttributionSource.UNATTRIBUTED : resolution.source(),
                recruiterUserId == null ? AttributionSource.UNATTRIBUTED : resolution.source(),
                null,
                null,
                "3543332",
                resolution);
    }

    private OrderDefaultAttributionResult unattributed() {
        return OrderDefaultAttributionResult.unattributed(
                null, null, "3543332", AttributionSource.UNATTRIBUTED,
                AttributionSource.UNATTRIBUTED, "MAPPING_NOT_FOUND", null);
    }
}
