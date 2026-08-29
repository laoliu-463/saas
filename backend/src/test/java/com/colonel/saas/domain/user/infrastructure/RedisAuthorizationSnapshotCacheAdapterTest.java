package com.colonel.saas.domain.user.infrastructure;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.colonel.saas.config.JacksonConfig;
import com.colonel.saas.domain.user.api.AuthorizationScope;
import com.colonel.saas.domain.user.api.PermissionCode;
import com.colonel.saas.domain.user.domain.AuthorizationSnapshot;
import com.colonel.saas.domain.user.domain.AuthorizationSubject;
import com.colonel.saas.domain.user.domain.GrantedRolePermission;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisAuthorizationSnapshotCacheAdapterTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private ObjectMapper objectMapper;
    private RedisAuthorizationSnapshotCacheAdapter adapter;

    @BeforeEach
    void setUp() {
        objectMapper = new JacksonConfig().objectMapper(new Jackson2ObjectMapperBuilder());
        adapter = new RedisAuthorizationSnapshotCacheAdapter(redisTemplate, objectMapper);
    }

    @Test
    void putThenGetRoundTripsThroughStrictPayloadWithoutSensitiveFields() throws Exception {
        useValueOperations();
        UUID userId = UUID.randomUUID();
        AuthorizationSnapshot snapshot = snapshot(userId, 3L, "sample:read");
        Duration ttl = Duration.ofMinutes(5);

        adapter.put(snapshot, ttl);

        ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
        verify(valueOperations).set(
                eq("authz:snapshot:" + userId + ":3"), valueCaptor.capture(), eq(ttl));
        assertThat(valueCaptor.getValue()).isInstanceOf(String.class);
        String json = (String) valueCaptor.getValue();
        assertThat(objectMapper.readTree(json).at("/grants/0/permissionCode").asText())
                .isEqualTo("sample:read");
        assertThat(objectMapper.readTree(json).at("/grants/0/dataScopeRequired").isBoolean())
                .isTrue();
        assertThat(json.toLowerCase(Locale.ROOT))
                .doesNotContain("\"token\"", "\"password\"", "\"secret\"", "\"permissions\"");

        when(valueOperations.get("authz:snapshot:" + userId + ":3")).thenReturn(json);

        assertThat(adapter.get(userId, 3L)).contains(snapshot);
    }

    @Test
    void getKeepsDifferentAuthorizationVersionsIsolated() throws Exception {
        useValueOperations();
        UUID userId = UUID.randomUUID();
        when(valueOperations.get("authz:snapshot:" + userId + ":3"))
                .thenReturn(objectMapper.writeValueAsString(
                        validPayload(userId, 3L, "sample:read")));
        when(valueOperations.get("authz:snapshot:" + userId + ":4"))
                .thenReturn(objectMapper.writeValueAsString(
                        validPayload(userId, 4L, "sample:write")));

        assertThat(adapter.get(userId, 3L))
                .map(AuthorizationSnapshot::subject)
                .map(AuthorizationSubject::authzVersion)
                .contains(3L);
        assertThat(adapter.get(userId, 4L))
                .map(AuthorizationSnapshot::subject)
                .map(AuthorizationSubject::authzVersion)
                .contains(4L);

        verify(valueOperations).get("authz:snapshot:" + userId + ":3");
        verify(valueOperations).get("authz:snapshot:" + userId + ":4");
    }

    @Test
    void missingKeyReturnsCacheMiss() {
        useValueOperations();
        UUID userId = UUID.randomUUID();
        when(valueOperations.get("authz:snapshot:" + userId + ":3")).thenReturn(null);

        assertThat(adapter.get(userId, 3L)).isEmpty();
    }

    @Test
    void payloadForDifferentVersionIsDeletedAndTreatedAsMiss() throws Exception {
        useValueOperations();
        UUID userId = UUID.randomUUID();
        String key = "authz:snapshot:" + userId + ":3";
        when(valueOperations.get(key))
                .thenReturn(objectMapper.writeValueAsString(
                        validPayload(userId, 4L, "sample:read")));

        assertThat(adapter.get(userId, 3L)).isEmpty();

        verify(redisTemplate).delete(key);
    }

    @Test
    void payloadForDifferentUserIsDeletedAndTreatedAsMiss() throws Exception {
        useValueOperations();
        UUID requestedUserId = UUID.randomUUID();
        String key = "authz:snapshot:" + requestedUserId + ":3";
        when(valueOperations.get(key))
                .thenReturn(objectMapper.writeValueAsString(
                        validPayload(UUID.randomUUID(), 3L, "sample:read")));

        assertThat(adapter.get(requestedUserId, 3L)).isEmpty();

        verify(redisTemplate).delete(key);
    }

    @Test
    void missingSubjectIsDeletedAndTreatedAsMiss() throws Exception {
        UUID userId = UUID.randomUUID();
        ObjectNode payload = validPayload(userId, 3L, "sample:read");
        payload.remove("subject");

        assertInvalidPayloadIsDeletedAndMissed(userId, 3L, payload);
    }

    @ParameterizedTest
    @ValueSource(strings = {"userId", "authzVersion"})
    void missingRequiredSubjectFieldIsDeletedAndTreatedAsMiss(String field) throws Exception {
        UUID userId = UUID.randomUUID();
        ObjectNode payload = validPayload(userId, 3L, "sample:read");
        ((ObjectNode) payload.get("subject")).remove(field);

        assertInvalidPayloadIsDeletedAndMissed(userId, 3L, payload);
    }

    @Test
    void nullSubjectVersionIsDeletedAndTreatedAsMiss() throws Exception {
        UUID userId = UUID.randomUUID();
        ObjectNode payload = validPayload(userId, 3L, "sample:read");
        ((ObjectNode) payload.get("subject")).putNull("authzVersion");

        assertInvalidPayloadIsDeletedAndMissed(userId, 3L, payload);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "dataScopeRequired",
            "roleId",
            "permissionCode",
            "domainCode",
            "scope"
    })
    void missingRequiredGrantFieldIsDeletedAndTreatedAsMiss(String field) throws Exception {
        UUID userId = UUID.randomUUID();
        ObjectNode payload = validPayload(userId, 3L, "sample:read");
        grant(payload).remove(field);

        assertInvalidPayloadIsDeletedAndMissed(userId, 3L, payload);
    }

    @Test
    void nullDataScopeRequiredIsDeletedAndTreatedAsMissInsteadOfDefaultingFalse()
            throws Exception {
        UUID userId = UUID.randomUUID();
        ObjectNode payload = validPayload(userId, 3L, "sample:read");
        grant(payload).putNull("dataScopeRequired");

        assertInvalidPayloadIsDeletedAndMissed(userId, 3L, payload);
    }

    @Test
    void missingGrantsIsDeletedAndTreatedAsMiss() throws Exception {
        UUID userId = UUID.randomUUID();
        ObjectNode payload = validPayload(userId, 3L, "sample:read");
        payload.remove("grants");

        assertInvalidPayloadIsDeletedAndMissed(userId, 3L, payload);
    }

    @Test
    void nullGrantIsDeletedAndTreatedAsMiss() throws Exception {
        UUID userId = UUID.randomUUID();
        ObjectNode payload = validPayload(userId, 3L, "sample:read");
        payload.withArray("grants").removeAll().addNull();

        assertInvalidPayloadIsDeletedAndMissed(userId, 3L, payload);
    }

    @Test
    void coercibleWrongPayloadTypeIsDeletedAndTreatedAsMiss() throws Exception {
        UUID userId = UUID.randomUUID();
        ObjectNode payload = validPayload(userId, 3L, "sample:read");
        grant(payload).put("dataScopeRequired", "false");

        assertInvalidPayloadIsDeletedAndMissed(userId, 3L, payload);
    }

    @ParameterizedTest
    @ValueSource(strings = {"permissionCode", "domainCode"})
    void invalidGrantValueIsDeletedAndTreatedAsMiss(String field) throws Exception {
        UUID userId = UUID.randomUUID();
        ObjectNode payload = validPayload(userId, 3L, "sample:read");
        grant(payload).put(field, field.equals("permissionCode") ? "malformed" : "   ");

        assertInvalidPayloadIsDeletedAndMissed(userId, 3L, payload);
    }

    @Test
    void invalidScopeIsDeletedAndTreatedAsMiss() throws Exception {
        UUID userId = UUID.randomUUID();
        ObjectNode payload = validPayload(userId, 3L, "sample:read");
        grant(payload).put("scope", "UNKNOWN_SCOPE");

        assertInvalidPayloadIsDeletedAndMissed(userId, 3L, payload);
    }

    @Test
    void corruptedJsonIsSafelyLoggedDeletedAndTreatedAsMiss() {
        useValueOperations();
        UUID userId = UUID.randomUUID();
        String key = "authz:snapshot:" + userId + ":3";
        String corrupted =
                "{\"token\":\"forbidden-token\",\"permissions\":\"sample:read\",\"secret\":";
        when(valueOperations.get(key)).thenReturn(corrupted);

        Logger logger = (Logger) LoggerFactory.getLogger(RedisAuthorizationSnapshotCacheAdapter.class);
        Level originalLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(Level.WARN);
        logger.addAppender(appender);
        try {
            assertThat(adapter.get(userId, 3L)).isEmpty();

            verify(redisTemplate).delete(key);
            String logText = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .collect(Collectors.joining("\n"));
            assertThat(logText)
                    .contains(key)
                    .doesNotContain(
                            corrupted,
                            "forbidden-token",
                            "sample:read",
                            "token",
                            "permissions",
                            "secret");
            assertThat(appender.list)
                    .allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
        }
    }

    @Test
    void jsonNullIsDeletedAndTreatedAsMiss() {
        useValueOperations();
        UUID userId = UUID.randomUUID();
        String key = "authz:snapshot:" + userId + ":3";
        when(valueOperations.get(key)).thenReturn("null");

        assertThat(adapter.get(userId, 3L)).isEmpty();

        verify(redisTemplate).delete(key);
    }

    @Test
    void nonStringValueIsDeletedAndTreatedAsMiss() {
        useValueOperations();
        UUID userId = UUID.randomUUID();
        String key = "authz:snapshot:" + userId + ":3";
        when(valueOperations.get(key)).thenReturn(42L);

        assertThat(adapter.get(userId, 3L)).isEmpty();

        verify(redisTemplate).delete(key);
    }

    @Test
    void redisConnectionFailurePropagatesWithoutBeingConvertedToMiss() {
        useValueOperations();
        UUID userId = UUID.randomUUID();
        String key = "authz:snapshot:" + userId + ":3";
        RedisConnectionFailureException failure =
                new RedisConnectionFailureException("redis unavailable", null);
        when(valueOperations.get(key)).thenThrow(failure);

        assertThatThrownBy(() -> adapter.get(userId, 3L)).isSameAs(failure);

        verify(redisTemplate, never()).delete(key);
    }

    @Test
    void evictDeletesOnlyTheExactVersionedKey() {
        UUID userId = UUID.randomUUID();

        adapter.evict(userId, 7L);

        verify(redisTemplate).delete("authz:snapshot:" + userId + ":7");
        verify(redisTemplate, never()).delete("authz:snapshot:" + userId + ":6");
        verify(redisTemplate, never()).delete("authz:snapshot:" + userId + ":8");
    }

    @Test
    void getRejectsInvalidKeyInputBeforeRedisInteraction() {
        assertThatThrownBy(() -> adapter.get(null, 1L))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("userId");
        assertThatThrownBy(() -> adapter.get(UUID.randomUUID(), 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("authzVersion");
        assertThatThrownBy(() -> adapter.get(UUID.randomUUID(), -1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("authzVersion");

        verifyNoInteractions(redisTemplate, valueOperations);
    }

    @Test
    void evictRejectsInvalidKeyInputBeforeRedisInteraction() {
        assertThatThrownBy(() -> adapter.evict(null, 1L))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("userId");
        assertThatThrownBy(() -> adapter.evict(UUID.randomUUID(), 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("authzVersion");

        verifyNoInteractions(redisTemplate, valueOperations);
    }

    @Test
    void putRejectsInvalidSnapshotOrTtlBeforeRedisInteraction() {
        AuthorizationSnapshot snapshot = snapshot(UUID.randomUUID(), 3L, "sample:read");

        assertThatThrownBy(() -> adapter.put(null, Duration.ofMinutes(1)))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("snapshot");
        assertThatThrownBy(() -> adapter.put(snapshot, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ttl");
        assertThatThrownBy(() -> adapter.put(snapshot, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ttl");
        assertThatThrownBy(() -> adapter.put(snapshot, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ttl");

        verifyNoInteractions(redisTemplate, valueOperations);
    }

    @Test
    void serializationFailureUsesDedicatedRedisSerializationException() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        JsonProcessingException failure = new JsonProcessingException("cannot serialize") { };
        when(failingMapper.writeValueAsString(any())).thenThrow(failure);
        RedisAuthorizationSnapshotCacheAdapter failingAdapter =
                new RedisAuthorizationSnapshotCacheAdapter(redisTemplate, failingMapper);

        assertThatThrownBy(() -> failingAdapter.put(
                snapshot(UUID.randomUUID(), 3L, "sample:read"),
                Duration.ofMinutes(1)))
                .isInstanceOf(SerializationException.class)
                .hasCause(failure);

        verifyNoInteractions(redisTemplate, valueOperations);
    }

    private void useValueOperations() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private void assertInvalidPayloadIsDeletedAndMissed(
            UUID userId,
            long authzVersion,
            ObjectNode payload) throws Exception {
        useValueOperations();
        String key = "authz:snapshot:" + userId + ":" + authzVersion;
        when(valueOperations.get(key)).thenReturn(objectMapper.writeValueAsString(payload));

        assertThat(adapter.get(userId, authzVersion)).isEmpty();

        verify(redisTemplate).delete(key);
    }

    private ObjectNode validPayload(
            UUID userId,
            long authzVersion,
            String permissionCode) {
        ObjectNode payload = objectMapper.createObjectNode();
        ObjectNode subject = payload.putObject("subject");
        subject.put("userId", userId.toString());
        subject.put("deptId", UUID.randomUUID().toString());
        subject.put("authzVersion", authzVersion);
        ObjectNode grant = payload.putArray("grants").addObject();
        grant.put("roleId", UUID.randomUUID().toString());
        grant.put("permissionCode", permissionCode);
        grant.put("domainCode", "sample");
        grant.put("dataScopeRequired", true);
        grant.put("scope", AuthorizationScope.SELF.name());
        return payload;
    }

    private static ObjectNode grant(ObjectNode payload) {
        return (ObjectNode) payload.withArray("grants").get(0);
    }

    private static AuthorizationSnapshot snapshot(
            UUID userId,
            long authzVersion,
            String permissionCode) {
        return new AuthorizationSnapshot(
                new AuthorizationSubject(userId, UUID.randomUUID(), authzVersion),
                List.of(new GrantedRolePermission(
                        UUID.randomUUID(),
                        new PermissionCode(permissionCode),
                        "sample",
                        true,
                        AuthorizationScope.SELF)));
    }
}
