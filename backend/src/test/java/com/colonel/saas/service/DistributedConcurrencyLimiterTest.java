package com.colonel.saas.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DistributedConcurrencyLimiterTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    void tryAcquire_shouldUseOwnerLeaseAndRejectWhenSlotsAreExhausted() {
        when(redisTemplate.execute(any(), anyList(), any(), any(), any(), any()))
                .thenReturn(1L)
                .thenReturn(0L);
        DistributedConcurrencyLimiter limiter = new DistributedConcurrencyLimiter(redisTemplate, false, 2);

        assertThat(limiter.tryAcquire("owner-1", Duration.ofMinutes(2))).isTrue();
        assertThat(limiter.tryAcquire("owner-2", Duration.ofMinutes(2))).isFalse();
    }

    @Test
    void localFallback_shouldReleaseByOwnerAndReclaimExpiredLease() {
        doThrow(new RedisConnectionFailureException("down", null))
                .when(redisTemplate).execute(any(), anyList(), any(), any(), any(), any());
        DistributedConcurrencyLimiter limiter = new DistributedConcurrencyLimiter(redisTemplate, true, 1);

        assertThat(limiter.tryAcquire("owner-1", Duration.ofMillis(500))).isTrue();
        assertThat(limiter.tryAcquire("owner-2", Duration.ofMinutes(2))).isFalse();
        limiter.release("owner-2");
        assertThat(limiter.tryAcquire("owner-2", Duration.ofMinutes(2))).isFalse();

        try {
            Thread.sleep(550L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
        assertThat(limiter.tryAcquire("owner-2", Duration.ofMinutes(2))).isTrue();
    }
}
