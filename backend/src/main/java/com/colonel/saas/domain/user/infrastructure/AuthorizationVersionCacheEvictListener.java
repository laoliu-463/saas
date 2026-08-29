package com.colonel.saas.domain.user.infrastructure;

import com.colonel.saas.domain.user.event.AuthorizationVersionChangedEvent;
import com.colonel.saas.domain.user.port.AuthorizationSnapshotCache;
import com.colonel.saas.domain.user.port.AuthorizationVersionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AuthorizationVersionCacheEvictListener {

    private static final Logger log =
            LoggerFactory.getLogger(AuthorizationVersionCacheEvictListener.class);

    private final AuthorizationSnapshotCache cache;

    public AuthorizationVersionCacheEvictListener(AuthorizationSnapshotCache cache) {
        this.cache = cache;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVersionChanged(AuthorizationVersionChangedEvent event) {
        for (AuthorizationVersionStore.VersionChange change : event.changes()) {
            try {
                cache.evict(change.userId(), change.previousVersion());
            } catch (RuntimeException cacheFailure) {
                log.warn(
                        "Authorization snapshot cache eviction failed: "
                                + "userId={}, previousVersion={}, exceptionClass={}",
                        change.userId(),
                        change.previousVersion(),
                        cacheFailure.getClass().getSimpleName());
            }
        }
    }
}
