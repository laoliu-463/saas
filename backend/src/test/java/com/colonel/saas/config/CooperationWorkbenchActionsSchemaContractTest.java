package com.colonel.saas.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CooperationWorkbenchActionsSchemaContractTest {

    private static final String[] SCHEMA_RESOURCES = {
            "db/migrate/V20260716_001__cooperation_workbench_actions.sql",
            "db/init-db.sql",
            "db/migrate-all.sql"
    };

    @Test
    void everySchemaEntry_shouldOnlyDeclarePrivateNotesForCooperationActions() throws IOException {
        for (String resource : SCHEMA_RESOURCES) {
            String sql = readResource(resource);

            assertThat(sql)
                    .as(resource)
                    .contains("CREATE TABLE IF NOT EXISTS sample_private_note")
                    .contains("uk_sample_private_note_owner")
                    .contains("WHERE deleted = 0");
        }
    }

    private String readResource(String resource) throws IOException {
        try (var input = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            assertThat(input).as(resource).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
