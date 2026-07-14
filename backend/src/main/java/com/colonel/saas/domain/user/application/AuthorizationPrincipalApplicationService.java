package com.colonel.saas.domain.user.application;

import com.colonel.saas.domain.user.api.AuthorizationPrincipal;
import com.colonel.saas.domain.user.api.AuthorizationTokenRejectedException;
import com.colonel.saas.domain.user.api.AuthorizationUnavailableException;
import com.colonel.saas.domain.user.facade.AuthorizationPrincipalFacade;
import com.colonel.saas.domain.user.port.AuthorizationPrincipalStore;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthorizationPrincipalApplicationService implements AuthorizationPrincipalFacade {

    private final AuthorizationPrincipalStore store;

    public AuthorizationPrincipalApplicationService(AuthorizationPrincipalStore store) {
        this.store = store;
    }

    @Override
    @Transactional(readOnly = true)
    public AuthorizationPrincipal requireCurrent(UUID userId, Long tokenAuthzVersion) {
        if (userId == null || tokenAuthzVersion == null || tokenAuthzVersion < 1) {
            throw new AuthorizationTokenRejectedException();
        }
        try {
            AuthorizationPrincipal principal = store.loadLoginEligible(userId)
                    .orElseThrow(AuthorizationTokenRejectedException::new);
            if (principal.authzVersion() != tokenAuthzVersion.longValue()) {
                throw new AuthorizationTokenRejectedException();
            }
            return principal;
        } catch (DataAccessException exception) {
            throw new AuthorizationUnavailableException(exception);
        }
    }
}
