package com.colonel.saas.domain.order.facade;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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

    private static ColonelsettlementOrder order(String orderId) {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setOrderId(orderId);
        return order;
    }
}
