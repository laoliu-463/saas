package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddPerformanceConfigConsumptionContractTest {

    @Test
    void commissionServiceShouldReadCommissionConfigThroughConfigDomainFacade() throws IOException {
        String service = readProjectFile("src/main/java/com/colonel/saas/service/CommissionService.java");

        assertThat(service)
                .contains(
                        "import com.colonel.saas.domain.config.facade.ConfigDomainFacade;",
                        "private final ConfigDomainFacade configDomainFacade;",
                        "SystemConfigKeys.COMMISSION_BUSINESS_DEFAULT_RATIO",
                        "SystemConfigKeys.COMMISSION_CHANNEL_DEFAULT_RATIO",
                        "configDomainFacade.getDecimal(key, DEFAULT_RATIO)",
                        "configDomainFacade.getConfig(key)");
        assertThat(service)
                .doesNotContain(
                        "BusinessRuleConfigService",
                        "SystemConfigMapper",
                        "SystemConfigChangeLogMapper",
                        "SysConfigService");
    }

    @Test
    void commissionServiceShouldKeepRuleActivityDefaultPrecedenceVisible() throws IOException {
        String service = readProjectFile("src/main/java/com/colonel/saas/service/CommissionService.java");

        assertThat(service)
                .contains(
                        "CommissionRuleService.TYPE_RECRUITER",
                        "CommissionRuleService.TYPE_CHANNEL",
                        "commissionRuleService.resolveRule",
                        "bizRatioSourceVersion",
                        "channelRatioSourceVersion",
                        "KEY_BIZ_ACTIVITY_RATIO_PREFIX",
                        "KEY_CHANNEL_ACTIVITY_RATIO_PREFIX",
                        "loadRatio(KEY_BIZ_ACTIVITY_RATIO_PREFIX, bucket.activityId(), defaultRatio)",
                        "loadRatio(KEY_CHANNEL_ACTIVITY_RATIO_PREFIX, bucket.activityId(), defaultRatio)");
    }

    @Test
    void executableEvidenceShouldCoverConfigFacadeFallbackAndRulePrecedence() throws IOException {
        String routingTest = readProjectFile(
                "src/test/java/com/colonel/saas/architecture/DddConfig003ConfigRoutingTest.java");
        String commissionTest = readProjectFile(
                "src/test/java/com/colonel/saas/service/CommissionServiceTest.java");

        assertThat(routingTest)
                .contains(
                        "commissionRates_shouldReadFromConfigDomainFacade",
                        "commissionRates_shouldFallbackWhenConfigMissing",
                        "commissionService_shouldNotAutoRecalculateOnConfigChange");
        assertThat(commissionTest)
                .contains(
                        "calculate_shouldUseConfigRatios",
                        "calculate_shouldUseActivitySpecificRatiosWhenConfigured",
                        "calculate_shouldMixDefaultAndActivitySpecificRatiosByActivity",
                        "calculate_shouldUseCommissionRuleRatiosBeforeLegacyActivityConfig",
                        "calculate_shouldFallbackWhenRatioQueryFails");
    }

    private static String readProjectFile(String relativePath) throws IOException {
        return Files.readString(Path.of(System.getProperty("user.dir")).resolve(relativePath))
                .replace("\r\n", "\n");
    }
}
