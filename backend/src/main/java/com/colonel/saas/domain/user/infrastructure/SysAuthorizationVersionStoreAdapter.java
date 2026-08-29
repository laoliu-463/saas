package com.colonel.saas.domain.user.infrastructure;

import com.colonel.saas.domain.user.port.AuthorizationVersionStore;
import com.colonel.saas.mapper.AuthorizationVersionMapper;
import com.colonel.saas.mapper.projection.AuthorizationVersionChangeRow;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class SysAuthorizationVersionStoreAdapter implements AuthorizationVersionStore {

    private final AuthorizationVersionMapper mapper;

    public SysAuthorizationVersionStoreAdapter(AuthorizationVersionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<VersionChange> incrementUser(UUID userId) {
        if (userId == null) {
            return List.of();
        }
        List<AuthorizationVersionChangeRow> rows =
                requireRows(mapper.incrementUser(userId));
        if (rows.isEmpty()) {
            return List.of();
        }
        if (rows.size() != 1) {
            throw new IllegalStateException(
                    "authorization user version update returned multiple rows");
        }
        VersionChange change = toChange(rows.get(0));
        if (!userId.equals(change.userId())) {
            throw new IllegalStateException(
                    "authorization version row user does not match request");
        }
        return List.of(change);
    }

    @Override
    public List<VersionChange> incrementUsersByRole(UUID roleId) {
        if (roleId == null) {
            return List.of();
        }
        List<AuthorizationVersionChangeRow> rows =
                requireRows(mapper.incrementUsersByRole(roleId));
        if (rows.isEmpty()) {
            return List.of();
        }

        List<VersionChange> changes = new ArrayList<>(rows.size());
        Set<UUID> seenUserIds = new HashSet<>(rows.size());
        String previousUserId = null;
        for (AuthorizationVersionChangeRow row : rows) {
            VersionChange change = toChange(row);
            String currentUserId = change.userId().toString();
            if (!seenUserIds.add(change.userId())) {
                throw new IllegalStateException(
                        "authorization role version update returned duplicate users");
            }
            if (previousUserId != null && previousUserId.compareTo(currentUserId) >= 0) {
                throw new IllegalStateException(
                        "authorization role version rows are not sorted");
            }
            changes.add(change);
            previousUserId = currentUserId;
        }
        return List.copyOf(changes);
    }

    private static List<AuthorizationVersionChangeRow> requireRows(
            List<AuthorizationVersionChangeRow> rows) {
        if (rows == null) {
            throw new IllegalStateException(
                    "authorization version mapper returned null rows");
        }
        return rows;
    }

    private static VersionChange toChange(AuthorizationVersionChangeRow row) {
        if (row == null) {
            throw new IllegalStateException(
                    "authorization version row must not be null");
        }
        if (row.getUserId() == null) {
            throw new IllegalStateException(
                    "authorization version row userId must not be null");
        }
        if (row.getPreviousVersion() == null || row.getCurrentVersion() == null) {
            throw new IllegalStateException(
                    "authorization version row versions must not be null");
        }
        try {
            return new VersionChange(
                    row.getUserId(),
                    row.getPreviousVersion(),
                    row.getCurrentVersion());
        } catch (IllegalArgumentException invalidRow) {
            throw new IllegalStateException(
                    "authorization version row is invalid",
                    invalidRow);
        }
    }
}
