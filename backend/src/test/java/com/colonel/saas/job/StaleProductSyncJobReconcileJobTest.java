package com.colonel.saas.job;

import com.colonel.saas.entity.ProductSyncJobLog;
import com.colonel.saas.mapper.ProductSyncJobLogMapper;
import com.colonel.saas.service.DistributedConcurrencyLimiter;
import com.colonel.saas.service.DistributedJobLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 4-1.5 stale RUNNING 清理任务测试。
 */
@ExtendWith(MockitoExtension.class)
class StaleProductSyncJobReconcileJobTest {

    @Mock private ProductSyncJobLogMapper jobLogMapper;
    @Mock private DistributedJobLockService jobLockService;
    @Mock private DistributedConcurrencyLimiter concurrencyLimiter;

    private StaleProductSyncJobReconcileJob job;

    @BeforeEach
    void setUp() {
        job = new StaleProductSyncJobReconcileJob(jobLogMapper, jobLockService, 30, concurrencyLimiter);
    }

    @Test
    void reconcile_lockHeldByOther_shouldSkipAndNotTouchJobs() {
        when(jobLockService.tryAcquire(eq(JobLockKeys.PRODUCT_BACKFILL_GLOBAL), any(Duration.class))).thenReturn(false);
        when(jobLogMapper.selectStaleRunningJobs(any())).thenReturn(List.of());

        job.reconcile();

        verify(jobLogMapper).selectStaleRunningJobs(any());
        verify(jobLogMapper, never()).abandonStaleRunningJob(any(), any());
    }

    @Test
    void reconcile_staleManualJob_shouldAbandonAndReleaseActivityLockAndSlot() {
        String jobId = "activity-product-sync-stale";
        String activityId = "ACT-1";
        String owner = "manual:" + jobId + ":activity:" + activityId;
        ProductSyncJobLog stale = new ProductSyncJobLog();
        stale.setId(UUID.randomUUID());
        stale.setJobId(jobId);
        stale.setJobType("activity_product_manual_sync");
        stale.setScope("ACTIVITY:" + activityId);
        stale.setStatus("RUNNING");
        stale.setStartedAt(LocalDateTime.now().minusHours(1));

        when(jobLockService.tryAcquire(eq(JobLockKeys.PRODUCT_BACKFILL_GLOBAL), any(Duration.class)))
                .thenReturn(false);
        when(jobLogMapper.selectStaleRunningJobs(any())).thenReturn(List.of(stale));
        when(jobLogMapper.abandonStaleRunningJob(eq(stale.getId()), any(LocalDateTime.class))).thenReturn(1);

        job.reconcile();

        verify(jobLogMapper).selectStaleRunningJobs(any());
        verify(jobLogMapper).abandonStaleRunningJob(eq(stale.getId()), any(LocalDateTime.class));
        verify(jobLockService).releaseWithOwner(JobLockKeys.productBackfillActivityLock(activityId), owner);
        verify(concurrencyLimiter).release(owner);
        verify(jobLockService, never()).releaseWithOwner(JobLockKeys.PRODUCT_BACKFILL_GLOBAL, owner);
    }

    @Test
    void reconcile_staleJobsExist_shouldMarkEachAsAbandoned() {
        when(jobLockService.tryAcquire(eq(JobLockKeys.PRODUCT_BACKFILL_GLOBAL), any(Duration.class))).thenReturn(true);
        ProductSyncJobLog stale = new ProductSyncJobLog();
        stale.setId(UUID.randomUUID());
        stale.setJobId("product-backfill-stale-1");
        stale.setStatus("RUNNING");
        stale.setStartedAt(LocalDateTime.now().minusHours(1));
        when(jobLogMapper.selectStaleRunningJobs(any())).thenReturn(List.of(stale));
        when(jobLogMapper.abandonStaleRunningJob(any(), any())).thenReturn(1);

        job.reconcile();

        verify(jobLogMapper, times(1)).abandonStaleRunningJob(eq(stale.getId()), any());
        verify(jobLockService).release(JobLockKeys.PRODUCT_BACKFILL_GLOBAL);
    }

    @Test
    void reconcile_noStaleJobs_shouldNotCallAbandon() {
        when(jobLockService.tryAcquire(eq(JobLockKeys.PRODUCT_BACKFILL_GLOBAL), any(Duration.class))).thenReturn(true);
        when(jobLogMapper.selectStaleRunningJobs(any())).thenReturn(List.of());

        job.reconcile();

        verify(jobLogMapper, never()).abandonStaleRunningJob(any(), any());
        verify(jobLockService).release(JobLockKeys.PRODUCT_BACKFILL_GLOBAL);
    }
}
