package com.colonel.saas;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "debug=false",
        "spring.main.banner-mode=off",
        "spring.main.log-startup-info=false",
        "spring.devtools.restart.enabled=false",
        "logging.level.org.springframework=INFO",
        "logging.level.org.springframework.boot=INFO",
        "logging.level.org.springframework.boot.autoconfigure.logging.ConditionEvaluationReportLogger=ERROR",
        "logging.level.org.springframework.web=INFO"
})
@ActiveProfiles("test")
class ColonelSaasApplicationTests {

    @Test
    void contextLoads() {
    }
}
