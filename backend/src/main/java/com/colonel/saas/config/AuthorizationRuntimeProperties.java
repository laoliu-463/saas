package com.colonel.saas.config;

import com.colonel.saas.domain.user.api.AuthorizationRuntimeMode;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Getter
@Component
@ConfigurationProperties(prefix = "authorization.runtime")
public class AuthorizationRuntimeProperties {

    private AuthorizationRuntimeMode defaultMode = AuthorizationRuntimeMode.LEGACY;
    private Map<String, AuthorizationRuntimeMode> domainModes = new LinkedHashMap<>();
    private Duration snapshotCacheTtl = Duration.ofMinutes(5);

    public void setDefaultMode(AuthorizationRuntimeMode defaultMode) {
        if (defaultMode == null) {
            throw new IllegalArgumentException("defaultMode must not be null");
        }
        this.defaultMode = defaultMode;
    }

    public Map<String, AuthorizationRuntimeMode> getDomainModes() {
        return Collections.unmodifiableMap(domainModes);
    }

    public void setDomainModes(Map<String, AuthorizationRuntimeMode> domainModes) {
        if (domainModes == null) {
            throw new IllegalArgumentException("domainModes must not be null");
        }

        Map<String, AuthorizationRuntimeMode> normalizedModes = new LinkedHashMap<>();
        for (Map.Entry<String, AuthorizationRuntimeMode> entry : domainModes.entrySet()) {
            String normalizedKey = normalizeDomainKey(entry.getKey());
            AuthorizationRuntimeMode mode = entry.getValue();
            if (mode == null) {
                throw new IllegalArgumentException(
                        "domainModes value must not be null for key: " + normalizedKey);
            }
            if (normalizedModes.putIfAbsent(normalizedKey, mode) != null) {
                throw new IllegalArgumentException("duplicate domainModes key: " + normalizedKey);
            }
        }
        this.domainModes = normalizedModes;
    }

    public void setSnapshotCacheTtl(Duration snapshotCacheTtl) {
        if (snapshotCacheTtl == null || snapshotCacheTtl.isZero() || snapshotCacheTtl.isNegative()) {
            throw new IllegalArgumentException("snapshotCacheTtl must be greater than zero");
        }
        this.snapshotCacheTtl = snapshotCacheTtl;
    }

    public AuthorizationRuntimeMode modeFor(String domainCode) {
        if (domainCode == null || domainCode.isBlank()) {
            return defaultMode;
        }
        String normalized = domainCode.trim().toLowerCase(Locale.ROOT);
        return domainModes.getOrDefault(normalized, defaultMode);
    }

    public boolean requiresVersionValidation() {
        return defaultMode != AuthorizationRuntimeMode.LEGACY
                || domainModes.values().stream()
                .anyMatch(mode -> mode != null && mode != AuthorizationRuntimeMode.LEGACY);
    }

    private static String normalizeDomainKey(String domainCode) {
        if (domainCode == null || domainCode.isBlank()) {
            throw new IllegalArgumentException("domainModes key must not be null or blank");
        }
        return domainCode.trim().toLowerCase(Locale.ROOT);
    }
}
