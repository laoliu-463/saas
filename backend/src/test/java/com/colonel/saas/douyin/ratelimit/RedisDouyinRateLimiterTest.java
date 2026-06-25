package com.colonel.saas.douyin.ratelimit;

import com.colonel.saas.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisDouyinRateLimiterTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    private DouyinRateLimitProperties properties;
    private RedisDouyinRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        properties = new DouyinRateLimitProperties();
        properties.setAppPerSecond(60);
        properties.setGlobalPerSecond(900);
        properties.setAcquireTimeoutMs(0);
        properties.setBackoffMs(1);
        rateLimiter = new RedisDouyinRateLimiter(redisTemplate, properties);
    }

    @Test
    void acquireShouldUseGlobalAndAppBuckets() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any()))
                .thenReturn(1L);

        rateLimiter.acquire(" app bad/id ", "alliance.colonelActivityProduct");

        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(any(DefaultRedisScript.class), keysCaptor.capture(), any(), any(), any());
        assertThat(keysCaptor.getValue()).hasSize(2);
        assertThat(keysCaptor.getValue().get(0)).startsWith("douyin:rate:global:");
        assertThat(keysCaptor.getValue().get(1)).startsWith("douyin:rate:app:app_bad_id:");
    }

    @Test
    void acquireShouldThrowRateLimitWhenBucketsAreFull() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any()))
                .thenReturn(0L);

        assertThatThrownBy(() -> rateLimiter.acquire("app123", "alliance.colonelActivityProduct"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo("UPSTREAM_RATE_LIMIT"));
    }

    @Test
    void acquireShouldBlockWhenRedisIsUnavailable() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any()))
                .thenThrow(new IllegalStateException("redis down"));

        assertThatThrownBy(() -> rateLimiter.acquire("app123", "alliance.colonelActivityProduct"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo("UPSTREAM_SERVICE_ERROR"));
    }

    @Test
    void acquireShouldSkipWhenLimiterDisabled() {
        properties.setEnabled(false);

        rateLimiter.acquire("app123", "alliance.colonelActivityProduct");
    }
}
