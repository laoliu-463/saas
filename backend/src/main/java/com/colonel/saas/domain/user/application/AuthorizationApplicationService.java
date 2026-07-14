package com.colonel.saas.domain.user.application;

import com.colonel.saas.domain.user.api.AuthorizationDecision;
import com.colonel.saas.domain.user.api.AuthorizationPrincipal;
import com.colonel.saas.domain.user.api.AuthorizationReason;
import com.colonel.saas.domain.user.api.PermissionCode;
import com.colonel.saas.domain.user.facade.AuthorizationFacade;
import com.colonel.saas.domain.user.policy.AuthorizationDecisionPolicy;
import com.colonel.saas.domain.user.port.AuthorizationSnapshotStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthorizationApplicationService implements AuthorizationFacade {

    private final AuthorizationSnapshotStore store;
    private final AuthorizationDecisionPolicy policy;

    public AuthorizationApplicationService(
            AuthorizationSnapshotStore store,
            AuthorizationDecisionPolicy policy) {
        this.store = store;
        this.policy = policy;
    }

    @Override
    @Transactional(readOnly = true)
    public AuthorizationDecision authorize(
            AuthorizationPrincipal principal,
            String rawPermissionCode) {
        PermissionCode permission = new PermissionCode(rawPermissionCode);
        if (principal == null) {
            return AuthorizationDecision.deny(
                    permission,
                    null,
                    AuthorizationReason.SUBJECT_NOT_ACTIVE);
        }
        return store.loadActiveSnapshot(principal.userId(), principal.authzVersion())
                .map(snapshot -> policy.decide(permission, snapshot))
                .orElseGet(() -> AuthorizationDecision.deny(
                        permission,
                        null,
                        AuthorizationReason.SUBJECT_NOT_ACTIVE));
    }
}
