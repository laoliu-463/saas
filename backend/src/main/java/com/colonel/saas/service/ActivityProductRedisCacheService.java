package com.colonel.saas.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 活动商品列表 Redis 短 TTL 缓存。
 *
 * <p>PostgreSQL 仍是商品事实源；Redis 只缓存高频读取的活动商品视图和状态计数。
 * 写操作通过递增 activity 级 version 失效旧 key，旧缓存依赖 TTL 自然回收，避免 KEYS 扫描。</p>
 */
@Slf4j
@Service
public class ActivityProductRedisCacheService {

    private static final String VERSION_PREFIX = "product:activity:version:";
    private static final String LIST_PREFIX = "product:activity:list:";
    private static final String STATUS_COUNTS_PREFIX = "product:activity:status-counts:";
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final Duration ttl;

    public ActivityProductRedisCacheService(
            RedisTemplate<String, Object> redisTemplate,
            ObjectMapper objectMapper,
            @Value("${product.activity.cache.redis.enabled:true}") boolean enabled,
            @Value("${product.activity.cache.redis.ttl-ms:250}") long ttlMs) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.ttl = Duration.ofMillis(Math.max(ttlMs, 0L));
    }

    public Map<String, Object> getOrLoadActivityProductList(
            String activityId,
            String queryKey,
            Supplier<Map<String, Object>> loader) {
        if (!cacheUsable(activityId) || !StringUtils.hasText(queryKey)) {
            return loader.get();
        }
        return getOrLoad(listKey(activityId, queryKey), loader);
    }

    public Map<String, Object> getOrLoadActivityProductStatusCounts(
            String activityId,
            Supplier<Map<String, Object>> loader) {
        if (!cacheUsable(activityId)) {
            return loader.get();
        }
        return getOrLoad(statusCountsKey(activityId), loader);
    }

    public void evictActivity(String activityId) {
        if (!cacheUsable(activityId)) {
            return;
        }
        try {
            redisTemplate.opsForValue().increment(versionKey(activityId));
        } catch (RuntimeException ex) {
            log.debug("Activity product Redis cache eviction skipped, activityId={}, message={}", activityId, ex.getMessage());
        }
    }

    private Map<String, Object> getOrLoad(String key, Supplier<Map<String, Object>> loader) {
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached instanceof String text && StringUtils.hasText(text)) {
                return objectMapper.readValue(text, MAP_TYPE);
            }
        } catch (Exception ex) {
            try {
                redisTemplate.delete(key);
            } catch (RuntimeException ignored) {
                // Redis is an acceleration layer only; DB fallback must continue.
            }
            log.debug("Activity product Redis cache read skipped, key={}, message={}", key, ex.getMessage());
        }
        Map<String, Object> loaded = loader.get();
        put(key, loaded);
        return loaded;
    }

    private void put(String key, Map<String, Object> value) {
        if (value == null || ttl.isZero()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception ex) {
            log.debug("Activity product Redis cache write skipped, key={}, message={}", key, ex.getMessage());
        }
    }

    private String listKey(String activityId, String queryKey) {
        return LIST_PREFIX + normalize(activityId) + ":v" + version(activityId) + ":" + sha256(queryKey);
    }

    private String statusCountsKey(String activityId) {
        return STATUS_COUNTS_PREFIX + normalize(activityId) + ":v" + version(activityId);
    }

    private String version(String activityId) {
        try {
            Object raw = redisTemplate.opsForValue().get(versionKey(activityId));
            return raw == null || !StringUtils.hasText(String.valueOf(raw)) ? "0" : String.valueOf(raw).trim();
        } catch (RuntimeException ex) {
            log.debug("Activity product Redis cache version read skipped, activityId={}, message={}", activityId, ex.getMessage());
            return "0";
        }
    }

    private String versionKey(String activityId) {
        return VERSION_PREFIX + normalize(activityId);
    }

    private boolean cacheUsable(String activityId) {
        return enabled && !ttl.isZero() && StringUtils.hasText(activityId);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
