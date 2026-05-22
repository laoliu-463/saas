package com.colonel.saas.job;

import com.colonel.saas.service.DistributedJobLockService;
import com.colonel.saas.service.SampleLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SampleLifecycleJobTest {

    @Mock
    private SampleLifecycleService sampleLifecycleService;
    @Mock
    private DistributedJobLockService jobLockService;

    @BeforeEach
    void grantLock() {
        lenient().when(jobLockService.tryAcquire(eq(JobLockKeys.SAMPLE_LIFECYCLE), any(Duration.class))).thenReturn(true);
    }

    private SampleLifecycleJob newJob() {
        return new SampleLifecycleJob(sampleLifecycleService, jobLockService);
    }

    @Test
    void autoCloseTimeoutRequests_shouldCallService() {
        SampleLifecycleJob job = newJob();
        when(sampleLifecycleService.autoCloseTimeoutPendingHomework()).thenReturn(7);
        when(sampleLifecycleService.autoCloseTimeoutPendingShip()).thenReturn(3);

        job.autoCloseTimeoutRequests();

        verify(sampleLifecycleService).autoCloseTimeoutPendingHomework();
        verify(sampleLifecycleService).autoCloseTimeoutPendingShip();
        verify(jobLockService).release(JobLockKeys.SAMPLE_LIFECYCLE);
    }

    @Test
    void autoCloseTimeoutRequests_shouldCatchException() {
        SampleLifecycleJob job = newJob();
        when(sampleLifecycleService.autoCloseTimeoutPendingHomework())
                .thenThrow(new RuntimeException("db error"));
        when(sampleLifecycleService.autoCloseTimeoutPendingShip())
                .thenThrow(new RuntimeException("db error"));

        job.autoCloseTimeoutRequests();

        verify(sampleLifecycleService).autoCloseTimeoutPendingHomework();
        verify(sampleLifecycleService).autoCloseTimeoutPendingShip();
        verify(jobLockService).release(JobLockKeys.SAMPLE_LIFECYCLE);
    }

    @Test
    void autoCloseTimeoutRequests_shouldSkipWhenLockNotAcquired() {
        SampleLifecycleJob job = newJob();
        when(jobLockService.tryAcquire(eq(JobLockKeys.SAMPLE_LIFECYCLE), any(Duration.class))).thenReturn(false);

        job.autoCloseTimeoutRequests();

        verify(sampleLifecycleService, never()).autoCloseTimeoutPendingHomework();
        verify(sampleLifecycleService, never()).autoCloseTimeoutPendingShip();
        verify(jobLockService, never()).release(JobLockKeys.SAMPLE_LIFECYCLE);
    }
}
