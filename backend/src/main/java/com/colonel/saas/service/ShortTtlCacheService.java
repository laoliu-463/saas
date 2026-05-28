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

/**
 * 短 TTL 内存缓存服务。
 * <p>
 * 基于 ConcurrentHashMap 实现的短生命周期本地缓存，支持 TTL 过期、容量淘汰、
 * 以及通过 Redis Pub/Sub 跨节点前缀驱逐。适用于高频读取但变化不频繁的配置类数据。
 * </p>
 *
 * <ul>
 *     <li>带 TTL 的缓存读取与自动加载（{@link #get}）</li>
 *     <li>按前缀驱逐本地及远程节点缓存（{@link #evictByPrefix}）</li>
 *     <li>单键驱逐（{@link #evict}）</li>
 *     <li>仅本地前缀驱逐（{@link #evictLocalByPrefix}）</li>
 *     <li>超容量时按过期时间淘汰最早过期条目</li>
 * </ul>
 *
 * <p><b>业务域：</b>基础设施 — 本地缓存</p>
 * <p><b>协作关系：</b></p>
 * <ul>
 *     <li>{@link RedisTemplate} — 可选的跨节点缓存驱逐通道</li>
 * </ul>
 */
@Service
public class ShortTtlCacheService {

    /** Redis Pub/Sub 驱逐频道名 */
    public static final String EVICT_CHANNEL = "saas:short-ttl-cache:evict-prefix";

    /** 本地缓存存储 */
    private final ConcurrentHashMap<String, CacheEntry<?>> cache = new ConcurrentHashMap<>();
    /** 最大缓存条目数 */
    private final int maxEntries;
    /** Redis 模板（可选，用于跨节点驱逐通知） */
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

    /**
     * 获取缓存值，缓存未命中或已过期时通过 loader 加载并写入缓存。
     * <p>处理流程：</p>
     * <ol>
     *     <li>清理已过期条目</li>
     *     <li>命中有效缓存则直接返回</li>
     *     <li>未命中则调用 loader 加载值并写入缓存</li>
     *     <li>执行容量淘汰策略</li>
     * </ol>
     *
     * @param <T>   值类型
     * @param key   缓存键
     * @param ttl   缓存存活时间
     * @param loader 缓存未命中时的数据加载器
     * @return 缓存值或新加载的值
     */
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

    /**
     * 按前缀驱逐缓存（本地 + 远程）。
     * <p>先清除本地匹配前缀的条目，再通过 Redis Pub/Sub 通知其他节点驱逐。</p>
     *
     * @param prefix 缓存键前缀
     */
    public void evictByPrefix(String prefix) {
        evictLocalByPrefix(prefix);
        publishEviction(prefix);
    }

    /**
     * 驱逐单个缓存条目。
     *
     * @param key 缓存键（空或空白时跳过）
     */
    public void evict(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        cache.remove(key);
    }

    /**
     * 仅清除本地匹配前缀的缓存条目（不通知远程节点）。
     *
     * @param prefix 缓存键前缀（空或空白时跳过）
     */
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

    /**
     * 缓存条目，包含值和过期时间戳。
     *
     * @param <T>            值类型
     * @param value          缓存值
     * @param expiresAtMillis 过期时间戳（毫秒）
     */
    private record CacheEntry<T>(T value, long expiresAtMillis) {
    }
}
