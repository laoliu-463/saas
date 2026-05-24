package com.colonel.saas.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);
    private static final String PLACEHOLDER_SECRET = "dev-secret-key-replace-in-production-with-random-64-char-string";
    private static final int MIN_SECRET_LENGTH = 32;

    private final SecretKey secretKey;
    private final long expireSeconds;
    private final long refreshExpireSeconds;

    public JwtTokenProvider(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.expire-seconds:7200}") long expireSeconds,
            @Value("${security.jwt.refresh-expire-seconds:604800}") long refreshExpireSeconds) {
        validateSecret(secret);
        this.secretKey = Keys.hmacShaKeyFor(sha256(secret));
        this.expireSeconds = expireSeconds;
        this.refreshExpireSeconds = refreshExpireSeconds;
    }

    private void validateSecret(String secret) {
        if (PLACEHOLDER_SECRET.equals(secret)) {
            log.warn("!!! JWT secret is using the default placeholder value. "
                    + "Set JWT_SECRET environment variable to a random string of at least "
                    + MIN_SECRET_LENGTH + " characters. !!!");
        }
        if (secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "JWT secret is too short (" + secret.length() + " chars). "
                    + "Minimum length is " + MIN_SECRET_LENGTH + " characters.");
        }
    }

    /**
     * @deprecated Use {@link #generateAccessToken} instead.
     */
    @Deprecated
    public String generateToken(
            UUID userId,
            UUID deptId,
            int dataScope,
            List<String> roleCodes,
            String username) {
        return generateAccessToken(userId, deptId, dataScope, roleCodes, username);
    }

    public String generateAccessToken(
            UUID userId,
            UUID deptId,
            int dataScope,
            List<String> roleCodes,
            String username) {
        return generateAccessToken(userId, deptId, dataScope, roleCodes, username, false);
    }

    public String generateAccessToken(
            UUID userId,
            UUID deptId,
            int dataScope,
            List<String> roleCodes,
            String username,
            boolean pendingActivation) {
        Instant now = Instant.now();
        Instant expireAt = now.plusSeconds(expireSeconds);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("type", "access")
                .claim("deptId", deptId == null ? null : deptId.toString())
                .claim("dataScope", dataScope)
                .claim("roleCodes", roleCodes)
                .claim("username", username)
                .claim("pendingActivation", pendingActivation)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expireAt))
                .signWith(secretKey)
                .compact();
    }

    public String generateRefreshToken(UUID userId) {
        Instant now = Instant.now();
        Instant expireAt = now.plusSeconds(refreshExpireSeconds);
        String jti = UUID.randomUUID().toString();

        return Jwts.builder()
                .subject(userId.toString())
                .id(jti)
                .claim("type", "refresh")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expireAt))
                .signWith(secretKey)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String parseTokenId(String token) {
        return parseClaims(token).getId();
    }

    public String getTokenHash(String token) {
        byte[] hash = sha256(token);
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    public long getExpireSeconds() {
        return expireSeconds;
    }

    public long getRefreshExpireSeconds() {
        return refreshExpireSeconds;
    }

    public long getRemainingSeconds(String token) {
        Claims claims = parseClaims(token);
        Date expiration = claims.getExpiration();
        long remaining = (expiration.getTime() - System.currentTimeMillis()) / 1000;
        return Math.max(remaining, 1);
    }

    private static byte[] sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
