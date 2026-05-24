package com.colonel.saas.job;

import com.colonel.saas.config.LogisticsProperties;
import com.colonel.saas.service.DistributedJobLockService;
import com.colonel.saas.service.SampleLogisticsSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
public class SampleLogisticsSyncJob {

    private static final Duration LOCK_TTL = Duration.ofMinutes(30);

    private final SampleLogisticsSyncService sampleLogisticsSyncService;
    private final DistributedJobLockService jobLockService;
    private final LogisticsProperties logisticsProperties;
    private final boolean testEnabled;

    public SampleLogisticsSyncJob(
            SampleLogisticsSyncService sampleLogisticsSyncService,
            DistributedJobLockService jobLockService,
            LogisticsProperties logisticsProperties,
            @Value("${app.test.enabled:false}") boolean testEnabled) {
        this.sampleLogisticsSyncService = sampleLogisticsSyncService;
        this.jobLockService = jobLockService;
        this.logisticsProperties = logisticsProperties;
        this.testEnabled = testEnabled;
    }

    @Scheduled(cron = "${logistics.sync.cron:0 */30 * * * ?}")
    public void syncInTransitSamples() {
        if (!logisticsProperties.getSync().isEnabled()) {
            return;
        }
        if (testEnabled) {
            return;
        }
        if (!jobLockService.tryAcquire(JobLockKeys.LOGISTICS_TRACK, LOCK_TTL)) {
            log.info("SampleLogisticsSyncJob skipped, lock held");
            return;
        }
        try {
            SampleLogisticsSyncService.SyncBatchSummary summary =
                    sampleLogisticsSyncService.syncPendingInTransit(logisticsProperties.getSync().getBatchSize());
            log.info("SampleLogisticsSyncJob done: total={}, success={}, failed={}, skipped={}",
                    summary.total(), summary.success(), summary.failed(), summary.skipped());
        } finally {
            jobLockService.release(JobLockKeys.LOGISTICS_TRACK);
        }
    }
}
