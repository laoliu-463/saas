package com.colonel.saas.domain.user.application;

import com.colonel.saas.domain.user.event.AuthorizationVersionChangedEvent;
import com.colonel.saas.domain.user.port.AuthorizationVersionStore;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class AuthorizationVersionApplicationService {

    private final AuthorizationVersionStore store;
    private final ApplicationEventPublisher publisher;

    public AuthorizationVersionApplicationService(
            AuthorizationVersionStore store,
            ApplicationEventPublisher publisher) {
        this.store = Objects.requireNonNull(store, "store");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
    }

    public void incrementUser(UUID userId, String cause, UUID actorUserId) {
        requireTransaction();
        AuthorizationVersionChangedEvent.validateCause(cause);
        publish(store.incrementUser(userId), cause, actorUserId);
    }

    public void incrementUsersByRole(UUID roleId, String cause, UUID actorUserId) {
        requireTransaction();
        AuthorizationVersionChangedEvent.validateCause(cause);
        publish(store.incrementUsersByRole(roleId), cause, actorUserId);
    }

    private void publish(
            List<AuthorizationVersionStore.VersionChange> changes,
            String cause,
            UUID actorUserId) {
        if (changes == null) {
            throw new IllegalStateException(
                    "authorization version store returned null changes");
        }
        if (!changes.isEmpty()) {
            publisher.publishEvent(new AuthorizationVersionChangedEvent(
                    changes,
                    cause,
                    actorUserId));
        }
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "authorization version change requires an active transaction");
        }
    }
}
