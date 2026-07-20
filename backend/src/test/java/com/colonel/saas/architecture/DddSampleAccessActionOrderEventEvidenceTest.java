package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddSampleAccessActionOrderEventEvidenceTest {

    @Test
    void sampleListAndDetailDataScopeNegativeEvidenceShouldStayConnected() throws IOException {
        String sampleService = readProjectFile("backend/src/main/java/com/colonel/saas/service/sample/SampleApplicationService.java");
        String controllerTest = readProjectFile("backend/src/test/java/com/colonel/saas/controller/SampleControllerTest.java");
        String boundaryTest = readProjectFile("backend/src/test/java/com/colonel/saas/architecture/DddUserDataScopePolicySampleApplicationBoundaryTest.java");

        assertThat(sampleService)
                .contains("public ApiResult<PageResult<SampleVO>> getSamplePage(")
                .contains("public ApiResult<SampleVO> getSampleById(")
                .contains("findPageForAuditor(")
                .contains("canAccessSampleByDataScopeLegacy")
                .contains("canAccessSampleByDataScopeWithPolicy")
                .contains("throw new ForbiddenException");

        assertThat(controllerTest)
                .contains("getSampleById_shouldRejectCrossUserAccessInPersonalScope")
                .contains("getSampleById_dataScopePolicyEnabledPath_shouldDelegatePersonalAccessDecisionToUserPolicy")
                .contains("getSampleById_shouldRejectPersonalBizStaffWhenProductHasNoSourceMapping")
                .contains("getSamplePage_shouldUseAuditorQueryForPersonalBizStaffDefaultPendingAudit")
                .contains("getSamplePage_dataScopePolicyEnabledPath_shouldKeepLegacyAuditorQueryWhenPersonalScopeMissesUserContext")
                .contains("verify(sampleRequestMapper).findPageForAuditor");

        assertThat(boundaryTest)
                .contains("DataScopeResolver")
                .contains("dataScopeResolver.resolve")
                .contains("resolved.missingUser()")
                .contains("resolved.contextSatisfied()")
                .contains("resolved.filtersUser()")
                .contains("resolved.filtersDept()")
                .contains(".doesNotContain(\"dataScopePolicy.\")");
    }

    @Test
    void sampleReviewAndShippingActionPermissionEvidenceShouldStayConnected() throws IOException {
        String controller = readProjectFile("backend/src/main/java/com/colonel/saas/controller/SampleController.java");
        String sampleService = readProjectFile("backend/src/main/java/com/colonel/saas/service/sample/SampleApplicationService.java");
        String policy = readProjectFile("backend/src/main/java/com/colonel/saas/domain/sample/policy/SampleActionPermissionPolicy.java");
        String controllerTest = readProjectFile("backend/src/test/java/com/colonel/saas/controller/SampleControllerTest.java");
        String policyTest = readProjectFile("backend/src/test/java/com/colonel/saas/domain/sample/policy/SampleActionPermissionPolicyTest.java");

        assertThat(controller)
                .contains("@PostMapping(\"/batch-approve\")")
                .contains("@PostMapping(\"/batch-reject\")")
                .contains("@PostMapping(\"/batch-ship\")")
                .contains("@RequirePermission(\"sample:batch-approve\")")
                .contains("@RequirePermission(\"sample:batch-reject\")")
                .contains("@RequirePermission(\"sample:batch-ship\")");

        assertThat(sampleService)
                .contains("ensureActionRolePermission(\"PENDING_SHIP\", roleCodes)")
                .contains("ensureActionRolePermission(\"REJECTED\", roleCodes)")
                .contains("ensureActionRolePermission(\"SHIPPING\", roleCodes)")
                .contains("sampleActionPermissionPolicy.ensureCanPerformAction(action, roleCodes)");

        assertThat(policy)
                .contains("case \"PENDING_SHIP\", \"REJECTED\" -> ensureReviewAction(roleCodes)")
                .contains("case \"SHIPPING\", \"DELIVERED\", \"PENDING_HOMEWORK\" -> ensureLogisticsAction(roleCodes)")
                .contains("case \"COMPLETED\", \"CLOSED\" -> throw new ForbiddenException");

        assertThat(controllerTest)
                .contains("sensitiveSampleBatchAndExportEndpoints_shouldDeclareStablePermissions")
                .contains("actionSample_shouldAllowChannelStaffAuditAction")
                .contains("actionSample_shouldAllowBizLeaderApproveFromPendingAudit")
                .contains("actionSample_shouldRejectOpsCompleteAction")
                .contains("batchApprove_shouldCountSuccessAndFailures")
                .contains("batchReject_shouldRequireRemark")
                .contains("batchShip_shouldCountSuccessAndFailuresAndMarkManualLogistics");

        assertThat(policyTest)
                .contains("actionTransition_shouldKeepReviewAndLogisticsRoleGroups")
                .contains("policy.ensureCanPerformAction(\"REJECTED\", List.of(RoleCodes.OPS_STAFF))")
                .contains("policy.ensureCanPerformAction(\"PENDING_HOMEWORK\", List.of(RoleCodes.BIZ_STAFF))")
                .contains("policy.ensureCanPerformAction(\"COMPLETED\", List.of(RoleCodes.ADMIN))");
    }

    @Test
    void sampleOrderSyncedConsumerEvidenceShouldStayConnected() throws IOException {
        String listener = readProjectFile("backend/src/main/java/com/colonel/saas/listener/SampleOrderSyncedHomeworkListener.java");
        String bridge = readProjectFile("backend/src/main/java/com/colonel/saas/domain/order/application/OrderSampleHomeworkBridge.java");
        String lifecycle = readProjectFile("backend/src/main/java/com/colonel/saas/service/SampleLifecycleService.java");
        String contractTest = readProjectFile("backend/src/test/java/com/colonel/saas/architecture/DddSampleOrderEventConsumptionClosureContractTest.java");
        String listenerTest = readProjectFile("backend/src/test/java/com/colonel/saas/listener/SampleOrderSyncedHomeworkListenerTest.java");
        String bridgeTest = readProjectFile("backend/src/test/java/com/colonel/saas/domain/order/application/OrderSampleHomeworkBridgeTest.java");
        String lifecycleTest = readProjectFile("backend/src/test/java/com/colonel/saas/service/SampleLifecycleServiceTest.java");

        assertThat(listener)
                .contains("@EventListener")
                .contains("public void onOrderSynced(OrderSyncedEvent event)")
                .contains("orderSampleHomeworkBridge.completeHomeworkForSyncedOrder(event)")
                .contains("Sample homework event handling failed");

        assertThat(bridge)
                .contains("SampleHomeworkFacade")
                .contains("resolveOrder(event)")
                .contains("int completed = sampleHomeworkFacade.completePendingHomeworkByOrder(order)")
                .contains("if (completed > 0)")
                .doesNotContain("SampleLifecycleService");

        assertThat(lifecycle)
                .contains("public int completePendingHomeworkByOrder(ColonelsettlementOrder order)")
                .contains("STATUS_PENDING_HOMEWORK")
                .contains("STATUS_COMPLETED")
                .contains("sampleDomainEventPublisher.publishSampleCompleted")
                .contains("LIMIT 1");

        assertThat(contractTest)
                .contains("sampleHomeworkOrderEventConsumerShouldStayOnListenerBridgeFacadeLifecyclePath")
                .contains("sampleHomeworkOrderEventConsumerShouldHaveExecutableTestCoverage");

        assertThat(listenerTest)
                .contains("onOrderSynced_shouldDelegateToHomeworkBridgeWhenEnabled")
                .contains("onOrderSynced_shouldSwallowBridgeFailure");

        assertThat(bridgeTest)
                .contains("completeHomework_whenSwitchOn_shouldCompleteHomeworkAndLog")
                .contains("completeHomework_whenOrderMissing_shouldSkipHomework")
                .contains("completeHomework_whenSameOrderEventRepeated_shouldWriteCompletionLogOnlyOnce");

        assertThat(lifecycleTest)
                .contains("completePendingHomeworkByOrder_shouldCompleteMatchedRequests")
                .contains("completePendingHomeworkByOrder_shouldCompleteOnlyOldestMatchedRequest")
                .contains("completePendingHomeworkByOrder_shouldUseAuthorIdAndFilterInvalidRowsAndSamples");
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
