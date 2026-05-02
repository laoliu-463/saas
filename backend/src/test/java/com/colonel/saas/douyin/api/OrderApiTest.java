package com.colonel.saas.douyin.api;

import com.colonel.saas.douyin.DouyinApiClient;
import com.colonel.saas.douyin.DouyinApiException;
import com.colonel.saas.gateway.douyin.contract.DouyinContractFixtureProvider;
import com.colonel.saas.gateway.douyin.contract.DouyinUpstreamModeSupport;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderApiTest {

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
    void listSettlement_shouldPassParamsAndReturnResponse() {
        when(douyinApiClient.post(eq("buyin.instituteOrderColonel"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(Map.of("data", List.of()));

        Map<String, Object> result = orderApi.listSettlement(1000L, 2000L, 50, "50");

        assertThat(result).containsKey("data");
        ArgumentCaptor<Map<String, Object>> captor = mapCaptor();
        verify(douyinApiClient).post(eq("buyin.instituteOrderColonel"), captor.capture());
        Map<String, Object> params = captor.getValue();
        assertThat(params.get("start_time")).isEqualTo(1000L);
        assertThat(params.get("end_time")).isEqualTo(2000L);
        assertThat(params.get("count")).isEqualTo(50);
        assertThat(params.get("page")).isEqualTo(51L);
    }

    @Test
    void listSettlement_normalizesCount() {
        when(douyinApiClient.post(eq("buyin.instituteOrderColonel"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(Map.of("data", List.of()));

        orderApi.listSettlement(1000L, 2000L, 0, null);

        ArgumentCaptor<Map<String, Object>> captor = mapCaptor();
        verify(douyinApiClient).post(eq("buyin.instituteOrderColonel"), captor.capture());
        assertThat(captor.getValue().get("count")).isEqualTo(100);
    }

    @Test
    void listSettlement_normalizesCursor() {
        when(douyinApiClient.post(eq("buyin.instituteOrderColonel"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(Map.of("data", List.of()));

        orderApi.listSettlement(1000L, 2000L, 10, "not_a_number");

        ArgumentCaptor<Map<String, Object>> captor = mapCaptor();
        verify(douyinApiClient).post(eq("buyin.instituteOrderColonel"), captor.capture());
        assertThat(captor.getValue().get("page")).isEqualTo(1L);
    }

    @Test
    void listSettlementWindow_shouldUseDefaultTimeWindow() {
        when(douyinApiClient.post(eq("buyin.instituteOrderColonel"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(Map.of("data", List.of()));

        orderApi.listSettlementWindow(null, null);

        ArgumentCaptor<Map<String, Object>> captor = mapCaptor();
        verify(douyinApiClient).post(eq("buyin.instituteOrderColonel"), captor.capture());
        Map<String, Object> params = captor.getValue();
        assertThat(params).containsKey("start_time");
        assertThat(params).containsKey("end_time");
        assertThat(params.get("count")).isEqualTo(100);
        assertThat(params.get("page")).isEqualTo(1L);
    }

    @Test
    void listSettlementWindow_withCustomCount_shouldUseIt() {
        when(douyinApiClient.post(eq("buyin.instituteOrderColonel"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(Map.of("data", List.of()));

        orderApi.listSettlementWindow("0", 25);

        ArgumentCaptor<Map<String, Object>> captor = mapCaptor();
        verify(douyinApiClient).post(eq("buyin.instituteOrderColonel"), captor.capture());
        assertThat(captor.getValue().get("count")).isEqualTo(25);
    }

    @Test
    void listSettlement_shouldRetryWithCursorWhenPageParamsInvalid() {
        when(douyinApiClient.post(eq("buyin.instituteOrderColonel"), org.mockito.ArgumentMatchers.anyMap()))
                .thenThrow(new DouyinApiException(50002, "参数校验失败", "isv.parameter-invalid", "log-x", "buyin.instituteOrderColonel"))
                .thenReturn(Map.of("data", List.of()));

        orderApi.listSettlement(1000L, 2000L, 20, "3");

        ArgumentCaptor<Map<String, Object>> captor = mapCaptor();
        verify(douyinApiClient, times(2)).post(eq("buyin.instituteOrderColonel"), captor.capture());
        assertThat(captor.getAllValues().get(0)).containsEntry("page", 4L);
        assertThat(captor.getAllValues().get(1)).containsEntry("cursor", 3L);
    }

    @Test
    void decryptSensitiveData_shouldPassOrderIdsWithTypeOne() {
        when(douyinApiClient.post(eq("order.batchSensitiveDataRequest"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(Map.of("data", Map.of()));

        orderApi.decryptSensitiveData(List.of("oid1", "oid2"));

        ArgumentCaptor<Map<String, Object>> captor = mapCaptor();
        verify(douyinApiClient).post(eq("order.batchSensitiveDataRequest"), captor.capture());
        Map<String, Object> params = captor.getValue();
        assertThat(params.get("order_ids")).isEqualTo(List.of("oid1", "oid2"));
        assertThat(params.get("type")).isEqualTo(1);
    }

    @Test
    void listColonelMultiSettlementOrders_shouldPassNormalizedParams() {
        when(douyinApiClient.post(eq("buyin.colonelMultiSettlementOrders"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(Map.of("data", Map.of("orders", List.of())));

        Map<String, Object> result = orderApi.listColonelMultiSettlementOrders(
                "test_app",
                20,
                "12",
                "settle",
                "2026-04-01 00:00:00",
                "2026-04-20 23:59:59",
                "4737996432465788974, 4737996432465788973");

        assertThat(result).containsKey("data");
        ArgumentCaptor<Map<String, Object>> captor = mapCaptor();
        verify(douyinApiClient).post(eq("buyin.colonelMultiSettlementOrders"), captor.capture());
        Map<String, Object> params = captor.getValue();
        assertThat(params.get("appId")).isEqualTo("test_app");
        assertThat(params.get("size")).isEqualTo(20);
        assertThat(params.get("cursor")).isEqualTo("12");
        assertThat(params.get("time_type")).isEqualTo("settle");
        assertThat(params.get("start_time")).isEqualTo("2026-04-01 00:00:00");
        assertThat(params.get("end_time")).isEqualTo("2026-04-20 23:59:59");
        assertThat(params.get("order_ids")).isEqualTo("4737996432465788974,4737996432465788973");
    }

    @Test
    void listColonelMultiSettlementOrders_shouldApplyDefaults() {
        when(douyinApiClient.post(eq("buyin.colonelMultiSettlementOrders"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(Map.of("data", Map.of()));

        orderApi.listColonelMultiSettlementOrders(
                null,
                null,
                null,
                null,
                "2026-04-01 00:00:00",
                "2026-04-02 00:00:00",
                null);

        ArgumentCaptor<Map<String, Object>> captor = mapCaptor();
        verify(douyinApiClient).post(eq("buyin.colonelMultiSettlementOrders"), captor.capture());
        Map<String, Object> params = captor.getValue();
        assertThat(params.get("size")).isEqualTo(50);
        assertThat(params.get("cursor")).isEqualTo("0");
        assertThat(params.get("time_type")).isEqualTo("update");
    }

    @Test
    void listColonelMultiSettlementOrders_shouldRejectInvalidSize() {
        assertThatThrownBy(() -> orderApi.listColonelMultiSettlementOrders(
                null, 101, null, null, null, null, "1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("size must be between 1 and 100");
    }

    @Test
    void listColonelMultiSettlementOrders_shouldRejectInvalidTimeType() {
        assertThatThrownBy(() -> orderApi.listColonelMultiSettlementOrders(
                null, null, null, "created", null, null, "1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("timeType must be settle or update");
    }

    @Test
    void listColonelMultiSettlementOrders_shouldRejectMissingQueryCondition() {
        assertThatThrownBy(() -> orderApi.listColonelMultiSettlementOrders(
                null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("time range or orderIds is required");
    }

    @Test
    void listColonelMultiSettlementOrders_shouldRejectPartialTimeRange() {
        assertThatThrownBy(() -> orderApi.listColonelMultiSettlementOrders(
                null, null, null, null, "2026-04-01 00:00:00", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("startTime and endTime must be provided together");
    }

    @Test
    void listColonelMultiSettlementOrders_shouldRejectInvalidTimeRange() {
        assertThatThrownBy(() -> orderApi.listColonelMultiSettlementOrders(
                null, null, null, null, "2026-04-10 00:00:00", "2026-04-01 00:00:00", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("startTime must be earlier than or equal to endTime");
    }

    @Test
    void listColonelMultiSettlementOrders_shouldRejectRangeOverNinetyDays() {
        assertThatThrownBy(() -> orderApi.listColonelMultiSettlementOrders(
                null, null, null, null, "2026-01-01 00:00:00", "2026-04-05 00:00:01", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("time range must not exceed 90 days");
    }

    @Test
    void listColonelMultiSettlementOrders_shouldRejectInvalidCursor() {
        assertThatThrownBy(() -> orderApi.listColonelMultiSettlementOrders(
                null, null, "cursor-x", null, null, null, "1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cursor must be a numeric string");
    }
}
