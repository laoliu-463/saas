package com.colonel.saas.job;

import com.colonel.saas.service.SampleLifecycleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SampleLifecycleJobTest {

    @Mock
    private SampleLifecycleService sampleLifecycleService;

    @Test
    void autoCloseTimeoutRequests_shouldCallService() {
        SampleLifecycleJob job = new SampleLifecycleJob(sampleLifecycleService);
        when(sampleLifecycleService.autoCloseTimeoutPendingHomework(30)).thenReturn(7);

        job.autoCloseTimeoutRequests();

        verify(sampleLifecycleService).autoCloseTimeoutPendingHomework(30);
    }

    @Test
    void autoCloseTimeoutRequests_shouldCatchException() {
        SampleLifecycleJob job = new SampleLifecycleJob(sampleLifecycleService);
        when(sampleLifecycleService.autoCloseTimeoutPendingHomework(30))
                .thenThrow(new RuntimeException("db error"));

        job.autoCloseTimeoutRequests();

        verify(sampleLifecycleService).autoCloseTimeoutPendingHomework(30);
    }
}
