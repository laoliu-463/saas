package com.colonel.saas.job;

import com.colonel.saas.service.TalentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TalentWeeklyRefreshJobTest {

    @Mock
    private TalentService talentService;

    private TalentWeeklyRefreshJob newJob() {
        Executor directExecutor = Runnable::run;
        return new TalentWeeklyRefreshJob(talentService, directExecutor, 2);
    }

    @Test
    void weeklyRefreshActiveTalents_shouldSkipWhenNoActiveTalents() {
        TalentWeeklyRefreshJob job = newJob();
        when(talentService.findActiveTalentIdsForRefresh()).thenReturn(List.of());

        job.weeklyRefreshActiveTalents();

        verify(talentService).findActiveTalentIdsForRefresh();
        verify(talentService, never()).refresh(org.mockito.ArgumentMatchers.any(UUID.class));
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
