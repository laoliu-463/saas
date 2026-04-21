package com.colonel.saas.douyin.api;

import com.colonel.saas.douyin.DouyinApiClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityApiTest {

    @Mock
    private DouyinApiClient douyinApiClient;

    @InjectMocks
    private ActivityApi activityApi;

    @Test
    void list_shouldCallBuyinColonelActivityListEndpoint() {
        Map<String, Object> response = new HashMap<>();
        response.put("data", java.util.List.of());
        when(douyinApiClient.post(eq("buyin.colonel.activity.list"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(response);

        activityApi.list();

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(douyinApiClient).post(eq("buyin.colonel.activity.list"), captor.capture());
        Map<String, Object> params = captor.getValue();
        assertThat(params).containsKey("start_time");
        assertThat(params).containsKey("end_time");
        assertThat(params.get("page_size")).isEqualTo(20);
        assertThat(params.get("start_time")).isInstanceOf(Long.class);
        assertThat(params.get("end_time")).isInstanceOf(Long.class);
    }
}
