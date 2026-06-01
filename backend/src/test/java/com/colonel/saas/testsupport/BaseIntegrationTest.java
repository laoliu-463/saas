package com.colonel.saas.testsupport;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "debug=false",
        "spring.main.banner-mode=off",
        "spring.main.log-startup-info=false",
        "spring.devtools.restart.enabled=false",
        "spring.task.scheduling.enabled=false",
        "app.domain-event.dispatch-enabled=false",
        "douyin.webhook.replay.enabled=false",
        "logging.level.org.springframework=INFO",
        "logging.level.org.springframework.boot=INFO",
        "logging.level.org.springframework.boot.autoconfigure.logging.ConditionEvaluationReportLogger=ERROR",
        "logging.level.org.springframework.web=INFO"
})
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class BaseIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("colonel_saas_test")
                    .withUsername("test")
                    .withPassword("test")
                    .withInitScript("db/mapper-integration-schema.sql");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanMapperIntegrationTables() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    sys_user,
                    sys_dept,
                    promotion_link,
                    performance_records,
                    colonel_partner
                RESTART IDENTITY CASCADE
                """);
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
