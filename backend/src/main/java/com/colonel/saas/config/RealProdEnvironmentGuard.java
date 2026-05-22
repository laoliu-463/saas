package com.colonel.saas.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Configuration
public class RealProdEnvironmentGuard {

    private static final String DEFAULT_JWT_SECRET = "dev-secret-key-replace-in-production-with-random-64-char-string";

    private final Environment environment;
    private final boolean appTestEnabled;
    private final boolean douyinTestEnabled;
    private final boolean verifyWebhookSign;
    private final String jwtSecret;
    private final String douyinAppId;
    private final String douyinClientKey;
    private final String douyinClientSecret;
    private final String corsAllowedOriginPatterns;
    private static final Set<String> PROTECTED_PROFILES = Set.of("real-pre", "real", "prod");
    private static final Set<String> FORBIDDEN_MOCK_PROFILES = Set.of("local-mock", "test");

    public RealProdEnvironmentGuard(
            Environment environment,
            @Value("${app.test.enabled:false}") boolean appTestEnabled,
            @Value("${douyin.test.enabled:false}") boolean douyinTestEnabled,
            @Value("${douyin.webhook.verify-sign:true}") boolean verifyWebhookSign,
            @Value("${security.jwt.secret:}") String jwtSecret,
            @Value("${douyin.app.app-id:}") String douyinAppId,
            @Value("${douyin.app.client-key:}") String douyinClientKey,
            @Value("${douyin.app.client-secret:}") String douyinClientSecret,
            @Value("${app.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}") String corsAllowedOriginPatterns) {
        this.environment = environment;
        this.appTestEnabled = appTestEnabled;
        this.douyinTestEnabled = douyinTestEnabled;
        this.verifyWebhookSign = verifyWebhookSign;
        this.jwtSecret = jwtSecret;
        this.douyinAppId = douyinAppId;
        this.douyinClientKey = douyinClientKey;
        this.douyinClientSecret = douyinClientSecret;
        this.corsAllowedOriginPatterns = corsAllowedOriginPatterns;
    }

    @PostConstruct
    public void validate() {
        Set<String> activeProfiles = normalizedActiveProfiles();
        if (activeProfiles.isEmpty()) {
            return;
        }
        if (!hasProtectedProfile(activeProfiles)) {
            return;
        }
        requireNoForbiddenProfileMix(activeProfiles);
        requireFalse("app.test.enabled", appTestEnabled);
        requireFalse("douyin.test.enabled", douyinTestEnabled);
        requireTrue("douyin.webhook.verify-sign", verifyWebhookSign);
        requireSecret("security.jwt.secret", jwtSecret, DEFAULT_JWT_SECRET);
        requireSecret("douyin.app.app-id", douyinAppId, null);
        requireSecret("douyin.app.client-key", douyinClientKey, null);
        requireSecret("douyin.app.client-secret", douyinClientSecret, null);
        requireSafeCorsOriginPatterns();
    }

    private void requireSafeCorsOriginPatterns() {
        if (!StringUtils.hasText(corsAllowedOriginPatterns)) {
            throw new IllegalStateException(activeProfileLabel(normalizedActiveProfiles())
                    + " profile requires non-empty app.cors.allowed-origin-patterns");
        }
        for (String pattern : corsAllowedOriginPatterns.split(",")) {
            String trimmed = pattern.trim();
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }
            if (isUnsafeWildcardOriginPattern(trimmed)) {
                throw new IllegalStateException(activeProfileLabel(normalizedActiveProfiles())
                        + " profile does not allow unsafe CORS pattern when credentials are enabled: " + trimmed);
            }
        }
    }

    private static boolean isUnsafeWildcardOriginPattern(String pattern) {
        return "*".equals(pattern)
                || "http://*".equalsIgnoreCase(pattern)
                || "https://*".equalsIgnoreCase(pattern)
                || "http://*:*".equalsIgnoreCase(pattern)
                || "https://*:*".equalsIgnoreCase(pattern);
    }

    private Set<String> normalizedActiveProfiles() {
        Set<String> profiles = new LinkedHashSet<>();
        Arrays.stream(environment.getActiveProfiles())
                .filter(StringUtils::hasText)
                .map(profile -> profile.trim().toLowerCase(Locale.ROOT))
                .forEach(profiles::add);
        return profiles;
    }

    private boolean hasProtectedProfile(Set<String> activeProfiles) {
        for (String profile : activeProfiles) {
            if (PROTECTED_PROFILES.contains(profile)) {
                return true;
            }
        }
        return false;
    }

    private void requireNoForbiddenProfileMix(Set<String> activeProfiles) {
        for (String forbidden : FORBIDDEN_MOCK_PROFILES) {
            if (activeProfiles.contains(forbidden)) {
                throw new IllegalStateException(activeProfileLabel(activeProfiles)
                        + " profile cannot be combined with " + forbidden);
            }
        }
    }

    private void requireFalse(String key, boolean value) {
        if (value) {
            throw new IllegalStateException(activeProfileLabel(normalizedActiveProfiles()) + " profile does not allow " + key + "=true");
        }
    }

    private void requireTrue(String key, boolean value) {
        if (!value) {
            throw new IllegalStateException(activeProfileLabel(normalizedActiveProfiles()) + " profile requires " + key + "=true");
        }
    }

    private void requireSecret(String key, String value, String disallowedExact) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(activeProfileLabel(normalizedActiveProfiles()) + " profile requires non-empty " + key);
        }
        String normalized = value.trim();
        if (disallowedExact != null && disallowedExact.equals(normalized)) {
            throw new IllegalStateException(activeProfileLabel(normalizedActiveProfiles()) + " profile does not allow default placeholder " + key);
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.startsWith("change-me")) {
            throw new IllegalStateException(activeProfileLabel(normalizedActiveProfiles()) + " profile does not allow placeholder " + key);
        }
    }

    private String activeProfileLabel(Set<String> activeProfiles) {
        for (String profile : activeProfiles) {
            if (PROTECTED_PROFILES.contains(profile)) {
                return profile;
            }
        }
        return "protected";
    }
}
