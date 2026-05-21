package com.colonel.saas.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
public class ShortTtlCacheService {

    public static final String EVICT_CHANNEL = "saas:short-ttl-cache:evict-prefix";

    private final ConcurrentHashMap<String, CacheEntry<?>> cache = new ConcurrentHashMap<>();
    private final int maxEntries;
    private final RedisTemplate<String, Object> redisTemplate;

    public ShortTtlCacheService() {
        this(1024, (RedisTemplate<String, Object>) null);
    }

    @Autowired
    public ShortTtlCacheService(
            @Value("${app.short-ttl-cache.max-entries:1024}") int maxEntries,
            ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider) {
        this(maxEntries, redisTemplateProvider.getIfAvailable());
    }

    private ShortTtlCacheService(int maxEntries, RedisTemplate<String, Object> redisTemplate) {
        this.maxEntries = Math.max(maxEntries, 1);
        this.redisTemplate = redisTemplate;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Duration ttl, Supplier<T> loader) {
        long now = System.currentTimeMillis();
        evictExpired(now);
        CacheEntry<?> existing = cache.get(key);
        if (existing != null && existing.expiresAtMillis() > now) {
            return (T) existing.value();
        }
        T value = loader.get();
        long ttlMillis = ttl == null ? 0L : ttl.toMillis();
        cache.put(key, new CacheEntry<>(value, now + Math.max(ttlMillis, 0L)));
        enforceCapacity(now);
        return value;
    }

    public void evictByPrefix(String prefix) {
        evictLocalByPrefix(prefix);
        publishEviction(prefix);
    }

    public void evictLocalByPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return;
        }
        cache.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private void publishEviction(String prefix) {
        if (redisTemplate == null || prefix == null || prefix.isBlank()) {
            return;
        }
        try {
            redisTemplate.convertAndSend(EVICT_CHANNEL, prefix);
        } catch (RuntimeException ignored) {
            // Redis invalidation is best-effort; local eviction already happened.
        }
    }

    private void evictExpired(long now) {
        cache.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() <= now);
    }

    private void enforceCapacity(long now) {
        if (cache.size() <= maxEntries) {
            return;
        }
        evictExpired(now);
        int overflow = cache.size() - maxEntries;
        if (overflow <= 0) {
            return;
        }
        cache.entrySet().stream()
                .sorted(Comparator.comparingLong(entry -> entry.getValue().expiresAtMillis()))
                .limit(overflow)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(cache::remove);
    }

    private record CacheEntry<T>(T value, long expiresAtMillis) {
    }
}
