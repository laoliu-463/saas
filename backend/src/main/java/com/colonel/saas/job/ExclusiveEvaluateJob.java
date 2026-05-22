package com.colonel.saas.job;

import com.colonel.saas.service.DistributedJobLockService;
import com.colonel.saas.service.ExclusiveMerchantService;
import com.colonel.saas.service.ExclusiveTalentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
public class ExclusiveEvaluateJob {

    private static final Duration LOCK_TTL = Duration.ofHours(1);

    private final ExclusiveTalentService exclusiveTalentService;
    private final ExclusiveMerchantService exclusiveMerchantService;
    private final DistributedJobLockService jobLockService;

    public ExclusiveEvaluateJob(
            ExclusiveTalentService exclusiveTalentService,
            ExclusiveMerchantService exclusiveMerchantService,
            DistributedJobLockService jobLockService) {
        this.exclusiveTalentService = exclusiveTalentService;
        this.exclusiveMerchantService = exclusiveMerchantService;
        this.jobLockService = jobLockService;
    }

    @Scheduled(cron = "0 0 3 1 * ?")
    public void evaluateTalentMonthly() {
        if (!jobLockService.tryAcquire(JobLockKeys.EXCLUSIVE_TALENT_EVALUATE, LOCK_TTL)) {
            log.info("Exclusive talent evaluate job skipped, another process is running");
            return;
        }
        try {
            int upserted = exclusiveTalentService.evaluatePreviousMonthAndApplyCurrentMonth();
            log.info("Exclusive talent evaluate completed, upserted={}", upserted);
        } catch (Exception ex) {
            log.error("Exclusive talent evaluate failed", ex);
        } finally {
            jobLockService.release(JobLockKeys.EXCLUSIVE_TALENT_EVALUATE);
        }
    }

    @Scheduled(cron = "0 30 3 1 * ?")
    public void evaluateMerchantMonthly() {
        if (!jobLockService.tryAcquire(JobLockKeys.EXCLUSIVE_MERCHANT_EVALUATE, LOCK_TTL)) {
            log.info("Exclusive merchant evaluate job skipped, another process is running");
            return;
        }
        try {
            int upserted = exclusiveMerchantService.evaluatePreviousMonthAndApplyCurrentMonth();
            log.info("Exclusive merchant evaluate completed, upserted={}", upserted);
        } catch (Exception ex) {
            log.error("Exclusive merchant evaluate failed", ex);
        } finally {
            jobLockService.release(JobLockKeys.EXCLUSIVE_MERCHANT_EVALUATE);
        }
    }
}
