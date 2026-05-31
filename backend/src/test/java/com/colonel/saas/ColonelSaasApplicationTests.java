package com.colonel.saas;

import com.colonel.saas.service.DouyinWebhookSchemaBootstrap;
import com.colonel.saas.service.OrderPaymentSchemaBootstrap;
import com.colonel.saas.service.OrderSyncDedupSchemaBootstrap;
import com.colonel.saas.service.TalentPresetTagsBootstrap;
import com.colonel.saas.testsupport.TestDataService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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

    @MockBean
    private DouyinWebhookSchemaBootstrap douyinWebhookSchemaBootstrap;

    @MockBean
    private OrderSyncDedupSchemaBootstrap orderSyncDedupSchemaBootstrap;

    @MockBean
    private OrderPaymentSchemaBootstrap orderPaymentSchemaBootstrap;

    @MockBean
    private TalentPresetTagsBootstrap talentPresetTagsBootstrap;

    @MockBean
    private TestDataService testDataService;

    @Test
    void contextLoads() {
    }
}
