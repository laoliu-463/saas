package com.colonel.saas.job;

import com.colonel.saas.service.DistributedJobLockService;
import com.colonel.saas.service.ProductPinService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
public class ProductPinCleanupJob {

    private static final Duration LOCK_TTL = Duration.ofMinutes(20);

    private final ProductPinService productPinService;
    private final DistributedJobLockService jobLockService;

    public ProductPinCleanupJob(
            ProductPinService productPinService,
            DistributedJobLockService jobLockService) {
        this.productPinService = productPinService;
        this.jobLockService = jobLockService;
    }

    @Scheduled(cron = "0 */10 * * * ?")
    public void cleanupExpiredPins() {
        if (!jobLockService.tryAcquire(JobLockKeys.PRODUCT_PIN_EXPIRE, LOCK_TTL)) {
            log.info("ProductPinCleanupJob skipped, another process is running");
            return;
        }
        try {
            log.info("ProductPinCleanupJob started");
            int expired = productPinService.expirePinnedProducts();
            log.info("ProductPinCleanupJob completed, expiredPins={}", expired);
        } catch (Exception ex) {
            log.warn("ProductPinCleanupJob failed", ex);
        } finally {
            jobLockService.release(JobLockKeys.PRODUCT_PIN_EXPIRE);
        }
    }
}
