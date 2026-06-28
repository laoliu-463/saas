package com.colonel.saas.service;

import com.colonel.saas.domain.order.infrastructure.Order2704SettlementDryRunService;
import com.colonel.saas.domain.order.infrastructure.OrderSyncPersistenceService;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.service.settlement.SettlementOrderGateway;
import com.colonel.saas.service.settlement.SettlementOrderPage;
import com.colonel.saas.service.settlement.SettlementOrderQuery;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Order2704SettlementDryRunServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private SettlementOrderGateway multiSettlementGateway;

    @Mock
    private ColonelsettlementOrderMapper orderMapper;

    private Order2704SettlementDryRunService service;

    @BeforeEach
    void setUp() {
        service = new Order2704SettlementDryRunService(multiSettlementGateway, orderMapper);
    }

    @Test
    void dryRun_shouldContinueWhenHasMoreFalseButCursorExistsAndCompareLocalOrders() {
        when(multiSettlementGateway.fetch(any()))
                .thenReturn(
                        page(List.of(order("ORDER-1", 1000L, 20L, 2L, 3L)), "cursor-2", false),
                        page(List.of(order("ORDER-2", 2000L, 40L, 4L, 6L)), "0", false)
                );
        ColonelsettlementOrder localOnly = new ColonelsettlementOrder();
        localOnly.setOrderId("ORDER-LOCAL");
        ColonelsettlementOrder localMatched = new ColonelsettlementOrder();
        localMatched.setOrderId("ORDER-1");
        when(orderMapper.selectList(any())).thenReturn(List.of(localOnly, localMatched));

        Order2704SettlementDryRunService.DryRunResult result = service.dryRun(request());

        assertThat(result.readOnly()).isTrue();
        assertThat(result.apiMethod()).isEqualTo("buyin.colonelMultiSettlementOrders");
        assertThat(result.timeType()).isEqualTo("settle");
        assertThat(result.pagesFetched()).isEqualTo(2);
        assertThat(result.rawOrderRows()).isEqualTo(2);
        assertThat(result.uniqueOrders()).isEqualTo(2);
        assertThat(result.stopReason()).isEqualTo("NO_NEXT_CURSOR");
        assertThat(result.summary().settleAmountCent()).isEqualTo(3000L);
        assertThat(result.summary().serviceFeeIncomeCent()).isEqualTo(60L);
        assertThat(result.summary().techServiceFeeCent()).isEqualTo(6L);
        assertThat(result.summary().serviceFeeExpenseCent()).isZero();
        assertThat(result.fieldSums().get("service_fee_expense")).isEqualTo(9L);
        assertThat(result.diff().onlyInUpstream()).isEqualTo(1);
        assertThat(result.diff().onlyInUpstreamOrderIds()).containsExactly("ORDER-2");
        assertThat(result.diff().onlyInLocal()).isEqualTo(1);
        assertThat(result.diff().onlyInLocalOrderIds()).containsExactly("ORDER-LOCAL");

        ArgumentCaptor<SettlementOrderQuery> captor = ArgumentCaptor.forClass(SettlementOrderQuery.class);
        verify(multiSettlementGateway, times(2)).fetch(captor.capture());
        assertThat(captor.getAllValues().get(0).timeType()).isEqualTo("settle");
        assertThat(captor.getAllValues().get(0).writeEnabled()).isFalse();
        assertThat(captor.getAllValues().get(1).cursor()).isEqualTo("cursor-2");
        verify(orderMapper, never()).insert(any());
        verify(orderMapper, never()).insertIgnoreByOrderId(any());
        verify(orderMapper, never()).updateSyncedById(any());
    }

    @Test
    void dryRun_shouldStopWhenCursorRepeats() {
        when(multiSettlementGateway.fetch(any()))
                .thenReturn(
                        page(List.of(order("ORDER-1", 1000L, 20L, 2L, 0L)), "cursor-2", false),
                        page(List.of(order("ORDER-2", 2000L, 40L, 4L, 0L)), "cursor-2", false)
                );
        when(orderMapper.selectList(any())).thenReturn(List.of());

        Order2704SettlementDryRunService.DryRunResult result = service.dryRun(request());

        assertThat(result.stopReason()).isEqualTo("DUPLICATE_CURSOR");
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("cursor-2"));
    }

    private Order2704SettlementDryRunService.DryRunRequest request() {
        return new Order2704SettlementDryRunService.DryRunRequest(
                "2026-06-12 00:00:00",
                "2026-06-13 00:00:00",
                "settle",
                100,
                "0",
                10,
                50000,
                500,
                List.of()
        );
    }

    private SettlementOrderPage page(List<Map<String, Object>> orders, String nextCursor, boolean hasMore) {
        List<JsonNode> nodes = orders.stream()
                .map(order -> (JsonNode) OBJECT_MAPPER.valueToTree(order))
                .toList();
        return new SettlementOrderPage(
                nodes,
                nextCursor,
                hasMore,
                OBJECT_MAPPER.valueToTree(Map.of("data", Map.of("cursor", nextCursor))),
                "buyin.colonelMultiSettlementOrders",
                OrderSyncPersistenceService.SYNC_SOURCE_SETTLEMENT);
    }

    private Map<String, Object> order(
            String orderId,
            long settleAmountCent,
            long serviceFeeCent,
            long techFeeCent,
            long expenseCent) {
        return Map.of(
                "order_id", orderId,
                "settled_goods_amount", settleAmountCent,
                "settle_time", "2026-06-12 10:00:00",
                "service_fee_expense", expenseCent,
                "colonel_order_info", Map.of(
                        "real_commission", serviceFeeCent,
                        "tech_service_fee", techFeeCent
                )
        );
    }
}
