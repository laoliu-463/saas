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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    void putUsesExactVersionedKeyJsonStringAndTtlWithoutSensitiveFields() throws Exception {
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
        assertThat(objectMapper.readValue(json, AuthorizationSnapshot.class)).isEqualTo(snapshot);
        assertThat(json.toLowerCase(Locale.ROOT))
                .doesNotContain("\"token\"", "\"password\"", "\"secret\"", "\"permissions\"");
    }

    @Test
    void getKeepsDifferentAuthorizationVersionsIsolated() throws Exception {
        useValueOperations();
        UUID userId = UUID.randomUUID();
        AuthorizationSnapshot versionThree = snapshot(userId, 3L, "sample:read");
        AuthorizationSnapshot versionFour = snapshot(userId, 4L, "sample:write");
        when(valueOperations.get("authz:snapshot:" + userId + ":3"))
                .thenReturn(objectMapper.writeValueAsString(versionThree));
        when(valueOperations.get("authz:snapshot:" + userId + ":4"))
                .thenReturn(objectMapper.writeValueAsString(versionFour));

        assertThat(adapter.get(userId, 3L)).contains(versionThree);
        assertThat(adapter.get(userId, 4L)).contains(versionFour);

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
                .thenReturn(objectMapper.writeValueAsString(snapshot(userId, 4L, "sample:read")));

        assertThat(adapter.get(userId, 3L)).isEmpty();

        verify(redisTemplate).delete(key);
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

    private void useValueOperations() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
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
