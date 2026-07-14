package com.colonel.saas.domain.user.infrastructure;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.colonel.saas.config.AuthorizationRuntimeProperties;
import com.colonel.saas.domain.user.api.AuthorizationUnavailableException;
import com.colonel.saas.domain.user.domain.AuthorizationSnapshot;
import com.colonel.saas.domain.user.domain.AuthorizationSubject;
import com.colonel.saas.domain.user.port.AuthorizationSnapshotCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.serializer.SerializationException;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VersionedAuthorizationSnapshotStoreTest {

    private static final Duration CACHE_TTL = Duration.ofMinutes(7);

    @Mock
    private SysAuthorizationSnapshotStoreAdapter databaseStore;

    @Mock
    private AuthorizationSnapshotCache cache;

    private VersionedAuthorizationSnapshotStore store;

    @BeforeEach
    void setUp() {
        AuthorizationRuntimeProperties properties = new AuthorizationRuntimeProperties();
        properties.setSnapshotCacheTtl(CACHE_TTL);
        store = new VersionedAuthorizationSnapshotStore(databaseStore, cache, properties);
    }

    @Test
    void cacheHitDoesNotQueryDatabase() {
        UUID userId = UUID.randomUUID();
        AuthorizationSnapshot snapshot = snapshot(userId, 3L);
        when(cache.get(userId, 3L)).thenReturn(Optional.of(snapshot));

        assertThat(store.loadActiveSnapshot(userId, 3L)).contains(snapshot);

        verifyNoInteractions(databaseStore);
        verify(cache, never()).put(snapshot, CACHE_TTL);
    }

    @Test
    void invalidInputFailsClosedWithoutQueryingCacheOrDatabase() {
        assertThat(store.loadActiveSnapshot(null, 3L)).isEmpty();
        assertThat(store.loadActiveSnapshot(UUID.randomUUID(), 0L)).isEmpty();
        assertThat(store.loadActiveSnapshot(UUID.randomUUID(), -1L)).isEmpty();

        verifyNoInteractions(cache, databaseStore);
    }

    @Test
    void cacheMissLoadsDatabaseAndWritesVersionedEntryWithConfiguredTtl() {
        UUID userId = UUID.randomUUID();
        AuthorizationSnapshot snapshot = snapshot(userId, 3L);
        when(cache.get(userId, 3L)).thenReturn(Optional.empty());
        when(databaseStore.loadActiveSnapshot(userId, 3L)).thenReturn(Optional.of(snapshot));

        assertThat(store.loadActiveSnapshot(userId, 3L)).contains(snapshot);

        verify(cache).put(snapshot, CACHE_TTL);
    }

    @Test
    void cacheMissDoesNotWriteWhenDatabaseHasNoActiveSnapshot() {
        UUID userId = UUID.randomUUID();
        when(cache.get(userId, 3L)).thenReturn(Optional.empty());
        when(databaseStore.loadActiveSnapshot(userId, 3L)).thenReturn(Optional.empty());

        assertThat(store.loadActiveSnapshot(userId, 3L)).isEmpty();

        verify(cache, never()).put(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void redisReadFailureFallsBackToDatabase() {
        UUID userId = UUID.randomUUID();
        AuthorizationSnapshot snapshot = snapshot(userId, 3L);
        RedisConnectionFailureException failure =
                new RedisConnectionFailureException("redis unavailable", null);
        when(cache.get(userId, 3L)).thenThrow(failure);
        when(databaseStore.loadActiveSnapshot(userId, 3L)).thenReturn(Optional.of(snapshot));

        assertThat(store.loadActiveSnapshot(userId, 3L)).contains(snapshot);

        verify(cache).put(snapshot, CACHE_TTL);
    }

    @Test
    void redisSerializationReadFailureFallsBackToDatabase() {
        UUID userId = UUID.randomUUID();
        AuthorizationSnapshot snapshot = snapshot(userId, 3L);
        when(cache.get(userId, 3L)).thenThrow(new SerializationException("invalid cache json"));
        when(databaseStore.loadActiveSnapshot(userId, 3L)).thenReturn(Optional.of(snapshot));

        assertThat(store.loadActiveSnapshot(userId, 3L)).contains(snapshot);

        verify(cache).put(snapshot, CACHE_TTL);
    }

    @ParameterizedTest
    @MethodSource("programmingFailures")
    void cacheReadProgrammingFailurePropagatesWithoutDatabaseFallback(
            RuntimeException failure) {
        UUID userId = UUID.randomUUID();
        when(cache.get(userId, 3L)).thenThrow(failure);

        assertThatThrownBy(() -> store.loadActiveSnapshot(userId, 3L)).isSameAs(failure);

        verifyNoInteractions(databaseStore);
    }

    @Test
    void redisWriteFailureDoesNotHideSuccessfulDatabaseResult() {
        UUID userId = UUID.randomUUID();
        AuthorizationSnapshot snapshot = snapshot(userId, 3L);
        when(cache.get(userId, 3L)).thenReturn(Optional.empty());
        when(databaseStore.loadActiveSnapshot(userId, 3L)).thenReturn(Optional.of(snapshot));
        doThrow(new RedisConnectionFailureException("redis unavailable", null))
                .when(cache).put(snapshot, CACHE_TTL);

        assertThat(store.loadActiveSnapshot(userId, 3L)).contains(snapshot);
    }

    @Test
    void redisSerializationWriteFailureDoesNotHideSuccessfulDatabaseResult() {
        UUID userId = UUID.randomUUID();
        AuthorizationSnapshot snapshot = snapshot(userId, 3L);
        when(cache.get(userId, 3L)).thenReturn(Optional.empty());
        when(databaseStore.loadActiveSnapshot(userId, 3L)).thenReturn(Optional.of(snapshot));
        doThrow(new SerializationException("cannot serialize"))
                .when(cache).put(snapshot, CACHE_TTL);

        assertThat(store.loadActiveSnapshot(userId, 3L)).contains(snapshot);
    }

    @ParameterizedTest
    @MethodSource("programmingFailures")
    void cacheWriteProgrammingFailurePropagates(RuntimeException failure) {
        UUID userId = UUID.randomUUID();
        AuthorizationSnapshot snapshot = snapshot(userId, 3L);
        when(cache.get(userId, 3L)).thenReturn(Optional.empty());
        when(databaseStore.loadActiveSnapshot(userId, 3L)).thenReturn(Optional.of(snapshot));
        doThrow(failure).when(cache).put(snapshot, CACHE_TTL);

        assertThatThrownBy(() -> store.loadActiveSnapshot(userId, 3L)).isSameAs(failure);
    }

    @Test
    void cacheAndDatabaseFailureMapsDataAccessFailureToUnavailable() {
        UUID userId = UUID.randomUUID();
        DataAccessResourceFailureException databaseFailure =
                new DataAccessResourceFailureException("database unavailable");
        when(cache.get(userId, 3L))
                .thenThrow(new RedisConnectionFailureException("redis unavailable", null));
        when(databaseStore.loadActiveSnapshot(userId, 3L)).thenThrow(databaseFailure);

        assertThatThrownBy(() -> store.loadActiveSnapshot(userId, 3L))
                .isInstanceOf(AuthorizationUnavailableException.class)
                .hasMessage("授权事实暂时不可用")
                .hasCause(databaseFailure);
    }

    @Test
    void databaseDataAccessFailureMapsToUnavailableAfterNormalCacheMiss() {
        UUID userId = UUID.randomUUID();
        DataAccessResourceFailureException databaseFailure =
                new DataAccessResourceFailureException("database unavailable");
        when(cache.get(userId, 3L)).thenReturn(Optional.empty());
        when(databaseStore.loadActiveSnapshot(userId, 3L)).thenThrow(databaseFailure);

        assertThatThrownBy(() -> store.loadActiveSnapshot(userId, 3L))
                .isInstanceOf(AuthorizationUnavailableException.class)
                .hasCause(databaseFailure);
    }

    @Test
    void existingUnavailableFailurePropagatesUnchanged() {
        UUID userId = UUID.randomUUID();
        AuthorizationUnavailableException failure = new AuthorizationUnavailableException();
        when(cache.get(userId, 3L)).thenReturn(Optional.empty());
        when(databaseStore.loadActiveSnapshot(userId, 3L)).thenThrow(failure);

        assertThatThrownBy(() -> store.loadActiveSnapshot(userId, 3L)).isSameAs(failure);
    }

    @Test
    void databaseProgrammingFailurePropagatesUnchanged() {
        UUID userId = UUID.randomUUID();
        IllegalStateException failure = new IllegalStateException("invalid mapped row");
        when(cache.get(userId, 3L)).thenReturn(Optional.empty());
        when(databaseStore.loadActiveSnapshot(userId, 3L)).thenThrow(failure);

        assertThatThrownBy(() -> store.loadActiveSnapshot(userId, 3L)).isSameAs(failure);
    }

    @Test
    void cacheFailureLogsOnlyKeyAndExceptionMetadata() {
        UUID userId = UUID.randomUUID();
        String sensitiveMessage =
                "token=forbidden password=forbidden secret=forbidden permissions=[sample:read]";
        when(cache.get(userId, 3L))
                .thenThrow(new RedisConnectionFailureException(sensitiveMessage, null));
        when(databaseStore.loadActiveSnapshot(userId, 3L)).thenReturn(Optional.empty());

        Logger logger = (Logger) LoggerFactory.getLogger(VersionedAuthorizationSnapshotStore.class);
        Level originalLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(Level.WARN);
        logger.addAppender(appender);
        try {
            assertThat(store.loadActiveSnapshot(userId, 3L)).isEmpty();

            String logText = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .collect(Collectors.joining("\n"));
            assertThat(logText)
                    .contains("authz:snapshot:" + userId + ":3")
                    .contains("RedisConnectionFailureException")
                    .doesNotContain(
                            sensitiveMessage,
                            "token=",
                            "password=",
                            "secret=",
                            "permissions=",
                            "sample:read");
            assertThat(appender.list)
                    .allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
        }
    }

    private static AuthorizationSnapshot snapshot(UUID userId, long authzVersion) {
        return new AuthorizationSnapshot(
                new AuthorizationSubject(userId, UUID.randomUUID(), authzVersion),
                List.of());
    }

    private static Stream<RuntimeException> programmingFailures() {
        return Stream.of(
                new IllegalStateException("cache programming failure"),
                new NullPointerException("cache programming failure"));
    }
}
