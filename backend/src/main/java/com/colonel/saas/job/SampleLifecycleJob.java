package com.colonel.saas.job;

import com.colonel.saas.service.DistributedJobLockService;
import com.colonel.saas.service.SampleLifecycleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
public class SampleLifecycleJob {

    private static final Duration LOCK_TTL = Duration.ofMinutes(30);

    private final SampleLifecycleService sampleLifecycleService;
    private final DistributedJobLockService jobLockService;

    public SampleLifecycleJob(
            SampleLifecycleService sampleLifecycleService,
            DistributedJobLockService jobLockService) {
        this.sampleLifecycleService = sampleLifecycleService;
        this.jobLockService = jobLockService;
    }

    @Scheduled(cron = "0 0 2 * * ?")
    public void autoCloseTimeoutRequests() {
        if (!jobLockService.tryAcquire(JobLockKeys.SAMPLE_LIFECYCLE, LOCK_TTL)) {
            log.info("SampleLifecycleJob skipped, another process is running");
            return;
        }
        try {
            try {
                int closedHomework = sampleLifecycleService.autoCloseTimeoutPendingHomework();
                log.info("SampleLifecycleJob auto close homework completed, closed={}", closedHomework);
            } catch (Exception ex) {
                log.error("SampleLifecycleJob auto close homework failed", ex);
            }
            try {
                int closedShip = sampleLifecycleService.autoCloseTimeoutPendingShip();
                log.info("SampleLifecycleJob auto close ship completed, closed={}", closedShip);
            } catch (Exception ex) {
                log.error("SampleLifecycleJob auto close ship failed", ex);
            }
        } finally {
            jobLockService.release(JobLockKeys.SAMPLE_LIFECYCLE);
        }
    }
}
