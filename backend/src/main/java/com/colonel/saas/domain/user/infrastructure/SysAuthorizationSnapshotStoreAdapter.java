package com.colonel.saas.domain.user.infrastructure;

import com.colonel.saas.domain.user.api.AuthorizationScope;
import com.colonel.saas.domain.user.api.PermissionCode;
import com.colonel.saas.domain.user.domain.AuthorizationSnapshot;
import com.colonel.saas.domain.user.domain.AuthorizationSubject;
import com.colonel.saas.domain.user.domain.GrantedRolePermission;
import com.colonel.saas.domain.user.port.AuthorizationSnapshotStore;
import com.colonel.saas.mapper.AuthorizationSnapshotMapper;
import com.colonel.saas.mapper.projection.AuthorizationSnapshotRow;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public class SysAuthorizationSnapshotStoreAdapter implements AuthorizationSnapshotStore {

    private final AuthorizationSnapshotMapper mapper;

    public SysAuthorizationSnapshotStoreAdapter(AuthorizationSnapshotMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<AuthorizationSnapshot> loadActiveSnapshot(UUID userId) {
        List<AuthorizationSnapshotRow> rows = mapper.findActiveSnapshotRows(userId);
        if (rows == null || rows.isEmpty()) {
            return Optional.empty();
        }

        AuthorizationSnapshotRow first = rows.get(0);
        validateRows(userId, rows, first);
        AuthorizationSubject subject = new AuthorizationSubject(
                first.getUserId(), first.getDeptId(), first.getAuthzVersion().longValue());
        List<GrantedRolePermission> grants = rows.stream()
                .filter(row -> row.getPermissionCode() != null)
                .map(this::toGrant)
                .toList();
        return Optional.of(new AuthorizationSnapshot(subject, grants));
    }

    private void validateRows(
            UUID requestedUserId,
            List<AuthorizationSnapshotRow> rows,
            AuthorizationSnapshotRow first) {
        if (first == null) {
            throw new IllegalStateException("authorization snapshot first row must not be null");
        }
        if (first.getUserId() == null || !Objects.equals(requestedUserId, first.getUserId())) {
            throw new IllegalStateException("authorization snapshot user does not match request");
        }
        if (first.getAuthzVersion() == null) {
            throw new IllegalStateException("authorization snapshot version must not be null");
        }
        for (AuthorizationSnapshotRow row : rows) {
            if (row == null) {
                throw new IllegalStateException("authorization snapshot row must not be null");
            }
            if (!Objects.equals(first.getUserId(), row.getUserId())
                    || !Objects.equals(first.getDeptId(), row.getDeptId())
                    || !Objects.equals(first.getAuthzVersion(), row.getAuthzVersion())) {
                throw new IllegalStateException("authorization snapshot rows have inconsistent subjects");
            }
        }
    }

    private GrantedRolePermission toGrant(AuthorizationSnapshotRow row) {
        if (row.getRoleId() == null) {
            throw new IllegalStateException("authorization grant role must not be null");
        }
        if (row.getDomainCode() == null || row.getDomainCode().isBlank()) {
            throw new IllegalStateException("authorization grant domain must not be blank");
        }
        if (row.getDataScopeRequired() == null) {
            throw new IllegalStateException("authorization grant data scope flag must not be null");
        }
        return new GrantedRolePermission(
                row.getRoleId(),
                new PermissionCode(row.getPermissionCode()),
                row.getDomainCode(),
                row.getDataScopeRequired().booleanValue(),
                toScope(row.getScopeCode()));
    }

    private AuthorizationScope toScope(String scopeCode) {
        if (scopeCode == null) {
            return AuthorizationScope.DENY;
        }
        try {
            return AuthorizationScope.valueOf(scopeCode);
        } catch (IllegalArgumentException ignored) {
            return AuthorizationScope.DENY;
        }
    }
}
