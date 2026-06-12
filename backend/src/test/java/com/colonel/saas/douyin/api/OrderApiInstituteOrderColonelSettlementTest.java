package com.colonel.saas.douyin.api;

import com.colonel.saas.douyin.DouyinApiClient;
import com.colonel.saas.gateway.douyin.contract.DouyinContractFixtureProvider;
import com.colonel.saas.gateway.douyin.contract.DouyinUpstreamModeSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderApiInstituteOrderColonelSettlementTest {

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Map<String, Object>> mapCaptor() {
        return (ArgumentCaptor<Map<String, Object>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Map.class);
    }

    @Mock
    private DouyinApiClient douyinApiClient;
    @Mock
    private DouyinUpstreamModeSupport upstreamModeSupport;
    @Mock
    private DouyinContractFixtureProvider contractFixtureProvider;

    @InjectMocks
    private OrderApi orderApi;

    @BeforeEach
    void setUp() {
        lenient().when(upstreamModeSupport.isContract()).thenReturn(false);
    }

    @Test
    void listInstituteOrderColonelForSettlement_shouldPassTimeTypeAndIgnoreOrderIds() {
        when(douyinApiClient.post(eq("buyin.instituteOrderColonel"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(Map.of("data", Map.of("order_list", List.of())));

        JsonNode result = orderApi.listInstituteOrderColonelForSettlement(
                "2026-06-01 00:00:00",
                "2026-06-01 23:59:59",
                "settle",
                20,
                "10",
                List.of("ORDER_1", "ORDER_2"));

        assertThat(result.path("data").path("order_list").isArray()).isTrue();
        ArgumentCaptor<Map<String, Object>> captor = mapCaptor();
        verify(douyinApiClient).post(eq("buyin.instituteOrderColonel"), captor.capture());
        Map<String, Object> params = captor.getValue();
        assertThat(params).containsEntry("start_time", "2026-06-01 00:00:00");
        assertThat(params).containsEntry("end_time", "2026-06-01 23:59:59");
        assertThat(params).containsEntry("time_type", "settle");
        assertThat(params).containsEntry("size", 20);
        assertThat(params).containsEntry("cursor", "10");
        assertThat(params).doesNotContainKey("order_ids");
    }

    @Test
    void listSettlement_shouldDelegateToInstituteSettlementMethod() {
        when(douyinApiClient.post(eq("buyin.instituteOrderColonel"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(Map.of("data", Map.of("order_list", List.of())));

        orderApi.listSettlement(1774972800L, 1775059200L, 50, null);

        ArgumentCaptor<Map<String, Object>> captor = mapCaptor();
        verify(douyinApiClient).post(eq("buyin.instituteOrderColonel"), captor.capture());
        assertThat(captor.getValue()).containsEntry("cursor", "0");
    }
}
