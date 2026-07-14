package com.colonel.saas.domain.user.facade;

import com.colonel.saas.domain.user.api.AuthorizationDecision;

import java.util.UUID;

public interface AuthorizationFacade {

    AuthorizationDecision authorize(UUID userId, String permissionCode);
}
