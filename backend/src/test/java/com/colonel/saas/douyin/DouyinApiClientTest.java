package com.colonel.saas.douyin;

import com.colonel.saas.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DouyinApiClientTest {

    @Mock
    private DouyinTokenService douyinTokenService;
    @Mock
    private RestTemplate douyinRestTemplate;
    @Mock
    private DouyinConfig douyinConfig;

    @InjectMocks
    private DouyinApiClient douyinApiClient;

    @Test
    void post_shouldIncludeTokenAndAppId() {
        when(douyinConfig.getAppId()).thenReturn("app123");
        when(douyinConfig.getBaseUrl()).thenReturn("https://open.douyin.com");
        doReturn("token").when(douyinTokenService).getValidToken(any());
        when(douyinRestTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Map.of("err_no", 0));

        Map<String, Object> params = new HashMap<>();
        params.put("page_size", 20);
        douyinApiClient.post("buyin.colonel.activity.list", params);

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(douyinRestTemplate).postForObject(eq("https://open.douyin.com/buyin.colonel.activity.list"), captor.capture(), eq(Map.class));
        @SuppressWarnings("unchecked")
        Map<String, Object> sent = (Map<String, Object>) captor.getValue().getBody();
        assertThat(sent).containsEntry("access_token", "token");
        assertThat(sent).containsEntry("app_id", "app123");
        assertThat(sent).containsEntry("page_size", 20);
    }

    @Test
    void post_shouldUseOverriddenAppId() {
        when(douyinConfig.getBaseUrl()).thenReturn("https://open.douyin.com");
        when(douyinTokenService.getValidToken("custom-app")).thenReturn("custom-token");
        when(douyinRestTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Map.of("err_no", 0));

        douyinApiClient.post("test.method", Map.of("appId", "custom-app"));

        verify(douyinTokenService).getValidToken("custom-app");
    }

    @Test
    void post_shouldThrowWhenResponseIsNull() {
        when(douyinConfig.getAppId()).thenReturn("app123");
        when(douyinConfig.getBaseUrl()).thenReturn("https://open.douyin.com");
        when(douyinTokenService.getValidToken("app123")).thenReturn("token");
        when(douyinRestTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(null);

        assertThatThrownBy(() -> douyinApiClient.post("test", null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void post_shouldThrowDouyinApiExceptionWhenCodeNonZero() {
        when(douyinConfig.getAppId()).thenReturn("app123");
        when(douyinConfig.getBaseUrl()).thenReturn("https://open.douyin.com");
        when(douyinTokenService.getValidToken("app123")).thenReturn("token");
        when(douyinRestTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Map.of("err_no", 10001, "err_msg", "bad"));

        assertThatThrownBy(() -> douyinApiClient.post("test", null))
                .isInstanceOf(DouyinApiException.class);
    }

    @Test
    void post_shouldMarkReauthorizeFor31012() {
        when(douyinConfig.getAppId()).thenReturn("app123");
        when(douyinConfig.getBaseUrl()).thenReturn("https://open.douyin.com");
        when(douyinTokenService.getValidToken("app123")).thenReturn("token");
        when(douyinRestTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Map.of("err_no", 31012, "err_msg", "token expired"));

        assertThatThrownBy(() -> douyinApiClient.post("test", null))
                .isInstanceOf(DouyinApiException.class);

        verify(douyinTokenService).markReauthorizeRequired("app123", "token expired");
    }

    @Test
    void post_shouldParseCodeFromCodeField() {
        when(douyinConfig.getAppId()).thenReturn("app123");
        when(douyinConfig.getBaseUrl()).thenReturn("https://open.douyin.com");
        when(douyinTokenService.getValidToken("app123")).thenReturn("token");
        when(douyinRestTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Map.of("code", 0, "message", "ok"));

        Map<String, Object> result = douyinApiClient.post("test", null);

        assertThat(result).containsEntry("code", 0);
    }
}
