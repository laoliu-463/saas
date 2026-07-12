package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddOrderSyncFailureEvidenceMatrixTest {

    @Test
    void syncResultShouldExposeWindowCountsAndStopReason() throws IOException {
        String source = readProjectFile("src/main/java/com/colonel/saas/service/OrderSyncService.java");
        String syncResult = section(source, "public record SyncResult(", "public int inserted() {");

        assertThat(source)
                .contains(
                        "STOP_REASON_LOCKED = \"LOCKED\"",
                        "STOP_REASON_EMPTY_PAGE = \"EMPTY_PAGE\"",
                        "STOP_REASON_NO_NEXT_CURSOR = \"NO_NEXT_CURSOR\"",
                        "STOP_REASON_DUPLICATE_CURSOR = \"DUPLICATE_CURSOR\"",
                        "STOP_REASON_MAX_PAGES = \"MAX_PAGES\"",
                        "STOP_REASON_MAX_ORDERS = \"MAX_ORDERS\"",
                        "STOP_REASON_SINGLE_PAGE = \"SINGLE_PAGE\"",
                        "STOP_REASON_FETCH_ERROR = \"FETCH_ERROR\"");

        assertThat(syncResult)
                .contains(
                        "long startTime",
                        "long endTime",
                        "int pages",
                        "int totalFetched",
                        "int created",
                        "int updated",
                        "int attributed",
                        "int unattributed",
                        "int failed",
                        "boolean locked",
                        "int uniqueOrders",
                        "String stopReason");
    }

    @Test
    void syncLoopShouldLogPerPageAndFinalFailureEvidenceFields() throws IOException {
        String source = readProjectFile("src/main/java/com/colonel/saas/service/OrderSyncService.java");
        String syncLoop = section(source, "private SyncResult syncItemsWithLimits(", "private String resolveNextCursor(");

        assertThat(syncLoop)
                .contains(
                        "pageNo={} cursor={} nextCursor={} logId={} fetched={} ",
                        "inserted={} updated={} failed={}",
                        "startTime={} endTime={} range=[{}, {}] pagesFetched={} ",
                        "uniqueOrders={} fetched={} inserted={} updated={} attributed={} unattributed={} ",
                        "noPickSource={} noMapping={} failed={} logId={} hasSettleCount={} ",
                        "hasEffectiveFeeCount={} stopReason={}");

        assertThat(syncLoop)
                .contains(
                        "failedCount++",
                        "pageFailed++",
                        "Skip order during sync, reason={}, orderId={}",
                        "Unexpected error processing order, orderId={}, type={}",
                        "Unexpected error persisting order, orderId={}, type={}",
                        "stopReason = STOP_REASON_FETCH_ERROR;");
    }

    @Test
    void stopReasonMatrixShouldMapEveryFailureBranchToEvidence() throws IOException {
        String source = readProjectFile("src/main/java/com/colonel/saas/service/OrderSyncService.java");
        String syncLoop = section(source, "private SyncResult syncItemsWithLimits(", "private String resolveNextCursor(");

        assertThat(syncLoop)
                .contains(
                        "stopReason = STOP_REASON_EMPTY_PAGE;",
                        "stopReason = STOP_REASON_MAX_ORDERS;",
                        "stopReason = STOP_REASON_SINGLE_PAGE;",
                        "stopReason = STOP_REASON_NO_NEXT_CURSOR;",
                        "stopReason = STOP_REASON_MAX_PAGES;",
                        "stopReason = STOP_REASON_DUPLICATE_CURSOR;",
                        "stopReason = STOP_REASON_FETCH_ERROR;");

        assertThat(source)
                .contains(
                        "return new SyncResult(startTime, endTime, 0, 0, 0, true);",
                        "locked ? STOP_REASON_LOCKED : STOP_REASON_UNKNOWN");
    }

    @Test
    void scheduledJobsShouldKeepFailureAndSummaryLoggingDiscoverable() throws IOException {
        String job = readProjectFile("src/main/java/com/colonel/saas/job/OrderSyncJob.java");

        assertThat(job)
                .contains(
                        "OrderSyncJob done, mode=INCREMENTAL, window=[{}, {}], pages={}, inserted={}, updated={}, attributed={}, unattributed={}, failed={}",
                        "OrderSyncJob.syncPayRecent done, mode=PAY_RECENT, window=[{}, {}], pages={}, inserted={}, updated={}, attributed={}, unattributed={}, failed={}",
                        "OrderSyncJob done, mode=INSTITUTE_HOT_RECENT, window=[{}, {}], pages={}, inserted={}, updated={}, failed={}, stopReason={}",
                        "OrderSyncJob.syncSettlementSettle done, mode=SETTLE, timeType=settle, window=[{}, {}], ",
                        "pages={}, fetched={}, inserted={}, updated={}, failed={}, stopReason={}",
                        "log.error(\"OrderSyncJob failed\", e);",
                        "log.error(\"OrderSyncJob.syncPayRecent failed\", e);",
                        "log.error(\"OrderSyncJob.syncInstituteOrdersHot failed\", e);",
                        "log.error(\"OrderSyncJob.syncSettlementSettle failed\", e);");
    }

    @Test
    void existingBehaviorTestsShouldCoverFailureMatrixRows() throws IOException {
        assertFileContains(
                "src/test/java/com/colonel/saas/service/OrderSyncServiceTest.java",
                "syncPayRecentWindow_shouldReturnLockedWithoutPersistingWaterlineWhenLockBusy",
                "syncInstituteOrdersHotRecent_shouldReturnLockedWithoutPersistingWhenBusy",
                "syncInstituteOrdersRecentWindow_shouldStopWhenCursorRepeats",
                "syncInstituteOrdersRecentWindow_shouldStopWhenMaxPagesReached",
                "syncInstituteOrdersRecentWindow_shouldStopWhenMaxOrdersReached",
                "syncInstituteOrdersHotRecent_shouldNotPersistWaterlineOnFailure",
                "shouldAdvanceSettleCheckpoint_requiresFetchedOrders");

        assertFileContains(
                "src/test/java/com/colonel/saas/job/OrderSyncJobTest.java",
                "syncOrders_shouldSkipWhenLocked",
                "syncOrders_shouldRethrowException",
                "syncPayRecent_shouldSkipWhenLocked",
                "syncPayRecent_shouldRethrowException",
                "syncInstituteOrdersHot_shouldSkipWhenLocked",
                "syncInstituteOrdersHot_shouldRethrowException",
                "syncSettlementSettle_shouldInvokeSettleWindow");

        assertFileContains(
                "../docs/对接/订单同步.md",
                "订单同步必须具备幂等、时间窗口、错误记录和真实响应证据",
                "同步日志包含时间窗口、数量、成功 / 失败摘要");
    }

    private static void assertFileContains(String relativePath, String... expectedFragments) throws IOException {
        String source = readProjectFile(relativePath);
        assertThat(source).as(relativePath).contains(expectedFragments);
    }

    private static String section(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        assertThat(startIndex).as("section start: %s", start).isGreaterThanOrEqualTo(0);
        assertThat(endIndex).as("section end: %s", end).isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex).replace("\r\n", "\n");
    }

    private static String readProjectFile(String relativePath) throws IOException {
        return Files.readString(Path.of(System.getProperty("user.dir")).resolve(relativePath))
                .replace("\r\n", "\n");
    }
}
