package com.colonel.saas.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    @Test
    void shouldParseClaimsWithValidToken() {
        // Arrange
        JwtTokenProvider provider = new JwtTokenProvider("unit-test-secret-long-enough-for-32-chars-minimum", 300L, 604800L);
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();

        // Act
        String token = provider.generateToken(userId, deptId, 2, List.of("admin"), "tester");
        Claims claims = provider.parseClaims(token);

        // Assert
        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("deptId", String.class)).isEqualTo(deptId.toString());
        assertThat(claims.get("dataScope", Integer.class)).isEqualTo(2);
    }

    @Test
    void shouldFailWithTamperedToken() {
        // Arrange
        JwtTokenProvider provider = new JwtTokenProvider("unit-test-secret-long-enough-for-32-chars-minimum", 300L, 604800L);
        String valid = provider.generateToken(UUID.randomUUID(), UUID.randomUUID(), 1, List.of("zs_staff"), "tester");
        String tampered = valid.substring(0, valid.length() - 2) + "xx";

        // Act + Assert
        assertThatThrownBy(() -> provider.parseClaims(tampered))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldFailWhenTokenExpired() {
        // Arrange
        JwtTokenProvider provider = new JwtTokenProvider("unit-test-secret-long-enough-for-32-chars-minimum", -1L, 604800L);
        String token = provider.generateToken(UUID.randomUUID(), UUID.randomUUID(), 1, List.of("zs_staff"), "tester");

        // Act + Assert
        assertThatThrownBy(() -> provider.parseClaims(token))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldGenerateRefreshTokenHashAndRemainingTtl() {
        JwtTokenProvider provider = new JwtTokenProvider(
                "unit-test-secret-long-enough-for-32-chars-minimum", 300L, 604800L);
        UUID userId = UUID.randomUUID();

        String refreshToken = provider.generateRefreshToken(userId);
        Claims claims = provider.parseClaims(refreshToken);

        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("type", String.class)).isEqualTo("refresh");
        assertThat(provider.parseTokenId(refreshToken)).isNotBlank();
        assertThat(provider.getTokenHash(refreshToken)).hasSize(64);
        assertThat(provider.getRemainingSeconds(refreshToken)).isPositive();
        assertThat(provider.getExpireSeconds()).isEqualTo(300L);
        assertThat(provider.getRefreshExpireSeconds()).isEqualTo(604800L);
    }

    @Test
    void shouldSupportAccessTokenWithNullDeptAndPlaceholderSecret() {
        JwtTokenProvider provider = new JwtTokenProvider(
                "dev-secret-key-replace-in-production-with-random-64-char-string", 120L, 240L);
        UUID userId = UUID.randomUUID();

        String token = provider.generateAccessToken(userId, null, 3, List.of("admin"), "admin");
        Claims claims = provider.parseClaims(token);

        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("deptId")).isNull();
        assertThat(claims.get("type", String.class)).isEqualTo("access");
        assertThat(claims.get("username", String.class)).isEqualTo("admin");
    }

    @Test
    void shouldIncludePendingActivationClaimWhenRequested() {
        JwtTokenProvider provider = new JwtTokenProvider(
                "unit-test-secret-long-enough-for-32-chars-minimum", 120L, 240L);
        UUID userId = UUID.randomUUID();

        String token = provider.generateAccessToken(userId, null, 1, List.of("biz_staff"), "pending", true);
        Claims claims = provider.parseClaims(token);

        assertThat(claims.get("pendingActivation", Boolean.class)).isTrue();
    }

    @Test
    void shouldRejectShortSecret() {
        assertThatThrownBy(() -> new JwtTokenProvider("too-short", 1L, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("too short");
    }
}
