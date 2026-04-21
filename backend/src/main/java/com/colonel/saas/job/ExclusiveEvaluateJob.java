package com.colonel.saas.job;

import com.colonel.saas.service.ExclusiveMerchantService;
import com.colonel.saas.service.ExclusiveTalentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ExclusiveEvaluateJob {

    private final ExclusiveTalentService exclusiveTalentService;
    private final ExclusiveMerchantService exclusiveMerchantService;

    public ExclusiveEvaluateJob(
            ExclusiveTalentService exclusiveTalentService,
            ExclusiveMerchantService exclusiveMerchantService) {
        this.exclusiveTalentService = exclusiveTalentService;
        this.exclusiveMerchantService = exclusiveMerchantService;
    }

    @Scheduled(cron = "0 0 3 1 * ?")
    public void evaluateTalentMonthly() {
        try {
            int upserted = exclusiveTalentService.evaluatePreviousMonthAndApplyCurrentMonth();
            log.info("Exclusive talent evaluate completed, upserted={}", upserted);
        } catch (Exception ex) {
            log.error("Exclusive talent evaluate failed", ex);
        }
    }

    @Scheduled(cron = "0 30 3 1 * ?")
    public void evaluateMerchantMonthly() {
        try {
            int upserted = exclusiveMerchantService.evaluatePreviousMonthAndApplyCurrentMonth();
            log.info("Exclusive merchant evaluate completed, upserted={}", upserted);
        } catch (Exception ex) {
            log.error("Exclusive merchant evaluate failed", ex);
        }
    }
}
