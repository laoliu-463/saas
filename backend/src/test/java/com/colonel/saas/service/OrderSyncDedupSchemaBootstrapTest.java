package com.colonel.saas.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class OrderSyncDedupSchemaBootstrapTest {

    @Test
    void run_shouldEnsureOrderSyncDedupSchema() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        OrderSyncDedupSchemaBootstrap bootstrap = new OrderSyncDedupSchemaBootstrap(jdbcTemplate);

        bootstrap.run(null);

        verify(jdbcTemplate).execute(contains("CREATE TABLE IF NOT EXISTS order_sync_dedup_claim"));
        verify(jdbcTemplate).execute(contains("CREATE INDEX IF NOT EXISTS idx_order_sync_dedup_claim_row_id"));
        verify(jdbcTemplate).execute(contains("INSERT INTO order_sync_dedup_claim"));
        verify(jdbcTemplate, times(3)).execute(org.mockito.ArgumentMatchers.anyString());
    }
}
