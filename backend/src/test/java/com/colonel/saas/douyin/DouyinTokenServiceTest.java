package com.colonel.saas.douyin;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.gateway.douyin.DouyinTokenGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DouyinTokenServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private DouyinTokenGateway douyinTokenGateway;
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
        when(douyinConfig.getClientKey()).thenReturn("app123");
        when(douyinConfig.getClientSecret()).thenReturn("client_secret");
        when(douyinConfig.getBaseUrl()).thenReturn("https://open.douyin.com");
        when(valueOperations.get(startsWith("douyin:token:reauthorize_required:"))).thenReturn(null);

        tokenService = new DouyinTokenService(
                redisTemplate, douyinTokenGateway, douyinConfig,
                tokenRefreshExecutor, 300L, 5L);
    }

    @Test
    void getValidToken_shouldReturnCachedToken() {
        when(valueOperations.get("douyin:token:app123")).thenReturn("cached-token");
        when(valueOperations.get("douyin:token:expire_at:app123"))
                .thenReturn(Instant.now().getEpochSecond() + 3600);

        String token = tokenService.getValidToken(null);

        assertThat(token).isEqualTo("cached-token");
    }

    @Test
    void getValidToken_shouldRefreshWhenCacheMiss() {
        when(valueOperations.get("douyin:token:app123")).thenReturn(null, "new-access-token");
        when(valueOperations.get("douyin:refresh:app123")).thenReturn("refresh-token");
        when(valueOperations.setIfAbsent(eq("douyin:token:lock:app123"), eq("1"), any(Duration.class)))
                .thenReturn(true);
        when(douyinTokenGateway.refreshToken("app123", "refresh-token")).thenReturn(
                new DouyinTokenGateway.TokenPayload("new-access-token", "new-refresh-token", 7200L, null, null, 0L)
        );

        String token = tokenService.getValidToken(null);

        assertThat(token).isEqualTo("new-access-token");
    }

    @Test
    void getValidToken_shouldReturnTokenAfterWaitingWhenRefreshInProgress() {
        when(valueOperations.get("douyin:token:app123")).thenReturn(null, "waited-token");
        when(valueOperations.setIfAbsent(eq("douyin:token:lock:app123"), eq("1"), any(Duration.class)))
                .thenReturn(false);

        String token = tokenService.getValidToken(null);

        assertThat(token).isEqualTo("waited-token");
    }

    @Test
    void getValidToken_shouldThrowWhenRefreshCompletesWithoutCachedAccessToken() {
        when(valueOperations.get("douyin:token:app123")).thenReturn(null, null);
        when(valueOperations.get("douyin:refresh:app123")).thenReturn("refresh-token");
        when(valueOperations.setIfAbsent(eq("douyin:token:lock:app123"), eq("1"), any(Duration.class)))
                .thenReturn(true);
        when(douyinTokenGateway.refreshToken("app123", "refresh-token")).thenReturn(
                new DouyinTokenGateway.TokenPayload("new-access-token", "new-refresh-token", 7200L, null, null, 0L)
        );

        assertThatThrownBy(() -> tokenService.getValidToken(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未包含有效 access_token");
    }

    @Test
    void getValidToken_shouldTriggerAsyncRefreshWhenCachedTokenIsExpiringSoon() {
        when(valueOperations.get("douyin:token:app123")).thenReturn("cached-token");
        when(valueOperations.get("douyin:token:expire_at:app123"))
                .thenReturn(Instant.now().getEpochSecond() + 10);
        when(valueOperations.setIfAbsent(eq("douyin:token:lock:app123"), eq("1"), any(Duration.class)))
                .thenReturn(false);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(tokenRefreshExecutor).execute(any(Runnable.class));

        String token = tokenService.getValidToken("app123");

        assertThat(token).isEqualTo("cached-token");
        verify(tokenRefreshExecutor).execute(any(Runnable.class));
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
    void refreshToken_shouldPropagate31012WithoutMarkingReauthorize() {
        when(valueOperations.setIfAbsent(eq("douyin:token:lock:app123"), eq("1"), any(Duration.class)))
                .thenReturn(true);
        when(valueOperations.get("douyin:refresh:app123")).thenReturn("refresh-token");
        when(douyinTokenGateway.refreshToken("app123", "refresh-token"))
                .thenThrow(new DouyinApiException(31012, "concurrent refresh"));

        assertThatThrownBy(() -> tokenService.refreshToken("app123"))
                .isInstanceOf(DouyinApiException.class);
    }

    @Test
    void refreshToken_shouldRejectMissingRefreshTokenAndReleaseLock() {
        when(valueOperations.setIfAbsent(eq("douyin:token:lock:app123"), eq("1"), any(Duration.class)))
                .thenReturn(true);
        when(valueOperations.get("douyin:refresh:app123")).thenReturn(" ");

        assertThatThrownBy(() -> tokenService.refreshToken("app123"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("missing refresh_token");
        verify(redisTemplate).delete("douyin:token:lock:app123");
    }

    @Test
    void refreshToken_shouldWrapUnexpectedGatewayFailureAndReleaseLock() {
        when(valueOperations.setIfAbsent(eq("douyin:token:lock:app123"), eq("1"), any(Duration.class)))
                .thenReturn(true);
        when(valueOperations.get("douyin:refresh:app123")).thenReturn("refresh-token");
        when(douyinTokenGateway.refreshToken("app123", "refresh-token"))
                .thenThrow(new IllegalStateException("sdk down"));

        assertThatThrownBy(() -> tokenService.refreshToken("app123"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("failed to refresh token");
        verify(redisTemplate).delete("douyin:token:lock:app123");
    }

    @Test
    void isTokenExpiringSoon_shouldReturnTrueWhenNoExpireTime() {
        when(valueOperations.get("douyin:token:expire_at:app123")).thenReturn(null);

        assertThat(tokenService.isTokenExpiringSoon("app123")).isTrue();
    }

    @Test
    void saveRefreshToken_shouldPersistToRedis() {
        tokenService.saveRefreshToken("app123", "refresh-123");

        verify(valueOperations).set("douyin:refresh:app123", "refresh-123", Duration.ofDays(14));
    }

    @Test
    void saveRefreshToken_shouldRejectBlankValue() {
        assertThatThrownBy(() -> tokenService.saveRefreshToken("app123", " "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("refresh_token cannot be blank");
    }

    @Test
    void markReauthorizeRequired_shouldPersistMarkerWithEmptyReasonWhenNull() {
        tokenService.markReauthorizeRequired("app123", null);

        verify(valueOperations).set(eq("douyin:token:reauthorize_required:app123"),
                org.mockito.ArgumentMatchers.contains("reason="),
                eq(Duration.ofDays(1)));
    }

    @Test
    void bootstrapWithRefreshToken_shouldOnlyPersistAfterRemoteRefreshSucceeds() {
        when(douyinTokenGateway.refreshToken("app123", "refresh-123")).thenReturn(
                new DouyinTokenGateway.TokenPayload("new-access-token", "new-refresh-token", 7200L, null, null, 0L)
        );

        tokenService.bootstrapWithRefreshToken("app123", "refresh-123");

        verify(valueOperations).set(eq("douyin:token:app123"), eq("new-access-token"), any(Duration.class));
        verify(valueOperations).set("douyin:refresh:app123", "new-refresh-token", Duration.ofDays(14));
        verify(valueOperations).set(eq("douyin:token:expire_at:app123"), any(String.class), any(Duration.class));
    }

    @Test
    void bootstrapWithRefreshToken_shouldUseFallbackRefreshTokenAndDefaultExpiry() {
        when(douyinTokenGateway.refreshToken("app123", "refresh-123")).thenReturn(
                new DouyinTokenGateway.TokenPayload(" new-access-token ", null, null, null, null, 0L)
        );

        tokenService.bootstrapWithRefreshToken("app123", " refresh-123 ");

        verify(valueOperations).set(eq("douyin:token:app123"), eq("new-access-token"), eq(Duration.ofSeconds(7200)));
        verify(valueOperations).set("douyin:refresh:app123", "refresh-123", Duration.ofDays(14));
    }

    @Test
    void bootstrapWithRefreshToken_shouldNormalizeNonPositiveExpiry() {
        when(douyinTokenGateway.refreshToken("app123", "refresh-123")).thenReturn(
                new DouyinTokenGateway.TokenPayload("new-access-token", "new-refresh-token", -1L, null, null, 0L)
        );

        tokenService.bootstrapWithRefreshToken("app123", "refresh-123");

        verify(valueOperations).set(eq("douyin:token:app123"), eq("new-access-token"), eq(Duration.ofSeconds(7200)));
    }

    @Test
    void bootstrapWithRefreshToken_shouldRejectBlankInputOrBadPayload() {
        assertThatThrownBy(() -> tokenService.bootstrapWithRefreshToken("app123", " "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("refresh_token cannot be blank");

        when(douyinTokenGateway.refreshToken("app123", "refresh-123")).thenReturn(null);
        assertThatThrownBy(() -> tokenService.bootstrapWithRefreshToken("app123", "refresh-123"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("token payload is empty");

        when(douyinTokenGateway.refreshToken("app123", "refresh-456")).thenReturn(
                new DouyinTokenGateway.TokenPayload(" ", "new-refresh-token", 7200L, null, null, 0L)
        );
        assertThatThrownBy(() -> tokenService.bootstrapWithRefreshToken("app123", "refresh-456"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("token payload missing access_token");
    }

    @Test
    void bootstrapWithRefreshToken_shouldNotPersistWhenRemoteRefreshFails() {
        when(douyinTokenGateway.refreshToken("app123", "refresh-123"))
                .thenThrow(new DouyinApiException(31008, "token expired"));

        assertThatThrownBy(() -> tokenService.bootstrapWithRefreshToken("app123", "refresh-123"))
                .isInstanceOf(DouyinApiException.class);

        verify(valueOperations, never()).set(eq("douyin:refresh:app123"), any(), eq(Duration.ofDays(14)));
        verify(valueOperations, never()).set(eq("douyin:token:app123"), any(), any(Duration.class));
    }

    @Test
    void getTokenStatus_shouldReturnMaskedTokenFields() {
        when(valueOperations.get("douyin:token:app123")).thenReturn("access-token-123456");
        when(valueOperations.get("douyin:refresh:app123")).thenReturn("refresh-token-abcdef");
        when(valueOperations.get("douyin:token:expire_at:app123")).thenReturn(Instant.now().getEpochSecond() + 3600);
        when(valueOperations.get("douyin:token:reauthorize_required:app123")).thenReturn(null);

        DouyinTokenService.TokenStatus status = tokenService.getTokenStatus("app123");

        assertThat(status.getAppId()).isEqualTo("app123");
        assertThat(status.isHasAccessToken()).isTrue();
        assertThat(status.isHasRefreshToken()).isTrue();
        assertThat(status.getMaskedAccessToken()).isEqualTo("****");
        assertThat(status.getMaskedRefreshToken()).isEqualTo("****");
        assertThat(status.getMaskedAccessToken()).doesNotContain("acce", "3456");
        assertThat(status.getMaskedRefreshToken()).doesNotContain("refr", "cdef");
        assertThat(status.isReauthorizeRequired()).isFalse();
    }

    @Test
    void exchangeCodeAndBootstrap_shouldCacheAllianceTokens() {
        when(douyinTokenGateway.createToken(any())).thenReturn(
                new DouyinTokenGateway.TokenPayload("alliance-access", "alliance-refresh", 7200L, "auth-456", "Colonel", 1L)
        );

        tokenService.exchangeCodeAndBootstrap("app123", "auth-code", "authorization_code", null, null, "auth-456", "Colonel");

        verify(valueOperations).set(eq("douyin:token:app123"), eq("alliance-access"), any(Duration.class));
        verify(valueOperations).set(eq("douyin:refresh:app123"), eq("alliance-refresh"), eq(Duration.ofDays(14)));
        verify(valueOperations).set(eq("douyin:token:expire_at:app123"), any(String.class), any(Duration.class));
        verify(redisTemplate).delete("douyin:token:reauthorize_required:app123");
    }

    @Test
    void exchangeCodeAndBootstrap_shouldRejectUnsupportedGrantTypeOrBlankCode() {
        assertThatThrownBy(() -> tokenService.exchangeCodeAndBootstrap("app123", "auth-code", "refresh_token", null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("grant_type=authorization_code");
        assertThatThrownBy(() -> tokenService.exchangeCodeAndBootstrap("app123", " ", "authorization_code", null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("authorization_code cannot be blank");
    }

    @Test
    void exchangeCodeAndBootstrap_shouldTranslateExpiredAuthorizationCode() {
        when(douyinTokenGateway.createToken(any()))
                .thenThrow(new DouyinApiException(31005, "code expired"));

        assertThatThrownBy(() -> tokenService.exchangeCodeAndBootstrap("app123", "auth-code"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("authorization code does not match current app");
    }

    @Test
    void exchangeCodeAndBootstrap_shouldRequireRefreshTokenFromPayload() {
        when(douyinTokenGateway.createToken(any())).thenReturn(
                new DouyinTokenGateway.TokenPayload("access-from-code", null, 7200L, null, null, 0L)
        );

        assertThatThrownBy(() -> tokenService.exchangeCodeAndBootstrap("app123", "auth-code"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("refresh_token cannot be blank");
    }

    @Test
    void exchangeCodeAndBootstrap_shouldRejectShopIdWhenAuthSubjectTypeProvidedForAllianceCode() {
        assertThatThrownBy(() -> tokenService.exchangeCodeAndBootstrap(
                "app123", "auth-code", "authorization_code", null, "17239", "auth-456", "kol"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("auth_subject_type and shop_id cannot be provided at the same time");
    }

    @Test
    void exchangeCodeAndBootstrap_shouldCacheTokens() {
        when(douyinTokenGateway.createToken(any())).thenReturn(
                new DouyinTokenGateway.TokenPayload("access-from-code", "refresh-from-code", 7200L, null, null, 0L)
        );

        tokenService.exchangeCodeAndBootstrap("app123", "auth-code");

        verify(valueOperations).set(eq("douyin:token:app123"), eq("access-from-code"), any(Duration.class));
        verify(valueOperations).set(eq("douyin:refresh:app123"), eq("refresh-from-code"), eq(Duration.ofDays(14)));
        verify(valueOperations).set(eq("douyin:token:expire_at:app123"), any(String.class), any(Duration.class));
        verify(redisTemplate).delete("douyin:token:reauthorize_required:app123");
    }

    @Test
    void exchangeCodeAndBootstrap_shouldRejectShopIdWhenAuthSubjectTypeProvided() {
        assertThatThrownBy(() -> tokenService.exchangeCodeAndBootstrap(
                "app123", "auth-code", "authorization_code", null, "17239", "auth-456", "Colonel"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("auth_subject_type and shop_id cannot be provided at the same time");
    }

    @Test
    void privateHelpers_shouldNormalizePrimitiveAndMaskValues() {
        DouyinConfig configWithoutClientKey = new DouyinConfig();
        configWithoutClientKey.setAppId("fallback-app");
        DouyinTokenService fallbackService = new DouyinTokenService(
                redisTemplate, douyinTokenGateway, configWithoutClientKey,
                tokenRefreshExecutor, 300L, 5L);

        assertThat(ReflectionTestUtils.<String>invokeMethod(fallbackService, "resolveCacheKey", " "))
                .isEqualTo("fallback-app");
        assertThat(ReflectionTestUtils.<Long>invokeMethod(tokenService, "asLong", 12, 0L)).isEqualTo(12L);
        assertThat(ReflectionTestUtils.<Long>invokeMethod(tokenService, "asLong", "34", 0L)).isEqualTo(34L);
        assertThat(ReflectionTestUtils.<Long>invokeMethod(tokenService, "asLong", "bad", 9L)).isEqualTo(9L);
        assertThat(ReflectionTestUtils.<String>invokeMethod(tokenService, "maskSecret", (String) null)).isEqualTo("");
        assertThat(ReflectionTestUtils.<String>invokeMethod(tokenService, "maskSecret", " ")).isEqualTo("");
        assertThat(ReflectionTestUtils.<String>invokeMethod(tokenService, "maskSecret", "secret")).isEqualTo("****");
        assertThat(ReflectionTestUtils.<String>invokeMethod(tokenService, "normalizeGrantType", (String) null))
                .isEqualTo("authorization_code");
        assertThat(ReflectionTestUtils.<String>invokeMethod(tokenService, "maskAppId", "app123")).isEqualTo("app123");
        assertThat(ReflectionTestUtils.<String>invokeMethod(tokenService, "maskAppId", "abcdefghi")).isEqualTo("abc***ghi");
    }
}
