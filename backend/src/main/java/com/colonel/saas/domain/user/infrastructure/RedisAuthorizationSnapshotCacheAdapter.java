package com.colonel.saas.domain.user.infrastructure;

import com.colonel.saas.domain.user.domain.AuthorizationSnapshot;
import com.colonel.saas.domain.user.port.AuthorizationSnapshotCache;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public class RedisAuthorizationSnapshotCacheAdapter implements AuthorizationSnapshotCache {

    private static final Logger log =
            LoggerFactory.getLogger(RedisAuthorizationSnapshotCacheAdapter.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisAuthorizationSnapshotCacheAdapter(
            RedisTemplate<String, Object> redisTemplate,
            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<AuthorizationSnapshot> get(UUID userId, long authzVersion) {
        String cacheKey = key(userId, authzVersion);
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached == null) {
            return Optional.empty();
        }
        if (!(cached instanceof String json)) {
            discardInvalidEntry(cacheKey, "UnexpectedValueType");
            return Optional.empty();
        }
        try {
            AuthorizationSnapshot snapshot =
                    objectMapper.readValue(json, AuthorizationSnapshot.class);
            if (snapshot == null) {
                discardInvalidEntry(cacheKey, "NullSnapshot");
                return Optional.empty();
            }
            if (!userId.equals(snapshot.subject().userId())
                    || snapshot.subject().authzVersion() != authzVersion) {
                discardInvalidEntry(cacheKey, "SnapshotKeyMismatch");
                return Optional.empty();
            }
            return Optional.of(snapshot);
        } catch (JsonProcessingException exception) {
            discardInvalidEntry(cacheKey, exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @Override
    public void put(AuthorizationSnapshot snapshot, Duration ttl) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(ttl, "ttl");
        String cacheKey = key(
                snapshot.subject().userId(),
                snapshot.subject().authzVersion());
        try {
            String json = objectMapper.writeValueAsString(snapshot);
            redisTemplate.opsForValue().set(cacheKey, json, ttl);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "authorization snapshot serialization failed",
                    exception);
        }
    }

    @Override
    public void evict(UUID userId, long authzVersion) {
        redisTemplate.delete(key(userId, authzVersion));
    }

    static String key(UUID userId, long authzVersion) {
        return "authz:snapshot:" + userId + ":" + authzVersion;
    }

    private void discardInvalidEntry(String cacheKey, String exceptionClass) {
        log.warn(
                "Authorization snapshot cache entry is invalid: key={}, exception={}",
                cacheKey,
                exceptionClass);
        redisTemplate.delete(cacheKey);
    }
}
