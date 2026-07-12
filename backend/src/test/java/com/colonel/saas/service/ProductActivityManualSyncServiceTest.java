package com.colonel.saas.service;

import com.colonel.saas.entity.ProductSyncJobLog;
import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.job.JobLockKeys;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import com.colonel.saas.mapper.ProductSyncJobLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DuplicateKeyException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    @Mock
    private DistributedJobLockService jobLockService;
    @Mock
    private DistributedConcurrencyLimiter concurrencyLimiter;

    @BeforeEach
    void setUp() {
        lenient().when(productService.refreshActivitySnapshotsByStatusPartitions(
                        any(DouyinProductGateway.ActivityProductQueryRequest.class),
                        anyInt(),
                        anyInt(),
                        eq(300L),
                        anyInt(),
                        any()))
                .thenReturn(new ProductService.ActivityProductRefreshResult(3, 1, 1, 2, 0));
        lenient().when(productService.refreshActivitySnapshotsByStatusPartitions(
                        any(DouyinProductGateway.ActivityProductQueryRequest.class),
                        anyList(),
                        anyInt(),
                        anyInt(),
                        eq(100L),
                        anyInt(),
                        any()))
                .thenReturn(new ProductService.ActivityProductRefreshResult(50, 1, 20, 30, 0));
        lenient().when(jobLogMapper.markQueuedJobRunning(any(), any(LocalDateTime.class), any()))
                .thenReturn(1);
        lenient().when(jobLogMapper.countQueuedJobs(any())).thenReturn(0);
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
        assertThat(result.syncStatus()).isEqualTo("QUEUED");
        verify(colonelActivityService).syncActivitySummaryFromUpstream("ACT-1", null);
        ArgumentCaptor<DouyinProductGateway.ActivityProductQueryRequest> captor =
                ArgumentCaptor.forClass(DouyinProductGateway.ActivityProductQueryRequest.class);
        verify(productService).refreshActivitySnapshotsByStatusPartitions(
                captor.capture(), eq(3000), eq(50000), eq(300L), eq(2), any());
        assertThat(captor.getValue().activityId()).isEqualTo("ACT-1");
        assertThat(captor.getValue().count()).isEqualTo(20);
        verify(activityMapper).touchLastSyncAt(eq("ACT-1"), any(LocalDateTime.class));
        ArgumentCaptor<ProductSyncJobLog> jobLogCaptor = ArgumentCaptor.forClass(ProductSyncJobLog.class);
        verify(jobLogMapper).insert(jobLogCaptor.capture());
        assertThat(jobLogCaptor.getValue().getJobId()).isEqualTo(result.jobId());
        assertThat(jobLogCaptor.getValue().getStatus()).isEqualTo("SUCCESS");
        assertThat(jobLogCaptor.getValue().getStartedAt()).isNotNull();
        assertThat(jobLogCaptor.getValue().getApiFetchedRows()).isEqualTo(3L);
        verify(jobLogMapper).updateById(any(ProductSyncJobLog.class));
    }

    @Test
    void trigger_shouldReuseQueuedJobWhenSameActivityAlreadyQueued() {
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

        assertThat(first.syncStatus()).isEqualTo("QUEUED");
        assertThat(first.jobId()).startsWith("activity-product-sync-");
        assertThat(second.syncStatus()).isEqualTo("QUEUED");
        assertThat(second.jobId()).isEqualTo(first.jobId());
        assertThat(queuedTasks).hasSize(1);

        queuedTasks.get(0).run();
        ProductActivityManualSyncService.SyncTriggerResult third = service.trigger("ACT-1", null);
        assertThat(third.syncStatus()).isEqualTo("QUEUED");
        assertThat(third.jobId()).isNotEqualTo(first.jobId());
        assertThat(queuedTasks).hasSize(2);
    }

    @Test
    void trigger_shouldNotTouchLastSyncAtWhenRefreshIsIncomplete() {
        when(productService.refreshActivitySnapshotsByStatusPartitions(
                        any(DouyinProductGateway.ActivityProductQueryRequest.class),
                        anyInt(),
                        anyInt(),
                        eq(300L),
                        anyInt(),
                        any()))
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

        assertThat(result.syncStatus()).isEqualTo("QUEUED");
        verify(activityMapper, never()).touchLastSyncAt(eq("ACT-1"), any(LocalDateTime.class));
        ArgumentCaptor<ProductSyncJobLog> jobLogCaptor = ArgumentCaptor.forClass(ProductSyncJobLog.class);
        verify(jobLogMapper).updateById(jobLogCaptor.capture());
        assertThat(jobLogCaptor.getValue().getStatus()).isEqualTo("PARTIAL");
    }

    @Test
    void trigger_shouldPassConfiguredStatusPartitionParallelismToRefresh() {
        ProductActivityManualSyncService service = new ProductActivityManualSyncService(
                productService,
                colonelActivityService,
                activityMapper,
                jobLogMapper,
                Runnable::run);
        ReflectionTestUtils.setField(service, "manualStatusPartitionParallelism", 3);

        service.trigger("ACT-1", null);

        verify(productService).refreshActivitySnapshotsByStatusPartitions(
                any(DouyinProductGateway.ActivityProductQueryRequest.class),
                eq(3000),
                eq(50000),
                eq(300L),
                eq(3),
                any());
    }

    @Test
    void trigger_shouldRunPrioritySyncWithRequestedRowsAndStatuses() {
        when(productService.refreshActivitySnapshotsByStatusPartitions(
                        any(DouyinProductGateway.ActivityProductQueryRequest.class),
                        anyList(),
                        anyInt(),
                        anyInt(),
                        eq(100L),
                        anyInt(),
                        any()))
                .thenReturn(new ProductService.ActivityProductRefreshResult(50, 1, 20, 30, 0));
        ProductActivityManualSyncService service = new ProductActivityManualSyncService(
                productService,
                colonelActivityService,
                activityMapper,
                jobLogMapper,
                Runnable::run);

        ProductActivityManualSyncService.SyncTriggerResult result = service.trigger(
                "ACT-1",
                null,
                null,
                new ProductActivityManualSyncService.SyncOptions("PRIORITY_1000", 1000, List.of(0, 1)));

        assertThat(result.syncStatus()).isEqualTo("QUEUED");
        ArgumentCaptor<DouyinProductGateway.ActivityProductQueryRequest> requestCaptor =
                ArgumentCaptor.forClass(DouyinProductGateway.ActivityProductQueryRequest.class);
        verify(productService).refreshActivitySnapshotsByStatusPartitions(
                requestCaptor.capture(),
                eq(List.of(0, 1)),
                eq(3000),
                eq(1000),
                eq(100L),
                eq(2),
                any());
        assertThat(requestCaptor.getValue().status()).isNull();
        verify(productService, never()).refreshActivitySnapshots(
                any(DouyinProductGateway.ActivityProductQueryRequest.class),
                anyInt(),
                anyInt(),
                anyLong(),
                any());
    }

    @Test
    void trigger_shouldKeepDifferentActivityQueuedWhenUpstreamStartIntervalIsNotReached() {
        ProductActivityManualSyncService service = new ProductActivityManualSyncService(
                productService,
                colonelActivityService,
                activityMapper,
                jobLogMapper,
                Runnable::run);
        ReflectionTestUtils.setField(service, "manualUpstreamMinStartIntervalMs", 10_000L);

        ProductActivityManualSyncService.SyncTriggerResult first = service.trigger("ACT-1", null);
        ProductActivityManualSyncService.SyncTriggerResult second = service.trigger("ACT-2", null);

        assertThat(first.syncStatus()).isEqualTo("QUEUED");
        assertThat(second.syncStatus()).isEqualTo("QUEUED");
        verify(productService, times(1)).refreshActivitySnapshotsByStatusPartitions(
                any(DouyinProductGateway.ActivityProductQueryRequest.class),
                anyInt(),
                anyInt(),
                anyLong(),
                anyInt(),
                any());
    }

    @Test
    void trigger_shouldRunWhenGlobalBackfillLockIsHeldButActivityLockIsAvailable() {
        when(jobLockService.tryAcquire(
                eq(JobLockKeys.productBackfillActivityLock("ACT-1")),
                any(java.time.Duration.class),
                any(String.class))).thenReturn(true);
        ProductActivityManualSyncService service = new ProductActivityManualSyncService(
                productService,
                colonelActivityService,
                activityMapper,
                jobLogMapper,
                jobLockService,
                null,
                Runnable::run);

        ProductActivityManualSyncService.SyncTriggerResult result = service.trigger("ACT-1", null);

        assertThat(result.activityId()).isEqualTo("ACT-1");
        assertThat(result.jobId()).startsWith("activity-product-sync-");
        assertThat(result.syncStatus()).isEqualTo("QUEUED");
        verify(jobLogMapper).insert(any(ProductSyncJobLog.class));
        verify(jobLogMapper).updateById(any(ProductSyncJobLog.class));
        verify(productService).refreshActivitySnapshotsByStatusPartitions(
                any(DouyinProductGateway.ActivityProductQueryRequest.class),
                anyInt(),
                anyInt(),
                anyLong(),
                anyInt(),
                any());
        verify(colonelActivityService).syncActivitySummaryFromUpstream("ACT-1", null);
        verify(jobLockService, never()).tryAcquire(
                eq(JobLockKeys.PRODUCT_BACKFILL_GLOBAL),
                any(java.time.Duration.class),
                any(String.class));
    }

    @Test
    void trigger_shouldStayQueuedWhenDistributedConcurrencySlotIsUnavailable() {
        when(jobLockService.tryAcquire(
                eq(JobLockKeys.productBackfillActivityLock("ACT-1")),
                any(java.time.Duration.class),
                any(String.class))).thenReturn(true);
        when(concurrencyLimiter.tryAcquire(any(String.class), any(java.time.Duration.class))).thenReturn(false);
        ProductActivityManualSyncService service = new ProductActivityManualSyncService(
                productService,
                colonelActivityService,
                activityMapper,
                jobLogMapper,
                jobLockService,
                null,
                Runnable::run,
                concurrencyLimiter);

        ProductActivityManualSyncService.SyncTriggerResult result = service.trigger("ACT-1", null);

        assertThat(result.syncStatus()).isEqualTo("QUEUED");
        verify(productService, never()).refreshActivitySnapshotsByStatusPartitions(
                any(DouyinProductGateway.ActivityProductQueryRequest.class),
                anyInt(),
                anyInt(),
                anyLong(),
                anyInt(),
                any());
        verify(jobLockService).releaseWithOwner(
                eq(JobLockKeys.productBackfillActivityLock("ACT-1")), any(String.class));
    }

    @Test
    void trigger_shouldReturnQueueFullWithoutCreatingJobWhenManualQueueLimitReached() {
        when(jobLogMapper.countQueuedJobs("activity_product_manual_sync")).thenReturn(100);
        ProductActivityManualSyncService service = new ProductActivityManualSyncService(
                productService,
                colonelActivityService,
                activityMapper,
                jobLogMapper,
                Runnable::run);

        ProductActivityManualSyncService.SyncTriggerResult result = service.trigger("ACT-1", null);

        assertThat(result.activityId()).isEqualTo("ACT-1");
        assertThat(result.jobId()).isNull();
        assertThat(result.syncStatus()).isEqualTo("QUEUE_FULL");
        assertThat(result.message()).contains("队列已满");
        verify(jobLogMapper, never()).insert(any(ProductSyncJobLog.class));
        verify(productService, never()).refreshActivitySnapshotsByStatusPartitions(
                any(DouyinProductGateway.ActivityProductQueryRequest.class),
                anyInt(),
                anyInt(),
                anyLong(),
                anyInt(),
                any());
    }

    @Test
    void trigger_shouldReuseActiveJobWhenDatabaseUniqueConstraintRejectsDuplicateInsert() {
        List<Runnable> queuedTasks = new ArrayList<>();
        ProductSyncJobLog activeJob = new ProductSyncJobLog();
        activeJob.setJobId("activity-product-sync-existing");
        activeJob.setJobType("activity_product_manual_sync");
        activeJob.setScope("ACTIVITY:ACT-1");
        activeJob.setStatus("QUEUED");
        when(jobLogMapper.insert(any(ProductSyncJobLog.class)))
                .thenThrow(new DuplicateKeyException("duplicate active sync"));
        when(jobLogMapper.selectLatestActiveByJobTypeAndScope(
                "activity_product_manual_sync",
                "ACTIVITY:ACT-1"))
                .thenReturn(null, activeJob);
        ProductActivityManualSyncService service = new ProductActivityManualSyncService(
                productService,
                colonelActivityService,
                activityMapper,
                jobLogMapper,
                queuedTasks::add);

        ProductActivityManualSyncService.SyncTriggerResult result = service.trigger("ACT-1", null);

        assertThat(result.activityId()).isEqualTo("ACT-1");
        assertThat(result.jobId()).isEqualTo("activity-product-sync-existing");
        assertThat(result.syncStatus()).isEqualTo("QUEUED");
        assertThat(queuedTasks).hasSize(1);
        verify(productService, never()).refreshActivitySnapshotsByStatusPartitions(
                any(DouyinProductGateway.ActivityProductQueryRequest.class),
                anyInt(),
                anyInt(),
                anyLong(),
                anyInt(),
                any());
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
