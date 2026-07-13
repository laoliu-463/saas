package com.colonel.saas.job;

import com.colonel.saas.service.DistributedJobLockService;
import com.colonel.saas.service.ProductDisplayRuleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 商品展示状态规则对账定时任务。
 * <p>
 * 每小时的第 15 分钟执行，遍历所有商品并根据展示规则（如置顶状态、活动关联、
 * 过期时间等）重新校验商品的展示状态，确保商品列表的展示状态与业务规则一致。
 * </p>
 * <p>
 * 对账场景：
 * <ul>
 *   <li>置顶到期后商品应从置顶位移除</li>
 *   <li>活动结束后关联商品的展示状态应更新</li>
 *   <li>手动修改与自动规则不一致时以规则为准</li>
 * </ul>
 * </p>
 * <p>
 * 调度策略：
 * <ul>
 *   <li>Cron：{@code 0 15 * * * ?}（每小时第 15 分钟）</li>
 *   <li>分布式锁 TTL：55 分钟，确保在下一个小时窗口前完成或超时释放</li>
 * </ul>
 * </p>
 *
 * @see ProductDisplayRuleService#reconcileAll()
 * @see JobLockKeys#PRODUCT_DISPLAY_REFRESH
 */
@Slf4j
@Component
public class ProductDisplayRuleJob {

    /** 分布式锁 TTL，55 分钟确保在下一个调度周期前释放 */
    private static final Duration LOCK_TTL = Duration.ofMinutes(55);

    /** 商品展示规则服务 */
    private final ProductDisplayRuleService displayRuleService;
    /** 分布式锁服务 */
    private final DistributedJobLockService jobLockService;

    public ProductDisplayRuleJob(
            ProductDisplayRuleService displayRuleService,
            DistributedJobLockService jobLockService) {
        this.displayRuleService = displayRuleService;
        this.jobLockService = jobLockService;
    }

    /**
     * 对账所有商品的展示状态。
     * <p>
     * 遍历商品并根据最新规则校正展示状态，返回本次处理的商品 ID 数量。
     * </p>
     */
    @Scheduled(cron = "0 15 * * * ?")
    public void reconcileDisplayStatus() {
        String owner = "display-rule:" + Thread.currentThread().getId() + ":" + System.nanoTime();
        if (!jobLockService.tryAcquire(JobLockKeys.PRODUCT_BACKFILL_GLOBAL, LOCK_TTL, owner)) {
            log.info("ProductDisplayRuleJob skipped, product backfill global lock held");
            return;
        }
        if (!jobLockService.tryAcquire(JobLockKeys.PRODUCT_DISPLAY_REFRESH, LOCK_TTL, owner)) {
            log.info("ProductDisplayRuleJob skipped, another process is running");
            jobLockService.releaseWithOwner(JobLockKeys.PRODUCT_BACKFILL_GLOBAL, owner);
            return;
        }
        try {
            int processed = displayRuleService.reconcileAll();
            log.info("ProductDisplayRuleJob completed, processedProductIds={}", processed);
        } catch (Exception ex) {
            log.error("ProductDisplayRuleJob failed", ex);
        } finally {
            jobLockService.releaseWithOwner(JobLockKeys.PRODUCT_DISPLAY_REFRESH, owner);
            jobLockService.releaseWithOwner(JobLockKeys.PRODUCT_BACKFILL_GLOBAL, owner);
        }
    }
}
