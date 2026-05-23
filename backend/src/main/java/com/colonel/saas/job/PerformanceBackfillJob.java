package com.colonel.saas.job;

import com.colonel.saas.service.DistributedJobLockService;
import com.colonel.saas.service.PerformanceBackfillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
public class PerformanceBackfillJob {

    private static final Duration LOCK_TTL = Duration.ofMinutes(30);
    private static final int NIGHTLY_LIMIT = 500;

    private final PerformanceBackfillService performanceBackfillService;
    private final DistributedJobLockService jobLockService;

    public PerformanceBackfillJob(
            PerformanceBackfillService performanceBackfillService,
            DistributedJobLockService jobLockService) {
        this.performanceBackfillService = performanceBackfillService;
        this.jobLockService = jobLockService;
    }

    @Scheduled(cron = "0 30 3 * * ?")
    public void backfillMissingPerformanceRecords() {
        if (!jobLockService.tryAcquire(JobLockKeys.PERFORMANCE_BACKFILL, LOCK_TTL)) {
            log.info("PerformanceBackfillJob skipped, another process is running");
            return;
        }
        try {
            PerformanceBackfillService.BackfillResult result = performanceBackfillService.backfill(
                    null,
                    null,
                    null,
                    NIGHTLY_LIMIT,
                    true);
            log.info(
                    "PerformanceBackfillJob completed, scanned={}, upserted={}, failed={}, onlyMissing={}",
                    result.scanned(),
                    result.upserted(),
                    result.failed(),
                    result.onlyMissing());
        } catch (Exception ex) {
            log.error("PerformanceBackfillJob failed", ex);
        } finally {
            jobLockService.release(JobLockKeys.PERFORMANCE_BACKFILL);
        }
    }
}
