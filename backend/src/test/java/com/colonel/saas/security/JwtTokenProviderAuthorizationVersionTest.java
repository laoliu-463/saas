package com.colonel.saas.security;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderAuthorizationVersionTest {

    private final JwtTokenProvider provider = new JwtTokenProvider(
            "test-jwt-secret-for-authorization-version-32chars",
            7200,
            604800);

    @Test
    void accessAndRefreshTokensContainExactAuthorizationVersion() {
        UUID userId = UUID.randomUUID();

        String access = provider.generateAccessToken(
                userId, null, 1, List.of("biz_staff"), "alice", false, 9L);
        String refresh = provider.generateRefreshToken(userId, 9L);

        assertThat(provider.parseClaims(access).get("authzVersion", Long.class)).isEqualTo(9L);
        assertThat(provider.parseClaims(refresh).get("authzVersion", Long.class)).isEqualTo(9L);
    }

    @Test
    void accessTokenGenerationRejectsZeroAuthorizationVersion() {
        assertThatThrownBy(() -> provider.generateAccessToken(
                UUID.randomUUID(), null, 1, List.of(), "alice", false, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void accessTokenGenerationRejectsNegativeAuthorizationVersion() {
        assertThatThrownBy(() -> provider.generateAccessToken(
                UUID.randomUUID(), null, 1, List.of(), "alice", false, -1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refreshTokenGenerationRejectsZeroAuthorizationVersion() {
        assertThatThrownBy(() -> provider.generateRefreshToken(UUID.randomUUID(), 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refreshTokenGenerationRejectsNegativeAuthorizationVersion() {
        assertThatThrownBy(() -> provider.generateRefreshToken(UUID.randomUUID(), -1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
