package com.colonel.saas.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DDD-SLIM-SAMPLE-001：SampleApplicationService 不再内联寄样申请校验规则。
 */
class DddSlimSample001RoutingTest {

    @Test
    @DisplayName("SampleApplicationService 申请校验委派 SampleEligibilityService")
    void sampleApplicationServiceShouldDelegateEligibilityRules() throws Exception {
        Path source = Path.of("src/main/java/com/colonel/saas/service/sample/SampleApplicationService.java");
        String content = Files.readString(source);

        assertThat(content).contains("sampleEligibilityService.classifyFailureRules(");
        assertThat(content).doesNotContain("classifyEligibilityFailures(");
    }
}
