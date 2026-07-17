package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddPerformanceAttributionTraceabilityContractTest {

    @Test
    void calculationShouldMapCurrentOrderAttributionInputsToPerformanceRecord() throws IOException {
        String service = readProjectFile(
                "src/main/java/com/colonel/saas/domain/performance/application/PerformanceCalculationApplicationService.java");

        assertThat(service)
                .contains(
                        "UUID channelUserId = order.getChannelUserId();",
                        "UUID recruiterUserId = order.getColonelUserId();",
                        "PerformanceAttributionPolicy.AttributionResult attribution = attributionResolver.resolve(order);",
                        "record.setDefaultChannelUserId(channelUserId);",
                        "record.setDefaultRecruiterUserId(recruiterUserId);",
                        "record.setFinalChannelUserId(attribution.finalChannelId());",
                        "record.setFinalRecruiterUserId(attribution.finalRecruiterId());",
                        "record.setChannelAttribution(attribution.channelAttributionType());",
                        "record.setRecruiterAttribution(attribution.recruiterAttributionType());",
                        "record.setTalentId(order.getTalentId());",
                        "record.setPartnerId(order.getShopId());",
                        "record.setProductId(order.getProductId());",
                        "record.setActivityId(order.getActivityId());");
    }

    @Test
    void performanceRecordMapperShouldPersistTraceableAttributionColumns() throws IOException {
        String mapper = readProjectFile("src/main/resources/mapper/PerformanceRecordMapper.xml");

        assertThat(mapper)
                .contains(
                        "property=\"defaultChannelUserId\" column=\"default_channel_user_id\"",
                        "property=\"defaultRecruiterUserId\" column=\"default_recruiter_user_id\"",
                        "property=\"finalChannelUserId\" column=\"final_channel_user_id\"",
                        "property=\"finalRecruiterUserId\" column=\"final_recruiter_user_id\"",
                        "property=\"channelAttribution\" column=\"channel_attribution\"",
                        "property=\"recruiterAttribution\" column=\"recruiter_attribution\"",
                        "property=\"talentId\" column=\"talent_id\"",
                        "property=\"partnerId\" column=\"partner_id\"",
                        "property=\"productId\" column=\"product_id\"",
                        "property=\"activityId\" column=\"activity_id\"",
                        "default_channel_user_id = EXCLUDED.default_channel_user_id",
                        "default_recruiter_user_id = EXCLUDED.default_recruiter_user_id",
                        "final_channel_user_id = EXCLUDED.final_channel_user_id",
                        "final_recruiter_user_id = EXCLUDED.final_recruiter_user_id",
                        "channel_attribution = EXCLUDED.channel_attribution",
                        "recruiter_attribution = EXCLUDED.recruiter_attribution");
    }

    @Test
    void executableUnitEvidenceShouldPinAttributionInputTraceability() throws IOException {
        String test = readProjectFile(
                "src/test/java/com/colonel/saas/domain/performance/application/PerformanceCalculationApplicationServiceTest.java");

        assertThat(test)
                .contains(
                        "upsertFromOrder_shouldPreserveTraceableAttributionInputsOnPerformanceRecord",
                        "order.setChannelUserId(channelUserId)",
                        "order.setColonelUserId(recruiterUserId)",
                        "order.setUserId(fallbackUserId)",
                        "order.setTalentId(talentId)",
                        "order.setShopId(90000001L)",
                        "order.setProductId(\"PROD-TRACE-1\")",
                        "order.setActivityId(\"ACT-TRACE-1\")",
                        "assertThat(result.getDefaultChannelUserId()).isEqualTo(channelUserId)",
                        "assertThat(result.getDefaultRecruiterUserId()).isEqualTo(recruiterUserId)",
                        "assertThat(result.getFinalChannelUserId()).isEqualTo(channelUserId)",
                        "assertThat(result.getFinalRecruiterUserId()).isEqualTo(recruiterUserId)",
                        "upsertFromOrder_shouldNotUseLegacyChannelUserAsRecruiter",
                        "assertThat(result.getDefaultRecruiterUserId()).isNull()",
                        "upsertFromOrder_shouldUseResolvedFinalAttributionInsteadOfCopyingOrderDefaults",
                        "assertThat(result.getFinalRecruiterUserId()).isEqualTo(exclusiveMerchantUserId)",
                        "assertThat(result.getRecruiterAttribution()).isEqualTo(\"EXCLUSIVE_MERCHANT\")");
    }

    private static String readProjectFile(String relativePath) throws IOException {
        return Files.readString(Path.of(System.getProperty("user.dir")).resolve(relativePath))
                .replace("\r\n", "\n");
    }
}
