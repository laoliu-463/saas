package com.colonel.saas.config;

import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class RuntimeExposurePolicy {

    private static final List<String> BASE_PUBLIC_PATTERNS = List.of(
            "/auth/login",
            "/auth/refresh",
            "/douyin/webhooks/**",
            "/douyin/oauth/callback",
            "/api/douyin/oauth/callback",
            "/error",
            "/system/health",
            "/api/system/health"
    );

    private static final List<String> DOC_PUBLIC_PATTERNS = List.of(
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-resources/**",
            "/doc.html"
    );

    private static final List<String> ENV_PUBLIC_PATTERNS = List.of(
            "/system/env",
            "/api/system/env"
    );

    private RuntimeExposurePolicy() {
    }

    public static boolean isProdProfileActive(Environment environment) {
        if (environment == null) {
            return false;
        }
        return Arrays.stream(environment.getActiveProfiles())
                .filter(StringUtils::hasText)
                .map(profile -> profile.trim().toLowerCase(Locale.ROOT))
                .anyMatch("prod"::equals);
    }

    public static boolean requiresAdminForSystemEnv(Environment environment) {
        return isProdProfileActive(environment);
    }

    public static String[] publicSecurityPatterns(Environment environment) {
        List<String> patterns = new ArrayList<>(BASE_PUBLIC_PATTERNS);
        if (!isProdProfileActive(environment)) {
            patterns.addAll(DOC_PUBLIC_PATTERNS);
            patterns.addAll(ENV_PUBLIC_PATTERNS);
        }
        return patterns.toArray(String[]::new);
    }

    public static boolean shouldBypassAuthentication(Environment environment, String path) {
        if (!StringUtils.hasText(path)) {
            return false;
        }
        String normalizedPath = path.trim();
        for (String pattern : publicSecurityPatterns(environment)) {
            if (matches(pattern, normalizedPath)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(String pattern, String path) {
        if (!StringUtils.hasText(pattern)) {
            return false;
        }
        if (pattern.endsWith("/**")) {
            String prefix = pattern.substring(0, pattern.length() - 3);
            return path.equals(prefix) || path.startsWith(prefix + "/");
        }
        return path.equals(pattern);
    }
}
