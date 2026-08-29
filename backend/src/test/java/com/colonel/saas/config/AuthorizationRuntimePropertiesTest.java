package com.colonel.saas.config;

import com.colonel.saas.domain.user.api.AuthorizationRuntimeMode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizationRuntimePropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void untouchedPropertiesUseSafeLegacyDefaults() {
        AuthorizationRuntimeProperties properties = new AuthorizationRuntimeProperties();

        assertThat(properties.getDefaultMode()).isEqualTo(AuthorizationRuntimeMode.LEGACY);
        assertThat(properties.getDomainModes()).isEmpty();
        assertThat(properties.requiresVersionValidation()).isFalse();
        assertThat(properties.getSnapshotCacheTtl()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void domainOverridesAreNormalizedBeforeLookup() {
        AuthorizationRuntimeProperties properties = new AuthorizationRuntimeProperties();
        properties.setDomainModes(Map.of(" Sample ", AuthorizationRuntimeMode.SHADOW));

        assertThat(properties.getDomainModes())
                .containsOnlyKeys("sample")
                .containsEntry("sample", AuthorizationRuntimeMode.SHADOW);
        assertThat(properties.modeFor(" SAMPLE ")).isEqualTo(AuthorizationRuntimeMode.SHADOW);
        assertThat(properties.modeFor("order")).isEqualTo(AuthorizationRuntimeMode.LEGACY);
    }

    @Test
    void nullDefaultModeFailsFastWithoutChangingSafeDefault() {
        AuthorizationRuntimeProperties properties = new AuthorizationRuntimeProperties();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties.setDefaultMode(null))
                .withMessageContaining("defaultMode");

        assertThat(properties.getDefaultMode()).isEqualTo(AuthorizationRuntimeMode.LEGACY);
        assertThat(properties.modeFor("sample")).isEqualTo(AuthorizationRuntimeMode.LEGACY);
    }

    @Test
    void nullDomainModesFailFastWithoutChangingExistingOverrides() {
        AuthorizationRuntimeProperties properties = new AuthorizationRuntimeProperties();
        properties.setDomainModes(Map.of("sample", AuthorizationRuntimeMode.SHADOW));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties.setDomainModes(null))
                .withMessageContaining("domainModes");

        assertThat(properties.getDomainModes())
                .containsOnlyKeys("sample")
                .containsEntry("sample", AuthorizationRuntimeMode.SHADOW);
        assertThat(properties.modeFor("sample")).isEqualTo(AuthorizationRuntimeMode.SHADOW);
    }

    @Test
    void nullDomainKeyFailsFast() {
        AuthorizationRuntimeProperties properties = new AuthorizationRuntimeProperties();
        Map<String, AuthorizationRuntimeMode> domainModes = new LinkedHashMap<>();
        domainModes.put(null, AuthorizationRuntimeMode.SHADOW);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties.setDomainModes(domainModes))
                .withMessageContaining("domainModes key");
    }

    @Test
    void blankDomainKeyFailsFast() {
        AuthorizationRuntimeProperties properties = new AuthorizationRuntimeProperties();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties.setDomainModes(Map.of("  ", AuthorizationRuntimeMode.SHADOW)))
                .withMessageContaining("domainModes key");
    }

    @Test
    void nullDomainModeFailsFast() {
        AuthorizationRuntimeProperties properties = new AuthorizationRuntimeProperties();
        Map<String, AuthorizationRuntimeMode> domainModes = new LinkedHashMap<>();
        domainModes.put("sample", null);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties.setDomainModes(domainModes))
                .withMessageContaining("domainModes value");
    }

    @Test
    void duplicateNormalizedDomainKeyFailsFast() {
        AuthorizationRuntimeProperties properties = new AuthorizationRuntimeProperties();
        Map<String, AuthorizationRuntimeMode> domainModes = new LinkedHashMap<>();
        domainModes.put("Sample", AuthorizationRuntimeMode.SHADOW);
        domainModes.put(" sample ", AuthorizationRuntimeMode.ENFORCE);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties.setDomainModes(domainModes))
                .withMessageContaining("duplicate domainModes key")
                .withMessageContaining("sample");

        assertThat(properties.getDomainModes()).isEmpty();
    }

    @Test
    void domainModesCannotBeMutatedWithoutValidation() {
        AuthorizationRuntimeProperties properties = new AuthorizationRuntimeProperties();

        assertThatThrownBy(() -> properties.getDomainModes()
                .put(" Sample ", AuthorizationRuntimeMode.SHADOW))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullZeroOrNegativeSnapshotCacheTtlFailsFast() {
        AuthorizationRuntimeProperties properties = new AuthorizationRuntimeProperties();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties.setSnapshotCacheTtl(null))
                .withMessageContaining("snapshotCacheTtl");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties.setSnapshotCacheTtl(Duration.ZERO))
                .withMessageContaining("snapshotCacheTtl");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties.setSnapshotCacheTtl(Duration.ofSeconds(-1)))
                .withMessageContaining("snapshotCacheTtl");

        assertThat(properties.getSnapshotCacheTtl()).isEqualTo(Duration.ofMinutes(5));
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

    @Test
    void springConfigurationBindingUsesValidatedNormalizedValues() {
        contextRunner
                .withPropertyValues(
                        "authorization.runtime.default-mode=shadow",
                        "authorization.runtime.domain-modes.Sample=enforce",
                        "authorization.runtime.snapshot-cache-ttl=30s")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AuthorizationRuntimeProperties.class);
                    AuthorizationRuntimeProperties properties = context.getBean(
                            AuthorizationRuntimeProperties.class);

                    assertThat(properties.getDefaultMode()).isEqualTo(AuthorizationRuntimeMode.SHADOW);
                    assertThat(properties.getDomainModes())
                            .containsOnlyKeys("sample")
                            .containsEntry("sample", AuthorizationRuntimeMode.ENFORCE);
                    assertThat(properties.getSnapshotCacheTtl()).isEqualTo(Duration.ofSeconds(30));
                    assertThat(properties.requiresVersionValidation()).isTrue();
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AuthorizationRuntimeProperties.class)
    static class TestConfiguration {
    }
}
