package com.colonel.saas.douyin;

import com.colonel.saas.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DouyinTokenServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private RestTemplate douyinRestTemplate;
    @Mock
    private DouyinConfig douyinConfig;
    @Mock
    private ValueOperations<String, Object> valueOperations;
    @Mock
    private Executor tokenRefreshExecutor;

    private DouyinTokenService tokenService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(douyinConfig.getAppId()).thenReturn("app123");
        when(douyinConfig.getClientKey()).thenReturn("client_key");
        when(douyinConfig.getClientSecret()).thenReturn("client_secret");
        when(douyinConfig.getBaseUrl()).thenReturn("https://open.douyin.com");
        when(valueOperations.get(startsWith("douyin:token:reauthorize_required:"))).thenReturn(null);

        tokenService = new DouyinTokenService(
                redisTemplate, douyinRestTemplate, douyinConfig,
                tokenRefreshExecutor, 300L, 5L);
    }

    @Test
    void getValidToken_shouldReturnCachedToken() {
        when(valueOperations.get("douyin:token:app123")).thenReturn("cached-token");

        String token = tokenService.getValidToken(null);

        assertThat(token).isEqualTo("cached-token");
    }

    @Test
    void getValidToken_shouldRefreshWhenCacheMiss() {
        when(valueOperations.get("douyin:token:app123")).thenReturn(null, "new-access-token");
        when(valueOperations.get("douyin:refresh:app123")).thenReturn("refresh-token");
        when(valueOperations.setIfAbsent(eq("douyin:token:lock:app123"), eq("1"), any(Duration.class)))
                .thenReturn(true);

        Map<String, Object> refreshResponse = new HashMap<>();
        refreshResponse.put("err_no", 0);
        refreshResponse.put("data", Map.of(
                "access_token", "new-access-token",
                "refresh_token", "new-refresh-token",
                "expires_in", 7200L
        ));
        when(douyinRestTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(refreshResponse);

        String token = tokenService.getValidToken(null);

        assertThat(token).isEqualTo("new-access-token");
    }

    @Test
    void getValidToken_shouldThrowWhenReauthorizeRequired() {
        when(valueOperations.get("douyin:token:reauthorize_required:app123")).thenReturn("required");

        assertThatThrownBy(() -> tokenService.getValidToken(null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void refreshToken_shouldThrowWhenLockNotAcquired() {
        when(valueOperations.setIfAbsent(eq("douyin:token:lock:app123"), eq("1"), any(Duration.class)))
                .thenReturn(false);

        assertThatThrownBy(() -> tokenService.refreshToken("app123"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void refreshToken_shouldMarkReauthorizeRequiredFor31012() {
        when(valueOperations.setIfAbsent(eq("douyin:token:lock:app123"), eq("1"), any(Duration.class)))
                .thenReturn(true);
        when(valueOperations.get("douyin:refresh:app123")).thenReturn("refresh-token");
        when(douyinRestTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Map.of("err_no", 31012, "err_msg", "token expired"));

        assertThatThrownBy(() -> tokenService.refreshToken("app123"))
                .isInstanceOf(BusinessException.class);

        verify(valueOperations).set(
                eq("douyin:token:reauthorize_required:app123"),
                argThat(val -> val != null && val.toString().contains("token expired")),
                any(Duration.class)
        );
    }

    @Test
    void isTokenExpiringSoon_shouldReturnTrueWhenNoExpireTime() {
        when(valueOperations.get("douyin:token:expire_at:app123")).thenReturn(null);

        assertThat(tokenService.isTokenExpiringSoon("app123")).isTrue();
    }
}
