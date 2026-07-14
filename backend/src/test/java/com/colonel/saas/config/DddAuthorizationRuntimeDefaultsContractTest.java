package com.colonel.saas.config;

import com.colonel.saas.domain.user.api.AuthorizationRuntimeMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DddAuthorizationRuntimeDefaultsContractTest {

    @Test
    void allCheckedInRuntimeProfilesAndDomainsAvoidEnforce() throws Exception {
        List<String> resources = checkedInRuntimeProfiles();
        assertThat(resources).isNotEmpty();

        for (String resource : resources) {
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
            assertThat(properties.getDefaultMode())
                    .as("%s should not configure ENFORCE as its default", resource)
                    .isNotEqualTo(AuthorizationRuntimeMode.ENFORCE);
            assertThat(properties.getSnapshotCacheTtl())
                    .as("%s should bind the five-minute snapshot TTL", resource)
                    .isEqualTo(Duration.ofMinutes(5));
            assertThat(properties.getDomainModes())
                    .as("%s should bind no domain overrides", resource)
                    .isEmpty();
            assertThat(properties.getDomainModes())
                    .as("%s should not configure any domain as ENFORCE", resource)
                    .doesNotContainValue(AuthorizationRuntimeMode.ENFORCE);
        }
    }

    @Test
    void laterYamlDocumentOverridesEarlierAndEnforceGateRejectsIt(
            @TempDir Path root) throws Exception {
        Path yaml = root.resolve("application-multi-document.yml");
        Files.writeString(yaml, """
                authorization:
                  runtime:
                    default-mode: LEGACY
                ---
                authorization:
                  runtime:
                    default-mode: ENFORCE
                """);

        AuthorizationRuntimeProperties properties = bindAuthorizationRuntime(yaml);

        assertThat(properties.getDefaultMode())
                .as("later YAML documents should override earlier documents")
                .isEqualTo(AuthorizationRuntimeMode.ENFORCE);
        assertThatThrownBy(() -> assertThat(properties.getDefaultMode())
                .as("the runtime default ENFORCE gate")
                .isNotEqualTo(AuthorizationRuntimeMode.ENFORCE))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ENFORCE");
    }

    @Test
    void checkedInRuntimeProfilesDiscoverDottedProfilesAndOnlyYamlFiles(
            @TempDir Path root) throws Exception {
        Files.writeString(root.resolve("application.yml"), "");
        Files.writeString(root.resolve("application-prod.eu.yml"), "");
        Files.writeString(root.resolve("application-prod.eu.yaml"), "");
        Files.writeString(root.resolve("application-.yml"), "");
        Files.writeString(root.resolve("application-prod.eu.properties"), "");
        Files.writeString(root.resolve("bootstrap-prod.eu.yml"), "");

        assertThat(checkedInRuntimeProfiles(root))
                .containsExactlyInAnyOrder(
                        "application.yml",
                        "application-prod.eu.yml",
                        "application-prod.eu.yaml");
    }

    private static List<String> checkedInRuntimeProfiles() throws Exception {
        return checkedInRuntimeProfiles(Path.of("src/main/resources"));
    }

    private static List<String> checkedInRuntimeProfiles(Path root) throws Exception {
        try (Stream<Path> paths = Files.list(root)) {
            return paths
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.matches("^application(?:-.+)?\\.ya?ml$"))
                    .sorted()
                    .toList();
        }
    }

    private static AuthorizationRuntimeProperties bindAuthorizationRuntime(String resource) throws Exception {
        return bindAuthorizationRuntime(Path.of("src/main/resources", resource));
    }

    private static AuthorizationRuntimeProperties bindAuthorizationRuntime(Path yaml) throws Exception {
        String resource = yaml.getFileName().toString();
        ConfigurableEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);

        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        FileSystemResource yamlResource = new FileSystemResource(yaml);
        for (PropertySource<?> propertySource : loader.load(resource, yamlResource)) {
            environment.getPropertySources().addFirst(propertySource);
        }

        return Binder.get(environment)
                .bind("authorization.runtime", Bindable.of(AuthorizationRuntimeProperties.class))
                .orElseThrow(() -> new AssertionError(
                        resource + " should bind authorization.runtime"));
    }
}
