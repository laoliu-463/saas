package com.colonel.saas.job;

import com.colonel.saas.service.OperationLogService;
import io.lettuce.core.RedisCommandExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class LogCleanupJob {

    private static final String JOB_LOCK_KEY = "operation-log:cleanup:job:lock";

    private final OperationLogService operationLogService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final int retentionDays;
    private final boolean testEnabled;
    private final AtomicBoolean localLock = new AtomicBoolean(false);

    public LogCleanupJob(
            OperationLogService operationLogService,
            RedisTemplate<String, Object> redisTemplate,
            @Value("${operation.log.retention-days:90}") int retentionDays,
            @Value("${app.test.enabled:false}") boolean testEnabled) {
        this.operationLogService = operationLogService;
        this.redisTemplate = redisTemplate;
        this.retentionDays = retentionDays;
        this.testEnabled = testEnabled;
    }

    @Scheduled(cron = "${operation.log.cleanup.cron:0 30 2 * * ?}")
    public void cleanupOldPartitions() {
        if (!acquireJobLock()) {
            log.info("LogCleanupJob skipped, another process is running");
            return;
        }
        try {
            int dropped = operationLogService.cleanupOldPartitions(retentionDays);
            log.info("LogCleanupJob completed, droppedPartitions={}, retentionDays={}", dropped, retentionDays);
        } catch (Exception ex) {
            log.error("LogCleanupJob failed", ex);
        } finally {
            releaseJobLock();
        }
    }

    private boolean acquireJobLock() {
        try {
            Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                    Objects.requireNonNull(JOB_LOCK_KEY),
                    "1",
                    Objects.requireNonNull(Duration.ofMinutes(30))
            );
            return Boolean.TRUE.equals(locked);
        } catch (RedisConnectionFailureException | RedisCommandExecutionException ex) {
            if (testEnabled) {
                log.warn("Redis unavailable in test mode when acquiring log cleanup lock, fallback to local lock: {}", ex.getMessage());
                return localLock.compareAndSet(false, true);
            }
            throw ex;
        }
    }

    private void releaseJobLock() {
        localLock.set(false);
        try {
            redisTemplate.delete(JOB_LOCK_KEY);
        } catch (RedisConnectionFailureException | RedisCommandExecutionException ex) {
            if (testEnabled) {
                log.warn("Redis unavailable in test mode when releasing log cleanup lock, local lock already released: {}", ex.getMessage());
                return;
            }
            throw ex;
        }
    }
}
