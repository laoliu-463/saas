package com.colonel.saas.job;

import com.colonel.saas.domain.talent.application.TalentClaimReleaseApplicationService;
import com.colonel.saas.service.DistributedJobLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TalentClaimReleaseJobTest {

    @Mock
    private TalentClaimReleaseApplicationService talentClaimReleaseApplicationService;
    @Mock
    private DistributedJobLockService jobLockService;

    @BeforeEach
    void grantLock() {
        lenient().when(jobLockService.tryAcquire(eq(JobLockKeys.TALENT_CLAIM_RELEASE), any(Duration.class))).thenReturn(true);
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
        verify(jobLockService).release(JobLockKeys.TALENT_CLAIM_RELEASE);
    }

    @Test
    void releaseExpiredClaimsDaily_shouldSkipWhenLockNotAcquired() {
        when(jobLockService.tryAcquire(eq(JobLockKeys.TALENT_CLAIM_RELEASE), any(Duration.class))).thenReturn(false);
        TalentClaimReleaseJob job = new TalentClaimReleaseJob(talentClaimReleaseApplicationService, jobLockService);

        job.releaseExpiredClaimsDaily();

        verify(talentClaimReleaseApplicationService, never()).releaseExpiredClaims(any());
    }

    @Test
    void releaseExpiredClaimsDaily_shouldUseAsiaShanghaiSchedule() throws NoSuchMethodException {
        Method method = TalentClaimReleaseJob.class.getDeclaredMethod("releaseExpiredClaimsDaily");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("0 15 2 * * ?");
        assertThat(scheduled.zone()).isEqualTo("Asia/Shanghai");
    }
}
