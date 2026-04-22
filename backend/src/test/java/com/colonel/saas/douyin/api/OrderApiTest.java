package com.colonel.saas.douyin.api;

import com.colonel.saas.douyin.DouyinApiClient;
import com.colonel.saas.douyin.DouyinApiException;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderApiTest {

    @Mock
    private DouyinApiClient douyinApiClient;

    @InjectMocks
    private OrderApi orderApi;

    @Test
    void listSettlement_shouldPassParamsAndReturnResponse() {
        when(douyinApiClient.post(eq("buyin.instituteOrderColonel"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(Map.of("data", List.of()));

        Map<String, Object> result = orderApi.listSettlement(1000L, 2000L, 50, "50");

        assertThat(result).containsKey("data");
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
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

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(douyinApiClient).post(eq("buyin.instituteOrderColonel"), captor.capture());
        assertThat(captor.getValue().get("count")).isEqualTo(100);
    }

    @Test
    void listSettlement_normalizesCursor() {
        when(douyinApiClient.post(eq("buyin.instituteOrderColonel"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(Map.of("data", List.of()));

        orderApi.listSettlement(1000L, 2000L, 10, "not_a_number");

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(douyinApiClient).post(eq("buyin.instituteOrderColonel"), captor.capture());
        assertThat(captor.getValue().get("page")).isEqualTo(1L);
    }

    @Test
    void listSettlementWindow_shouldUseDefaultTimeWindow() {
        when(douyinApiClient.post(eq("buyin.instituteOrderColonel"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(Map.of("data", List.of()));

        orderApi.listSettlementWindow(null, null);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
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

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(douyinApiClient).post(eq("buyin.instituteOrderColonel"), captor.capture());
        assertThat(captor.getValue().get("count")).isEqualTo(25);
    }

    @Test
    void listSettlement_shouldRetryWithCursorWhenPageParamsInvalid() {
        when(douyinApiClient.post(eq("buyin.instituteOrderColonel"), org.mockito.ArgumentMatchers.anyMap()))
                .thenThrow(new DouyinApiException(50002, "参数校验失败", "isv.parameter-invalid", "log-x", "buyin.instituteOrderColonel"))
                .thenReturn(Map.of("data", List.of()));

        orderApi.listSettlement(1000L, 2000L, 20, "3");

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(douyinApiClient, times(2)).post(eq("buyin.instituteOrderColonel"), captor.capture());
        assertThat(captor.getAllValues().get(0)).containsEntry("page", 4L);
        assertThat(captor.getAllValues().get(1)).containsEntry("cursor", 3L);
    }

    @Test
    void decryptSensitiveData_shouldPassOrderIdsWithTypeOne() {
        when(douyinApiClient.post(eq("order.batchSensitiveDataRequest"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(Map.of("data", Map.of()));

        orderApi.decryptSensitiveData(List.of("oid1", "oid2"));

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(douyinApiClient).post(eq("order.batchSensitiveDataRequest"), captor.capture());
        Map<String, Object> params = captor.getValue();
        assertThat(params.get("order_ids")).isEqualTo(List.of("oid1", "oid2"));
        assertThat(params.get("type")).isEqualTo(1);
    }
}
