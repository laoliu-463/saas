package com.colonel.saas.domain.order.facade;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegacyOrderReadFacadeTest {

    @Mock
    private ColonelsettlementOrderMapper orderMapper;

    private LegacyOrderReadFacade facade;

    @BeforeEach
    void setUp() {
        facade = new LegacyOrderReadFacade(orderMapper);
    }

    @Test
    void findByOrderId_shouldTrimAndDelegateToMapper() {
        ColonelsettlementOrder order = order("ORD-1");
        when(orderMapper.findByOrderId("ORD-1")).thenReturn(order);

        assertThat(facade.findByOrderId(" ORD-1 ")).isSameAs(order);
    }

    @Test
    void findByOrderIds_shouldDropBlankAndDeduplicateBeforeQuerying() {
        ColonelsettlementOrder order = order("ORD-1");
        when(orderMapper.selectList(any())).thenReturn(List.of(order));

        List<ColonelsettlementOrder> result = facade.findByOrderIds(List.of(" ORD-1 ", "", "ORD-1"));

        assertThat(result).containsExactly(order);
        verify(orderMapper).selectList(any());
    }

    @Test
    void findByOrderIds_shouldSkipMapperWhenInputEmptyAfterNormalization() {
        assertThat(facade.findByOrderIds(List.of(" ", ""))).isEmpty();

        verify(orderMapper, never()).selectList(any());
    }

    @Test
    void backfillQueries_shouldDelegateToMapperWithSafeLimit() {
        when(orderMapper.selectList(any())).thenReturn(List.of(order("ORD-2")));

        List<ColonelsettlementOrder> result = facade.findOrdersForBackfill(
                LocalDateTime.of(2026, 5, 1, 0, 0),
                LocalDateTime.of(2026, 5, 31, 23, 59),
                true,
                99999);

        assertThat(result).hasSize(1);
        verify(orderMapper).selectList(any());
    }

    @Test
    void findUnsettledOrdersByCreateTimeRange_shouldDelegateToMapper() {
        when(orderMapper.selectList(any())).thenReturn(List.of(order("ORD-3")));

        List<ColonelsettlementOrder> result = facade.findUnsettledOrdersByCreateTimeRange(
                LocalDateTime.of(2026, 6, 1, 0, 0),
                LocalDateTime.of(2026, 7, 1, 0, 0),
                100);

        assertThat(result).hasSize(1);
        verify(orderMapper).selectList(any());
    }

    @Test
    void findActiveOrderIdsBySettleTimeRange_shouldReturnNonBlankOrderIds() {
        ColonelsettlementOrder blank = order(" ");
        when(orderMapper.selectList(any())).thenReturn(List.of(order("ORD-LOCAL"), blank, order("ORD-1")));

        assertThat(facade.findActiveOrderIdsBySettleTimeRange(
                LocalDateTime.of(2026, 6, 12, 0, 0),
                LocalDateTime.of(2026, 6, 13, 0, 0)))
                .containsExactly("ORD-LOCAL", "ORD-1");
        verify(orderMapper).selectList(any());
    }

    @Test
    void findOrdersSettledSince_shouldDelegateToMapperWithScopeFilters() {
        ColonelsettlementOrder order = order("ORD-4");
        Page<ColonelsettlementOrder> page = new Page<>(1, 2000, 1);
        page.setRecords(List.of(order));
        when(orderMapper.selectPage(any(), any())).thenReturn(page);

        OrderReadFacade.OrderPage result = facade.findOrdersSettledSince(
                LocalDateTime.of(2026, 6, 1, 0, 0),
                UUID.randomUUID(),
                UUID.randomUUID(),
                1L,
                99999L);

        assertThat(result.records()).containsExactly(order);
        assertThat(result.pages()).isEqualTo(1L);
        verify(orderMapper).selectPage(any(), any());
        assertThat(facade.findOrdersSettledSince(null, null, null, 1L, 2000L).records()).isEmpty();
    }

    @Test
    void dashboardAttributionSummary_shouldApplyVisibilityAndSortReasons() {
        LocalDateTime start = LocalDateTime.of(2026, 6, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 6, 30, 23, 59);
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(orderMapper.selectCount(any())).thenReturn(6L).thenReturn(2L);
        when(orderMapper.selectMaps(any())).thenReturn(List.of(
                Map.of("reason", "B", "count", 1L),
                Map.of("reason", "A", "count", 3L)));

        OrderReadFacade.DashboardAttributionSummary result = facade.getDashboardAttributionSummary(
                start,
                end,
                OrderReadFacade.OrderVisibility.user(userId));

        assertThat(result.attributedOrderCount()).isEqualTo(6L);
        assertThat(result.unattributedOrderCount()).isEqualTo(2L);
        assertThat(result.unattributedReasons())
                .extracting(OrderReadFacade.DashboardReasonCount::reason)
                .containsExactly("A", "B");
        ArgumentCaptor<QueryWrapper<ColonelsettlementOrder>> countCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(orderMapper, times(2)).selectCount(countCaptor.capture());
        assertThat(countCaptor.getAllValues())
                .allSatisfy(wrapper -> assertThat(wrapper.getSqlSegment())
                        .contains("settle_time")
                        .contains("user_id"));
    }

    @Test
    void dashboardFallbackSummary_shouldReadTotalsAndLeaderboardFacts() {
        when(orderMapper.selectMaps(any()))
                .thenReturn(List.of(Map.of("ordercount", 8L, "orderamount", 90000L, "servicefee", 1800L)))
                .thenReturn(List.of(Map.of(
                        "channeluserid", "channel-1",
                        "channelusername", "渠道A",
                        "ordercount", 3L,
                        "orderamount", 30000L,
                        "servicefee", 600L)))
                .thenReturn(List.of(Map.of(
                        "coloneluserid", "colonel-1",
                        "colonelusername", "招商A",
                        "ordercount", 2L,
                        "orderamount", 20000L,
                        "servicefee", 400L)));

        OrderReadFacade.DashboardFallbackSummary result = facade.getDashboardFallbackSummary(
                null,
                null,
                OrderReadFacade.OrderVisibility.all());

        assertThat(result.orderCount()).isEqualTo(8L);
        assertThat(result.orderAmountCent()).isEqualTo(90000L);
        assertThat(result.serviceFeeCent()).isEqualTo(1800L);
        assertThat(result.channelPerformance().get(0).userName()).isEqualTo("渠道A");
        assertThat(result.colonelPerformance().get(0).userName()).isEqualTo("招商A");
        verify(orderMapper, times(3)).selectMaps(any());
    }

    private static ColonelsettlementOrder order(String orderId) {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setOrderId(orderId);
        return order;
    }
}
