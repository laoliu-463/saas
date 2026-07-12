package com.colonel.saas.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DddRuntimeDefaultsContractTest {

    private static final List<String> DDD_ENVIRONMENT_FLAGS = List.of(
            "DDD_REFACTOR_ENABLED",
            "DDD_REFACTOR_USER_FACADE_ENABLED",
            "DDD_REFACTOR_CONFIG_FACADE_ENABLED",
            "DDD_REFACTOR_PRODUCT_FACADE_ENABLED",
            "DDD_REFACTOR_PRODUCT_DISPLAY_POLICY_ENABLED",
            "DDD_REFACTOR_TALENT_FACADE_ENABLED",
            "DDD_REFACTOR_SAMPLE_APPLICATION_ENABLED",
            "DDD_REFACTOR_ORDER_APPLICATION_ENABLED",
            "DDD_REFACTOR_ORDER_ATTRIBUTION_ENABLED",
            "DDD_REFACTOR_ORDER_AMOUNT_POLICY_ENABLED",
            "DDD_REFACTOR_PERFORMANCE_CALC_ENABLED",
            "DDD_REFACTOR_PERFORMANCE_QUERY_ENABLED",
            "DDD_REFACTOR_ANALYTICS_SHADOW_ENABLED",
            "DDD_REFACTOR_OUTBOX_ENABLED",
            "DDD_REFACTOR_DATA_SCOPE_POLICY_ENABLED",
            "DDD_REFACTOR_SAMPLE_HOMEWORK_EVENT_ENABLED",
            "DDD_REFACTOR_COLONEL_PARTNER_CONTACT_ENABLED"
    );

    @Test
    void allRuntimeProfilesDefaultToDddEnabled() throws Exception {
        for (String resource : List.of(
                "application.yml",
                "application-test.yml",
                "application-real-pre.yml")) {
            String source = Files.readString(Path.of("src/main/resources", resource));
            for (String environmentFlag : DDD_ENVIRONMENT_FLAGS) {
                assertThat(source)
                        .as("%s should default %s to true", resource, environmentFlag)
                        .contains("${" + environmentFlag + ":true}")
                        .doesNotContain("${" + environmentFlag + ":false}");
            }
        }
    }
}
