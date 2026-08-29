package com.colonel.saas.domain.user.event;

import com.colonel.saas.domain.user.port.AuthorizationVersionStore;

import java.util.List;
import java.util.UUID;

public record AuthorizationVersionChangedEvent(
        List<AuthorizationVersionStore.VersionChange> changes,
        String cause,
        UUID actorUserId) {

    public AuthorizationVersionChangedEvent {
        changes = changes == null ? List.of() : List.copyOf(changes);
        validateCause(cause);
    }

    public static void validateCause(String cause) {
        if (cause == null || cause.isBlank()) {
            throw new IllegalArgumentException(
                    "authorization version change cause must not be blank");
        }
        if (cause.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(
                    "authorization version change cause must not contain control characters");
        }
    }
}
