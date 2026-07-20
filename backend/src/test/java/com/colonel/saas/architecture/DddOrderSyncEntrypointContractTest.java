package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class DddOrderSyncEntrypointContractTest {

    @Test
    void orderSyncHttpEntrypointsShouldRemainExplicitAdminOnlyAndReadonlyForDryRun() throws IOException {
        String controller = readProjectFile("src/main/java/com/colonel/saas/controller/OrderController.java");

        assertThat(controller)
                .contains(
                        "@PostMapping(\"/sync\")",
                        "syncOrders(",
                        "orderSyncService.syncInstituteOrdersHotRecent()",
                        "@PostMapping(\"/sync-range\")",
                        "syncOrdersByRange(",
                        "orderSyncService.syncByTimeRange(start, end)",
                        "@PostMapping(\"/6468-pagination-dry-run\")",
                        "order6468PaginationDryRunService.dryRun(command)",
                        "@PostMapping(\"/1603-settlement-dry-run\")",
                        "order1603SettlementDryRunService.dryRun(command)",
                        "@PostMapping(\"/2704-settlement-dry-run\")",
                        "order2704SettlementDryRunService.dryRun(command)");

        assertThat(controller)
                .containsPattern("@RequirePermission\\(\"order:sync-orders\"\\)\\s+@PostMapping\\(\"/sync\"\\)")
                .containsPattern("@RequirePermission\\(\"order:sync-orders-by-range\"\\)\\s+@PostMapping\\(\"/sync-range\"\\)")
                .containsPattern("@RequirePermission\\(\"order:dry-run6468-pagination\"\\)\\s+@PostMapping\\(\"/6468-pagination-dry-run\"\\)")
                .containsPattern("@RequirePermission\\(\"order:dry-run1603-settlement\"\\)\\s+@PostMapping\\(\"/1603-settlement-dry-run\"\\)")
                .containsPattern("@RequirePermission\\(\"order:dry-run2704-settlement\"\\)\\s+@PostMapping\\(\"/2704-settlement-dry-run\"\\)");
    }

    @Test
    void orderSyncErrorCodeInventoryShouldCoverGlobalMappingAndSyncResultErrorFields() throws IOException {
        assertFileContains(
                "src/main/java/com/colonel/saas/common/result/ResultCode.java",
                "PARAM_ERROR(400, \"",
                "BUSINESS_ERROR(460, \"",
                "EXTERNAL_SERVICE(470, \"",
                "SERVER_ERROR(500, \"");

        assertFileContains(
                "src/main/java/com/colonel/saas/common/exception/GlobalExceptionHandler.java",
                "handleValidate(Exception e)",
                "ResultCode.PARAM_ERROR.getCode()",
                "handleDouyinApi(DouyinApiException e)",
                "ResultCode.BUSINESS_ERROR.getCode()",
                "e.getErrorCodeTag()",
                "handleQueryTimeout(Exception e)",
                "ERROR_CODE_QUERY_TIMEOUT",
                "handleBusiness(BusinessException e)",
                "handleGeneral(Exception e)",
                "ResultCode.SERVER_ERROR");

        assertFileContains(
                "src/main/java/com/colonel/saas/service/OrderSyncService.java",
                "STOP_REASON_LOCKED",
                "STOP_REASON_FETCH_ERROR",
                "failedCount++",
                "BusinessException.external(\"Order sync upstream circuit is open\")",
                "int failed",
                "String stopReason");
    }

    @Test
    void orderSyncExistingTestsShouldCoverEntrypointErrorAndReadonlyEvidence() throws IOException {
        assertFileContains(
                "src/test/java/com/colonel/saas/controller/OrderSyncControllerTest.java",
                "syncOrders_shouldReturnManualSyncResult",
                "syncOrdersByRange_shouldCallExplicitTimeRangeSync",
                "dryRun2704Settlement_shouldReturnReadonlyProbeResult",
                "verify(orderMapper, never()).insert(any())");

        assertFileContains(
                "src/test/java/com/colonel/saas/controller/OrderControllerTest.java",
                "syncOrders_shouldUseInstituteHotRecentForManualRealPreProbe",
                "syncOrders_shouldUseInstituteHotRecentWhenBodyMissing",
                "syncOrders_shouldRequireAdminRoleAnnotation",
                "dryRun6468Pagination_shouldForwardReadonlyRequestWithoutOperationLog",
                "verify(operationLogService, never()).recordSystemAction");

        assertFileContains(
                "src/test/java/com/colonel/saas/common/exception/GlobalExceptionHandlerTest.java",
                "handleDouyinApi_returnsFailWithErrorCode",
                "handleValidate_withMethodArgumentNotValid_pullsFieldMessage",
                "handleBusiness_withUnauthorizedCode_returns401Status");

        assertFileContains(
                "src/test/java/com/colonel/saas/service/OrderSyncServiceTest.java",
                "upstream failed",
                "verify(valueOperations, never()).set(eq(\"order:sync:institute_hot_last_time\"), any())");
    }

    @Test
    void orderSyncDocsShouldDescribeEntrypointErrorLogAndExternalEvidenceBoundaries() throws IOException {
        assertFileContains(
                "../docs/对接/订单同步.md",
                "管理端或任务触发订单同步",
                "订单同步必须具备幂等、时间窗口、错误记录和真实响应证据",
                "同步日志包含时间窗口、数量、成功 / 失败摘要",
                "不在订单同步时直接写最终提成",
                "不用 mock 订单证明 real-pre 订单回流");

        assertFileContains(
                "../docs/05-API契约总表.md",
                "/api/orders/**",
                "/api/order-sync/**",
                "订单同步、订单事实、退款事实",
                "订单表、同步日志");
    }

    private static void assertFileContains(String relativePath, String... expectedFragments) throws IOException {
        String source = readProjectFile(relativePath);

        assertThat(source)
                .as(relativePath)
                .contains(expectedFragments);
    }

    private static String readProjectFile(String relativePath) throws IOException {
        return Files.readString(projectPath(relativePath));
    }

    private static Path projectPath(String relativePath) {
        return Paths.get(System.getProperty("user.dir")).resolve(relativePath);
    }
}
