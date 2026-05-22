package com.colonel.saas.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShortTtlCacheServiceTest {

    @Test
    void get_shouldReturnCachedValueBeforeTtlExpires() {
        ShortTtlCacheService service = new ShortTtlCacheService();
        AtomicInteger loads = new AtomicInteger();

        String first = service.get("dashboard:summary", Duration.ofMinutes(1), () -> "value-" + loads.incrementAndGet());
        String second = service.get("dashboard:summary", Duration.ofMinutes(1), () -> "value-" + loads.incrementAndGet());

        assertThat(first).isEqualTo("value-1");
        assertThat(second).isEqualTo("value-1");
        assertThat(loads).hasValue(1);
    }

    @Test
    void get_shouldReloadExpiredOrZeroTtlEntries() {
        ShortTtlCacheService service = new ShortTtlCacheService();
        AtomicInteger loads = new AtomicInteger();

        String first = service.get("k", Duration.ZERO, () -> "value-" + loads.incrementAndGet());
        String second = service.get("k", Duration.ZERO, () -> "value-" + loads.incrementAndGet());

        assertThat(first).isEqualTo("value-1");
        assertThat(second).isEqualTo("value-2");
    }

    @Test
    void get_shouldEnforceCapacityByEarliestExpiry() {
        @SuppressWarnings("unchecked")
        ObjectProvider<RedisTemplate<String, Object>> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        ShortTtlCacheService service = new ShortTtlCacheService(1, provider);

        service.get("first", Duration.ofMinutes(1), () -> "first-value");
        service.get("second", Duration.ofMinutes(2), () -> "second-value");

        assertThat(service.get("first", Duration.ofMinutes(1), () -> "first-reloaded"))
                .isEqualTo("first-reloaded");
        assertThat(service.get("second", Duration.ofMinutes(2), () -> "second-reloaded"))
                .isEqualTo("second-value");
    }

    @Test
    void evictByPrefix_shouldEvictLocallyAndPublishBestEffort() {
        @SuppressWarnings("unchecked")
        ObjectProvider<RedisTemplate<String, Object>> provider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        when(provider.getIfAvailable()).thenReturn(redisTemplate);
        ShortTtlCacheService service = new ShortTtlCacheService(10, provider);
        service.get("dashboard:summary", Duration.ofMinutes(1), () -> "stale");

        service.evictByPrefix("dashboard:");

        assertThat(service.get("dashboard:summary", Duration.ofMinutes(1), () -> "fresh"))
                .isEqualTo("fresh");
        verify(redisTemplate).convertAndSend(ShortTtlCacheService.EVICT_CHANNEL, "dashboard:");
    }

    @Test
    void evictByPrefix_shouldIgnoreBlankPrefixAndRedisFailure() {
        @SuppressWarnings("unchecked")
        ObjectProvider<RedisTemplate<String, Object>> provider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        when(provider.getIfAvailable()).thenReturn(redisTemplate);
        doThrow(new RuntimeException("redis down"))
                .when(redisTemplate).convertAndSend(ShortTtlCacheService.EVICT_CHANNEL, "orders:");
        ShortTtlCacheService service = new ShortTtlCacheService(10, provider);

        service.evictLocalByPrefix(" ");
        service.evictByPrefix("orders:");

        verify(redisTemplate).convertAndSend(ShortTtlCacheService.EVICT_CHANNEL, "orders:");
    }
}
