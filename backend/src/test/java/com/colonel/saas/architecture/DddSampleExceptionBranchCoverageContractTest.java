package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddSampleExceptionBranchCoverageContractTest {

    @Test
    void sampleExceptionBranchesShouldStayBackedByExplicitSourceGuards() throws IOException {
        String sampleService = readProjectFile("backend/src/main/java/com/colonel/saas/service/sample/SampleApplicationService.java");
        String lifecycle = readProjectFile("backend/src/main/java/com/colonel/saas/service/SampleLifecycleService.java");
        String listener = readProjectFile("backend/src/main/java/com/colonel/saas/listener/SampleOrderSyncedHomeworkListener.java");
        String bridge = readProjectFile("backend/src/main/java/com/colonel/saas/domain/order/application/OrderSampleHomeworkBridge.java");
        String stateMachine = readProjectFile("backend/src/main/java/com/colonel/saas/domain/sample/policy/SampleStateMachine.java");

        assertThat(sampleService)
                .contains("throw BusinessException.param(\"reason is required when reject sample request\")")
                .contains("throw BusinessException.param(\"trackingNo is required when shipping\")")
                .contains("throw BusinessException.param(\"Unsupported action: \" + request.getAction())")
                .contains("throw BusinessException.param(\"remark is required when batch reject\")")
                .contains("catch (BusinessException | ForbiddenException e)")
                .contains("Batch approve failed for requestNo={}")
                .contains("Batch reject failed for requestNo={}")
                .contains("Batch ship failed for requestNo={}")
                .contains("throw BusinessException.param(\"Invalid status: \" + status)")
                .contains("throw new ForbiddenException(\"运营仅可查看待发货及后续物流寄样单\")");

        assertThat(lifecycle)
                .contains("@Transactional(rollbackFor = Exception.class)")
                .contains("completePendingHomeworkByOrder(ColonelsettlementOrder order)")
                .contains("autoCloseTimeoutPendingHomework")
                .contains("autoCloseTimeoutPendingShip")
                .contains("return List.of()")
                .contains("catch (Exception ex)")
                .contains("STATUS_PENDING_HOMEWORK");

        assertThat(listener)
                .contains("event == null || !orderSampleHomeworkBridge.isEventDrivenHomeworkEnabled()")
                .contains("orderSampleHomeworkBridge.completeHomeworkForSyncedOrder(event)")
                .contains("catch (Exception ex)")
                .contains("Sample homework event handling failed");

        assertThat(bridge)
                .contains("if (!isEventDrivenHomeworkEnabled() || event == null)")
                .contains("if (order == null)")
                .contains("int completed = sampleHomeworkFacade.completePendingHomeworkByOrder(order)")
                .contains("if (completed > 0)");

        assertThat(stateMachine)
                .contains("String message = String.format(")
                .contains("throw BusinessException.stateInvalid(message)")
                .contains("throw BusinessException.stateInvalid(\"Only pending/rejected sample can be deleted\")");
    }

    @Test
    void sampleUnitAndExceptionBranchesShouldHaveCurrentExecutableEvidence() throws IOException {
        String controllerTest = readProjectFile("backend/src/test/java/com/colonel/saas/controller/SampleControllerTest.java");
        String lifecycleTest = readProjectFile("backend/src/test/java/com/colonel/saas/service/SampleLifecycleServiceTest.java");
        String listenerTest = readProjectFile("backend/src/test/java/com/colonel/saas/listener/SampleOrderSyncedHomeworkListenerTest.java");
        String bridgeTest = readProjectFile("backend/src/test/java/com/colonel/saas/domain/order/application/OrderSampleHomeworkBridgeTest.java");
        String stateMachineTest = readProjectFile("backend/src/test/java/com/colonel/saas/domain/sample/policy/SampleStateMachineTest.java");
        String permissionTest = readProjectFile("backend/src/test/java/com/colonel/saas/domain/sample/policy/SampleActionPermissionPolicyTest.java");

        assertThat(controllerTest)
                .contains("createSample_shouldRejectWhenDuplicateWithinSevenDays")
                .contains("createSample_shouldRejectWhenSnapshotProductIsNotSelectedToLibrary")
                .contains("createSample_shouldRejectOpsStaff")
                .contains("getSampleById_shouldRejectCrossUserAccessInPersonalScope")
                .contains("actionSample_shouldRejectInvalidTransition")
                .contains("actionSample_shouldReportExpectedAndActualStatusWhenTransitionInvalid")
                .contains("actionSample_shouldRejectOpsCompleteAction")
                .contains("batchApprove_shouldCountSuccessAndFailures")
                .contains("batchReject_shouldRequireRemark")
                .contains("batchReject_shouldCountSuccessAndFailures")
                .contains("batchShip_shouldCountSuccessAndFailuresAndMarkManualLogistics")
                .contains("syncAllLogistics_shouldReturnSummaryAndRejectUnauthorizedRole")
                .contains("deleteSample_shouldRejectNonPendingOrRejectedSample")
                .contains("privateRoleAndStatusHelpers_shouldNormalizeAliasesAndPermissions");

        assertThat(lifecycleTest)
                .contains("automaticLifecycleEntrypoints_shouldRollbackOnCheckedExceptions")
                .contains("completePendingHomeworkByOrder_shouldSkipWhenRequiredOrderFieldsMissing")
                .contains("completePendingHomeworkByOrder_shouldSkipWhenNoTalentUid")
                .contains("completePendingHomeworkByOrder_shouldUseAuthorIdAndFilterInvalidRowsAndSamples")
                .contains("autoCloseTimeoutPendingHomework_shouldUseConfiguredTimeout")
                .contains("autoCloseTimeoutPendingShip_shouldUseConfiguredTimeout")
                .contains("privateDefensiveHelpers_shouldHandleNullEmptyAndNonListCollections");

        assertThat(listenerTest)
                .contains("onOrderSynced_shouldSkipNullEventWithoutTouchingBridge")
                .contains("onOrderSynced_shouldSkipWhenEventDrivenHomeworkIsDisabled")
                .contains("onOrderSynced_shouldSwallowBridgeFailure");

        assertThat(bridgeTest)
                .contains("completeHomework_whenSwitchOff_shouldDoNothing")
                .contains("completeHomework_whenDuplicateOrderDoesNotCompleteSample_shouldNotWriteCompletionLog")
                .contains("completeHomework_whenSameOrderEventRepeated_shouldWriteCompletionLogOnlyOnce")
                .contains("completeHomework_whenOrderMissing_shouldSkipHomework");

        assertThat(stateMachineTest)
                .contains("ensureTransition_shouldRejectInvalidFromStatus")
                .contains("isDeletable_shouldOnlyAllowPendingAuditOrRejected");

        assertThat(permissionTest)
                .contains("applyAndDelete_shouldAllowApplicantRolesOrAdmin")
                .contains("actionTransition_shouldKeepReviewAndLogisticsRoleGroups")
                .contains("exportAndLogisticsSync_shouldKeepCurrentRoleGroups")
                .contains("logisticsImport_shouldKeepImportAndOverwriteRoleGroups");
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
