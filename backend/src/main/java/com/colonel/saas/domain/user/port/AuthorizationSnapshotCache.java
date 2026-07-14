package com.colonel.saas.domain.user.port;

import com.colonel.saas.domain.user.domain.AuthorizationSnapshot;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface AuthorizationSnapshotCache {

    Optional<AuthorizationSnapshot> get(UUID userId, long authzVersion);

    void put(AuthorizationSnapshot snapshot, Duration ttl);

    void evict(UUID userId, long authzVersion);
}
