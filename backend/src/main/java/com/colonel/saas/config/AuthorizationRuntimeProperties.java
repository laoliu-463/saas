package com.colonel.saas.config;

import com.colonel.saas.domain.user.api.AuthorizationRuntimeMode;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "authorization.runtime")
public class AuthorizationRuntimeProperties {

    private AuthorizationRuntimeMode defaultMode = AuthorizationRuntimeMode.LEGACY;
    private Map<String, AuthorizationRuntimeMode> domainModes = new LinkedHashMap<>();
    private Duration snapshotCacheTtl = Duration.ofMinutes(5);

    public AuthorizationRuntimeMode modeFor(String domainCode) {
        if (domainCode == null || domainCode.isBlank()) {
            return defaultMode;
        }
        String normalized = domainCode.trim().toLowerCase(Locale.ROOT);
        return domainModes.entrySet().stream()
                .filter(entry -> entry.getKey() != null
                        && normalized.equals(entry.getKey().trim().toLowerCase(Locale.ROOT)))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(defaultMode);
    }

    public boolean requiresVersionValidation() {
        return defaultMode != AuthorizationRuntimeMode.LEGACY
                || domainModes.values().stream()
                .anyMatch(mode -> mode != null && mode != AuthorizationRuntimeMode.LEGACY);
    }
}
