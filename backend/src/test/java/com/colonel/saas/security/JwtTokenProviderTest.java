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
        JwtTokenProvider provider = new JwtTokenProvider("unit-test-secret", 300);
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
        JwtTokenProvider provider = new JwtTokenProvider("unit-test-secret", 300);
        String valid = provider.generateToken(UUID.randomUUID(), UUID.randomUUID(), 1, List.of("zs_staff"), "tester");
        String tampered = valid.substring(0, valid.length() - 2) + "xx";

        // Act + Assert
        assertThatThrownBy(() -> provider.parseClaims(tampered))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldFailWhenTokenExpired() {
        // Arrange
        JwtTokenProvider provider = new JwtTokenProvider("unit-test-secret", -1);
        String token = provider.generateToken(UUID.randomUUID(), UUID.randomUUID(), 1, List.of("zs_staff"), "tester");

        // Act + Assert
        assertThatThrownBy(() -> provider.parseClaims(token))
                .isInstanceOf(RuntimeException.class);
    }
}
