package com.colonel.saas.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityProductRedisCacheServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;

    private ActivityProductRedisCacheService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new ActivityProductRedisCacheService(redisTemplate, new ObjectMapper(), true, 5000);
    }

    @Test
    void getOrLoadActivityProductList_shouldReturnCachedJsonWithoutCallingLoader() {
        when(valueOperations.get(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            if (key.startsWith("product:activity:list:ACT-1:v0:")) {
                return "{\"total\":2,\"items\":[{\"productId\":\"P1\"}]}";
            }
            return null;
        });
        AtomicInteger loads = new AtomicInteger();

        Map<String, Object> result = service.getOrLoadActivityProductList(
                "ACT-1",
                "count=20|status=1",
                loader(loads, Map.of("total", 1)));

        assertThat(result.get("total")).isEqualTo(2);
        assertThat(loads).hasValue(0);
        verify(valueOperations, never()).set(anyString(), anyString(), eq(Duration.ofMillis(5000)));
    }

    @Test
    void getOrLoadActivityProductStatusCounts_shouldStoreLoadedValueOnMiss() {
        when(valueOperations.get(anyString())).thenReturn(null);
        AtomicInteger loads = new AtomicInteger();

        Map<String, Object> result = service.getOrLoadActivityProductStatusCounts(
                "ACT-1",
                loader(loads, Map.of("total", 99, "promoting", 88)));

        assertThat(result).containsEntry("total", 99);
        assertThat(loads).hasValue(1);
        verify(valueOperations).set(
                eq("product:activity:status-counts:ACT-1:v0"),
                contains("\"promoting\":88"),
                eq(Duration.ofMillis(5000)));
    }

    @Test
    void evictActivity_shouldIncrementActivityVersion() {
        service.evictActivity(" ACT-1 ");

        verify(valueOperations).increment("product:activity:version:ACT-1");
    }

    @Test
    void getOrLoad_shouldFallBackToLoaderWhenRedisFails() {
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("redis down"));
        when(redisTemplate.delete(anyString())).thenThrow(new RuntimeException("redis down"));
        AtomicInteger loads = new AtomicInteger();

        Map<String, Object> result = service.getOrLoadActivityProductList(
                "ACT-1",
                "count=20",
                loader(loads, Map.of("total", 3)));

        assertThat(result).containsEntry("total", 3);
        assertThat(loads).hasValue(1);
    }

    private Supplier<Map<String, Object>> loader(AtomicInteger loads, Map<String, Object> value) {
        return () -> {
            loads.incrementAndGet();
            return new LinkedHashMap<>(value);
        };
    }
}
