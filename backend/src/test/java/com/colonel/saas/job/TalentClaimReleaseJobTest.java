package com.colonel.saas.job;

import com.colonel.saas.domain.talent.application.TalentClaimReleaseApplicationService;
import com.colonel.saas.service.DistributedJobLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TalentClaimReleaseJobTest {

    private static final Duration LOCK_TTL = Duration.ofMinutes(30);

    @Mock
    private TalentClaimReleaseApplicationService talentClaimReleaseApplicationService;
    @Mock
    private DistributedJobLockService jobLockService;

    @BeforeEach
    void grantLock() {
        lenient().when(jobLockService.tryAcquire(JobLockKeys.TALENT_CLAIM_RELEASE, LOCK_TTL)).thenReturn(true);
    }

    @Test
    void releaseExpiredClaimsDaily_shouldDelegateWithCurrentTime() {
        TalentClaimReleaseJob job = new TalentClaimReleaseJob(talentClaimReleaseApplicationService, jobLockService);
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        job.releaseExpiredClaimsDaily();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(talentClaimReleaseApplicationService).releaseExpiredClaims(captor.capture());
        assertThat(captor.getValue()).isAfterOrEqualTo(before);
        assertThat(captor.getValue()).isBeforeOrEqualTo(LocalDateTime.now().plusSeconds(1));
        verify(jobLockService).tryAcquire(JobLockKeys.TALENT_CLAIM_RELEASE, LOCK_TTL);
        verify(jobLockService).release(JobLockKeys.TALENT_CLAIM_RELEASE);
    }

    @Test
    void releaseExpiredClaimsDaily_shouldPropagateFailureAndReleaseLock() {
        RuntimeException failure = new RuntimeException("release failed");
        doThrow(failure).when(talentClaimReleaseApplicationService).releaseExpiredClaims(any(LocalDateTime.class));
        TalentClaimReleaseJob job = new TalentClaimReleaseJob(talentClaimReleaseApplicationService, jobLockService);

        RuntimeException thrown = assertThrows(RuntimeException.class, job::releaseExpiredClaimsDaily);

        assertThat(thrown).isSameAs(failure);
        verify(talentClaimReleaseApplicationService).releaseExpiredClaims(any(LocalDateTime.class));
        verify(jobLockService).release(JobLockKeys.TALENT_CLAIM_RELEASE);
    }

    @Test
    void releaseExpiredClaimsDaily_shouldSkipWhenLockNotAcquired() {
        when(jobLockService.tryAcquire(JobLockKeys.TALENT_CLAIM_RELEASE, LOCK_TTL)).thenReturn(false);
        TalentClaimReleaseJob job = new TalentClaimReleaseJob(talentClaimReleaseApplicationService, jobLockService);

        job.releaseExpiredClaimsDaily();

        verify(talentClaimReleaseApplicationService, never()).releaseExpiredClaims(any());
        verify(jobLockService, never()).release(JobLockKeys.TALENT_CLAIM_RELEASE);
    }
}
