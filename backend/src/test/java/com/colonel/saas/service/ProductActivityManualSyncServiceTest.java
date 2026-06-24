package com.colonel.saas.service;

import com.colonel.saas.entity.ProductSyncJobLog;
import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import com.colonel.saas.mapper.ProductSyncJobLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ProductActivityManualSyncServiceTest {

    @Mock
    private ProductService productService;
    @Mock
    private ColonelsettlementActivityService colonelActivityService;
    @Mock
    private ColonelsettlementActivityMapper activityMapper;
    @Mock
    private ProductSyncJobLogMapper jobLogMapper;

    @BeforeEach
    void setUp() {
        lenient().when(productService.refreshActivitySnapshots(any()))
                .thenReturn(new ProductService.ActivityProductRefreshResult(3, 1, 1, 2, 0));
    }

    @Test
    void trigger_shouldReturnAcceptedAndRunRefreshInBackgroundExecutor() {
        ProductActivityManualSyncService service = new ProductActivityManualSyncService(
                productService,
                colonelActivityService,
                activityMapper,
                jobLogMapper,
                Runnable::run);

        ProductActivityManualSyncService.SyncTriggerResult result = service.trigger(" ACT-1 ", null);

        assertThat(result.activityId()).isEqualTo("ACT-1");
        assertThat(result.jobId()).startsWith("activity-product-sync-");
        assertThat(result.syncStatus()).isEqualTo("ACCEPTED");
        verify(colonelActivityService).syncActivitySummaryFromUpstream("ACT-1", null);
        ArgumentCaptor<DouyinProductGateway.ActivityProductQueryRequest> captor =
                ArgumentCaptor.forClass(DouyinProductGateway.ActivityProductQueryRequest.class);
        verify(productService).refreshActivitySnapshots(captor.capture());
        assertThat(captor.getValue().activityId()).isEqualTo("ACT-1");
        assertThat(captor.getValue().count()).isEqualTo(20);
        verify(activityMapper).touchLastSyncAt(eq("ACT-1"), any(LocalDateTime.class));
        ArgumentCaptor<ProductSyncJobLog> jobLogCaptor = ArgumentCaptor.forClass(ProductSyncJobLog.class);
        verify(jobLogMapper).insert(jobLogCaptor.capture());
        assertThat(jobLogCaptor.getValue().getJobId()).isEqualTo(result.jobId());
        assertThat(jobLogCaptor.getValue().getStatus()).isEqualTo("SUCCESS");
        assertThat(jobLogCaptor.getValue().getApiFetchedRows()).isEqualTo(3L);
        verify(jobLogMapper).updateById(any(ProductSyncJobLog.class));
    }

    @Test
    void trigger_shouldReturnRunningWhenSameActivityAlreadyQueued() {
        List<Runnable> queuedTasks = new ArrayList<>();
        Executor queuedExecutor = queuedTasks::add;
        ProductActivityManualSyncService service = new ProductActivityManualSyncService(
                productService,
                colonelActivityService,
                activityMapper,
                jobLogMapper,
                queuedExecutor);

        ProductActivityManualSyncService.SyncTriggerResult first = service.trigger("ACT-1", null);
        ProductActivityManualSyncService.SyncTriggerResult second = service.trigger("ACT-1", null);

        assertThat(first.syncStatus()).isEqualTo("ACCEPTED");
        assertThat(first.jobId()).startsWith("activity-product-sync-");
        assertThat(second.syncStatus()).isEqualTo("RUNNING");
        assertThat(second.jobId()).isEqualTo(first.jobId());
        assertThat(queuedTasks).hasSize(1);

        queuedTasks.get(0).run();
        ProductActivityManualSyncService.SyncTriggerResult third = service.trigger("ACT-1", null);
        assertThat(third.syncStatus()).isEqualTo("ACCEPTED");
        assertThat(third.jobId()).isNotEqualTo(first.jobId());
        assertThat(queuedTasks).hasSize(2);
    }

    @Test
    void trigger_shouldNotTouchLastSyncAtWhenRefreshIsIncomplete() {
        when(productService.refreshActivitySnapshots(any()))
                .thenReturn(new ProductService.ActivityProductRefreshResult(
                        2_000,
                        1,
                        100,
                        1_900,
                        0,
                        100,
                        2_000,
                        2_000,
                        0,
                        "MAX_PAGES_REACHED",
                        true,
                        false));
        ProductActivityManualSyncService service = new ProductActivityManualSyncService(
                productService,
                colonelActivityService,
                activityMapper,
                jobLogMapper,
                Runnable::run);

        ProductActivityManualSyncService.SyncTriggerResult result = service.trigger("ACT-1", null);

        assertThat(result.syncStatus()).isEqualTo("ACCEPTED");
        verify(activityMapper, never()).touchLastSyncAt(eq("ACT-1"), any(LocalDateTime.class));
        ArgumentCaptor<ProductSyncJobLog> jobLogCaptor = ArgumentCaptor.forClass(ProductSyncJobLog.class);
        verify(jobLogMapper).updateById(jobLogCaptor.capture());
        assertThat(jobLogCaptor.getValue().getStatus()).isEqualTo("PARTIAL");
    }

    @Test
    void getJobStatus_shouldReturnManualSyncJobStatus() {
        ProductSyncJobLog jobLog = new ProductSyncJobLog();
        jobLog.setJobId("activity-product-sync-1");
        jobLog.setJobType("activity_product_manual_sync");
        jobLog.setScope("ACTIVITY:ACT-1");
        jobLog.setStatus("SUCCESS");
        jobLog.setApiFetchedRows(3L);
        jobLog.setApiDistinctProductIds(3L);
        jobLog.setInserted(1);
        jobLog.setUpdated(2);
        jobLog.setSkipped(0);
        jobLog.setFailed(0);
        jobLog.setStartedAt(LocalDateTime.parse("2026-06-24T10:00:00"));
        jobLog.setFinishedAt(LocalDateTime.parse("2026-06-24T10:00:01"));
        when(jobLogMapper.selectLatestByJobId("activity-product-sync-1")).thenReturn(jobLog);
        ProductActivityManualSyncService service = new ProductActivityManualSyncService(
                productService,
                colonelActivityService,
                activityMapper,
                jobLogMapper,
                Runnable::run);

        ProductActivityManualSyncService.SyncJobStatus status = service.getJobStatus(" activity-product-sync-1 ");

        assertThat(status.activityId()).isEqualTo("ACT-1");
        assertThat(status.syncStatus()).isEqualTo("SUCCESS");
        assertThat(status.fetchedRows()).isEqualTo(3L);
        assertThat(status.createdCount()).isEqualTo(1);
        assertThat(status.finishedAt()).isEqualTo("2026-06-24T10:00:01");
    }
}
