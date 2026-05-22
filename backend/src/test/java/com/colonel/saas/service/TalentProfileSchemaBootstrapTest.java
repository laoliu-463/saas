package com.colonel.saas.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TalentProfileSchemaBootstrapTest {

    @Test
    void splitStatements_removesCommentsAndKeepsTailStatementWithoutSemicolon() {
        String sql = """
                -- comment
                CREATE TABLE talent_profile (
                  id uuid
                );

                -- another comment
                ALTER TABLE talent_profile
                  ADD COLUMN source text
                """;

        assertThat(TalentProfileSchemaBootstrap.splitStatements(sql))
                .containsExactly(
                        "CREATE TABLE talent_profile (\n  id uuid\n)",
                        "ALTER TABLE talent_profile\n  ADD COLUMN source text"
                );
    }

    @Test
    void run_executesEveryStatementFromClasspathMigration() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TalentProfileSchemaBootstrap bootstrap = new TalentProfileSchemaBootstrap(jdbcTemplate);

        bootstrap.run(null);

        verify(jdbcTemplate, atLeastOnce()).execute(anyString());
    }
}
