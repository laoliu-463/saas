package com.colonel.saas.job;

import com.colonel.saas.service.ExclusiveMerchantService;
import com.colonel.saas.service.ExclusiveTalentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExclusiveEvaluateJobTest {

    @Mock
    private ExclusiveTalentService exclusiveTalentService;

    @Mock
    private ExclusiveMerchantService exclusiveMerchantService;

    @Test
    void evaluateTalentMonthly_shouldCallService() {
        ExclusiveEvaluateJob job = new ExclusiveEvaluateJob(exclusiveTalentService, exclusiveMerchantService);
        when(exclusiveTalentService.evaluatePreviousMonthAndApplyCurrentMonth()).thenReturn(5);

        job.evaluateTalentMonthly();

        verify(exclusiveTalentService).evaluatePreviousMonthAndApplyCurrentMonth();
    }

    @Test
    void evaluateTalentMonthly_shouldCatchException() {
        ExclusiveEvaluateJob job = new ExclusiveEvaluateJob(exclusiveTalentService, exclusiveMerchantService);
        when(exclusiveTalentService.evaluatePreviousMonthAndApplyCurrentMonth())
                .thenThrow(new RuntimeException("eval failed"));

        job.evaluateTalentMonthly();

        verify(exclusiveTalentService).evaluatePreviousMonthAndApplyCurrentMonth();
    }

    @Test
    void evaluateMerchantMonthly_shouldCallService() {
        ExclusiveEvaluateJob job = new ExclusiveEvaluateJob(exclusiveTalentService, exclusiveMerchantService);
        when(exclusiveMerchantService.evaluatePreviousMonthAndApplyCurrentMonth()).thenReturn(3);

        job.evaluateMerchantMonthly();

        verify(exclusiveMerchantService).evaluatePreviousMonthAndApplyCurrentMonth();
    }

    @Test
    void evaluateMerchantMonthly_shouldCatchException() {
        ExclusiveEvaluateJob job = new ExclusiveEvaluateJob(exclusiveTalentService, exclusiveMerchantService);
        when(exclusiveMerchantService.evaluatePreviousMonthAndApplyCurrentMonth())
                .thenThrow(new RuntimeException("eval failed"));

        job.evaluateMerchantMonthly();

        verify(exclusiveMerchantService).evaluatePreviousMonthAndApplyCurrentMonth();
    }
}
