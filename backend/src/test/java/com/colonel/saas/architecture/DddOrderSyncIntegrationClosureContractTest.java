package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DddOrderSyncIntegrationClosureContractTest {

    @Test
    void orderSyncLocalClosureShouldConnectEntrypointServicePersistenceAndEvents() throws IOException {
        assertFileContains(
                "src/test/java/com/colonel/saas/architecture/DddOrderSyncEntrypointContractTest.java",
                "orderSyncHttpEntrypointsShouldRemainExplicitAdminOnlyAndReadonlyForDryRun",
                "syncOrders_shouldReturnManualSyncResult",
                "syncOrders_shouldUseInstituteHotRecentForManualRealPreProbe");
        assertFileContains(
                "src/test/java/com/colonel/saas/service/OrderSyncServiceTest.java",
                "syncInstituteOrdersRecentWindow_shouldPersistFactAndEstimateTrackOnly",
                "when(persistenceService.persistOrder(any(ColonelsettlementOrder.class))).thenReturn(true)",
                "assertThat(result.created()).isEqualTo(1)",
                "verify(persistenceService).persistOrder(orderCaptor.capture())");
        assertFileContains(
                "src/test/java/com/colonel/saas/service/OrderSyncPersistenceServiceTest.java",
                "persistOrder_shouldReturnTrueAndTriggerFollowUpsWhenInserted",
                "persistOrder_shouldPublishOrderEventWithTalentUidAndRawExtraData",
                "persistOrder_shouldDeferOrderSyncedEventUntilTransactionCommit",
                "persistOrder_shouldAppendOutboxInsteadOfSpringEventWhenRoutingEnabled");
        assertFileContains(
                "src/test/java/com/colonel/saas/architecture/DddOrderSyncedEventTimingContractTest.java",
                "orderPersistenceShouldPublishOrderSyncedOnlyAfterDurableWriteAndFollowUps",
                "outboxRoutingShouldAppendIdempotentOrderSyncedRecordInsteadOfSpringEvent");
    }

    @Test
    void orderSyncLocalClosureShouldCoverDownstreamConsumersWithoutMovingBusinessRulesToOrderDomain() throws IOException {
        assertFileContains(
                "src/test/java/com/colonel/saas/listener/PerformanceRecordSyncListenerTest.java",
                "onOrderSynced_shouldUpsertPerformanceRecordAndPublishCalculatedEventWhenOrderExists",
                "onOrderSynced_shouldPublishReversedCalculatedEventWhenRefundedOrderIsConsumed");
        assertFileContains(
                "src/test/java/com/colonel/saas/listener/SampleOrderSyncedHomeworkListenerTest.java",
                "onOrderSynced_shouldDelegateToHomeworkBridgeWhenEnabled",
                "onOrderSynced_shouldSwallowBridgeFailure");
        assertFileContains(
                "src/test/java/com/colonel/saas/listener/AnalyticsShadowEventListenerTest.java",
                "onOrderSynced_shouldDelegateWithOrderRowIdAndOrderSyncedType",
                "onOrderSynced_shouldUseNameBasedIdWhenOrderRowIdIsMissing",
                "OrderSyncedEvent");
        assertFileContains(
                "src/test/java/com/colonel/saas/architecture/DddOrderPerformanceBoundaryTest.java",
                "orderOwnedCodeMustNotWritePerformanceRecordsTable",
                "orderOwnedCodeMustNotCallPerformanceMutationServices");
        assertFileContains(
                "src/test/java/com/colonel/saas/architecture/DddOrderSampleHomeworkBoundaryTest.java",
                "orderOwnedCodeMustNotDirectlyMutateSampleHomeworkState",
                "orderPersistenceAndEventBridgeMustUseSampleHomeworkFacade",
                "OrderSyncPersistenceService");
    }

    @Test
    void orderSyncLocalClosureShouldKeepRealPreAndExternalSampleGapsExplicit() throws IOException {
        assertFileContains(
                "../docs/ddd-completion-evidence-matrix.md",
                "| O-15 | BLOCKED | 阻塞原因：依赖 real-pre 真实上游响应 / 样本证据",
                "| O-4 | BLOCKED |",
                "真实 `pick_source` 命中订单样本");
        assertFileContains(
                "../docs/领域/订单域.md",
                "real-pre 订单同步需提供真实接口响应、订单入库 SQL/API、同步日志",
                "订单域只存事实，不算提成，不应用独家覆盖");
    }

    @Test
    void orderSyncClosureEvidenceSetShouldRemainDiscoverable() {
        List<String> requiredTests = List.of(
                "DddOrderSyncEntrypointContractTest.java",
                "OrderSyncControllerTest.java",
                "OrderControllerTest.java",
                "OrderSyncServiceTest.java",
                "OrderSyncApplicationServiceTest.java",
                "OrderSyncPersistenceServiceTest.java",
                "OrderSyncPersistenceInstituteSettlementTest.java",
                "OrderSyncDedupSchemaBootstrapTest.java",
                "DddOrderSyncedEventTimingContractTest.java",
                "DddOutbox001OrderRoutingTest.java",
                "OrderEventPayloadMapperTest.java",
                "OrderSyncedEventListenerTest.java",
                "PerformanceRecordSyncListenerTest.java",
                "SampleOrderSyncedHomeworkListenerTest.java",
                "AnalyticsShadowEventListenerTest.java");

        List<String> missing = requiredTests.stream()
                .filter(fileName -> !testSourceExists(fileName))
                .toList();

        assertThat(missing)
                .as("O-17 local order-sync closure must keep entrypoint, sync, persistence, event and consumer tests discoverable")
                .isEmpty();
    }

    private static void assertFileContains(String relativePath, String... expectedFragments) throws IOException {
        String source = readProjectFile(relativePath);
        assertThat(source)
                .as(relativePath)
                .contains(expectedFragments);
    }

    private static boolean testSourceExists(String fileName) {
        try (var paths = Files.walk(projectPath("src/test/java"))) {
            return paths
                    .filter(path -> path.getFileName().toString().equals(fileName))
                    .findFirst()
                    .isPresent();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to scan test sources", e);
        }
    }

    private static String readProjectFile(String relativePath) throws IOException {
        return Files.readString(projectPath(relativePath)).replace("\r\n", "\n");
    }

    private static Path projectPath(String relativePath) {
        return Path.of(System.getProperty("user.dir")).resolve(relativePath);
    }
}
