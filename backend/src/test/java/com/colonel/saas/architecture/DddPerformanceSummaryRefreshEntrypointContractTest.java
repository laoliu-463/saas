package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddPerformanceSummaryRefreshEntrypointContractTest {

    @Test
    void orderSyncedListenerShouldRemainDashboardSummaryRefreshEntrypoint() throws IOException {
        String listener = readProjectFile("src/main/java/com/colonel/saas/listener/OrderSyncedEventListener.java");

        assertThat(listener)
                .contains(
                        "@EventListener",
                        "summaryService.applyOrderSynced(event)",
                        "shortTtlCacheService.evictByPrefix(OrderDerivedCacheKeys.DASHBOARD_SUMMARY_PREFIX)",
                        "shortTtlCacheService.evictByPrefix(OrderDerivedCacheKeys.DASHBOARD_METRICS_PREFIX)",
                        "shortTtlCacheService.evictByPrefix(OrderDerivedCacheKeys.ORDER_STATS_PREFIX)");
    }

    @Test
    void dashboardSummaryRefreshShouldStayOnDailyUpsertPath() throws IOException {
        String service = readProjectFile(
                "src/main/java/com/colonel/saas/service/DashboardPerformanceSummaryService.java");

        assertThat(service)
                .contains(
                        "DashboardPerformanceSummaryService",
                        "public void applyOrderSynced(OrderSyncedEvent event)",
                        "!event.newlyInserted()",
                        "OrderCommissionPolicy.countsTowardPerformance(event.orderStatus())",
                        "PerformanceMoneyPolicy.serviceFeeNetCent",
                        "INSERT INTO dashboard_performance_daily",
                        "ON CONFLICT (stat_date) DO UPDATE SET",
                        "dashboard_performance_daily.order_count + 1",
                        "dashboard_performance_daily.order_amount + EXCLUDED.order_amount",
                        "dashboard_performance_daily.service_fee_net + EXCLUDED.service_fee_net");
    }

    @Test
    void summaryRefreshEntrypointEvidenceShouldStayExecutable() throws IOException {
        String listenerTest = readProjectFile(
                "src/test/java/com/colonel/saas/listener/OrderSyncedEventListenerTest.java");
        String summaryServiceTest = readProjectFile(
                "src/test/java/com/colonel/saas/service/DashboardPerformanceSummaryServiceTest.java");

        assertThat(listenerTest)
                .contains(
                        "onOrderSynced_shouldRefreshDashboardSummaryAndEvictDerivedCachesAtEntrypoint",
                        "verify(summaryService).applyOrderSynced(event)",
                        "OrderDerivedCacheKeys.DASHBOARD_SUMMARY_PREFIX",
                        "OrderDerivedCacheKeys.DASHBOARD_METRICS_PREFIX",
                        "OrderDerivedCacheKeys.ORDER_STATS_PREFIX");
        assertThat(summaryServiceTest)
                .contains(
                        "applyOrderSynced_shouldSkipExistingOrderUpdatesToAvoidDuplicateDailyTotals",
                        "applyOrderSynced_shouldBucketByOrderCreateDate",
                        "applyOrderSynced_shouldUseSettlementServiceFeeExpenseForNetProfit");
    }

    private static String readProjectFile(String relativePath) throws IOException {
        return Files.readString(Path.of(System.getProperty("user.dir")).resolve(relativePath))
                .replace("\r\n", "\n");
    }
}
