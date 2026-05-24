package com.colonel.saas.job;

import com.colonel.saas.service.DistributedJobLockService;
import com.colonel.saas.service.PerformanceBackfillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@ConditionalOnProperty(name = "performance.recalculate-failed.enabled", havingValue = "true")
public class PerformanceRecalculateFailedJob {

    private static final Duration LOCK_TTL = Duration.ofMinutes(10);

    private final PerformanceBackfillService performanceBackfillService;
    private final DistributedJobLockService jobLockService;

    @Value("${performance.recalculate-failed.limit:100}")
    private int limit;

    public PerformanceRecalculateFailedJob(
            PerformanceBackfillService performanceBackfillService,
            DistributedJobLockService jobLockService) {
        this.performanceBackfillService = performanceBackfillService;
        this.jobLockService = jobLockService;
    }

    @Scheduled(cron = "${performance.recalculate-failed.cron:0 */10 * * * ?}")
    public void recalculateFailed() {
        if (!jobLockService.tryAcquire(JobLockKeys.PERFORMANCE_RECALCULATE_FAILED, LOCK_TTL)) {
            log.info("PerformanceRecalculateFailedJob skipped, another process is running");
            return;
        }
        try {
            PerformanceBackfillService.BackfillResult result = performanceBackfillService.backfill(
                    null,
                    null,
                    null,
                    limit,
                    true);
            log.info(
                    "PerformanceRecalculateFailedJob completed, scanned={}, upserted={}, failed={}",
                    result.scanned(),
                    result.upserted(),
                    result.failed());
        } catch (Exception ex) {
            log.error("PerformanceRecalculateFailedJob failed", ex);
        } finally {
            jobLockService.release(JobLockKeys.PERFORMANCE_RECALCULATE_FAILED);
        }
    }
}
