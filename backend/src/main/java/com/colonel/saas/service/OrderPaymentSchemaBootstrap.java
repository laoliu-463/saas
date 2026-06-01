package com.colonel.saas.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderPaymentSchemaBootstrap implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public OrderPaymentSchemaBootstrap(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!orderTableExists()) {
            log.warn("Skip order payment schema bootstrap because colonelsettlement_order does not exist");
            return;
        }
        jdbcTemplate.execute("""
                ALTER TABLE colonelsettlement_order
                    ADD COLUMN IF NOT EXISTS pay_time TIMESTAMP WITHOUT TIME ZONE,
                    ADD COLUMN IF NOT EXISTS order_create_time TIMESTAMP WITHOUT TIME ZONE,
                    ADD COLUMN IF NOT EXISTS estimate_service_fee BIGINT DEFAULT 0,
                    ADD COLUMN IF NOT EXISTS effective_service_fee BIGINT DEFAULT 0,
                    ADD COLUMN IF NOT EXISTS estimate_tech_service_fee BIGINT DEFAULT 0,
                    ADD COLUMN IF NOT EXISTS effective_tech_service_fee BIGINT DEFAULT 0,
                    ADD COLUMN IF NOT EXISTS flow_point VARCHAR(64)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_cso_pay_time
                    ON colonelsettlement_order(pay_time)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_cso_order_create_time
                    ON colonelsettlement_order(order_create_time)
                """);
        log.info("Order payment schema ensured");
    }

    private boolean orderTableExists() {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.colonelsettlement_order') IS NOT NULL",
                Boolean.class
        ));
    }
}
