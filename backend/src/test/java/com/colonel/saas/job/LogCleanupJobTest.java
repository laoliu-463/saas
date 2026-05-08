package com.colonel.saas.job;

import com.colonel.saas.service.OperationLogService;
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
class LogCleanupJobTest {

    @Mock
    private OperationLogService operationLogService;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;

    private LogCleanupJob newJob() {
        return new LogCleanupJob(operationLogService, redisTemplate, 90, false);
    }

    private LogCleanupJob newTestJob() {
        return new LogCleanupJob(operationLogService, redisTemplate, 90, true);
    }

    @Test
    void cleanupOldPartitions_shouldCallService() {
        LogCleanupJob job = newJob();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("operation-log:cleanup:job:lock"), eq("1"), any(Duration.class)))
                .thenReturn(true);
        when(operationLogService.cleanupOldPartitions(90)).thenReturn(2);

        job.cleanupOldPartitions();

        verify(operationLogService).cleanupOldPartitions(90);
        verify(redisTemplate).delete("operation-log:cleanup:job:lock");
    }

    @Test
    void cleanupOldPartitions_shouldSkipWhenLockNotAcquired() {
        LogCleanupJob job = newJob();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("operation-log:cleanup:job:lock"), eq("1"), any(Duration.class)))
                .thenReturn(false);

        job.cleanupOldPartitions();

        verify(operationLogService, never()).cleanupOldPartitions(90);
        verify(redisTemplate, never()).delete("operation-log:cleanup:job:lock");
    }

    @Test
    void cleanupOldPartitions_shouldFallbackToLocalLockInTestMode() {
        LogCleanupJob job = newTestJob();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("operation-log:cleanup:job:lock"), eq("1"), any(Duration.class)))
                .thenThrow(new RedisCommandExecutionException("redis down"));
        when(operationLogService.cleanupOldPartitions(90)).thenReturn(1);

        job.cleanupOldPartitions();

        verify(operationLogService).cleanupOldPartitions(90);
    }
}
