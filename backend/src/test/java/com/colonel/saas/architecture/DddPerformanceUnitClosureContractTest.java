package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DddPerformanceUnitClosureContractTest {

    @Test
    void performanceUnitSuiteShouldCoverCoreResponsibilities() throws IOException {
        List<String> requiredFiles = List.of(
                "src/test/java/com/colonel/saas/domain/performance/application/PerformanceCalculationApplicationServiceTest.java",
                "src/test/java/com/colonel/saas/service/PerformanceCalculationServiceTest.java",
                "src/test/java/com/colonel/saas/service/PerformanceCalculationEffectiveTrackTest.java",
                "src/test/java/com/colonel/saas/domain/performance/policy/PerformanceMoneyPolicyTest.java",
                "src/test/java/com/colonel/saas/service/OrderCommissionPolicyTest.java",
                "src/test/java/com/colonel/saas/service/CommissionServiceTest.java",
                "src/test/java/com/colonel/saas/service/CommissionRuleServiceTest.java",
                "src/test/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationServiceTest.java",
                "src/test/java/com/colonel/saas/domain/performance/application/PerformanceSummaryApplicationServiceTest.java",
                "src/test/java/com/colonel/saas/service/PerformanceSummaryServiceTest.java",
                "src/test/java/com/colonel/saas/service/PerformanceMetricsQueryServiceTest.java",
                "src/test/java/com/colonel/saas/service/PerformanceQueryServiceTest.java",
                "src/test/java/com/colonel/saas/domain/performance/application/PerformanceExportApplicationServiceTest.java",
                "src/test/java/com/colonel/saas/service/PerformanceExportServiceTest.java",
                "src/test/java/com/colonel/saas/domain/performance/policy/PerformanceAccessScopeTest.java",
                "src/test/java/com/colonel/saas/architecture/DddPerformanceAccessPolicyBoundaryTest.java",
                "src/test/java/com/colonel/saas/architecture/DddPerformance003RoutingTest.java",
                "src/test/java/com/colonel/saas/controller/PerformanceControllerTest.java",
                "src/test/java/com/colonel/saas/controller/PerformanceOrderAdminControllerTest.java",
                "src/test/java/com/colonel/saas/service/PerformanceBackfillServiceTest.java",
                "src/test/java/com/colonel/saas/listener/PerformanceRecordSyncListenerTest.java",
                "src/test/java/com/colonel/saas/domain/performance/application/PerformanceCalculationExecutionServiceTest.java",
                "src/test/java/com/colonel/saas/domain/performance/application/PerformanceCalculationRetryServiceTest.java",
                "src/test/java/com/colonel/saas/domain/performance/application/PerformanceRefundAdjustmentServiceTest.java",
                "src/test/java/com/colonel/saas/service/DashboardPerformanceSummaryServiceTest.java",
                "src/test/java/com/colonel/saas/domain/performance/application/PerformanceMonthRecalculationApplicationServiceTest.java",
                "src/test/java/com/colonel/saas/service/PerformanceMonthRecalculationServiceTest.java",
                "src/test/java/com/colonel/saas/domain/performance/facade/LegacyOrderPerformanceQueryFacadeTest.java",
                "src/test/java/com/colonel/saas/architecture/DddPerformanceOrderBoundaryContractTest.java",
                "src/test/java/com/colonel/saas/architecture/DddPerformanceIdempotencyContractTest.java",
                "src/test/java/com/colonel/saas/architecture/DddPerformanceCalculatedEventContractTest.java",
                "src/test/java/com/colonel/saas/architecture/DddPerformanceSummaryRefreshedEventContractTest.java",
                "src/test/java/com/colonel/saas/architecture/DddPerformanceRefundReversalSummaryContractTest.java",
                "src/test/java/com/colonel/saas/architecture/DddPerformanceAttributionTraceabilityContractTest.java",
                "src/test/java/com/colonel/saas/architecture/DddPerformanceCommissionRuleVersionContractTest.java",
                "src/test/java/com/colonel/saas/architecture/DddPerformanceConfigConsumptionContractTest.java");

        for (String file : requiredFiles) {
            String source = readProjectFile(file);
            assertThat(source)
                    .as(file)
                    .contains("@Test");
        }
    }

    @Test
    void unitSuiteShouldPinMainPerformanceBehaviorBuckets() throws IOException {
        assertThat(readProjectFile(
                "src/test/java/com/colonel/saas/domain/performance/application/PerformanceCalculationApplicationServiceTest.java"))
                .contains(
                        "upsertFromOrder_shouldCalculateEstimateAndEffectiveTracks",
                        "upsertFromOrder_shouldPreserveTraceableAttributionInputsOnPerformanceRecord",
                        "upsertFromOrder_shouldReverseRefundedExistingRecordAndAdvanceVersion",
                        "upsertFromOrder_existingRecordShouldReuseIdAndAdvanceVersionForDuplicateConsumption");
        assertThat(readProjectFile("src/test/java/com/colonel/saas/service/CommissionServiceTest.java"))
                .contains(
                        "calculate_shouldUseConfigRatios",
                        "calculate_shouldUseCommissionRuleRatiosBeforeLegacyActivityConfig",
                        "assertThat(summary.bizRatioSourceVersion()).isEqualTo(\"6\")",
                        "calculate_shouldFallbackWhenRatioQueryFails");
        assertThat(readProjectFile("src/test/java/com/colonel/saas/service/CommissionRuleServiceTest.java"))
                .contains(
                        "resolveRule_shouldExposeMatchedRuleVersionEvidence",
                        "update_shouldKeepLoadedVersionForOptimisticLockEvidence");
        assertThat(readProjectFile(
                "src/test/java/com/colonel/saas/domain/performance/application/PerformanceSummaryApplicationServiceTest.java"))
                .contains(
                        "aggregateEstimate_shouldUseEstimateColumns",
                        "aggregateEffective_shouldUseEffectiveColumns",
                        "pr.is_reversed = FALSE");
        assertThat(readProjectFile("src/test/java/com/colonel/saas/service/PerformanceMetricsQueryServiceTest.java"))
                .contains(
                        "aggregateRange_shouldUseEstimateColumnsForCreateTrack",
                        "aggregateRange_shouldReadTalentCommissionFromOrderSettlementField",
                        "aggregateDashboardSummary_shouldUseEffectiveTrackColumns");
        assertThat(readProjectFile(
                "src/test/java/com/colonel/saas/domain/performance/policy/PerformanceAccessScopeTest.java"))
                .contains(
                        "assertFilterAllowed_shouldRejectMissingContextAndCrossStaffFilters",
                        "appendScopeCondition_shouldFailClosedWhenUserOrDeptMissing");
        assertThat(readProjectFile("src/test/java/com/colonel/saas/listener/PerformanceRecordSyncListenerTest.java"))
                .contains(
                        "onOrderSynced_shouldUpsertPerformanceRecordAndPublishCalculatedEventWhenOrderExists",
                        "onOrderSynced_shouldPublishReversedCalculatedEventWhenRefundedOrderIsConsumed",
                        "onOrderSynced_shouldPropagateMissingOrderSoOutboxCanRetry");
        assertThat(readProjectFile(
                "src/test/java/com/colonel/saas/domain/performance/application/PerformanceRefundAdjustmentServiceTest.java"))
                .contains(
                        "recordRefundShouldCreateIdempotentProportionalAdjustmentForPartialRefund",
                        "getDeltaEffectiveServiceFee()",
                        "getDeltaTalentCommission()");
    }

    @Test
    void integrationLikeMapperTestsShouldRemainExplicitlySeparatedFromUnitClosure() throws IOException {
        String mapperTest = readProjectFile("src/test/java/com/colonel/saas/mapper/PerformanceRecordMapperTest.java");

        assertThat(mapperTest)
                .contains("extends BaseIntegrationTest")
                .contains("shouldExcludeInvalidRecords");
    }

    private static String readProjectFile(String relativePath) throws IOException {
        return Files.readString(Path.of(System.getProperty("user.dir")).resolve(relativePath))
                .replace("\r\n", "\n");
    }
}
