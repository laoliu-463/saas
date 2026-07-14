package com.colonel.saas.config;

import com.colonel.saas.domain.user.api.AuthorizationRuntimeMode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationRuntimePropertiesTest {

    @Test
    void untouchedPropertiesUseSafeLegacyDefaults() {
        AuthorizationRuntimeProperties properties = new AuthorizationRuntimeProperties();

        assertThat(properties.getDefaultMode()).isEqualTo(AuthorizationRuntimeMode.LEGACY);
        assertThat(properties.getDomainModes()).isInstanceOf(LinkedHashMap.class).isEmpty();
        assertThat(properties.requiresVersionValidation()).isFalse();
        assertThat(properties.getSnapshotCacheTtl()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void domainOverrideMatchingIsTrimmedAndCaseInsensitive() {
        AuthorizationRuntimeProperties properties = new AuthorizationRuntimeProperties();
        properties.setDomainModes(Map.of(" Sample ", AuthorizationRuntimeMode.SHADOW));

        assertThat(properties.modeFor(" SAMPLE ")).isEqualTo(AuthorizationRuntimeMode.SHADOW);
        assertThat(properties.modeFor("order")).isEqualTo(AuthorizationRuntimeMode.LEGACY);
    }

    @Test
    void emptyDomainUsesDefaultMode() {
        AuthorizationRuntimeProperties properties = new AuthorizationRuntimeProperties();
        properties.setDefaultMode(AuthorizationRuntimeMode.ENFORCE);

        assertThat(properties.modeFor(null)).isEqualTo(AuthorizationRuntimeMode.ENFORCE);
        assertThat(properties.modeFor("  ")).isEqualTo(AuthorizationRuntimeMode.ENFORCE);
    }

    @Test
    void anyNonLegacyDomainOverrideRequiresVersionValidation() {
        for (AuthorizationRuntimeMode mode : new AuthorizationRuntimeMode[]{
                AuthorizationRuntimeMode.SHADOW,
                AuthorizationRuntimeMode.ENFORCE
        }) {
            AuthorizationRuntimeProperties properties = new AuthorizationRuntimeProperties();
            properties.setDomainModes(Map.of("sample", mode));

            assertThat(properties.requiresVersionValidation())
                    .as("domain override %s should require version validation", mode)
                    .isTrue();
        }
    }

    @Test
    void legacyOnlyDomainOverrideDoesNotRequireVersionValidation() {
        AuthorizationRuntimeProperties properties = new AuthorizationRuntimeProperties();
        properties.setDomainModes(Map.of("sample", AuthorizationRuntimeMode.LEGACY));

        assertThat(properties.requiresVersionValidation()).isFalse();
    }

    @Test
    void nonLegacyDefaultModeRequiresVersionValidation() {
        for (AuthorizationRuntimeMode mode : new AuthorizationRuntimeMode[]{
                AuthorizationRuntimeMode.SHADOW,
                AuthorizationRuntimeMode.ENFORCE
        }) {
            AuthorizationRuntimeProperties properties = new AuthorizationRuntimeProperties();
            properties.setDefaultMode(mode);

            assertThat(properties.requiresVersionValidation())
                    .as("default mode %s should require version validation", mode)
                    .isTrue();
        }
    }
}
