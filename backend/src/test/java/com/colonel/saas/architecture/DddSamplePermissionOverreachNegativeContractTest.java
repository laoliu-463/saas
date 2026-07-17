package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DddSamplePermissionOverreachNegativeContractTest {

    @Test
    void samplePermissionRuntimeGuardsShouldStayConnectedToPolicyControllerAndService() throws IOException {
        String policy = readProjectFile("backend/src/main/java/com/colonel/saas/domain/sample/policy/SampleActionPermissionPolicy.java");
        String controller = readProjectFile("backend/src/main/java/com/colonel/saas/controller/SampleController.java");
        String sampleService = readProjectFile("backend/src/main/java/com/colonel/saas/service/sample/SampleApplicationService.java");
        String policyTest = readProjectFile("backend/src/test/java/com/colonel/saas/domain/sample/policy/SampleActionPermissionPolicyTest.java");

        assertThat(policy)
                .contains("public void ensureCanApply(Object roleCodes)")
                .contains("public void ensureCanDelete(Object roleCodes)")
                .contains("public void ensureCanPerformAction(String action, Object roleCodes)")
                .contains("public void ensureCanSyncLogistics(Object roleCodes)")
                .contains("public void ensureCanExport(Object roleCodes)");

        assertThat(controller)
                .contains("@RequireRoles({RoleCodes.ADMIN, RoleCodes.OPS_STAFF})")
                .contains("@RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF})")
                .contains("@RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.OPS_STAFF})")
                .contains("@PostMapping(\"/batch-approve\")")
                .contains("@PostMapping(\"/batch-reject\")")
                .contains("@PostMapping(\"/batch-ship\")")
                .contains("@GetMapping(\"/exports\")");

        assertThat(sampleService)
                .contains("ensureActionRolePermission(\"PENDING_SHIP\", roleCodes)")
                .contains("ensureActionRolePermission(\"REJECTED\", roleCodes)")
                .contains("ensureActionRolePermission(\"SHIPPING\", roleCodes)")
                .contains("sampleActionPermissionPolicy.ensureCanApply(roleCodes)")
                .contains("sampleActionPermissionPolicy.ensureCanDelete(roleCodes)")
                .contains("sampleActionPermissionPolicy.ensureCanPerformAction(action, roleCodes)")
                .contains("sampleActionPermissionPolicy.ensureCanSyncLogistics(roleCodes)")
                .contains("sampleActionPermissionPolicy.ensureCanExport(roleCodes)");

        assertThat(policyTest)
                .contains("policy.ensureCanApply(List.of(RoleCodes.BIZ_STAFF))")
                .contains("policy.ensureCanDelete(List.of(RoleCodes.OPS_STAFF))")
                .contains("policy.ensureCanPerformAction(\"REJECTED\", List.of(RoleCodes.OPS_STAFF))")
                .contains("policy.ensureCanPerformAction(\"PENDING_HOMEWORK\", List.of(RoleCodes.BIZ_STAFF))")
                .contains("policy.ensureCanPerformAction(\"COMPLETED\", List.of(RoleCodes.ADMIN))")
                .contains("policy.ensureCanSyncLogistics(List.of(RoleCodes.BIZ_STAFF))")
                .contains("policy.ensureCanExport(List.of(RoleCodes.CHANNEL_STAFF))");
    }

    @Test
    void sampleDomainShouldNotSynchronizeOrderFactsDirectly() throws IOException {
        String sampleSources = sampleProductionSources();
        String bridge = readProjectFile("backend/src/main/java/com/colonel/saas/domain/order/application/OrderSampleHomeworkBridge.java");
        String lifecycle = readProjectFile("backend/src/main/java/com/colonel/saas/service/SampleLifecycleService.java");

        assertThat(sampleSources)
                .doesNotContain("OrderSyncService")
                .doesNotContain("OrderSyncApplicationService")
                .doesNotContain("OrderSyncPersistenceService")
                .doesNotContain("DouyinOrderGateway")
                .doesNotContain("OrderApi")
                .doesNotContain("OrderDomainEventPublisher")
                .doesNotContain("InProcessOrderDomainEventPublisher");

        assertThat(bridge)
                .contains("OrderSyncedEvent")
                .contains("SampleHomeworkFacade")
                .contains("completeHomeworkForSyncedOrder");
        assertThat(lifecycle)
                .contains("completePendingHomeworkByOrder(ColonelsettlementOrder order)")
                .contains("sampleDomainEventPublisher.publishSampleCompleted");
    }

    @Test
    void sampleDomainShouldNotCalculatePerformanceAttribution() throws IOException {
        String sampleSources = sampleProductionSources();

        assertThat(sampleSources)
                .doesNotContain("PerformanceCalculationService")
                .doesNotContain("PerformanceCalculationApplicationService")
                .doesNotContain("PerformanceRecordSyncListener")
                .doesNotContain("PerformanceRecordMapper")
                .doesNotContain("PerformanceRecord")
                .doesNotContain("PerformanceAttributionPolicy")
                .doesNotContain("OrderAttributionService")
                .doesNotContain("OrderDefaultAttributionResolver")
                .doesNotContain("finalChannelUserId")
                .doesNotContain("finalRecruiterUserId");
    }

    @Test
    void sampleDomainShouldNotCalculateCommissionRefundOrReversal() throws IOException {
        String sampleSources = sampleProductionSources();

        assertThat(sampleSources)
                .doesNotContain("CommissionService")
                .doesNotContain("CommissionRuleService")
                .doesNotContain("CommissionRuleMapper")
                .doesNotContain("CommissionRule")
                .doesNotContain("OrderCommissionPolicy")
                .doesNotContain("OrderRefundFactSyncedEvent")
                .doesNotContain("refundAmount")
                .doesNotContain("correctionType")
                .doesNotContain("REVERSAL")
                .doesNotContain("reversed");
    }

    private static String sampleProductionSources() throws IOException {
        StringBuilder builder = new StringBuilder();
        for (Path file : sampleProductionFiles()) {
            builder.append("\n// ").append(repoRoot().relativize(file)).append("\n");
            builder.append(Files.readString(file).replace("\r\n", "\n"));
        }
        return builder.toString();
    }

    private static List<Path> sampleProductionFiles() throws IOException {
        Path root = repoRoot();
        List<Path> files = new ArrayList<>();
        addJavaFiles(root.resolve("backend/src/main/java/com/colonel/saas/domain/sample"), files);
        addJavaFiles(root.resolve("backend/src/main/java/com/colonel/saas/service/sample"), files);
        addJavaFiles(root.resolve("backend/src/main/java/com/colonel/saas/listener"), files, "Sample");
        addJavaFiles(root.resolve("backend/src/main/java/com/colonel/saas/service"), files, "Sample");
        return files;
    }

    private static void addJavaFiles(Path directory, List<Path> files) throws IOException {
        addJavaFiles(directory, files, null);
    }

    private static void addJavaFiles(Path directory, List<Path> files, String fileNamePrefix) throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(directory)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(path -> fileNamePrefix == null
                            || path.getFileName().toString().startsWith(fileNamePrefix))
                    .forEach(files::add);
        }
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
