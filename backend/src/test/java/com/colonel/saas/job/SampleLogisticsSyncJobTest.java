package com.colonel.saas.job;

import com.colonel.saas.config.LogisticsProperties;
import com.colonel.saas.service.DistributedJobLockService;
import com.colonel.saas.service.SampleLogisticsSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SampleLogisticsSyncJobTest {

    @Mock
    private SampleLogisticsSyncService sampleLogisticsSyncService;
    @Mock
    private DistributedJobLockService jobLockService;

    private LogisticsProperties logisticsProperties;

    @BeforeEach
    void setUp() {
        logisticsProperties = new LogisticsProperties();
        logisticsProperties.getSync().setEnabled(true);
        logisticsProperties.getSync().setBatchSize(25);
        lenient().when(jobLockService.tryAcquire(eq(JobLockKeys.LOGISTICS_TRACK), any(Duration.class)))
                .thenReturn(true);
    }

    @Test
    void syncInTransitSamples_shouldSkipWhenSyncDisabled() {
        logisticsProperties.getSync().setEnabled(false);
        SampleLogisticsSyncJob job = newJob(false);

        job.syncInTransitSamples();

        verify(jobLockService, never()).tryAcquire(any(), any());
        verify(sampleLogisticsSyncService, never()).syncPendingInTransit(anyInt());
    }

    @Test
    void syncInTransitSamples_shouldSkipWhenTestModeEnabled() {
        SampleLogisticsSyncJob job = newJob(true);

        job.syncInTransitSamples();

        verify(jobLockService, never()).tryAcquire(any(), any());
        verify(sampleLogisticsSyncService, never()).syncPendingInTransit(anyInt());
    }

    @Test
    void syncInTransitSamples_shouldSkipWhenLockNotAcquired() {
        when(jobLockService.tryAcquire(eq(JobLockKeys.LOGISTICS_TRACK), any(Duration.class))).thenReturn(false);
        SampleLogisticsSyncJob job = newJob(false);

        job.syncInTransitSamples();

        verify(sampleLogisticsSyncService, never()).syncPendingInTransit(anyInt());
        verify(jobLockService, never()).release(JobLockKeys.LOGISTICS_TRACK);
    }

    @Test
    void syncInTransitSamples_shouldSyncAndReleaseLock() {
        when(sampleLogisticsSyncService.syncPendingInTransit(25))
                .thenReturn(new SampleLogisticsSyncService.SyncBatchSummary(4, 2, 1, 1));
        SampleLogisticsSyncJob job = newJob(false);

        job.syncInTransitSamples();

        verify(sampleLogisticsSyncService).syncPendingInTransit(25);
        verify(jobLockService).release(JobLockKeys.LOGISTICS_TRACK);
    }

    @Test
    void syncInTransitSamples_shouldReleaseLockWhenServiceThrows() {
        when(sampleLogisticsSyncService.syncPendingInTransit(25))
                .thenThrow(new IllegalStateException("db down"));
        SampleLogisticsSyncJob job = newJob(false);

        assertThatThrownBy(job::syncInTransitSamples)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("db down");
        verify(jobLockService).release(JobLockKeys.LOGISTICS_TRACK);
    }

    private SampleLogisticsSyncJob newJob(boolean testEnabled) {
        return new SampleLogisticsSyncJob(
                sampleLogisticsSyncService,
                jobLockService,
                logisticsProperties,
                testEnabled);
    }
}
