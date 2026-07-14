package com.colonel.saas.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
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
        }
    }
}
