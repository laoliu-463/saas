package com.colonel.saas.job;

import com.colonel.saas.service.SampleLifecycleService;
import io.lettuce.core.RedisCommandExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SampleLifecycleJobTest {

    @Mock
    private SampleLifecycleService sampleLifecycleService;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;

    private SampleLifecycleJob newJob() {
        return new SampleLifecycleJob(sampleLifecycleService, redisTemplate, false);
    }

    private SampleLifecycleJob newTestJob() {
        return new SampleLifecycleJob(sampleLifecycleService, redisTemplate, true);
    }

    @Test
    void autoCloseTimeoutRequests_shouldCallService() {
        SampleLifecycleJob job = newJob();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("sample:lifecycle:job:lock"), eq("1"), any(Duration.class)))
                .thenReturn(true);
        when(sampleLifecycleService.autoCloseTimeoutPendingHomework()).thenReturn(7);
        when(sampleLifecycleService.autoCloseTimeoutPendingShip()).thenReturn(3);

        job.autoCloseTimeoutRequests();

        verify(sampleLifecycleService).autoCloseTimeoutPendingHomework();
        verify(sampleLifecycleService).autoCloseTimeoutPendingShip();
        verify(redisTemplate).delete("sample:lifecycle:job:lock");
    }

    @Test
    void autoCloseTimeoutRequests_shouldCatchException() {
        SampleLifecycleJob job = newJob();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("sample:lifecycle:job:lock"), eq("1"), any(Duration.class)))
                .thenReturn(true);
        when(sampleLifecycleService.autoCloseTimeoutPendingHomework())
                .thenThrow(new RuntimeException("db error"));
        when(sampleLifecycleService.autoCloseTimeoutPendingShip())
                .thenThrow(new RuntimeException("db error"));

        job.autoCloseTimeoutRequests();

        verify(sampleLifecycleService).autoCloseTimeoutPendingHomework();
        verify(sampleLifecycleService).autoCloseTimeoutPendingShip();
    }

    @Test
    void autoCloseTimeoutRequests_shouldSkipWhenLockNotAcquired() {
        SampleLifecycleJob job = newJob();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("sample:lifecycle:job:lock"), eq("1"), any(Duration.class)))
                .thenReturn(false);

        job.autoCloseTimeoutRequests();

        verify(sampleLifecycleService, never()).autoCloseTimeoutPendingHomework();
        verify(sampleLifecycleService, never()).autoCloseTimeoutPendingShip();
        verify(redisTemplate, never()).delete("sample:lifecycle:job:lock");
    }

    @Test
    void autoCloseTimeoutRequests_shouldFallbackToLocalLockInTestMode() {
        SampleLifecycleJob job = newTestJob();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("sample:lifecycle:job:lock"), eq("1"), any(Duration.class)))
                .thenThrow(new RedisCommandExecutionException("redis down"));
        when(sampleLifecycleService.autoCloseTimeoutPendingHomework()).thenReturn(1);
        when(sampleLifecycleService.autoCloseTimeoutPendingShip()).thenReturn(2);

        job.autoCloseTimeoutRequests();

        verify(sampleLifecycleService).autoCloseTimeoutPendingHomework();
        verify(sampleLifecycleService).autoCloseTimeoutPendingShip();
    }
}
