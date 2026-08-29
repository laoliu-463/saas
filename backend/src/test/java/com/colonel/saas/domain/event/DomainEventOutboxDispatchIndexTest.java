package com.colonel.saas.domain.event;

import com.colonel.saas.testsupport.BaseIntegrationTest;
import com.colonel.saas.testsupport.DockerAvailable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DockerAvailable
class DomainEventOutboxDispatchIndexTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldHavePartialIndexForDispatchableStatuses() {
        String predicate = jdbcTemplate.queryForObject("""
                SELECT pg_get_expr(indexrel.indpred, indexrel.indrelid)
                FROM pg_index indexrel
                JOIN pg_class index_name ON index_name.oid = indexrel.indexrelid
                JOIN pg_class table_name ON table_name.oid = indexrel.indrelid
                WHERE index_name.relname = 'idx_domain_event_outbox_dispatch_order'
                  AND table_name.relname = 'domain_event_outbox'
                """, String.class);

        assertThat(predicate)
                .contains("PENDING")
                .contains("FAILED")
                .contains("status");
    }
}
