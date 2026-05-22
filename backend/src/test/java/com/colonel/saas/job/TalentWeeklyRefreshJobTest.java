package com.colonel.saas.job;

import com.colonel.saas.service.DistributedJobLockService;
import com.colonel.saas.service.TalentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TalentWeeklyRefreshJobTest {

    @Mock
    private TalentService talentService;
    @Mock
    private DistributedJobLockService jobLockService;

    @BeforeEach
    void grantLock() {
        lenient().when(jobLockService.tryAcquire(eq(JobLockKeys.TALENT_WEEKLY_REFRESH), any(Duration.class))).thenReturn(true);
    }

    private TalentWeeklyRefreshJob newJob() {
        Executor directExecutor = Runnable::run;
        return new TalentWeeklyRefreshJob(talentService, jobLockService, directExecutor, 2);
    }

    @Test
    void weeklyRefreshActiveTalents_shouldSkipWhenNoActiveTalents() {
        TalentWeeklyRefreshJob job = newJob();
        when(talentService.findActiveTalentIdsForRefresh()).thenReturn(List.of());

        job.weeklyRefreshActiveTalents();

        verify(talentService).findActiveTalentIdsForRefresh();
        verify(talentService, never()).refresh(any(UUID.class));
    }

    @Test
    void weeklyRefreshActiveTalents_shouldSkipWhenLockNotAcquired() {
        when(jobLockService.tryAcquire(eq(JobLockKeys.TALENT_WEEKLY_REFRESH), any(Duration.class))).thenReturn(false);
        TalentWeeklyRefreshJob job = newJob();

        job.weeklyRefreshActiveTalents();

        verify(talentService, never()).findActiveTalentIdsForRefresh();
    }

    @Test
    void weeklyRefreshActiveTalents_shouldContinueWhenSingleTalentFails() {
        TalentWeeklyRefreshJob job = newJob();
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(talentService.findActiveTalentIdsForRefresh()).thenReturn(List.of(id1, id2));
        when(talentService.refresh(id1)).thenThrow(new RuntimeException("refresh failed"));

        job.weeklyRefreshActiveTalents();

        verify(talentService).refresh(id1);
        verify(talentService).refresh(id2);
    }

    @Test
    void weeklyRefreshActiveTalents_shouldRefreshAcrossMultipleBatches() {
        TalentWeeklyRefreshJob job = newJob();
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();
        when(talentService.findActiveTalentIdsForRefresh()).thenReturn(List.of(id1, id2, id3));

        job.weeklyRefreshActiveTalents();

        verify(talentService).refresh(id1);
        verify(talentService).refresh(id2);
        verify(talentService).refresh(id3);
    }
}
