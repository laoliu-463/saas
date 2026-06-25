package com.colonel.saas.service.settlement;

import com.colonel.saas.douyin.api.OrderApi;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InstituteOrderColonelSettlementGatewayTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void fetch_shouldCall1603AndExtractNestedOrdersAndCursor() {
        OrderApi orderApi = mock(OrderApi.class);
        InstituteOrderColonelSettlementGateway gateway =
                new InstituteOrderColonelSettlementGateway(orderApi, objectMapper);
        JsonNode response = objectMapper.valueToTree(Map.of(
                "log_id", "log-1603",
                "data", Map.of(
                        "data", Map.of(
                                "orders", List.of(Map.of("order_id", "ORDER_1")),
                                "has_more", true,
                                "next_cursor", "cursor-2"))));
        when(orderApi.listInstituteOrderColonelForSettlement(
                "2026-06-01 00:00:00",
                "2026-06-01 23:59:59",
                "settle",
                50,
                "0",
                List.of("ORDER_1")))
                .thenReturn(response);

        SettlementOrderPage page = gateway.fetch(new SettlementOrderQuery(
                "2026-06-01 00:00:00",
                "2026-06-01 23:59:59",
                "settle",
                50,
                "0",
                List.of("ORDER_1"),
                3,
                100,
                false));

        assertThat(page.apiMethod()).isEqualTo("buyin.instituteOrderColonel");
        assertThat(page.source()).isEqualTo("INSTITUTE_SETTLEMENT");
        assertThat(page.orders()).hasSize(1);
        assertThat(page.orders().get(0).path("order_id").asText()).isEqualTo("ORDER_1");
        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextCursor()).isEqualTo("cursor-2");
        assertThat(page.rawResponse()).isSameAs(response);
        verify(orderApi).listInstituteOrderColonelForSettlement(
                "2026-06-01 00:00:00",
                "2026-06-01 23:59:59",
                "settle",
                50,
                "0",
                List.of("ORDER_1"));
    }
}
