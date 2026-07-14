package com.colonel.saas.domain.user.port;

import com.colonel.saas.domain.user.api.AuthorizationPrincipal;

import java.util.Optional;
import java.util.UUID;

public interface AuthorizationPrincipalStore {

    Optional<AuthorizationPrincipal> loadLoginEligible(UUID userId);
}
