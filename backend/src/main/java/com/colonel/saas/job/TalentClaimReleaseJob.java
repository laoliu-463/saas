package com.colonel.saas.job;

import com.colonel.saas.service.TalentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
public class TalentClaimReleaseJob {

    private final TalentService talentService;

    public TalentClaimReleaseJob(TalentService talentService) {
        this.talentService = talentService;
    }

    @Scheduled(cron = "0 15 2 * * ?")
    public void releaseExpiredClaimsDaily() {
        LocalDateTime now = LocalDateTime.now();
        talentService.releaseExpiredClaims(now);
        log.info("Talent claim release job completed at {}", now);
    }
}
