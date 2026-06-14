package com.colonel.saas.service;

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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Order1603SettlementDryRunServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private SettlementOrderGateway instituteSettlementGateway;

    private Order1603SettlementDryRunService service;

    @BeforeEach
    void setUp() {
        service = new Order1603SettlementDryRunService(instituteSettlementGateway);
    }

    @Test
    void dryRun_shouldCall1603ReadonlyAndMapSettlementFields() {
        when(instituteSettlementGateway.fetch(any())).thenReturn(page(Map.of(
                "order_id", "ORDER-1603",
                "pay_goods_amount", 2550L,
                "settled_goods_amount", 2480L,
                "settle_time", "2026-06-05 10:00:00",
                "pay_success_time", "2026-06-03 12:00:00",
                "flow_point", "SETTLE",
                "order_status", 2,
                "colonel_order_info", Map.of(
                        "estimated_commission", 55L,
                        "real_commission", 50L,
                        "estimated_tech_service_fee", 7L,
                        "settled_tech_service_fee", 6L
                )
        )));

        Order1603SettlementDryRunService.DryRunResult result = service.dryRun(
                new Order1603SettlementDryRunService.DryRunRequest(
                        "2026-06-03 00:00:00",
                        "2026-06-06 13:30:00",
                        "settle",
                        20,
                        "0",
                        3,
                        100,
                        List.of("ORDER-1603")
                ));

        assertThat(result.source()).isEqualTo(OrderSyncPersistenceService.SYNC_SOURCE_INSTITUTE_SETTLEMENT);
        assertThat(result.apiMethod()).isEqualTo("buyin.instituteOrderColonel");
        assertThat(result.writeEnabled()).isFalse();
        assertThat(result.fetched()).isEqualTo(1);
        assertThat(result.mappingConfidence()).isEqualTo("HIGH");
        assertThat(result.mappingWarnings()).contains("order_ids_ignored_by_1603: use time window for dry-run evidence");
        assertThat(result.fieldKeys()).contains("settled_goods_amount", "colonel_order_info.real_commission");
        Order1603SettlementDryRunService.OrderMapping order = result.orders().get(0);
        assertThat(order.settleAmount()).isEqualTo(2480L);
        assertThat(order.effectiveServiceFee()).isEqualTo(50L);
        assertThat(order.effectiveTechServiceFee()).isEqualTo(6L);
        assertThat(order.settleTime()).isEqualTo("2026-06-05 10:00:00");
        assertThat(order.flowPoint()).isEqualTo("SETTLE");

        ArgumentCaptor<SettlementOrderQuery> captor = ArgumentCaptor.forClass(SettlementOrderQuery.class);
        verify(instituteSettlementGateway).fetch(captor.capture());
        assertThat(captor.getValue().timeType()).isEqualTo("settle");
        assertThat(captor.getValue().writeEnabled()).isFalse();
    }

    @Test
    void dryRun_shouldWarnWhenSettlementFieldsMissing() {
        when(instituteSettlementGateway.fetch(any())).thenReturn(page(Map.of(
                "order_id", "ORDER-PAY-ONLY",
                "pay_goods_amount", 2550L,
                "colonel_order_info", Map.of("estimated_commission", 55L)
        )));

        Order1603SettlementDryRunService.DryRunResult result = service.dryRun(
                new Order1603SettlementDryRunService.DryRunRequest(
                        "2026-06-03 00:00:00",
                        "2026-06-06 13:30:00",
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of()
                ));

        assertThat(result.mappingConfidence()).isEqualTo("LOW");
        assertThat(result.orders().get(0).settleAmount()).isZero();
        assertThat(result.orders().get(0).effectiveServiceFee()).isZero();
        assertThat(result.orders().get(0).mappingWarnings())
                .contains("missing_settle_amount", "missing_effective_service_fee", "missing_settle_time");

        ArgumentCaptor<SettlementOrderQuery> captor = ArgumentCaptor.forClass(SettlementOrderQuery.class);
        verify(instituteSettlementGateway).fetch(captor.capture());
        assertThat(captor.getValue().timeType()).isEqualTo("update");
    }

    @Test
    void dryRun_shouldContinueWhenHasMoreFalseButNextCursorExists() {
        when(instituteSettlementGateway.fetch(any()))
                .thenReturn(
                        page(Map.of("order_id", "ORDER-PAGE-1", "pay_goods_amount", 1000L), "cursor-2", false),
                        page(Map.of("order_id", "ORDER-PAGE-2", "pay_goods_amount", 2000L), "0", false)
                );

        Order1603SettlementDryRunService.DryRunResult result = service.dryRun(
                new Order1603SettlementDryRunService.DryRunRequest(
                        "2026-06-13 00:00:00",
                        "2026-06-13 23:59:59",
                        "settle",
                        20,
                        "0",
                        3,
                        100,
                        List.of()
                ));

        assertThat(result.fetched()).isEqualTo(2);
        assertThat(result.pagesFetched()).isEqualTo(2);
        assertThat(result.stopReason()).isEqualTo("NO_NEXT_CURSOR");
        verify(instituteSettlementGateway, times(2)).fetch(any());
    }

    @Test
    void dryRun_shouldStopWhenNextCursorRepeats() {
        when(instituteSettlementGateway.fetch(any()))
                .thenReturn(
                        page(Map.of("order_id", "ORDER-PAGE-1", "pay_goods_amount", 1000L), "cursor-2", false),
                        page(Map.of("order_id", "ORDER-PAGE-2", "pay_goods_amount", 2000L), "cursor-2", false)
                );

        Order1603SettlementDryRunService.DryRunResult result = service.dryRun(
                new Order1603SettlementDryRunService.DryRunRequest(
                        "2026-06-13 00:00:00",
                        "2026-06-13 23:59:59",
                        "settle",
                        20,
                        "0",
                        3,
                        100,
                        List.of()
                ));

        assertThat(result.fetched()).isEqualTo(2);
        assertThat(result.pagesFetched()).isEqualTo(2);
        assertThat(result.stopReason()).isEqualTo("DUPLICATE_CURSOR");
        verify(instituteSettlementGateway, times(2)).fetch(any());
    }

    private SettlementOrderPage page(Map<String, Object> rawOrder) {
        return page(rawOrder, "0", false);
    }

    private SettlementOrderPage page(Map<String, Object> rawOrder, String nextCursor, boolean hasMore) {
        JsonNode node = OBJECT_MAPPER.valueToTree(rawOrder);
        return new SettlementOrderPage(
                List.of(node),
                nextCursor,
                hasMore,
                OBJECT_MAPPER.valueToTree(Map.of("log_id", "log-1603")),
                "buyin.instituteOrderColonel",
                OrderSyncPersistenceService.SYNC_SOURCE_INSTITUTE_SETTLEMENT);
    }
}
