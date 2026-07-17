package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddSampleStateMachineIntegrationClosureContractTest {

    @Test
    void sampleStateMachineShouldStayIntegratedWithHttpAndCommandEntrypoints() throws IOException {
        String stateMachine = readProjectFile("backend/src/main/java/com/colonel/saas/domain/sample/policy/SampleStateMachine.java");
        String controller = readProjectFile("backend/src/main/java/com/colonel/saas/controller/SampleController.java");
        String sampleService = readProjectFile("backend/src/main/java/com/colonel/saas/service/sample/SampleApplicationService.java");
        String lifecycle = readProjectFile("backend/src/main/java/com/colonel/saas/service/SampleLifecycleService.java");

        assertThat(stateMachine)
                .contains("case \"APPROVED\" -> \"PENDING_SHIP\"")
                .contains("case \"SHIPPED\" -> \"SHIPPING\"")
                .contains("case \"SIGNED\", \"PENDING_TASK\" -> \"PENDING_HOMEWORK\"")
                .contains("case \"FINISHED\" -> \"COMPLETED\"")
                .contains("ensureTransition(SampleStatus current, SampleStatus expected)")
                .contains("ensurePendingHomeworkTransition(SampleStatus current)")
                .contains("isDeletable(SampleStatus status)");

        assertThat(controller)
                .contains("@GetMapping(\"/status-transitions\")")
                .contains("@PutMapping(\"/{id:[0-9a-fA-F\\\\-]{36}}/status\")")
                .contains("@PostMapping(\"/batch-approve\")")
                .contains("@PostMapping(\"/batch-reject\")")
                .contains("@PostMapping(\"/batch-ship\")");

        assertThat(sampleService)
                .contains("public ApiResult<List<SampleStatusTransitionVO>> getStatusTransitions()")
                .contains("public ApiResult<SampleVO> actionSample(")
                .contains("String action = SampleStateMachine.normalizeAction(request.getAction())")
                .contains("SampleStateMachine.ensureTransition(current, SampleStatus.PENDING_AUDIT)")
                .contains("SampleStateMachine.ensureTransition(current, SampleStatus.PENDING_SHIP)")
                .contains("SampleStateMachine.ensurePendingHomeworkTransition(current)")
                .contains("SampleStateMachine.ensureTransition(current, SampleStatus.PENDING_HOMEWORK)")
                .contains("ensureActionRolePermission(action, roleCodes)")
                .contains("public ApiResult<Map<String, Integer>> batchApprove(")
                .contains("public ApiResult<Map<String, Integer>> batchReject(")
                .contains("public ApiResult<Map<String, Integer>> batchShip(")
                .contains("publishActionDomainEvent(")
                .contains("sampleDomainEventPublisher.publishSampleApproved")
                .contains("sampleDomainEventPublisher.publishSampleRejected")
                .contains("sampleDomainEventPublisher.publishSampleShipped")
                .contains("sampleDomainEventPublisher.publishSampleSigned")
                .contains("sampleDomainEventPublisher.publishSampleCompleted")
                .contains("sampleDomainEventPublisher.publishSampleClosed");

        assertThat(lifecycle)
                .contains("completePendingHomeworkByOrder(ColonelsettlementOrder order)")
                .contains("autoCloseTimeoutPendingHomework")
                .contains("autoCloseTimeoutPendingShip")
                .contains("transitionSamples(")
                .contains("STATUS_PENDING_HOMEWORK")
                .contains("STATUS_COMPLETED")
                .contains("STATUS_CLOSED");
    }

    @Test
    void sampleStateMachineShouldHaveExecutableIntegrationEvidence() throws IOException {
        String stateMachineTest = readProjectFile("backend/src/test/java/com/colonel/saas/domain/sample/policy/SampleStateMachineTest.java");
        String controllerTest = readProjectFile("backend/src/test/java/com/colonel/saas/controller/SampleControllerTest.java");
        String lifecycleTest = readProjectFile("backend/src/test/java/com/colonel/saas/service/SampleLifecycleServiceTest.java");

        assertThat(stateMachineTest)
                .contains("normalizeAction_shouldMapLegacyAliases")
                .contains("ensureTransition_shouldRejectInvalidFromStatus")
                .contains("ensurePendingHomeworkTransition_shouldAllowShippingOrDelivered")
                .contains("isDeletable_shouldOnlyAllowPendingAuditOrRejected");

        assertThat(controllerTest)
                .contains("getStatusTransitions_shouldExposeRoleStateAndErrorMatrix")
                .contains("actionSample_shouldAllowBizLeaderApproveFromPendingAudit")
                .contains("actionSample_shouldAllowShippingFromPendingShip")
                .contains("actionSample_shouldAllowOpsSignDirectlyToPendingHomework")
                .contains("actionSample_shouldAllowOpsMoveDeliveredToPendingHomeworkWithoutOverwritingSource")
                .contains("actionSample_shouldRejectOpsCompleteAction")
                .contains("actionSample_shouldReportExpectedAndActualStatusWhenTransitionInvalid")
                .contains("actionSample_shouldAllowRejectFromPendingAudit")
                .contains("actionSample_shouldRejectInvalidTransition")
                .contains("batchApprove_shouldCountSuccessAndFailures")
                .contains("batchReject_shouldRequireRemark")
                .contains("batchReject_shouldCountSuccessAndFailures")
                .contains("batchShip_shouldCountSuccessAndFailuresAndMarkManualLogistics");

        assertThat(lifecycleTest)
                .contains("completePendingHomeworkByOrder_shouldCompleteMatchedRequests")
                .contains("completePendingHomeworkByOrder_shouldCompleteOnlyOldestMatchedRequest")
                .contains("completePendingHomeworkByOrder_shouldSkipWhenRequiredOrderFieldsMissing")
                .contains("autoCloseTimeoutPendingHomework_shouldCloseTimedOutRequests")
                .contains("autoCloseTimeoutPendingHomework_shouldUseConfiguredTimeout")
                .contains("autoCloseTimeoutPendingShip_shouldUseConfiguredTimeout")
                .contains("autoCloseTimeoutPendingShip_shouldUseDynamicCloseReason");
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
