package com.colonel.saas.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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

    private final SecretKey secretKey;
    private final long expireSeconds;

    public JwtTokenProvider(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.expire-seconds:7200}") long expireSeconds) {
        this.secretKey = Keys.hmacShaKeyFor(sha256(secret));
        this.expireSeconds = expireSeconds;
    }

    public String generateToken(
            UUID userId,
            UUID deptId,
            int dataScope,
            List<String> roleCodes,
            String username) {
        Instant now = Instant.now();
        Instant expireAt = now.plusSeconds(expireSeconds);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("deptId", deptId == null ? null : deptId.toString())
                .claim("dataScope", dataScope)
                .claim("roleCodes", roleCodes)
                .claim("username", username)
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

    public long getExpireSeconds() {
        return expireSeconds;
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
