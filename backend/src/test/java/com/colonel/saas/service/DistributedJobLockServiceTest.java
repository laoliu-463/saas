package com.colonel.saas.service;

import io.lettuce.core.RedisCommandExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DistributedJobLockServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Test
    void tryAcquireStrict_shouldThrowWhenRedisUnavailable() {
        DistributedJobLockService service = new DistributedJobLockService(redisTemplate, false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("job:lock"), eq("1"), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("down", null));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service.tryAcquireStrict("job:lock", Duration.ofMinutes(5)))
                .isInstanceOf(RedisConnectionFailureException.class);
    }

    @Test
    void tryAcquire_shouldReturnTrueWhenRedisLockGranted() {
        DistributedJobLockService service = new DistributedJobLockService(redisTemplate, false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("job:lock"), eq("1"), any(Duration.class))).thenReturn(true);

        assertThat(service.tryAcquire("job:lock", Duration.ofMinutes(5))).isTrue();
    }

    @Test
    void tryAcquire_shouldFallbackToLocalLockInTestModeWhenRedisUnavailable() {
        DistributedJobLockService service = new DistributedJobLockService(redisTemplate, true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("job:lock"), eq("1"), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("down", null));

        assertThat(service.tryAcquire("job:lock", Duration.ofMinutes(5))).isTrue();
        assertThat(service.tryAcquire("job:lock", Duration.ofMinutes(5))).isFalse();
    }

    @Test
    void release_shouldDeleteRedisKey() {
        DistributedJobLockService service = new DistributedJobLockService(redisTemplate, false);
        service.release("job:lock");
        verify(redisTemplate).delete("job:lock");
    }

    @Test
    void release_shouldTolerateRedisFailureInTestMode() {
        DistributedJobLockService service = new DistributedJobLockService(redisTemplate, true);
        when(redisTemplate.delete("job:lock")).thenThrow(new RedisCommandExecutionException("down"));
        service.release("job:lock");
    }
}
