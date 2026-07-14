package com.colonel.saas.config;

import com.colonel.saas.domain.user.api.AuthorizationRuntimeMode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DddAuthorizationRuntimeDefaultsContractTest {

    @Test
    void allRuntimeProfilesDefaultAuthorizationToLegacy() throws Exception {
        for (String resource : List.of(
                "application.yml",
                "application-real-pre.yml",
                "application-test.yml")) {
            String source = Files.readString(Path.of("src/main/resources", resource));

            assertThat(source)
                    .as("%s should keep authorization runtime dormant by default", resource)
                    .contains("${AUTHORIZATION_RUNTIME_DEFAULT_MODE:LEGACY}")
                    .contains("${AUTHORIZATION_SNAPSHOT_CACHE_TTL:5m}")
                    .contains("domain-modes: {}")
                    .doesNotContain("${AUTHORIZATION_RUNTIME_DEFAULT_MODE:SHADOW}")
                    .doesNotContain("${AUTHORIZATION_RUNTIME_DEFAULT_MODE:ENFORCE}");

            AuthorizationRuntimeProperties properties = bindAuthorizationRuntime(resource);
            assertThat(properties.getDefaultMode())
                    .as("%s should bind the LEGACY default", resource)
                    .isEqualTo(AuthorizationRuntimeMode.LEGACY);
            assertThat(properties.getSnapshotCacheTtl())
                    .as("%s should bind the five-minute snapshot TTL", resource)
                    .isEqualTo(Duration.ofMinutes(5));
            assertThat(properties.getDomainModes())
                    .as("%s should bind no domain overrides", resource)
                    .isEmpty();
        }
    }

    private static AuthorizationRuntimeProperties bindAuthorizationRuntime(String resource) throws Exception {
        ConfigurableEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);

        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        FileSystemResource yaml = new FileSystemResource(Path.of("src/main/resources", resource));
        for (PropertySource<?> propertySource : loader.load(resource, yaml)) {
            environment.getPropertySources().addLast(propertySource);
        }

        return Binder.get(environment)
                .bind("authorization.runtime", Bindable.of(AuthorizationRuntimeProperties.class))
                .orElseThrow(() -> new AssertionError(
                        resource + " should bind authorization.runtime"));
    }
}
