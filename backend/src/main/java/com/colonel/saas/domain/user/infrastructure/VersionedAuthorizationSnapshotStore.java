package com.colonel.saas.domain.user.infrastructure;

import com.colonel.saas.config.AuthorizationRuntimeProperties;
import com.colonel.saas.domain.user.api.AuthorizationUnavailableException;
import com.colonel.saas.domain.user.domain.AuthorizationSnapshot;
import com.colonel.saas.domain.user.port.AuthorizationSnapshotCache;
import com.colonel.saas.domain.user.port.AuthorizationSnapshotStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Primary
@Component
public class VersionedAuthorizationSnapshotStore implements AuthorizationSnapshotStore {

    private static final Logger log =
            LoggerFactory.getLogger(VersionedAuthorizationSnapshotStore.class);

    private final SysAuthorizationSnapshotStoreAdapter databaseStore;
    private final AuthorizationSnapshotCache cache;
    private final AuthorizationRuntimeProperties properties;

    public VersionedAuthorizationSnapshotStore(
            SysAuthorizationSnapshotStoreAdapter databaseStore,
            AuthorizationSnapshotCache cache,
            AuthorizationRuntimeProperties properties) {
        this.databaseStore = databaseStore;
        this.cache = cache;
        this.properties = properties;
    }

    @Override
    public Optional<AuthorizationSnapshot> loadActiveSnapshot(
            UUID userId,
            long authzVersion) {
        if (userId == null || authzVersion < 1) {
            return Optional.empty();
        }
        String cacheKey = RedisAuthorizationSnapshotCacheAdapter.key(userId, authzVersion);
        try {
            Optional<AuthorizationSnapshot> cached = cache.get(userId, authzVersion);
            if (cached.isPresent()) {
                return cached;
            }
        } catch (RuntimeException cacheFailure) {
            logCacheFailure("read", cacheKey, cacheFailure);
        }

        try {
            Optional<AuthorizationSnapshot> loaded =
                    databaseStore.loadActiveSnapshot(userId, authzVersion);
            loaded.ifPresent(snapshot -> writeCache(snapshot, cacheKey));
            return loaded;
        } catch (RuntimeException databaseFailure) {
            if (databaseFailure instanceof AuthorizationUnavailableException unavailable) {
                throw unavailable;
            }
            throw new AuthorizationUnavailableException(databaseFailure);
        }
    }

    private void writeCache(AuthorizationSnapshot snapshot, String cacheKey) {
        try {
            cache.put(snapshot, properties.getSnapshotCacheTtl());
        } catch (RuntimeException cacheFailure) {
            logCacheFailure("write", cacheKey, cacheFailure);
        }
    }

    private void logCacheFailure(
            String operation,
            String cacheKey,
            RuntimeException cacheFailure) {
        log.warn(
                "Authorization snapshot cache {} failed: key={}, exception={}",
                operation,
                cacheKey,
                cacheFailure.getClass().getSimpleName());
    }
}
