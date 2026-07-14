package com.colonel.saas.domain.user.infrastructure;

import com.colonel.saas.domain.user.api.AuthorizationScope;
import com.colonel.saas.domain.user.api.PermissionCode;
import com.colonel.saas.domain.user.domain.AuthorizationSnapshot;
import com.colonel.saas.domain.user.domain.AuthorizationSubject;
import com.colonel.saas.domain.user.domain.GrantedRolePermission;
import com.colonel.saas.domain.user.port.AuthorizationSnapshotCache;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
            JsonNode rawPayload = objectMapper.readTree(json);
            SnapshotPayload payload = objectMapper.treeToValue(rawPayload, SnapshotPayload.class);
            validatePayloadTypes(rawPayload);
            return Optional.of(toDomain(payload, userId, authzVersion));
        } catch (JsonProcessingException exception) {
            discardInvalidEntry(cacheKey, exception.getClass().getSimpleName());
            return Optional.empty();
        } catch (InvalidCachePayloadException exception) {
            discardInvalidEntry(cacheKey, exception.reason());
            return Optional.empty();
        }
    }

    @Override
    public void put(AuthorizationSnapshot snapshot, Duration ttl) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(ttl, "ttl");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        String cacheKey = key(
                snapshot.subject().userId(),
                snapshot.subject().authzVersion());
        try {
            String json = objectMapper.writeValueAsString(toPayload(snapshot));
            redisTemplate.opsForValue().set(cacheKey, json, ttl);
        } catch (JsonProcessingException exception) {
            throw new SerializationException(
                    "authorization snapshot serialization failed",
                    exception);
        }
    }

    @Override
    public void evict(UUID userId, long authzVersion) {
        redisTemplate.delete(key(userId, authzVersion));
    }

    static String key(UUID userId, long authzVersion) {
        Objects.requireNonNull(userId, "userId");
        if (authzVersion < 1) {
            throw new IllegalArgumentException("authzVersion must be positive");
        }
        return "authz:snapshot:" + userId + ":" + authzVersion;
    }

    private void discardInvalidEntry(String cacheKey, String exceptionClass) {
        log.warn(
                "Authorization snapshot cache entry is invalid: key={}, exception={}",
                cacheKey,
                exceptionClass);
        redisTemplate.delete(cacheKey);
    }

    private static SnapshotPayload toPayload(AuthorizationSnapshot snapshot) {
        AuthorizationSubject subject = snapshot.subject();
        List<GrantPayload> grants = snapshot.grants().stream()
                .map(grant -> new GrantPayload(
                        grant.roleId(),
                        grant.permission().value(),
                        grant.domainCode(),
                        grant.dataScopeRequired(),
                        grant.scope()))
                .toList();
        return new SnapshotPayload(
                new SubjectPayload(
                        subject.userId(),
                        subject.deptId(),
                        subject.authzVersion()),
                grants);
    }

    private static AuthorizationSnapshot toDomain(
            SnapshotPayload payload,
            UUID requestedUserId,
            long requestedAuthzVersion) {
        if (payload == null || payload.subject() == null) {
            throw invalid("MissingSubject");
        }
        SubjectPayload subject = payload.subject();
        if (subject.userId() == null || subject.authzVersion() == null) {
            throw invalid("MissingSubjectIdentity");
        }
        if (subject.authzVersion() < 1) {
            throw invalid("InvalidSubjectVersion");
        }
        if (!requestedUserId.equals(subject.userId())
                || requestedAuthzVersion != subject.authzVersion()) {
            throw invalid("SnapshotKeyMismatch");
        }
        if (payload.grants() == null) {
            throw invalid("MissingGrants");
        }

        List<GrantedRolePermission> grants = new ArrayList<>(payload.grants().size());
        for (GrantPayload grant : payload.grants()) {
            if (grant == null) {
                throw invalid("NullGrant");
            }
            if (grant.roleId() == null) {
                throw invalid("MissingRoleId");
            }
            if (grant.permissionCode() == null) {
                throw invalid("MissingPermissionCode");
            }
            PermissionCode permissionCode;
            try {
                permissionCode = new PermissionCode(grant.permissionCode());
            } catch (IllegalArgumentException exception) {
                throw invalid("InvalidPermissionCode");
            }
            if (grant.domainCode() == null || grant.domainCode().isBlank()) {
                throw invalid("InvalidDomainCode");
            }
            if (grant.dataScopeRequired() == null) {
                throw invalid("MissingDataScopeRequired");
            }
            if (grant.scope() == null) {
                throw invalid("MissingScope");
            }
            grants.add(new GrantedRolePermission(
                    grant.roleId(),
                    permissionCode,
                    grant.domainCode(),
                    grant.dataScopeRequired(),
                    grant.scope()));
        }

        return new AuthorizationSnapshot(
                new AuthorizationSubject(
                        subject.userId(),
                        subject.deptId(),
                        subject.authzVersion()),
                grants);
    }

    private static void validatePayloadTypes(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            throw invalid("InvalidPayloadType");
        }
        JsonNode subject = payload.get("subject");
        if (subject == null || !subject.isObject()) {
            throw invalid("InvalidSubjectType");
        }
        requireText(subject, "userId");
        JsonNode deptId = subject.get("deptId");
        if (deptId != null && !deptId.isNull() && !deptId.isTextual()) {
            throw invalid("InvalidDeptIdType");
        }
        JsonNode authzVersion = subject.get("authzVersion");
        if (authzVersion == null || !authzVersion.isIntegralNumber()) {
            throw invalid("InvalidAuthzVersionType");
        }

        JsonNode grants = payload.get("grants");
        if (grants == null || !grants.isArray()) {
            throw invalid("InvalidGrantsType");
        }
        for (JsonNode grant : grants) {
            if (grant == null || !grant.isObject()) {
                throw invalid("InvalidGrantType");
            }
            requireText(grant, "roleId");
            requireText(grant, "permissionCode");
            requireText(grant, "domainCode");
            JsonNode dataScopeRequired = grant.get("dataScopeRequired");
            if (dataScopeRequired == null || !dataScopeRequired.isBoolean()) {
                throw invalid("InvalidDataScopeRequiredType");
            }
            requireText(grant, "scope");
        }
    }

    private static void requireText(JsonNode parent, String fieldName) {
        JsonNode value = parent.get(fieldName);
        if (value == null || !value.isTextual()) {
            throw invalid("Invalid" + fieldName + "Type");
        }
    }

    private static InvalidCachePayloadException invalid(String reason) {
        return new InvalidCachePayloadException(reason);
    }

    private record SnapshotPayload(
            SubjectPayload subject,
            List<GrantPayload> grants) {
    }

    private record SubjectPayload(
            UUID userId,
            UUID deptId,
            Long authzVersion) {
    }

    private record GrantPayload(
            UUID roleId,
            String permissionCode,
            String domainCode,
            Boolean dataScopeRequired,
            AuthorizationScope scope) {
    }

    private static final class InvalidCachePayloadException extends RuntimeException {

        private final String reason;

        private InvalidCachePayloadException(String reason) {
            super(reason);
            this.reason = reason;
        }

        private String reason() {
            return reason;
        }
    }
}
