package com.colonel.saas.douyin.api;

import com.colonel.saas.douyin.DouyinApiClient;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductApiTest {

    @Mock
    private DouyinApiClient douyinApiClient;

    @InjectMocks
    private ProductApi productApi;

    @Test
    void listActivities_shouldCallCorrectEndpoint() {
        when(douyinApiClient.post(eq("buyin.colonel.activity.list"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(Map.of("data", List.of()));

        productApi.listActivities(null, null, null, null);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(douyinApiClient).post(eq("buyin.colonel.activity.list"), captor.capture());
        Map<String, Object> params = captor.getValue();
        assertThat(params).containsKey("start_time");
        assertThat(params).containsKey("end_time");
        assertThat(params.get("count")).isEqualTo(20);
        assertThat(params.get("cursor")).isEqualTo(0L);
    }

    @Test
    void listActivities_withCustomParams_shouldPassThemThrough() {
        when(douyinApiClient.post(eq("buyin.colonel.activity.list"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(Map.of("data", List.of()));

        productApi.listActivities(1000L, 2000L, 50, "42");

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(douyinApiClient).post(eq("buyin.colonel.activity.list"), captor.capture());
        Map<String, Object> params = captor.getValue();
        assertThat(params.get("start_time")).isEqualTo(1000L);
        assertThat(params.get("end_time")).isEqualTo(2000L);
        assertThat(params.get("count")).isEqualTo(50);
        assertThat(params.get("cursor")).isEqualTo(42L);
    }

    @Test
    void listByActivity_shouldPassActivityIdAndUseDefaults() {
        when(douyinApiClient.post(eq("buyin.colonel.product.list"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(Map.of("data", List.of()));

        productApi.listByActivity("act-123");

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(douyinApiClient).post(eq("buyin.colonel.product.list"), captor.capture());
        Map<String, Object> params = captor.getValue();
        assertThat(params.get("activity_id")).isEqualTo("act-123");
        assertThat(params.get("count")).isEqualTo(20);
        assertThat(params.get("cursor")).isEqualTo(0L);
    }

    @Test
    void listProductsByActivity_shouldPassAllParams() {
        when(douyinApiClient.post(eq("buyin.colonel.product.list"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(Map.of("data", List.of()));

        productApi.listProductsByActivity("act-100", 50, "42");

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(douyinApiClient).post(eq("buyin.colonel.product.list"), captor.capture());
        Map<String, Object> params = captor.getValue();
        assertThat(params.get("activity_id")).isEqualTo("act-100");
        assertThat(params.get("count")).isEqualTo(50);
        assertThat(params.get("cursor")).isEqualTo(42L);
    }

    @Test
    void listProductsByActivity_withNullCountAndCursor_shouldUseDefaults() {
        when(douyinApiClient.post(eq("buyin.colonel.product.list"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(Map.of("data", List.of()));

        productApi.listProductsByActivity("act", null, null);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(douyinApiClient).post(eq("buyin.colonel.product.list"), captor.capture());
        Map<String, Object> params = captor.getValue();
        assertThat(params.get("count")).isEqualTo(20);
        assertThat(params.get("cursor")).isEqualTo(0L);
    }
}
