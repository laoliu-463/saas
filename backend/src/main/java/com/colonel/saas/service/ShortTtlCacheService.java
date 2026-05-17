package com.colonel.saas.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
public class ShortTtlCacheService {

    private final ConcurrentHashMap<String, CacheEntry<?>> cache = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Duration ttl, Supplier<T> loader) {
        long now = System.currentTimeMillis();
        CacheEntry<?> existing = cache.get(key);
        if (existing != null && existing.expiresAtMillis() > now) {
            return (T) existing.value();
        }
        T value = loader.get();
        cache.put(key, new CacheEntry<>(value, now + Math.max(ttl.toMillis(), 0L)));
        return value;
    }

    public void evictByPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return;
        }
        cache.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private record CacheEntry<T>(T value, long expiresAtMillis) {
    }
}
