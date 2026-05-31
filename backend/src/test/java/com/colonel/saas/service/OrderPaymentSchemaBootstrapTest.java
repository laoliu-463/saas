package com.colonel.saas.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OrderPaymentSchemaBootstrapTest {

    @Test
    void run_shouldEnsureOrderPaymentColumnsWithoutBackfillingPayTime() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        OrderPaymentSchemaBootstrap bootstrap = new OrderPaymentSchemaBootstrap(jdbcTemplate);

        bootstrap.run(null);

        verify(jdbcTemplate).execute(contains("ADD COLUMN IF NOT EXISTS pay_time"));
        verify(jdbcTemplate).execute(contains("ADD COLUMN IF NOT EXISTS order_create_time"));
        verify(jdbcTemplate).execute(contains("ADD COLUMN IF NOT EXISTS estimate_service_fee"));
        verify(jdbcTemplate).execute(contains("ADD COLUMN IF NOT EXISTS effective_tech_service_fee"));
        verify(jdbcTemplate).execute(contains("CREATE INDEX IF NOT EXISTS idx_cso_pay_time"));
        verify(jdbcTemplate).execute(contains("CREATE INDEX IF NOT EXISTS idx_cso_order_create_time"));
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, org.mockito.Mockito.times(3)).execute(sqlCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(sqlCaptor.getAllValues())
                .allMatch(sql -> !sql.toLowerCase().contains("update colonelsettlement_order"));
    }
}
