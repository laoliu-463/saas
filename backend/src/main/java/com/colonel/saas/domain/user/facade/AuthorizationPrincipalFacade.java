package com.colonel.saas.domain.user.facade;

import com.colonel.saas.domain.user.api.AuthorizationPrincipal;

import java.util.UUID;

public interface AuthorizationPrincipalFacade {

    AuthorizationPrincipal requireCurrent(UUID userId, Long tokenAuthzVersion);
}
