package com.colonel.saas.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class DouyinWebhookSchemaBootstrapTest {

    @Test
    void run_shouldEnsureWebhookInboxSchema() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DouyinWebhookSchemaBootstrap bootstrap = new DouyinWebhookSchemaBootstrap(jdbcTemplate);

        bootstrap.run(null);

        verify(jdbcTemplate).execute(contains("CREATE TABLE IF NOT EXISTS douyin_webhook_event"));
        verify(jdbcTemplate).execute(contains("CREATE UNIQUE INDEX IF NOT EXISTS uk_douyin_webhook_event_key"));
        verify(jdbcTemplate).execute(contains("CREATE INDEX IF NOT EXISTS idx_douyin_webhook_event_status"));
        verify(jdbcTemplate).execute(contains("CREATE INDEX IF NOT EXISTS idx_douyin_webhook_event_type"));
        verify(jdbcTemplate, times(4)).execute(org.mockito.ArgumentMatchers.anyString());
    }
}
