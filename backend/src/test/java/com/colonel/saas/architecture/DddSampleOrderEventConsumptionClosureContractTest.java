package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddSampleOrderEventConsumptionClosureContractTest {

    @Test
    void sampleHomeworkOrderEventConsumerShouldStayOnListenerBridgeFacadeLifecyclePath() throws IOException {
        String listener = readProjectFile("backend/src/main/java/com/colonel/saas/listener/SampleOrderSyncedHomeworkListener.java");
        String bridge = readProjectFile("backend/src/main/java/com/colonel/saas/domain/order/application/OrderSampleHomeworkBridge.java");
        String facade = readProjectFile("backend/src/main/java/com/colonel/saas/domain/sample/facade/SampleHomeworkFacade.java");
        String legacyFacade = readProjectFile("backend/src/main/java/com/colonel/saas/domain/sample/facade/LegacySampleHomeworkFacade.java");
        String lifecycle = readProjectFile("backend/src/main/java/com/colonel/saas/service/SampleLifecycleService.java");

        assertThat(listener)
                .contains("@Async")
                .contains("@EventListener")
                .contains("public void onOrderSynced(OrderSyncedEvent event)")
                .contains("event == null || !orderSampleHomeworkBridge.isEventDrivenHomeworkEnabled()")
                .contains("orderSampleHomeworkBridge.completeHomeworkForSyncedOrder(event)")
                .contains("Sample homework event handling failed");

        assertThat(bridge)
                .contains("SampleHomeworkFacade")
                .contains("public boolean isEventDrivenHomeworkEnabled()")
                .contains("public void completeHomeworkForSyncedOrder(OrderSyncedEvent event)")
                .contains("resolveOrder(event)")
                .contains("orderMapper.selectById(rowId)")
                .contains("orderMapper.findByOrderId(event.orderId())")
                .contains("int completed = sampleHomeworkFacade.completePendingHomeworkByOrder(order)")
                .contains("if (completed > 0)")
                .contains("recordAttributionFollowUp(order)")
                .doesNotContain("SampleLifecycleService");

        assertThat(facade)
                .contains("interface SampleHomeworkFacade")
                .contains("int completePendingHomeworkByOrder(ColonelsettlementOrder order)");

        assertThat(legacyFacade)
                .contains("implements SampleHomeworkFacade")
                .contains("SampleLifecycleService")
                .contains("return sampleLifecycleService.completePendingHomeworkByOrder(order)");

        assertThat(lifecycle)
                .contains("public int completePendingHomeworkByOrder(ColonelsettlementOrder order)")
                .contains("STATUS_PENDING_HOMEWORK")
                .contains("STATUS_COMPLETED")
                .contains("findPendingHomeworkRequestIds")
                .contains("transitionSamples(")
                .contains("sampleDomainEventPublisher.publishSampleCompleted")
                .contains("AND sr.status = 5")
                .contains("ORDER BY sr.create_time ASC")
                .contains("LIMIT 1");
    }

    @Test
    void sampleHomeworkOrderEventConsumerShouldHaveExecutableTestCoverage() throws IOException {
        String listenerTest = readProjectFile("backend/src/test/java/com/colonel/saas/listener/SampleOrderSyncedHomeworkListenerTest.java");
        String bridgeTest = readProjectFile("backend/src/test/java/com/colonel/saas/domain/order/application/OrderSampleHomeworkBridgeTest.java");
        String lifecycleTest = readProjectFile("backend/src/test/java/com/colonel/saas/service/SampleLifecycleServiceTest.java");

        assertThat(listenerTest)
                .contains("onOrderSynced_shouldSkipNullEventWithoutTouchingBridge")
                .contains("onOrderSynced_shouldSkipWhenEventDrivenHomeworkIsDisabled")
                .contains("onOrderSynced_shouldDelegateToHomeworkBridgeWhenEnabled")
                .contains("onOrderSynced_shouldSwallowBridgeFailure");

        assertThat(bridgeTest)
                .contains("completeHomework_whenSwitchOff_shouldDoNothing")
                .contains("completeHomework_whenSwitchOn_shouldCompleteHomeworkAndLog")
                .contains("completeHomework_whenOrderMissing_shouldSkipHomework")
                .contains("completeHomework_whenDuplicateOrderDoesNotCompleteSample_shouldNotWriteCompletionLog")
                .contains("completeHomework_whenSameOrderEventRepeated_shouldWriteCompletionLogOnlyOnce");

        assertThat(lifecycleTest)
                .contains("completePendingHomeworkByOrder_shouldCompleteMatchedRequests")
                .contains("completePendingHomeworkByOrder_shouldCompleteOnlyOldestMatchedRequest")
                .contains("completePendingHomeworkByOrder_shouldSkipWhenRequiredOrderFieldsMissing")
                .contains("completePendingHomeworkByOrder_shouldSkipWhenNoTalentUid")
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
