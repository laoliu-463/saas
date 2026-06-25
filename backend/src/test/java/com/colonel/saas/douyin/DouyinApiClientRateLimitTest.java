package com.colonel.saas.douyin;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.UpstreamErrorCode;
import com.colonel.saas.douyin.ratelimit.DouyinRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DouyinApiClientRateLimitTest {

    @Mock
    private DouyinTokenService douyinTokenService;

    @Mock
    private RestTemplate douyinRestTemplate;

    @Mock
    private DouyinConfig douyinConfig;

    @Mock
    private DouyinRateLimiter douyinRateLimiter;

    private DouyinApiClient douyinApiClient;

    @BeforeEach
    void setUp() {
        when(douyinConfig.getAppId()).thenReturn("app123");
        douyinApiClient = new DouyinApiClient(
                douyinTokenService,
                douyinRestTemplate,
                douyinConfig,
                douyinRateLimiter);
    }

    @Test
    void postShouldAcquireRateLimitBeforeTransportCall() {
        when(douyinTokenService.getValidToken("app123")).thenReturn("token123");
        when(douyinConfig.getClientSecret()).thenReturn("secret123");
        when(douyinConfig.getBaseUrl()).thenReturn("https://openapi-fxg.jinritemai.com");
        when(douyinRestTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Map.of("code", 0, "data", Map.of()));

        douyinApiClient.post("alliance.colonelActivityProduct", Map.of());

        verify(douyinRateLimiter).acquire("app123", "alliance.colonelActivityProduct");
        verify(douyinRestTemplate).postForObject(any(String.class), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void postShouldNotCallTransportWhenRateLimiterRejects() {
        when(douyinTokenService.getValidToken("app123")).thenReturn("token123");
        doThrow(BusinessException.upstream(
                UpstreamErrorCode.UPSTREAM_RATE_LIMIT,
                "抖音接口调用触发本地限流，请稍后重试"))
                .when(douyinRateLimiter)
                .acquire("app123", "alliance.colonelActivityProduct");

        assertThatThrownBy(() -> douyinApiClient.post("alliance.colonelActivityProduct", Map.of()))
                .isInstanceOf(BusinessException.class);

        verify(douyinRestTemplate, never())
                .postForObject(any(String.class), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void postShouldSkipRateLimitWhenTestModeEnabled() {
        ReflectionTestUtils.setField(douyinApiClient, "testEnabled", true);

        douyinApiClient.post("alliance.colonelActivityProduct", Map.of("page", 0));

        verify(douyinRateLimiter, never()).acquire(any(), any());
        verify(douyinRestTemplate, never())
                .postForObject(any(String.class), any(HttpEntity.class), eq(Map.class));
    }
}
