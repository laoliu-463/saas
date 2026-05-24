package com.colonel.saas.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PendingActivationAccessPolicyTest {

    @Test
    void allowsCurrentUserProfileAndPasswordChangeAndLogout() {
        assertThat(PendingActivationAccessPolicy.isAllowed("GET", "/users/current")).isTrue();
        assertThat(PendingActivationAccessPolicy.isAllowed("PUT", "/users/current/password")).isTrue();
        assertThat(PendingActivationAccessPolicy.isAllowed("POST", "/auth/logout")).isTrue();
    }

    @Test
    void blocksBusinessEndpoints() {
        assertThat(PendingActivationAccessPolicy.isAllowed("GET", "/products")).isFalse();
        assertThat(PendingActivationAccessPolicy.isAllowed("GET", "/users/current/data-scope")).isFalse();
    }
}
