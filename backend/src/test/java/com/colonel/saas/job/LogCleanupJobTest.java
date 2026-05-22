package com.colonel.saas.job;

import com.colonel.saas.service.DistributedJobLockService;
import com.colonel.saas.service.OperationLogService;
import io.lettuce.core.RedisCommandExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogCleanupJobTest {

    @Mock
    private OperationLogService operationLogService;
    @Mock
    private DistributedJobLockService jobLockService;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;

    @BeforeEach
    void grantLock() {
        lenient().when(jobLockService.tryAcquire(eq(JobLockKeys.LOG_CLEANUP), any(Duration.class))).thenReturn(true);
    }

    private LogCleanupJob newJob() {
        return new LogCleanupJob(operationLogService, jobLockService, 90);
    }

    @Test
    void cleanupOldPartitions_shouldCallService() {
        LogCleanupJob job = newJob();
        when(operationLogService.cleanupOldPartitions(90)).thenReturn(2);

        job.cleanupOldPartitions();

        verify(operationLogService).cleanupOldPartitions(90);
        verify(jobLockService).release(JobLockKeys.LOG_CLEANUP);
    }

    @Test
    void cleanupOldPartitions_shouldSkipWhenLockNotAcquired() {
        LogCleanupJob job = newJob();
        when(jobLockService.tryAcquire(eq(JobLockKeys.LOG_CLEANUP), any(Duration.class))).thenReturn(false);

        job.cleanupOldPartitions();

        verify(operationLogService, never()).cleanupOldPartitions(90);
        verify(jobLockService, never()).release(JobLockKeys.LOG_CLEANUP);
    }

    @Test
    void cleanupOldPartitions_shouldFallbackToLocalLockInTestMode() {
        DistributedJobLockService realLockService = new DistributedJobLockService(redisTemplate, true);
        LogCleanupJob job = new LogCleanupJob(operationLogService, realLockService, 90);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(JobLockKeys.LOG_CLEANUP), eq("1"), any(Duration.class)))
                .thenThrow(new RedisCommandExecutionException("redis down"));
        when(operationLogService.cleanupOldPartitions(90)).thenReturn(1);

        job.cleanupOldPartitions();

        verify(operationLogService).cleanupOldPartitions(90);
    }
}
