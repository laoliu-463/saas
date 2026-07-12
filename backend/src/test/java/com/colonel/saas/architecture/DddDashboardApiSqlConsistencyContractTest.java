package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddDashboardApiSqlConsistencyContractTest {

    @Test
    void dashboardSummaryApiShouldStayConnectedToPerformanceSqlSummary() throws IOException {
        String controller = readProjectFile("backend/src/main/java/com/colonel/saas/controller/DashboardController.java");
        String service = readProjectFile("backend/src/main/java/com/colonel/saas/service/DashboardService.java");
        String metricsService = readProjectFile("backend/src/main/java/com/colonel/saas/service/PerformanceMetricsQueryService.java");
        String aggregateApplication = readProjectFile(
                "backend/src/main/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationService.java");

        assertThat(controller)
                .contains("@RequestMapping(\"/dashboard\")")
                .contains("@GetMapping(\"/summary\")")
                .contains("ApiResult<DashboardService.Summary>")
                .contains("dashboardService.getSummary(startTime, endTime, userId, deptId, dataScope)");

        assertThat(service)
                .contains("performanceMetricsQueryService.hasPerformanceRecords()")
                .contains("performanceMetricsQueryService.aggregateDashboardSummary(startTime, endTime, userId, deptId, dataScope)")
                .contains("summary.setOrderCount(orderCount)")
                .contains("summary.setOrderAmount(orderAmount)")
                .contains("summary.setServiceFee(serviceFee)")
                .contains("toPerformanceItems(performanceSummary.channelPerformance(), true)")
                .contains("toPerformanceItems(performanceSummary.colonelPerformance(), false)");

        assertThat(metricsService)
                .contains("PerformanceAggregateApplicationService")
                .contains("return aggregateApplicationService.aggregateDashboardSummary(");

        assertThat(aggregateApplication)
                .contains("public PerformanceMetricsQueryService.DashboardPerformanceSummary aggregateDashboardSummary(")
                .contains("FROM colonelsettlement_order co")
                .contains("LEFT JOIN performance_records pr ON pr.order_id = co.order_id")
                .contains("COUNT(*) AS order_count")
                .contains("COALESCE(SUM(co.settle_amount), 0) AS order_amount_cent")
                .contains("COALESCE(SUM(co.effective_service_fee), 0) AS service_fee_cent")
                .contains("\"pr.final_channel_user_id\"")
                .contains("\"pr.final_recruiter_user_id\"")
                .contains("co.attribution_status = 'ATTRIBUTED'")
                .contains("ORDER BY order_count DESC, order_amount_cent DESC")
                .contains("LIMIT 10");
    }

    @Test
    void dashboardApiSqlConsistencyShouldHaveExecutableLocalEvidence() throws IOException {
        String controllerTest = readProjectFile("backend/src/test/java/com/colonel/saas/controller/DashboardControllerTest.java");
        String dashboardServiceTest = readProjectFile("backend/src/test/java/com/colonel/saas/service/DashboardServiceTest.java");
        String metricsServiceTest = readProjectFile("backend/src/test/java/com/colonel/saas/service/PerformanceMetricsQueryServiceTest.java");
        String apiDocs = readProjectFile("docs/05-API契约总表.md");

        assertThat(apiDocs)
                .contains("/api/dashboard/**")
                .contains("GET /api/dashboard/metrics")
                .contains("estimate")
                .contains("settle")
                .contains("grossProfit");

        assertThat(controllerTest)
                .contains("getSummaryShouldCacheIdenticalScopeRequests")
                .contains("getSummaryShouldCacheDifferentScopeRequestsIndependently")
                .contains("getActivityProductsShouldConvertServicePageToApiPageResult");

        assertThat(dashboardServiceTest)
                .contains("getSummary_shouldUsePerformanceRecordsWhenAvailable")
                .contains("aggregateDashboardSummary")
                .contains("summary.getOrderCount()).isEqualTo(8L)")
                .contains("summary.getOrderAmount()).isEqualTo(90000L)")
                .contains("summary.getServiceFee()).isEqualTo(1800L)");

        assertThat(metricsServiceTest)
                .contains("aggregateDashboardSummary_shouldUseEffectiveTrackColumns")
                .contains("\"order_count\", 5L")
                .contains("\"order_amount_cent\", 120000L")
                .contains("\"service_fee_cent\", 2300L")
                .contains("summary.orderCount()).isEqualTo(5L)")
                .contains("summary.serviceFeeCent()).isEqualTo(2300L)")
                .contains("contains(\"settle_second_colonel_commission\")");
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
