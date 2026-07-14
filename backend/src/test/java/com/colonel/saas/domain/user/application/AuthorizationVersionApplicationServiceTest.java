package com.colonel.saas.domain.user.application;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.colonel.saas.domain.user.event.AuthorizationVersionChangedEvent;
import com.colonel.saas.domain.user.infrastructure.AuthorizationVersionCacheEvictListener;
import com.colonel.saas.domain.user.infrastructure.SysAuthorizationVersionStoreAdapter;
import com.colonel.saas.domain.user.port.AuthorizationSnapshotCache;
import com.colonel.saas.domain.user.port.AuthorizationVersionStore;
import com.colonel.saas.testsupport.BaseIntegrationTest;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class AuthorizationVersionApplicationServiceTest extends BaseIntegrationTest {

    private static final String CAUSE = "USER_ROLES_REPLACED";

    @Autowired
    private AuthorizationVersionApplicationService applicationService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SysAuthorizationVersionStoreAdapter versionStore;

    @MockBean
    private AuthorizationSnapshotCache cache;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUpTransactionTemplate() {
        reset(cache);
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    void callsWithoutActualTransaction_failBeforeStoreOrPublisherInteraction() {
        AuthorizationVersionStore store = mock(AuthorizationVersionStore.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        AuthorizationVersionApplicationService service =
                new AuthorizationVersionApplicationService(store, publisher);

        assertThatThrownBy(() -> service.incrementUser(UUID.randomUUID(), CAUSE, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active transaction");
        assertThatThrownBy(() -> service.incrementUsersByRole(UUID.randomUUID(), CAUSE, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active transaction");

        verifyNoInteractions(store, publisher);
    }

    @Test
    void emptyChanges_doNotPublishEvent() {
        UUID userId = UUID.randomUUID();
        AuthorizationVersionStore store = mock(AuthorizationVersionStore.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        when(store.incrementUser(userId)).thenReturn(List.of());
        AuthorizationVersionApplicationService service =
                new AuthorizationVersionApplicationService(store, publisher);

        transactionTemplate.executeWithoutResult(
                ignored -> service.incrementUser(userId, CAUSE, null));

        verify(store).incrementUser(userId);
        verifyNoInteractions(publisher);
    }

    @Test
    void activeTransaction_publishesImmutableEventAndAllowsNullActor() {
        UUID roleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AuthorizationVersionStore.VersionChange change =
                new AuthorizationVersionStore.VersionChange(userId, 3L, 4L);
        AuthorizationVersionStore store = mock(AuthorizationVersionStore.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        when(store.incrementUsersByRole(roleId)).thenReturn(List.of(change));
        AuthorizationVersionApplicationService service =
                new AuthorizationVersionApplicationService(store, publisher);

        transactionTemplate.executeWithoutResult(
                ignored -> service.incrementUsersByRole(roleId, CAUSE, null));

        org.mockito.ArgumentCaptor<AuthorizationVersionChangedEvent> eventCaptor =
                org.mockito.ArgumentCaptor.forClass(AuthorizationVersionChangedEvent.class);
        verify(publisher).publishEvent(eventCaptor.capture());
        AuthorizationVersionChangedEvent event = eventCaptor.getValue();
        assertThat(event.changes()).containsExactly(change);
        assertThat(event.cause()).isEqualTo(CAUSE);
        assertThat(event.actorUserId()).isNull();
        assertThatThrownBy(() -> event.changes().add(change))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void eventDefensivelyCopiesChangesAndRejectsInvalidCauses() {
        UUID userId = UUID.randomUUID();
        AuthorizationVersionStore.VersionChange change =
                new AuthorizationVersionStore.VersionChange(userId, 1L, 2L);
        List<AuthorizationVersionStore.VersionChange> mutableChanges =
                new ArrayList<>(List.of(change));

        AuthorizationVersionChangedEvent event =
                new AuthorizationVersionChangedEvent(mutableChanges, CAUSE, null);
        mutableChanges.clear();

        assertThat(event.changes()).containsExactly(change);
        assertThatThrownBy(() -> new AuthorizationVersionChangedEvent(List.of(change), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthorizationVersionChangedEvent(List.of(change), " ", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthorizationVersionChangedEvent(
                List.of(change), "USER\nROLES_REPLACED", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthorizationVersionChangedEvent(
                List.of(change), "USER\u0000ROLES_REPLACED", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidCause_failsBeforeStoreOrPublisherInteraction() {
        AuthorizationVersionStore store = mock(AuthorizationVersionStore.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        AuthorizationVersionApplicationService service =
                new AuthorizationVersionApplicationService(store, publisher);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(
                ignored -> service.incrementUser(UUID.randomUUID(), "BAD\rCAUSE", null)))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(store, publisher);
    }

    @Test
    void commitEvictsOnlyPreviousKeyAndRollbackRestoresVersionWithoutAdditionalEviction() {
        UUID userId = insertUser(4L);
        UUID actorUserId = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(
                ignored -> applicationService.incrementUser(userId, CAUSE, actorUserId));

        assertThat(selectVersion(userId)).isEqualTo(5L);
        verify(cache).evict(userId, 4L);
        verify(cache, never()).evict(userId, 5L);

        transactionTemplate.executeWithoutResult(status -> {
            applicationService.incrementUser(userId, CAUSE, actorUserId);
            assertThat(selectVersion(userId)).isEqualTo(6L);
            status.setRollbackOnly();
        });

        assertThat(selectVersion(userId)).isEqualTo(5L);
        verifyNoMoreInteractions(cache);
    }

    @Test
    void publisherFailure_propagatesAndRollsBackVersionWithoutCacheEviction() {
        UUID userId = insertUser(9L);
        RuntimeException publishFailure = new IllegalStateException("publisher unavailable");
        ApplicationEventPublisher throwingPublisher = mock(ApplicationEventPublisher.class);
        doThrow(publishFailure).when(throwingPublisher).publishEvent(any(Object.class));
        AuthorizationVersionApplicationService service =
                new AuthorizationVersionApplicationService(versionStore, throwingPublisher);

        Throwable thrown = catchThrowable(() -> transactionTemplate.executeWithoutResult(
                ignored -> service.incrementUser(userId, CAUSE, UUID.randomUUID())));

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(thrown).isSameAs(publishFailure);
            softly.assertThat(selectVersion(userId)).isEqualTo(9L);
        });
        verify(throwingPublisher).publishEvent(any(AuthorizationVersionChangedEvent.class));
        verifyNoInteractions(cache);
    }

    @Test
    void cacheFailureDoesNotRollbackCommittedVersionAndLogsOnlySafeMetadata() {
        UUID userId = insertUser(7L);
        UUID actorUserId = UUID.randomUUID();
        String sensitiveMessage =
                "token=forbidden payload=forbidden permissions=[sample:read]";
        doThrow(new IllegalStateException(sensitiveMessage))
                .when(cache).evict(userId, 7L);

        Logger logger = (Logger) LoggerFactory.getLogger(
                AuthorizationVersionCacheEvictListener.class);
        Level originalLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(Level.WARN);
        logger.addAppender(appender);
        try {
            transactionTemplate.executeWithoutResult(
                    ignored -> applicationService.incrementUser(
                            userId, CAUSE, actorUserId));

            assertThat(selectVersion(userId)).isEqualTo(8L);
            String logText = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .collect(Collectors.joining("\n"));
            assertThat(logText)
                    .contains(userId.toString(), "previousVersion=7", "IllegalStateException")
                    .doesNotContain(
                            sensitiveMessage,
                            CAUSE,
                            actorUserId.toString(),
                            "token=",
                            "payload=",
                            "permissions=",
                            "sample:read");
            assertThat(appender.list)
                    .allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
        }
    }

    private UUID insertUser(long authzVersion) {
        UUID userId = UUID.randomUUID();
        String suffix = userId.toString().replace("-", "").substring(20);
        jdbcTemplate.update("""
                        INSERT INTO sys_user (
                            id, username, password, real_name, channel_code,
                            status, deleted, authz_version
                        ) VALUES (?, ?, ?, ?, ?, 1, 0, ?)
                        """,
                userId,
                "version_app_" + suffix,
                "test-password-hash",
                "Authorization Version Application Test User",
                "a" + suffix,
                authzVersion);
        return userId;
    }

    private long selectVersion(UUID userId) {
        return jdbcTemplate.queryForObject(
                "SELECT authz_version FROM sys_user WHERE id = ?",
                Long.class,
                userId);
    }
}
