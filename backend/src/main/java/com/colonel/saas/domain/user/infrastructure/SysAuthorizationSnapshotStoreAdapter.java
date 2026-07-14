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
        AuthorizationSubject subject = new AuthorizationSubject(
                first.getUserId(), first.getDeptId(), first.getAuthzVersion());
        List<GrantedRolePermission> grants = rows.stream()
                .filter(row -> row.getPermissionCode() != null)
                .map(this::toGrant)
                .toList();
        return Optional.of(new AuthorizationSnapshot(subject, grants));
    }

    private GrantedRolePermission toGrant(AuthorizationSnapshotRow row) {
        return new GrantedRolePermission(
                row.getRoleId(),
                new PermissionCode(row.getPermissionCode()),
                row.getDomainCode(),
                Boolean.TRUE.equals(row.getDataScopeRequired()),
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
