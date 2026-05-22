package com.colonel.saas.job;

import com.colonel.saas.service.DistributedJobLockService;
import com.colonel.saas.service.OperationLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
public class LogCleanupJob {

    private static final Duration LOCK_TTL = Duration.ofMinutes(30);

    private final OperationLogService operationLogService;
    private final DistributedJobLockService jobLockService;
    private final int retentionDays;

    public LogCleanupJob(
            OperationLogService operationLogService,
            DistributedJobLockService jobLockService,
            @Value("${operation.log.retention-days:90}") int retentionDays) {
        this.operationLogService = operationLogService;
        this.jobLockService = jobLockService;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${operation.log.cleanup.cron:0 30 2 * * ?}")
    public void cleanupOldPartitions() {
        if (!jobLockService.tryAcquire(JobLockKeys.LOG_CLEANUP, LOCK_TTL)) {
            log.info("LogCleanupJob skipped, another process is running");
            return;
        }
        try {
            int dropped = operationLogService.cleanupOldPartitions(retentionDays);
            log.info("LogCleanupJob completed, droppedPartitions={}, retentionDays={}", dropped, retentionDays);
        } catch (Exception ex) {
            log.error("LogCleanupJob failed", ex);
        } finally {
            jobLockService.release(JobLockKeys.LOG_CLEANUP);
        }
    }
}
