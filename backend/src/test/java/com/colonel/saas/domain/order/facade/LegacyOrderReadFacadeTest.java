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
import java.util.Set;
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
    void findProductIdsByColonelBuyinId_shouldReturnNonBlankProductIds() {
        ColonelsettlementOrder first = order("ORD-P1");
        first.setProductId("P1");
        ColonelsettlementOrder blank = order("ORD-BLANK");
        blank.setProductId(" ");
        ColonelsettlementOrder second = order("ORD-P2");
        second.setProductId("P2");
        when(orderMapper.selectList(any())).thenReturn(List.of(first, blank, second));

        Set<String> result = facade.findProductIdsByColonelBuyinId(46128341673481000L);

        assertThat(result).containsExactly("P1", "P2");
        verify(orderMapper).selectList(any());
        assertThat(facade.findProductIdsByColonelBuyinId(null)).isEmpty();
    }

    @Test
    void summarizeProductOrdersByActivity_shouldAggregateProductOrderFacts() {
        LocalDateTime older = LocalDateTime.of(2026, 6, 1, 10, 0);
        LocalDateTime newer = LocalDateTime.of(2026, 6, 2, 10, 0);
        ColonelsettlementOrder attributed = order("ORD-A");
        attributed.setProductId("P1");
        attributed.setAttributionStatus("ATTRIBUTED");
        attributed.setOrderAmount(1000L);
        attributed.setSettleColonelCommission(100L);
        attributed.setCreateTime(older);
        ColonelsettlementOrder unattributed = order("ORD-U");
        unattributed.setProductId("P1");
        unattributed.setAttributionStatus("UNATTRIBUTED");
        unattributed.setOrderAmount(2000L);
        unattributed.setSettleColonelCommission(200L);
        unattributed.setSettleTime(newer);
        ColonelsettlementOrder otherProduct = order("ORD-P2");
        otherProduct.setProductId("P2");
        otherProduct.setAttributionStatus("ATTRIBUTED");
        otherProduct.setOrderAmount(3000L);
        otherProduct.setSettleColonelCommission(300L);
        when(orderMapper.selectList(any())).thenReturn(List.of(attributed, unattributed, otherProduct));

        Map<String, OrderReadFacade.ProductOrderSummary> result =
                facade.summarizeProductOrdersByActivity("ACT-1", List.of("P1", "P2"));

        assertThat(result.get("P1").orderCount()).isEqualTo(2L);
        assertThat(result.get("P1").attributedCount()).isEqualTo(1L);
        assertThat(result.get("P1").unattributedCount()).isEqualTo(1L);
        assertThat(result.get("P1").gmvCent()).isEqualTo(3000L);
        assertThat(result.get("P1").serviceFeeCent()).isEqualTo(300L);
        assertThat(result.get("P1").lastOrderTime()).isEqualTo(newer);
        assertThat(result.get("P2").orderCount()).isEqualTo(1L);
        verify(orderMapper).selectList(any());
        assertThat(facade.summarizeProductOrdersByActivity(" ", List.of("P1"))).isEmpty();
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

    @Test
    void summarizeTalentOrdersByDouyinUid_shouldAggregateTalentOrderFacts() {
        when(orderMapper.selectMaps(any())).thenReturn(List.of(Map.of(
                "talent_uid", "dy_1",
                "order_count", 3L,
                "order_amount", 12000L,
                "service_fee", 800L)));

        Map<String, OrderReadFacade.TalentOrderSummary> result = facade.summarizeTalentOrdersByDouyinUid(
                List.of(" dy_1 ", "dy_1", " "),
                LocalDateTime.of(2026, 6, 1, 0, 0));

        assertThat(result).containsOnlyKeys("dy_1");
        assertThat(result.get("dy_1").orderCount()).isEqualTo(3L);
        assertThat(result.get("dy_1").orderAmountCent()).isEqualTo(12000L);
        assertThat(result.get("dy_1").serviceFeeCent()).isEqualTo(800L);
        verify(orderMapper).selectMaps(any());
    }

    @Test
    void existsTalentOrderCreatedSince_shouldUseBoundedAuthorOrTalentUidQuery() {
        LocalDateTime createStart = LocalDateTime.of(2026, 6, 1, 0, 0);
        when(orderMapper.selectObjs(any())).thenReturn(List.of(1));

        boolean exists = facade.existsTalentOrderCreatedSince(" dy_1 ", createStart);

        assertThat(exists).isTrue();
        ArgumentCaptor<QueryWrapper<ColonelsettlementOrder>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(orderMapper).selectObjs(captor.capture());
        QueryWrapper<ColonelsettlementOrder> wrapper = captor.getValue();
        assertThat(wrapper.getSqlSelect()).isEqualTo("1");
        assertThat(wrapper.getCustomSqlSegment())
                .contains("create_time", "author_id", "talent_uid", "OR", "LIMIT 1");
        assertThat(wrapper.getParamNameValuePairs().values()).contains("dy_1", createStart);
    }

    @Test
    void existsTalentOrderCreatedSince_shouldRejectInvalidInputWithoutQuerying() {
        assertThat(facade.existsTalentOrderCreatedSince(" ", LocalDateTime.now())).isFalse();
        assertThat(facade.existsTalentOrderCreatedSince("dy_1", null)).isFalse();

        verify(orderMapper, never()).selectObjs(any());
    }

    @Test
    void findRecentOrdersByTalentUid_shouldReturnRecentTalentOrders() {
        LocalDateTime createTime = LocalDateTime.of(2026, 6, 2, 10, 0);
        when(orderMapper.selectMaps(any())).thenReturn(List.of(Map.of(
                "order_id", "ORD-1",
                "product_name", "商品A",
                "order_amount", 2000L,
                "service_fee", 120L,
                "channel_user_name", "渠道A",
                "create_time", createTime)));

        List<OrderReadFacade.TalentRecentOrder> result = facade.findRecentOrdersByTalentUid(" dy_1 ", 20);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).orderId()).isEqualTo("ORD-1");
        assertThat(result.get(0).productName()).isEqualTo("商品A");
        assertThat(result.get(0).orderAmountCent()).isEqualTo(2000L);
        assertThat(result.get(0).serviceFeeCent()).isEqualTo(120L);
        assertThat(result.get(0).channelName()).isEqualTo("渠道A");
        assertThat(result.get(0).createTime()).isEqualTo(createTime);
        verify(orderMapper).selectMaps(any());
    }

    private static ColonelsettlementOrder order(String orderId) {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setOrderId(orderId);
        return order;
    }
}
