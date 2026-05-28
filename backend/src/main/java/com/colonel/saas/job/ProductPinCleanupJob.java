package com.colonel.saas.job;

import com.colonel.saas.service.DistributedJobLockService;
import com.colonel.saas.service.ProductPinService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 商品置顶过期自动清除定时任务。
 * <p>
 * 每 10 分钟执行一次，扫描所有已超过置顶截止时间（{@code pinnedUntil}）的
 * 置顶商品记录，自动解除其置顶状态，使商品恢复正常排序展示。
 * </p>
 * <p>
 * 调度策略：
 * <ul>
 *   <li>Cron：{@code 0 0/10 * * * ?}（每 10 分钟整点执行）</li>
 *   <li>分布式锁 TTL：20 分钟</li>
 *   <li>与 {@link ProductDisplayRuleJob} 配合，清除置顶后由展示规则任务对账状态一致性</li>
 * </ul>
 * </p>
 *
 * @see ProductPinService#expirePinnedProducts()
 * @see PinnedProductVO
 * @see JobLockKeys#PRODUCT_PIN_EXPIRE
 */
@Slf4j
@Component
public class ProductPinCleanupJob {

    /** 分布式锁 TTL */
    private static final Duration LOCK_TTL = Duration.ofMinutes(20);

    /** 商品置顶服务 */
    private final ProductPinService productPinService;
    /** 分布式锁服务 */
    private final DistributedJobLockService jobLockService;

    public ProductPinCleanupJob(
            ProductPinService productPinService,
            DistributedJobLockService jobLockService) {
        this.productPinService = productPinService;
        this.jobLockService = jobLockService;
    }

    /**
     * 清除所有已过期的商品置顶记录。
     * <p>
     * 返回本次清除的置顶记录数量。
     * </p>
     */
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
