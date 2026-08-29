package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddPerformanceSummaryRefreshEntrypointContractTest {

    @Test
    void orderSyncedListenerShouldOnlyInvalidateDerivedCaches() throws IOException {
        String listener = readProjectFile("src/main/java/com/colonel/saas/listener/OrderSyncedEventListener.java");

        assertThat(listener)
                .contains(
                        "@EventListener",
                        "shortTtlCacheService.evictByPrefix(OrderDerivedCacheKeys.DASHBOARD_SUMMARY_PREFIX)",
                        "shortTtlCacheService.evictByPrefix(OrderDerivedCacheKeys.DASHBOARD_METRICS_PREFIX)",
                        "shortTtlCacheService.evictByPrefix(OrderDerivedCacheKeys.ORDER_STATS_PREFIX)");
    }

    @Test
    void dashboardSummaryRefreshShouldBeDrivenByPerformanceCalculatedEvent() throws IOException {
        String service = readProjectFile(
                "src/main/java/com/colonel/saas/service/DashboardPerformanceSummaryService.java");

        assertThat(service)
                .contains(
                        "DashboardPerformanceSummaryService",
                        "public void applyPerformanceCalculated(PerformanceCalculatedEvent event)",
                        "PerformanceRecord record = performanceRecordMapper.findByOrderId(event.orderId())",
                        "FROM performance_records pr",
                        "FROM performance_adjustment_ledger",
                        "INSERT INTO dashboard_performance_daily",
                        "ON CONFLICT (stat_date) DO UPDATE SET",
                        "COUNT(*)",
                        "SUM(COALESCE(pr.pay_amount, 0) + COALESCE(adj.delta_pay_amount, 0))",
                        "SUM(COALESCE(pr.effective_service_profit, 0)");
    }

    @Test
    void summaryRefreshEntrypointEvidenceShouldStayExecutable() throws IOException {
        String listenerTest = readProjectFile(
                "src/test/java/com/colonel/saas/listener/OrderSyncedEventListenerTest.java");
        String summaryServiceTest = readProjectFile(
                "src/test/java/com/colonel/saas/service/DashboardPerformanceSummaryServiceTest.java");

        assertThat(listenerTest)
                .contains(
                        "onOrderSynced_shouldOnlyEvictDerivedCachesAndExtendTalentProtection",
                        "OrderDerivedCacheKeys.DASHBOARD_SUMMARY_PREFIX",
                        "OrderDerivedCacheKeys.DASHBOARD_METRICS_PREFIX",
                        "OrderDerivedCacheKeys.ORDER_STATS_PREFIX");
        assertThat(summaryServiceTest)
                .contains(
                        "applyPerformanceCalculated_shouldRebuildDailySummaryFromPerformanceRecord",
                        "applyPerformanceCalculated_shouldIgnoreMissingPerformanceRecord");
    }

    private static String readProjectFile(String relativePath) throws IOException {
        return Files.readString(Path.of(System.getProperty("user.dir")).resolve(relativePath))
                .replace("\r\n", "\n");
    }
}
