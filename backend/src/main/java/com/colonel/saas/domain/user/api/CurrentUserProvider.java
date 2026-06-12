package com.colonel.saas.domain.user.api;

import java.util.UUID;

/**
 * Provides the current authenticated user id for user-domain query adapters.
 */
public interface CurrentUserProvider {

    /**
     * Returns the current user id, or null when no request user context exists.
     */
    UUID currentUserId();
}
