package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddTalentOrderFacadeBoundaryTest {

    @Test
    void talentServiceShouldReadOrdersThroughOrderReadFacade() throws Exception {
        String source = readProjectFile("backend/src/main/java/com/colonel/saas/service/TalentService.java");

        assertThat(source)
                .doesNotContain("ColonelsettlementOrderMapper")
                .doesNotContain("orderMapper")
                .contains("OrderReadFacade")
                .contains("orderReadFacade.findOrdersSettledSince");
    }

    @Test
    void talentQueryServiceShouldReadOrderFactsThroughOrderReadFacade() throws Exception {
        String source = readProjectFile("backend/src/main/java/com/colonel/saas/service/TalentQueryService.java");

        assertThat(source)
                .doesNotContain("JdbcTemplate")
                .doesNotContain("jdbcTemplate")
                .doesNotContain("colonelsettlement_order")
                .doesNotContain("queryForList")
                .contains("OrderReadFacade")
                .contains("orderReadFacade.summarizeTalentOrdersByDouyinUid")
                .contains("orderReadFacade.findRecentOrdersByTalentUid");
    }

    @Test
    void talentDomainShouldNotCalculateCommissionRefundOrReversal() throws Exception {
        String talentService = readProjectFile("backend/src/main/java/com/colonel/saas/service/TalentService.java");
        String talentQueryService = readProjectFile("backend/src/main/java/com/colonel/saas/service/TalentQueryService.java");
        String talentApplication = readProjectFile("backend/src/main/java/com/colonel/saas/domain/talent/application/TalentClaimApplicationService.java")
                + readProjectFile("backend/src/main/java/com/colonel/saas/domain/talent/application/ExclusiveTalentApplicationService.java")
                + readProjectFile("backend/src/main/java/com/colonel/saas/domain/talent/application/ExclusiveTalentCheckApplicationService.java");

        assertThat(talentService + talentQueryService + talentApplication)
                .doesNotContain("CommissionService")
                .doesNotContain("CommissionRuleService")
                .doesNotContain("OrderCommissionPolicy")
                .doesNotContain("PerformanceCalculationService")
                .doesNotContain("PerformanceCalculationApplicationService")
                .doesNotContain("PerformanceRecordMapper")
                .doesNotContain("OrderRefundFactSyncedEvent")
                .doesNotContain("correctionType")
                .doesNotContain("REVERSAL")
                .doesNotContain("reversed");
    }

    private static String readProjectFile(String relativePath) throws Exception {
        return Files.readString(repoRoot().resolve(relativePath)).replace("\r\n", "\n");
    }

    private static Path repoRoot() {
        Path userDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (userDir.getFileName() != null && "backend".equals(userDir.getFileName().toString())) {
            return userDir.getParent();
        }
        return userDir;
    }
}
