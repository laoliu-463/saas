package com.colonel.saas.service;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.gateway.douyin.DouyinPromotionGateway;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PromotionLinkIdempotencyService {

    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final Duration IN_FLIGHT_TTL = Duration.ofMinutes(2);
    private static final String COMPLETED_SUFFIX = ":completed";
    private static final String IN_FLIGHT_SUFFIX = ":inflight";
    private static final int MAX_KEY_LENGTH = 128;

    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ConcurrentHashMap<String, String> localStore = new ConcurrentHashMap<>();

    public PromotionLinkIdempotencyService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.redisTemplate = null;
    }

    @Autowired
    public PromotionLinkIdempotencyService(
            ObjectMapper objectMapper,
            ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider) {
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplateProvider == null ? null : redisTemplateProvider.getIfAvailable();
    }

    public String buildScopeKey(UUID userId, String activityId, String productId, String idempotencyKey) {
        String normalizedKey = normalizeKey(idempotencyKey);
        return userId + ":" + activityId + ":" + productId + ":" + normalizedKey;
    }

    public Optional<DouyinPromotionGateway.PromotionLinkResult> findCompleted(String scopeKey) {
        if (!StringUtils.hasText(scopeKey)) {
            return Optional.empty();
        }
        String payload = readValue(scopeKey + COMPLETED_SUFFIX);
        if (!StringUtils.hasText(payload)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(payload, DouyinPromotionGateway.PromotionLinkResult.class));
        } catch (JsonProcessingException ex) {
            deleteValue(scopeKey + COMPLETED_SUFFIX);
            return Optional.empty();
        }
    }

    public void markCompleted(String scopeKey, DouyinPromotionGateway.PromotionLinkResult result) {
        if (!StringUtils.hasText(scopeKey) || result == null) {
            return;
        }
        try {
            writeValue(scopeKey + COMPLETED_SUFFIX, objectMapper.writeValueAsString(result), IDEMPOTENCY_TTL);
            deleteValue(scopeKey + IN_FLIGHT_SUFFIX);
        } catch (JsonProcessingException ex) {
            throw BusinessException.conflict("推广链接幂等结果序列化失败");
        }
    }

    /**
     * @return true when this request acquired the in-flight slot; false when another request is processing the same key
     */
    public boolean tryAcquireInFlight(String scopeKey) {
        if (!StringUtils.hasText(scopeKey)) {
            return true;
        }
        String inflightKey = scopeKey + IN_FLIGHT_SUFFIX;
        if (redisTemplate != null) {
            Boolean locked = redisTemplate.opsForValue().setIfAbsent(inflightKey, "1", IN_FLIGHT_TTL);
            return Boolean.TRUE.equals(locked);
        }
        return localStore.putIfAbsent(inflightKey, "1") == null;
    }

    public void releaseInFlight(String scopeKey) {
        if (!StringUtils.hasText(scopeKey)) {
            return;
        }
        deleteValue(scopeKey + IN_FLIGHT_SUFFIX);
    }

    private String normalizeKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw BusinessException.param("Idempotency-Key 不能为空");
        }
        String trimmed = idempotencyKey.trim();
        if (trimmed.length() > MAX_KEY_LENGTH) {
            throw BusinessException.param("Idempotency-Key 长度不能超过 " + MAX_KEY_LENGTH);
        }
        return trimmed;
    }

    private String readValue(String storageKey) {
        if (redisTemplate != null) {
            Object value = redisTemplate.opsForValue().get(storageKey);
            return value == null ? null : String.valueOf(value);
        }
        return localStore.get(storageKey);
    }

    private void writeValue(String storageKey, String value, Duration ttl) {
        if (redisTemplate != null) {
            redisTemplate.opsForValue().set(storageKey, value, ttl);
            return;
        }
        // Local fallback is used in unit tests only; entries are short-lived per test JVM.
        localStore.put(storageKey, value);
    }

    private void deleteValue(String storageKey) {
        if (redisTemplate != null) {
            redisTemplate.delete(storageKey);
            return;
        }
        localStore.remove(storageKey);
    }
}
