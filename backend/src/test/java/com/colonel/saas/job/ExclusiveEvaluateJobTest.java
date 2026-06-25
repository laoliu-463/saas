package com.colonel.saas.job;

import com.colonel.saas.domain.talent.application.ExclusiveTalentApplicationService;
import com.colonel.saas.service.DistributedJobLockService;
import com.colonel.saas.service.ExclusiveMerchantService;
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
class ExclusiveEvaluateJobTest {

    @Mock
    private ExclusiveTalentApplicationService exclusiveTalentApplicationService;
    @Mock
    private ExclusiveMerchantService exclusiveMerchantService;
    @Mock
    private DistributedJobLockService jobLockService;

    private ExclusiveEvaluateJob job;

    @BeforeEach
    void setUp() {
        job = new ExclusiveEvaluateJob(exclusiveTalentApplicationService, exclusiveMerchantService, jobLockService);
        lenient().when(jobLockService.tryAcquire(any(), any(Duration.class))).thenReturn(true);
    }

    private ExclusiveEvaluateJob enabledJob() {
        return new ExclusiveEvaluateJob(exclusiveTalentApplicationService, exclusiveMerchantService, jobLockService, true);
    }

    @Test
    void evaluateTalentMonthly_shouldSkipByDefaultWhenExclusiveFeatureDisabled() {
        job.evaluateTalentMonthly();

        verify(jobLockService, never()).tryAcquire(eq(JobLockKeys.EXCLUSIVE_TALENT_EVALUATE), any(Duration.class));
        verify(exclusiveTalentApplicationService, never()).evaluatePreviousMonthAndApplyCurrentMonth();
    }

    @Test
    void evaluateTalentMonthly_shouldCallService() {
        job = enabledJob();
        when(exclusiveTalentApplicationService.evaluatePreviousMonthAndApplyCurrentMonth()).thenReturn(5);

        job.evaluateTalentMonthly();

        verify(exclusiveTalentApplicationService).evaluatePreviousMonthAndApplyCurrentMonth();
        verify(jobLockService).release(JobLockKeys.EXCLUSIVE_TALENT_EVALUATE);
    }

    @Test
    void evaluateTalentMonthly_shouldSkipWhenLockNotAcquired() {
        job = enabledJob();
        when(jobLockService.tryAcquire(eq(JobLockKeys.EXCLUSIVE_TALENT_EVALUATE), any(Duration.class))).thenReturn(false);

        job.evaluateTalentMonthly();

        verify(exclusiveTalentApplicationService, never()).evaluatePreviousMonthAndApplyCurrentMonth();
    }

    @Test
    void evaluateTalentMonthly_shouldCatchException() {
        job = enabledJob();
        when(exclusiveTalentApplicationService.evaluatePreviousMonthAndApplyCurrentMonth())
                .thenThrow(new RuntimeException("eval failed"));

        job.evaluateTalentMonthly();

        verify(exclusiveTalentApplicationService).evaluatePreviousMonthAndApplyCurrentMonth();
    }

    @Test
    void evaluateMerchantMonthly_shouldCallService() {
        job = enabledJob();
        when(exclusiveMerchantService.evaluatePreviousMonthAndApplyCurrentMonth()).thenReturn(3);

        job.evaluateMerchantMonthly();

        verify(exclusiveMerchantService).evaluatePreviousMonthAndApplyCurrentMonth();
    }

    @Test
    void evaluateMerchantMonthly_shouldCatchException() {
        job = enabledJob();
        when(exclusiveMerchantService.evaluatePreviousMonthAndApplyCurrentMonth())
                .thenThrow(new RuntimeException("eval failed"));

        job.evaluateMerchantMonthly();

        verify(exclusiveMerchantService).evaluatePreviousMonthAndApplyCurrentMonth();
    }
}
