package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DddSampleLogisticsApiBoundaryTest {

    @Test
    void sampleCoreShouldNotCallExternalLogisticsGatewayDirectly() throws IOException {
        String coreSources = readJoined(List.of(
                "backend/src/main/java/com/colonel/saas/controller/SampleController.java",
                "backend/src/main/java/com/colonel/saas/service/sample/SampleApplicationService.java",
                "backend/src/main/java/com/colonel/saas/service/SampleLifecycleService.java",
                "backend/src/main/java/com/colonel/saas/domain/sample/policy/SampleStateMachine.java",
                "backend/src/main/java/com/colonel/saas/domain/sample/policy/SampleActionPermissionPolicy.java"));

        assertThat(coreSources)
                .doesNotContain("LogisticsQueryGateway")
                .doesNotContain("Kuaidi100LogisticsGateway")
                .doesNotContain("LogisticsTrackCommand")
                .doesNotContain("LogisticsSubscribeCommand")
                .doesNotContain("subscribeTrack(")
                .doesNotContain("logisticsQueryGateway.query(");

        assertThat(coreSources)
                .contains("sampleLogisticsSyncService.syncOne")
                .contains("sampleLogisticsSubscriptionService.subscribeAfterShipment")
                .contains("sampleLogisticsImportService.importTrackingNumbers")
                .contains("sampleLogisticsImportService.generateTemplate");
    }

    @Test
    void externalLogisticsGatewayShouldStayInsideDedicatedLogisticsServices() throws IOException {
        String syncService = readProjectFile("backend/src/main/java/com/colonel/saas/service/SampleLogisticsSyncService.java");
        String subscriptionService = readProjectFile("backend/src/main/java/com/colonel/saas/service/SampleLogisticsSubscriptionService.java");
        String importService = readProjectFile("backend/src/main/java/com/colonel/saas/service/SampleLogisticsImportService.java");

        assertThat(syncService)
                .contains("LogisticsQueryGateway")
                .contains("LogisticsTrackCommand")
                .contains("logisticsQueryGateway.query(LogisticsTrackCommand.builder()")
                .contains("refreshShippingSamples")
                .contains("syncPendingInTransit");

        assertThat(subscriptionService)
                .contains("Kuaidi100LogisticsGateway")
                .contains("LogisticsSubscribeCommand")
                .contains("logisticsGateway.subscribeTrack(LogisticsSubscribeCommand.builder()")
                .contains("subscribeAfterShipment");

        assertThat(importService)
                .contains("importTrackingNumbers")
                .contains("generateTemplate")
                .contains("sampleRequestMapper.updateById(sample)")
                .doesNotContain("LogisticsQueryGateway")
                .doesNotContain("Kuaidi100LogisticsGateway")
                .doesNotContain("LogisticsTrackCommand")
                .doesNotContain("LogisticsSubscribeCommand")
                .doesNotContain("subscribeTrack(");
    }

    @Test
    void logisticsBoundaryShouldHaveExecutableCoverage() throws IOException {
        String controllerTest = readProjectFile("backend/src/test/java/com/colonel/saas/controller/SampleControllerTest.java");
        String syncTest = readProjectFile("backend/src/test/java/com/colonel/saas/service/SampleLogisticsSyncServiceTest.java");
        String subscriptionTest = readProjectFile("backend/src/test/java/com/colonel/saas/service/SampleLogisticsSubscriptionServiceTest.java");
        String importTest = readProjectFile("backend/src/test/java/com/colonel/saas/service/SampleLogisticsImportServiceTest.java");

        assertThat(controllerTest)
                .contains("syncAllLogistics_shouldReturnSummaryAndRejectUnauthorizedRole")
                .contains("logisticsImportTemplateAndUpload_shouldDelegateToServices");

        assertThat(syncTest)
                .contains("refreshShippingSamples_noCandidates_returnsEmptySummary")
                .contains("verifyNoInteractions(")
                .contains("logisticsQueryGateway");

        assertThat(subscriptionTest)
                .contains("subscribeAfterShipment_shouldSkipWhenSubscribeDisabledOrTrackingMissing")
                .contains("subscribeAfterShipment_shouldFailWhenConcreteKuaidi100GatewayMissing")
                .contains("subscribeAfterShipment_shouldReturnFailureEvenWhenGatewayOrPersistenceThrows");

        assertThat(importTest)
                .contains("import_validRow_shouldSucceed")
                .contains("import_missingSample_shouldFailRow")
                .contains("import_shouldRejectUnauthorizedRolesAndOverwriteByNonAdmin");
    }

    private static String readJoined(List<String> relativePaths) throws IOException {
        StringBuilder builder = new StringBuilder();
        for (String relativePath : relativePaths) {
            builder.append("\n// ").append(relativePath).append("\n");
            builder.append(readProjectFile(relativePath));
        }
        return builder.toString();
    }

    private static String readProjectFile(String relativePath) throws IOException {
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
