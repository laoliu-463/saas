package com.colonel.saas.domain.user.facade;

import com.colonel.saas.domain.user.api.AuthorizationDecision;
import com.colonel.saas.domain.user.api.AuthorizationPrincipal;

public interface AuthorizationFacade {

    AuthorizationDecision authorize(AuthorizationPrincipal principal, String permissionCode);
}
