package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DddPerformanceDomainInventoryTest {

    @Test
    void performanceApiAndApplicationEntrypointsShouldRemainDiscoverable() throws IOException {
        assertSourceContains(
                "src/main/java/com/colonel/saas/controller/PerformanceController.java",
                "@RequestMapping(\"/performance\")",
                "PerformanceAccessContext",
                "PerformanceQueryFacade",
                "PerformanceSummaryService",
                "PerformanceExportService",
                "PerformanceMonthRecalculationService");
        assertSourceContains(
                "src/main/java/com/colonel/saas/controller/PerformanceOrderAdminController.java",
                "@RequestMapping(\"/orders\")",
                "performance-backfill",
                "performance-reconcile-invalidated",
                "commission-batch",
                "PerformanceBackfillService");
        assertSourceContains(
                "src/main/java/com/colonel/saas/listener/PerformanceRecordSyncListener.java",
                "@EventListener",
                "OrderReadFacade",
                "PerformanceCalculationApplicationService",
                "PerformanceCalculatedEvent");
    }

    @Test
    void performanceApplicationServicesShouldCoverCalculationQuerySummaryExportAndRecalculation() throws IOException {
        List<String> requiredFiles = List.of(
                "src/main/java/com/colonel/saas/domain/performance/application/PerformanceCalculationApplicationService.java",
                "src/main/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationService.java",
                "src/main/java/com/colonel/saas/domain/performance/application/PerformanceSummaryApplicationService.java",
                "src/main/java/com/colonel/saas/domain/performance/application/PerformanceExportApplicationService.java",
                "src/main/java/com/colonel/saas/domain/performance/application/PerformanceMonthRecalculationApplicationService.java",
                "src/main/java/com/colonel/saas/domain/performance/policy/PerformanceAccessScope.java",
                "src/main/java/com/colonel/saas/domain/performance/policy/PerformanceMoneyPolicy.java",
                "src/main/java/com/colonel/saas/domain/performance/policy/PerformanceAttributionPolicy.java",
                "src/main/java/com/colonel/saas/domain/performance/facade/PerformanceQueryFacade.java",
                "src/main/java/com/colonel/saas/domain/performance/facade/OrderPerformanceQueryFacade.java");

        for (String file : requiredFiles) {
            assertThat(projectFile(file))
                    .as(file)
                    .exists();
        }

        assertSourceContains(
                "src/main/java/com/colonel/saas/domain/performance/application/PerformanceCalculationApplicationService.java",
                "upsertFromOrder",
                "PerformanceRecordMapper",
                "CommissionService",
                "PerformanceRecord");
    }

    @Test
    void performanceDataContractsShouldPinTablesMappersAndSummaryStore() throws IOException {
        assertSourceContains(
                "src/main/resources/db/alter-dual-track-performance-20260522.sql",
                "CREATE TABLE IF NOT EXISTS performance_records",
                "final_channel_user_id",
                "final_recruiter_user_id",
                "estimate_service_profit",
                "effective_service_profit",
                "CONSTRAINT uk_performance_records_order_id UNIQUE (order_id)");
        assertSourceContains(
                "src/main/resources/mapper/PerformanceRecordMapper.xml",
                "FROM performance_records",
                "<insert id=\"upsert\"",
                "findByOrderIds",
                "calculation_version = performance_records.calculation_version + 1");
        assertSourceContains(
                "src/main/resources/db/alter-v1-gaps-20260522.sql",
                "CREATE TABLE IF NOT EXISTS dashboard_performance_daily",
                "COMMENT ON TABLE dashboard_performance_daily");
    }

    @Test
    void performanceScheduledTasksShouldRemainListedWithRuntimeSwitches() throws IOException {
        assertSourceContains(
                "src/main/java/com/colonel/saas/job/PerformanceBackfillJob.java",
                "@Scheduled(cron = \"0 30 3 * * ?\")",
                "PERFORMANCE_BACKFILL",
                "PerformanceBackfillService");
        assertSourceContains(
                "src/main/java/com/colonel/saas/job/PerformanceCacheWarmupJob.java",
                "ConditionalOnProperty",
                "performance.cache.warmup.enabled",
                "performance.cache.warmup.cron",
                "PERFORMANCE_CACHE_WARMUP");
        assertSourceContains(
                "src/main/java/com/colonel/saas/job/PerformanceRecalculateFailedJob.java",
                "ConditionalOnProperty",
                "performance.recalculate-failed.enabled",
                "performance.recalculate-failed.cron",
                "PERFORMANCE_RECALCULATE_FAILED");
        assertSourceContains(
                "src/main/resources/application.yml",
                "performance:",
                "PERFORMANCE_CACHE_WARMUP_ENABLED",
                "PERFORMANCE_RECALCULATE_FAILED_ENABLED");
    }

    @Test
    void performanceTestInventoryShouldCoverMainBuckets() throws IOException {
        List<String> requiredTests = List.of(
                "src/test/java/com/colonel/saas/architecture/DddPerformance003RoutingTest.java",
                "src/test/java/com/colonel/saas/architecture/DddPerformanceOrderBoundaryContractTest.java",
                "src/test/java/com/colonel/saas/architecture/DddPerformanceUnitClosureContractTest.java",
                "src/test/java/com/colonel/saas/architecture/DddPerformanceAccessScopeClosureContractTest.java",
                "src/test/java/com/colonel/saas/architecture/DddPerformanceExceptionAndDuplicateContractTest.java",
                "src/test/java/com/colonel/saas/domain/performance/application/PerformanceCalculationApplicationServiceTest.java",
                "src/test/java/com/colonel/saas/domain/performance/application/PerformanceSummaryApplicationServiceTest.java",
                "src/test/java/com/colonel/saas/domain/performance/application/PerformanceExportApplicationServiceTest.java",
                "src/test/java/com/colonel/saas/service/PerformanceBackfillServiceTest.java",
                "src/test/java/com/colonel/saas/listener/PerformanceRecordSyncListenerTest.java",
                "src/test/java/com/colonel/saas/mapper/PerformanceRecordMapperTest.java");

        for (String test : requiredTests) {
            assertSourceContains(test, "@Test");
        }
    }

    private static void assertSourceContains(String relativePath, String... fragments) throws IOException {
        String source = Files.readString(projectFile(relativePath)).replace("\r\n", "\n");
        assertThat(source)
                .as(relativePath)
                .contains(fragments);
    }

    private static Path projectFile(String relativePath) {
        return Path.of(System.getProperty("user.dir")).resolve(relativePath);
    }
}
