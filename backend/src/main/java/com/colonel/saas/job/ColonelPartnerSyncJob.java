package com.colonel.saas.job;

import com.colonel.saas.service.ColonelPartnerSyncService;
import com.colonel.saas.service.DistributedJobLockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
public class ColonelPartnerSyncJob {

    private static final Duration LOCK_TTL = Duration.ofMinutes(25);

    private final ColonelPartnerSyncService colonelPartnerSyncService;
    private final DistributedJobLockService jobLockService;

    public ColonelPartnerSyncJob(
            ColonelPartnerSyncService colonelPartnerSyncService,
            DistributedJobLockService jobLockService) {
        this.colonelPartnerSyncService = colonelPartnerSyncService;
        this.jobLockService = jobLockService;
    }

    @Scheduled(cron = "0 30 * * * ?")
    public void syncPartners() {
        if (!jobLockService.tryAcquire(JobLockKeys.COLONEL_PARTNER_SYNC, LOCK_TTL)) {
            log.info("ColonelPartnerSyncJob skipped, another process is running");
            return;
        }
        try {
            int upserted = colonelPartnerSyncService.syncAll();
            log.info("ColonelPartnerSyncJob completed, upserted={}", upserted);
        } catch (Exception ex) {
            log.error("ColonelPartnerSyncJob failed", ex);
        } finally {
            jobLockService.release(JobLockKeys.COLONEL_PARTNER_SYNC);
        }
    }
}
