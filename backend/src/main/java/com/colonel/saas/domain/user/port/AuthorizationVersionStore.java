package com.colonel.saas.domain.user.port;

import java.util.List;
import java.util.UUID;

public interface AuthorizationVersionStore {

    List<VersionChange> incrementUser(UUID userId);

    List<VersionChange> incrementUsersByRole(UUID roleId);

    record VersionChange(UUID userId, long previousVersion, long currentVersion) {

        public VersionChange {
            if (userId == null) {
                throw new IllegalArgumentException("authorization version userId must not be null");
            }
            if (previousVersion < 1L) {
                throw new IllegalArgumentException(
                        "authorization previousVersion must be positive");
            }
            if (previousVersion == Long.MAX_VALUE
                    || currentVersion != previousVersion + 1L) {
                throw new IllegalArgumentException(
                        "authorization versions must be contiguous");
            }
        }
    }
}
