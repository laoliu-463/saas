package com.colonel.saas.job;

import com.colonel.saas.service.DistributedJobLockService;
import com.colonel.saas.service.ProductDisplayRuleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
public class ProductDisplayRuleJob {

    private static final Duration LOCK_TTL = Duration.ofMinutes(55);

    private final ProductDisplayRuleService displayRuleService;
    private final DistributedJobLockService jobLockService;

    public ProductDisplayRuleJob(
            ProductDisplayRuleService displayRuleService,
            DistributedJobLockService jobLockService) {
        this.displayRuleService = displayRuleService;
        this.jobLockService = jobLockService;
    }

    @Scheduled(cron = "0 15 * * * ?")
    public void reconcileDisplayStatus() {
        if (!jobLockService.tryAcquire(JobLockKeys.PRODUCT_DISPLAY_RULE, LOCK_TTL)) {
            log.info("ProductDisplayRuleJob skipped, another process is running");
            return;
        }
        try {
            int processed = displayRuleService.reconcileAll();
            log.info("ProductDisplayRuleJob completed, processedProductIds={}", processed);
        } catch (Exception ex) {
            log.error("ProductDisplayRuleJob failed", ex);
        } finally {
            jobLockService.release(JobLockKeys.PRODUCT_DISPLAY_RULE);
        }
    }
}
