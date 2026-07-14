package com.colonel.saas.domain.user.port;

import com.colonel.saas.domain.user.domain.AuthorizationSnapshot;

import java.util.Optional;
import java.util.UUID;

public interface AuthorizationSnapshotStore {

    Optional<AuthorizationSnapshot> loadActiveSnapshot(UUID userId);
}
