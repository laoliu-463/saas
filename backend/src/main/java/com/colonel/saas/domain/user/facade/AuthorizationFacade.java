package com.colonel.saas.domain.user.facade;

import com.colonel.saas.domain.user.api.AuthorizationDecision;

import java.util.UUID;
import java.util.List;

public interface AuthorizationFacade {

    AuthorizationDecision authorize(UUID userId, String permissionCode);

    List<String> grantedPermissionCodes(UUID userId);
}
