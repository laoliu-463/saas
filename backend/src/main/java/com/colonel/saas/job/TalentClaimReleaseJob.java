package com.colonel.saas.job;

import com.colonel.saas.service.DistributedJobLockService;
import com.colonel.saas.service.TalentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Component
public class TalentClaimReleaseJob {

    private static final Duration LOCK_TTL = Duration.ofMinutes(30);

    private final TalentService talentService;
    private final DistributedJobLockService jobLockService;

    public TalentClaimReleaseJob(TalentService talentService, DistributedJobLockService jobLockService) {
        this.talentService = talentService;
        this.jobLockService = jobLockService;
    }

    @Scheduled(cron = "0 15 2 * * ?")
    public void releaseExpiredClaimsDaily() {
        if (!jobLockService.tryAcquire(JobLockKeys.TALENT_CLAIM_RELEASE, LOCK_TTL)) {
            log.info("TalentClaimReleaseJob skipped, another process is running");
            return;
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            talentService.releaseExpiredClaims(now);
            log.info("Talent claim release job completed at {}", now);
        } finally {
            jobLockService.release(JobLockKeys.TALENT_CLAIM_RELEASE);
        }
    }
}
