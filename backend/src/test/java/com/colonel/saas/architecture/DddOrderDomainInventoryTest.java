package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DddOrderDomainInventoryTest {

    @Test
    void orderDomainCodeInventoryShouldCoverCurrentFactSyncQueryAndEventAnchors() throws IOException {
        assertFileContains(
                "src/main/java/com/colonel/saas/service/OrderSyncService.java",
                "syncInstituteOrdersHotRecent",
                "syncPayRecentWindow",
                "syncByTimeRange");
        assertFileContains(
                "src/main/java/com/colonel/saas/service/OrderSyncPersistenceService.java",
                "persistOrder",
                "insertIgnoreByOrderId",
                "publishOrderSynced",
                "OrderSyncedEvent");
        assertFileContains(
                "src/main/java/com/colonel/saas/domain/order/facade/OrderReadFacade.java",
                "findOrdersCreatedSince",
                "findOrdersSettledSince",
                "getDashboardAttributionSummary",
                "getDashboardFallbackSummary");
        assertFileContains(
                "src/main/java/com/colonel/saas/domain/order/event/OrderEventPayloadMapper.java",
                "toOrderSyncedEvent",
                "toOrderStatusChangedEvent");
    }

    @Test
    void orderApiInventoryShouldCoverSyncQueryAttributionAndDiagnosticsEndpoints() throws IOException {
        assertFileContains(
                "src/main/java/com/colonel/saas/controller/OrderController.java",
                "@RequestMapping(\"/orders\")",
                "@PostMapping(\"/sync\")",
                "@PostMapping(\"/sync-range\")",
                "@PostMapping(\"/6468-pagination-dry-run\")",
                "@PostMapping(\"/1603-settlement-dry-run\")",
                "@PostMapping(\"/2704-settlement-dry-run\")",
                "@PostMapping(\"/replay-attribution\")",
                "@GetMapping",
                "@GetMapping(\"/unattributed\")",
                "@GetMapping(\"/{orderId}\")",
                "@GetMapping(\"/stats\")",
                "@GetMapping(\"/filter-options\")");
        assertFileContains(
                "src/main/java/com/colonel/saas/controller/OrderAttributionController.java",
                "@GetMapping(\"/orders/order-attribution-unattributed\")",
                "@GetMapping(\"/dashboard/order-attribution-summary\")");
    }

    @Test
    void orderDataModelInventoryShouldCoverOrderFactsAttributionInputsAndDedupClaim() throws IOException {
        assertFileContains(
                "src/main/resources/db/init-db.sql",
                "CREATE TABLE IF NOT EXISTS colonelsettlement_order",
                "order_id",
                "product_id",
                "product_name",
                "order_amount",
                "actual_amount",
                "settle_amount",
                "estimate_service_fee",
                "effective_service_fee",
                "estimate_tech_service_fee",
                "effective_tech_service_fee",
                "pick_source",
                "channel_user_id",
                "colonel_user_id",
                "talent_id",
                "attribution_status",
                "extra_data",
                "CREATE INDEX IF NOT EXISTS idx_cso_order_id",
                "CREATE INDEX IF NOT EXISTS idx_cso_pick_source",
                "CREATE INDEX IF NOT EXISTS idx_cso_attribution_status");
        assertFileContains(
                "src/main/resources/db/create-order-sync-dedup-claim.sql",
                "CREATE TABLE IF NOT EXISTS order_sync_dedup_claim",
                "order_id",
                "order_row_id",
                "idx_order_sync_dedup_claim_row_id");
        assertFileContains(
                "src/main/java/com/colonel/saas/mapper/OrderSyncDedupClaimMapper.java",
                "order_sync_dedup_claim",
                "int claim(",
                "int bindOrderRow(");
    }

    @Test
    void orderTestInventoryShouldCoverCurrentLocalEvidenceSet() {
        List<String> requiredTests = List.of(
                "DddOrder003RoutingTest.java",
                "DddOrderPerformanceBoundaryTest.java",
                "DddOrderSampleHomeworkBoundaryTest.java",
                "DddOutbox001OrderRoutingTest.java",
                "DddSlimOrder001RoutingTest.java",
                "DddSlimOrder002RoutingTest.java",
                "OrderControllerTest.java",
                "OrderAttributionControllerTest.java",
                "OrderSyncControllerTest.java",
                "OrderSyncServiceTest.java",
                "OrderSyncPersistenceServiceTest.java",
                "OrderSyncPersistenceInstituteSettlementTest.java",
                "OrderEventPayloadMapperTest.java",
                "LegacyOrderReadFacadeTest.java",
                "OrderFilterOptionsQueryServiceTest.java",
                "OrderDetailQueryApplicationServiceTest.java",
                "OrderAmountMapperPolicyTest.java",
                "OrderDefaultAttributionPolicyTest.java",
                "RealDouyinOrderGatewayTest.java",
                "OrderApiTest.java",
                "OrderSyncJobTest.java");

        List<String> missing = requiredTests.stream()
                .filter(fileName -> !testSourceExists(fileName))
                .toList();

        assertThat(missing)
                .as("O-1 order-domain inventory must keep code, API, gateway, event and boundary tests discoverable")
                .isEmpty();
    }

    private static void assertFileContains(String relativePath, String... expectedFragments) throws IOException {
        String source = Files.readString(projectPath(relativePath));

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

    private static Path projectPath(String relativePath) {
        return Paths.get(System.getProperty("user.dir")).resolve(relativePath);
    }
}
