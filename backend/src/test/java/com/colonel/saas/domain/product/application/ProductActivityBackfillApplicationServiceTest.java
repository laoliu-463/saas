package com.colonel.saas.domain.product.application;

import com.colonel.saas.domain.product.query.ProductBackfillJobStatusQueryService;
import com.colonel.saas.domain.product.query.ProductBackfillJobStatusView;
import com.colonel.saas.service.ActivityProductPaginationRunner;
import com.colonel.saas.service.ProductActivityBackfillService;
import com.colonel.saas.service.ProductSyncDryRunProbeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductActivityBackfillApplicationServiceTest {

    @Mock
    private ProductActivityBackfillService backfillService;

    @Mock
    private ProductBackfillJobStatusQueryService jobStatusQueryService;

    private ProductActivityBackfillApplicationService applicationService;

    @BeforeEach
    void setUp() {
        applicationService = new ProductActivityBackfillApplicationService(backfillService, jobStatusQueryService);
    }

    @Test
    void backfill_shouldMapCommandAndDryRunSummaryWithoutExposingLegacyDto() {
        UUID requestedBy = UUID.randomUUID();
        ProductActivityBackfillApplicationService.BackfillCommand command = command();
        ActivityProductPaginationRunner.PageSummary pageSummary =
                new ActivityProductPaginationRunner.PageSummary(
                        1, "cursor-1", "cursor-2", 20, true, "P1", "P20", 1, 123L);
        ProductSyncDryRunProbeService.ActivityDryRunResult dryRunResult =
                new ProductSyncDryRunProbeService.ActivityDryRunResult(
                        "ACT-1",
                        true,
                        20,
                        1000,
                        2,
                        40,
                        39,
                        1,
                        10,
                        30,
                        30,
                        "MAX_PAGES_REACHED",
                        true,
                        List.of(pageSummary),
                        List.of("still has next page"));
        ProductActivityBackfillService.BackfillResult legacyResult =
                new ProductActivityBackfillService.BackfillResult(
                        "job-1",
                        true,
                        "CUSTOM_ACTIVITY_IDS",
                        1,
                        1,
                        0,
                        0,
                        40,
                        39,
                        10,
                        30,
                        0,
                        0,
                        0,
                        0,
                        Map.of("MAX_PAGES_REACHED", 1L),
                        List.of(dryRunResult),
                        2,
                        3,
                        4);
        when(backfillService.backfill(any(ProductActivityBackfillService.BackfillRequest.class), eq(requestedBy)))
                .thenReturn(legacyResult);

        ProductActivityBackfillApplicationService.BackfillResult result =
                applicationService.backfill(command, requestedBy);

        ArgumentCaptor<ProductActivityBackfillService.BackfillRequest> requestCaptor =
                ArgumentCaptor.forClass(ProductActivityBackfillService.BackfillRequest.class);
        verify(backfillService).backfill(requestCaptor.capture(), eq(requestedBy));
        ProductActivityBackfillService.BackfillRequest legacyRequest = requestCaptor.getValue();
        assertThat(legacyRequest.scope()).isEqualTo("CUSTOM_ACTIVITY_IDS");
        assertThat(legacyRequest.activityIds()).containsExactly("ACT-1");
        assertThat(legacyRequest.pageSize()).isEqualTo(20);
        assertThat(legacyRequest.dryRun()).isTrue();
        assertThat(legacyRequest.confirm()).isFalse();

        assertThat(result.jobId()).isEqualTo("job-1");
        assertThat(result.apiDistinctProductIds()).isEqualTo(39);
        assertThat(result.stopReasonStats()).containsEntry("MAX_PAGES_REACHED", 1L);
        assertThat(result.lockWaitCount()).isEqualTo(2);
        assertThat(result.deadlockRetryCount()).isEqualTo(3);
        assertThat(result.unchanged()).isEqualTo(4);
        assertThat(result.topGapActivities()).hasSize(1);
        ProductActivityBackfillApplicationService.ActivityDryRunSummary summary =
                result.topGapActivities().get(0);
        assertThat(summary.activityId()).isEqualTo("ACT-1");
        assertThat(summary.pageSamples()).hasSize(1);
        assertThat(summary.pageSamples().get(0).nextCursor()).isEqualTo("cursor-2");
        assertThat(summary.warnings()).containsExactly("still has next page");
    }

    @Test
    void backfillAsync_shouldMapCommandAndAsyncResponse() {
        UUID requestedBy = UUID.randomUUID();
        ProductActivityBackfillApplicationService.BackfillCommand command = command();
        when(backfillService.backfillAsync(any(ProductActivityBackfillService.BackfillRequest.class), eq(requestedBy)))
                .thenReturn(new ProductActivityBackfillService.BackfillAsyncResponse("job-async-1", "RUNNING"));

        ProductActivityBackfillApplicationService.BackfillAsyncResponse response =
                applicationService.backfillAsync(command, requestedBy);

        assertThat(response.jobId()).isEqualTo("job-async-1");
        assertThat(response.status()).isEqualTo("RUNNING");
        ArgumentCaptor<ProductActivityBackfillService.BackfillRequest> requestCaptor =
                ArgumentCaptor.forClass(ProductActivityBackfillService.BackfillRequest.class);
        verify(backfillService).backfillAsync(requestCaptor.capture(), eq(requestedBy));
        assertThat(requestCaptor.getValue().displayRefreshMode()).isEqualTo("DEFERRED");
    }

    @Test
    void getJobStatus_shouldMapQueryStatusToApplicationStatus() {
        ProductBackfillJobStatusView queryStatus =
                new ProductBackfillJobStatusView(
                        "job-1",
                        "RUNNING",
                        true,
                        "CUSTOM_ACTIVITY_IDS",
                        1,
                        0,
                        0,
                        0,
                        40,
                        39,
                        10,
                        30,
                        0,
                        0,
                        0,
                        0,
                        Map.of("DONE_NO_MORE", 1L),
                        "ACT-1",
                        "2026-06-27T15:20:00",
                        2,
                        3,
                        4,
                        "2026-06-27T15:19:00",
                        null);
        when(jobStatusQueryService.getJobStatus("job-1")).thenReturn(queryStatus);

        ProductActivityBackfillApplicationService.BackfillJobStatus status =
                applicationService.getJobStatus("job-1");

        assertThat(status.jobId()).isEqualTo("job-1");
        assertThat(status.currentActivityId()).isEqualTo("ACT-1");
        assertThat(status.stopReasonStats()).containsEntry("DONE_NO_MORE", 1L);
        assertThat(status.lockWaitCount()).isEqualTo(2);
        assertThat(status.deadlockRetryCount()).isEqualTo(3);
        assertThat(status.unchanged()).isEqualTo(4);
        verify(jobStatusQueryService).getJobStatus("job-1");
    }

    private ProductActivityBackfillApplicationService.BackfillCommand command() {
        return new ProductActivityBackfillApplicationService.BackfillCommand(
                "CUSTOM_ACTIVITY_IDS",
                List.of("ACT-1"),
                20,
                50,
                1000,
                50_000,
                true,
                false,
                "DEFERRED");
    }
}
